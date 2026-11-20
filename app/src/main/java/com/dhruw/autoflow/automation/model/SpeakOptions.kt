package com.dhruw.autoflow.automation.model

/**
 * Which Android audio stream a Speak step plays through. The choice decides
 * which volume key the user adjusts it with and whether Do Not Disturb
 * silences it: [NOTIFICATION] follows the notification volume and is muted
 * with it, while [ALARM] and [ACCESSIBILITY] stay audible through most
 * profiles — the right pick for a spoken warning.
 */
enum class SpeakStream { NOTIFICATION, ALARM, MUSIC, RING, ACCESSIBILITY }

val SpeakStream.label: String
    get() = when (this) {
        SpeakStream.NOTIFICATION -> "Notification"
        SpeakStream.ALARM -> "Alarm"
        SpeakStream.MUSIC -> "Media"
        SpeakStream.RING -> "Ringer"
        SpeakStream.ACCESSIBILITY -> "Accessibility"
    }

/** What a Speak step does when the engine is already speaking. */
enum class SpeakQueueMode { INTERRUPT, QUEUE }

val SpeakQueueMode.label: String
    get() = when (this) {
        SpeakQueueMode.INTERRUPT -> "Interrupt"
        SpeakQueueMode.QUEUE -> "Wait in queue"
    }

/**
 * Google's engine encodes a gender into some of its voice names
 * (`en-us-x-sfg#male_1-local`) and only an opaque variant code into others
 * (`hi-in-x-hid-local`). Android's Voice API carries no gender field at all,
 * so the name is the only source there is.
 */
private val VOICE_GENDER = Regex("""[#\-_](male|female)_?(\d*)""", RegexOption.IGNORE_CASE)

/**
 * "Male 1" / "Female 2" when [voiceName] carries a gender, else null — for
 * most languages the engine simply does not say.
 */
fun speakVoiceGenderLabel(voiceName: String): String? {
    val match = VOICE_GENDER.find(voiceName) ?: return null
    val gender = match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
    val index = match.groupValues[2]
    return if (index.isEmpty()) gender else "$gender $index"
}

/**
 * Label for one voice in the editor's picker. Falls back to the voice's
 * [position] — its 1-based place in the list being shown — so voices whose
 * names say nothing about them still read distinctly, and in dropdown order.
 */
fun speakVoiceLabel(voiceName: String, position: Int): String =
    speakVoiceGenderLabel(voiceName) ?: "Voice $position"
