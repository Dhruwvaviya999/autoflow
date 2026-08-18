package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.automation.processor.FileProcessor
import com.dhruw.autoflow.automation.processor.ProcessingResult
import com.dhruw.autoflow.automation.processor.ProcessorInput
import com.dhruw.autoflow.automation.processor.ZipArchiveReader
import com.dhruw.autoflow.automation.processor.ZipReadException
import com.dhruw.autoflow.instagram.model.InstagramAccount
import com.dhruw.autoflow.instagram.model.InstagramFollowData
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses an Instagram "Download your information" export (ZIP with JSON or
 * HTML data) into follower/following lists. Pure data work: no UI, no
 * network, no credentials. File names inside exports vary by version, so
 * relevant entries are found by heuristics rather than exact paths.
 */
class InstagramDataProcessor : FileProcessor<InstagramFollowData> {

    override val name: String = "Instagram export"

    override suspend fun process(input: ProcessorInput): ProcessingResult<InstagramFollowData> =
        withContext(Dispatchers.Default) {
            try {
                when (input.extension) {
                    "zip" -> processZip(input)
                    "json", "html", "htm" -> processSingleFile(input)
                    else -> ProcessingResult.Failure(
                        "Unsupported file type \".${input.extension}\" — select the Instagram export ZIP."
                    )
                }
            } catch (e: ZipReadException) {
                ProcessingResult.Failure(e.message ?: "Could not read the ZIP file", e)
            } catch (e: Exception) {
                ProcessingResult.Failure("Could not process this file", e)
            }
        }

    private fun processZip(input: ProcessorInput): ProcessingResult<InstagramFollowData> {
        val entries = ZipArchiveReader.readMatching(input.openStream) { isRelevantEntry(it) }
        val followers = mutableListOf<InstagramAccount>()
        val following = mutableListOf<InstagramAccount>()

        for (entry in entries) {
            val accounts = parseAccounts(entry.name, entry.bytes.decodeToString())
            when (sideOf(entry.name)) {
                Side.FOLLOWERS -> followers += accounts
                Side.FOLLOWING -> following += accounts
                null -> Unit
            }
        }

        return buildResult(followers, following, input.name)
    }

    /**
     * A bare followers/following JSON or HTML file. A single file rarely
     * carries both sides; whichever side its name/shape reveals is filled
     * and the result explains what is missing.
     */
    private fun processSingleFile(input: ProcessorInput): ProcessingResult<InstagramFollowData> {
        val text = input.openStream().use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > 128 * 1024 * 1024) {
                return ProcessingResult.Failure("This file is too large to process")
            }
            bytes.decodeToString()
        }
        val accounts = parseAccounts(input.name, text)
        val followers = mutableListOf<InstagramAccount>()
        val following = mutableListOf<InstagramAccount>()
        when (sideOf(input.name) ?: sideOfJsonShape(text)) {
            Side.FOLLOWERS -> followers += accounts
            Side.FOLLOWING -> following += accounts
            null -> Unit
        }
        return buildResult(followers, following, input.name)
    }

    private fun buildResult(
        followers: List<InstagramAccount>,
        following: List<InstagramAccount>,
        source: String
    ): ProcessingResult<InstagramFollowData> {
        val cleanFollowers = UsernameNormalizer.dedupe(followers)
        val cleanFollowing = UsernameNormalizer.dedupe(following)
        return when {
            cleanFollowers.isEmpty() && cleanFollowing.isEmpty() -> ProcessingResult.Failure(
                "Could not find followers/following data in this Instagram export."
            )
            cleanFollowing.isEmpty() -> ProcessingResult.Failure(
                "Found followers but no following data — select the full Instagram export ZIP."
            )
            cleanFollowers.isEmpty() -> ProcessingResult.Failure(
                "Found following but no followers data — select the full Instagram export ZIP."
            )
            else -> ProcessingResult.Success(
                InstagramFollowData(
                    followers = cleanFollowers,
                    following = cleanFollowing,
                    source = source
                )
            )
        }
    }

    // --- Entry heuristics ---

    private enum class Side { FOLLOWERS, FOLLOWING }

    private val blockedTokens = listOf(
        "request", "pending", "recent", "removed", "close_friend", "restricted",
        "blocked", "hide", "unfollowed", "dismiss", "suggestion", "hashtag",
        "favorite", "story"
    )

    private fun baseName(entryName: String): String =
        entryName.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)

    private fun sideOf(entryName: String): Side? {
        val base = baseName(entryName)
        if (blockedTokens.any { base.contains(it) }) return null
        val stem = base.substringBeforeLast('.')
        return when {
            stem.startsWith("followers") -> Side.FOLLOWERS
            stem.startsWith("following") -> Side.FOLLOWING
            else -> null
        }
    }

    private fun isRelevantEntry(entryName: String): Boolean {
        val base = baseName(entryName)
        val ext = base.substringAfterLast('.', "")
        return (ext == "json" || ext == "html" || ext == "htm") && sideOf(entryName) != null
    }

    /** For a bare JSON file whose name is unhelpful, the root key gives the side away. */
    private fun sideOfJsonShape(text: String): Side? = try {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) null
        else {
            val root = JSONObject(trimmed)
            when {
                root.keys().asSequence().any { it.contains("following") } -> Side.FOLLOWING
                root.keys().asSequence().any { it.contains("follower") } -> Side.FOLLOWERS
                else -> null
            }
        }
    } catch (e: JSONException) {
        null
    }

    // --- Content parsing ---

    private fun parseAccounts(fileName: String, text: String): List<InstagramAccount> {
        val ext = baseName(fileName).substringAfterLast('.', "")
        return when (ext) {
            "json" -> parseJsonAccounts(text)
            "html", "htm" -> parseHtmlAccounts(text)
            else -> emptyList()
        }
    }

    /**
     * JSON exports wrap accounts in items shaped like
     * `{"string_list_data":[{"href":"https://www.instagram.com/user","value":"user"}]}`,
     * either as a root array (followers_N.json) or under a
     * "relationships_*" key (following.json). Malformed JSON yields an
     * empty list rather than an exception.
     */
    private fun parseJsonAccounts(text: String): List<InstagramAccount> = try {
        val trimmed = text.trimStart()
        val items: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.keys().asSequence()
                    .mapNotNull { key -> root.optJSONArray(key) }
                    .firstOrNull() ?: JSONArray()
            }
            else -> JSONArray()
        }
        val accounts = mutableListOf<InstagramAccount>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val stringList = item.optJSONArray("string_list_data") ?: continue
            for (j in 0 until stringList.length()) {
                val data = stringList.optJSONObject(j) ?: continue
                val href = data.optString("href", "").ifBlank { null }
                val value = data.optString("value", "").ifBlank {
                    href?.trimEnd('/')?.substringAfterLast('/') ?: ""
                }
                if (value.isNotBlank()) {
                    accounts += InstagramAccount(username = value, profileUrl = href)
                }
            }
        }
        accounts
    } catch (e: JSONException) {
        emptyList()
    }

    /** HTML exports carry plain profile links; the username is the URL's path segment. */
    private fun parseHtmlAccounts(text: String): List<InstagramAccount> {
        val regex = Regex("""instagram\.com/([A-Za-z0-9._]{1,64})[/"']""")
        val reserved = setOf("p", "explore", "accounts", "direct", "stories", "reel", "reels")
        return regex.findAll(text)
            .map { it.groupValues[1] }
            .filter { it.lowercase(Locale.ROOT) !in reserved }
            .map { InstagramAccount(username = it, profileUrl = "https://www.instagram.com/$it") }
            .toList()
    }
}
