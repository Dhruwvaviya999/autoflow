package com.dhruw.autoflow.services.tts

import android.content.Context
import android.media.AudioManager
import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action

/**
 * Speaks a Speak step's text through the shared [TtsEngine]. The engine owns
 * every platform detail; this handler resolves the template, decides whether
 * the phone's profile means staying quiet, and turns a failed utterance into
 * a user-explainable step failure.
 */
class SpeakActionHandler(
    private val appContext: Context,
    private val tts: TtsEngine
) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.SpeakAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.SpeakAction

        val text = context.resolveTemplateOrFail(action.text)
        if (text.isBlank()) {
            context.log("Nothing to speak: the text resolved to blank")
            return
        }
        if (action.skipIfSilent && isSilenced()) {
            context.log("Stayed quiet: the phone is on silent or vibrate")
            return
        }

        tts.speak(
            text = text,
            languageTag = action.languageTag,
            voiceName = action.voiceName,
            pitch = action.pitch,
            speechRate = action.speechRate,
            volume = action.volume,
            stream = action.stream,
            queueMode = action.queueMode,
            waitUntilFinished = action.waitUntilFinished,
            log = context.log
        ).onFailure { failure ->
            throw ActionExecutionException(
                "Could not speak: ${failure.message ?: "text-to-speech failed"}",
                failure
            )
        }

        context.log(
            if (action.waitUntilFinished) "Spoke: \"$text\"" else "Started speaking: \"$text\""
        )
    }

    /** True while the ringer is muted or vibrate-only. */
    private fun isSilenced(): Boolean {
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }
}
