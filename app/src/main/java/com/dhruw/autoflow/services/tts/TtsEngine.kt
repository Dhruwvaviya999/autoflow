package com.dhruw.autoflow.services.tts

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dhruw.autoflow.automation.engine.EngineLimits
import com.dhruw.autoflow.automation.model.SpeakQueueMode
import com.dhruw.autoflow.automation.model.SpeakStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** A spoken utterance that could not be produced, with a user-readable reason. */
class TtsFailure(message: String) : Exception(message)

/**
 * One app-wide Text-to-Speech engine, built on first use and kept alive.
 *
 * A fresh [TextToSpeech] costs a few hundred milliseconds to initialize and
 * clips its first word, so building one per step made every spoken automation
 * start late. Language, voice, pitch and rate are instance-wide settings, so
 * every entry point here serializes through [mutex]: two automations speaking
 * at once would otherwise read each other's settings. Nothing is ever shut
 * down — the engine lives as long as the process, like the other services in
 * the container.
 */
class TtsEngine(private val context: Context) {

    private val mutex = Mutex()
    private val utterances = AtomicLong()

    private var engine: TextToSpeech? = null
    private var initialized: CompletableDeferred<Boolean>? = null

    /**
     * Speak [text], honoring every option of a Speak step. Returns a failure
     * rather than throwing so the calling handler decides how a dead engine
     * reads in the execution log. [log] carries the soft notes — a missing
     * language, an unavailable voice — that are worth recording but are not
     * reasons to fail the step.
     */
    suspend fun speak(
        text: String,
        languageTag: String = "",
        voiceName: String = "",
        pitch: Float = 1f,
        speechRate: Float = 1f,
        volume: Float = 1f,
        stream: SpeakStream = SpeakStream.NOTIFICATION,
        queueMode: SpeakQueueMode = SpeakQueueMode.INTERRUPT,
        waitUntilFinished: Boolean = true,
        log: (String) -> Unit = {}
    ): Result<Unit> = mutex.withLock {
        val tts = ensureEngine()
            ?: return Result.failure(TtsFailure("no text-to-speech engine is available on this device"))

        applyVoice(tts, languageTag, voiceName, pitch, speechRate, log)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream.androidStreamType)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        val mode = when (queueMode) {
            SpeakQueueMode.INTERRUPT -> TextToSpeech.QUEUE_FLUSH
            SpeakQueueMode.QUEUE -> TextToSpeech.QUEUE_ADD
        }
        val utteranceId = "autoflow-${utterances.incrementAndGet()}"

        if (!waitUntilFinished) {
            // Nothing is listening for the result, and a listener left over
            // from an earlier waiting call must not resume on this one.
            tts.setOnUtteranceProgressListener(null)
            return if (tts.speak(text, mode, params, utteranceId) == TextToSpeech.ERROR) {
                Result.failure(TtsFailure("the engine refused the request"))
            } else {
                Result.success(Unit)
            }
        }

        // A queued utterance waits for whatever is already speaking, so the
        // ceiling covers that wait too; without it a never-delivered callback
        // would hold the mutex and wedge every later Speak step.
        return withTimeoutOrNull(EngineLimits.MAX_SPEAK_MILLIS) {
            awaitUtterance(tts, text, mode, params, utteranceId)
        } ?: run {
            runCatching { tts.stop() }
            Result.failure(TtsFailure("the engine did not finish within ${EngineLimits.MAX_SPEAK_MILLIS / 1000} seconds"))
        }
    }

    /** Cut off whatever is speaking — used when the editor's preview is dismissed. */
    fun stop() {
        runCatching { engine?.stop() }
    }

    private suspend fun ensureEngine(): TextToSpeech? {
        initialized?.let { signal ->
            return if (signal.await()) engine else null
        }
        val signal = CompletableDeferred<Boolean>()
        initialized = signal
        val tts = TextToSpeech(context) { status -> signal.complete(status == TextToSpeech.SUCCESS) }
        engine = tts

        val ok = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { signal.await() } ?: false
        if (!ok) {
            // A half-initialized engine must not be reused, and clearing the
            // signal lets a later step try again (the user may install one).
            runCatching { tts.shutdown() }
            engine = null
            initialized = null
            return null
        }
        return tts
    }

    private fun applyVoice(
        tts: TextToSpeech,
        languageTag: String,
        voiceName: String,
        pitch: Float,
        speechRate: Float,
        log: (String) -> Unit
    ) {
        val locale = if (languageTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(languageTag)
        val result = runCatching { tts.setLanguage(locale) }.getOrNull()
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            log("Text-to-speech has no data for ${locale.toLanguageTag()}; using the system default")
            runCatching { tts.setLanguage(Locale.getDefault()) }
        }

        // A voice carries its own locale, so it is set after the language and
        // deliberately overrides it.
        if (voiceName.isNotBlank()) {
            val voice = runCatching { tts.voices }.getOrNull().orEmpty()
                .firstOrNull { it != null && it.name == voiceName }
            if (voice == null) log("Voice \"$voiceName\" is not installed; using the language default")
            else runCatching { tts.voice = voice }
        }

        tts.setPitch(pitch)
        tts.setSpeechRate(speechRate)
    }

    private suspend fun awaitUtterance(
        tts: TextToSpeech,
        text: String,
        mode: Int,
        params: Bundle,
        wantedId: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        fun finish(result: Result<Unit>) {
            if (continuation.isActive) continuation.resume(result)
        }
        // QUEUE_ADD leaves earlier utterances playing, so every callback is
        // matched against this call's id before it resumes anything.
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == wantedId) finish(Result.success(Unit))
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == wantedId) {
                    finish(Result.failure(TtsFailure("the engine reported an error")))
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == wantedId) {
                    finish(Result.failure(TtsFailure("the engine reported error code $errorCode")))
                }
            }
        })

        if (tts.speak(text, mode, params, wantedId) == TextToSpeech.ERROR) {
            finish(Result.failure(TtsFailure("the engine refused the request")))
        }
        continuation.invokeOnCancellation { runCatching { tts.stop() } }
    }

    private val SpeakStream.androidStreamType: Int
        get() = when (this) {
            SpeakStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            SpeakStream.ALARM -> AudioManager.STREAM_ALARM
            SpeakStream.MUSIC -> AudioManager.STREAM_MUSIC
            SpeakStream.RING -> AudioManager.STREAM_RING
            SpeakStream.ACCESSIBILITY -> AudioManager.STREAM_ACCESSIBILITY
        }

    private companion object {
        /** Long enough for a cold engine, short enough not to stall a run. */
        const val INIT_TIMEOUT_MILLIS = 10_000L
    }
}
