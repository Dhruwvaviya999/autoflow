package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.instagram.model.InstagramAnalysisResult
import com.dhruw.autoflow.instagram.model.InstagramFollowData
import java.util.Locale

/**
 * Set-based follower comparison: O(n) over the two lists, no nested loops.
 * The interesting output is following − followers (accounts that don't
 * follow back), plus mutual and followers-only counts.
 */
class InstagramFollowAnalyzer(
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun analyze(data: InstagramFollowData): InstagramAnalysisResult {
        val followers = UsernameNormalizer.dedupe(data.followers)
        val following = UsernameNormalizer.dedupe(data.following)

        val followerKeys = followers.mapTo(HashSet(followers.size)) { key(it.username) }
        val followingKeys = following.mapTo(HashSet(following.size)) { key(it.username) }

        val notFollowingBack = following
            .filter { key(it.username) !in followerKeys }
            .sortedBy { key(it.username) }
        val mutualCount = following.count { key(it.username) in followerKeys }
        val followersOnlyCount = followers.count { key(it.username) !in followingKeys }

        return InstagramAnalysisResult(
            followersCount = followers.size,
            followingCount = following.size,
            notFollowingBack = notFollowingBack,
            mutualCount = mutualCount,
            followersOnlyCount = followersOnlyCount,
            source = data.source,
            analyzedAt = clock()
        )
    }

    private fun key(username: String): String = username.lowercase(Locale.ROOT)
}
