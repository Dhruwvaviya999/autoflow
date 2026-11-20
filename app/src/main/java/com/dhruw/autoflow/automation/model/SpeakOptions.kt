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
 * Names for the voices the picker offers, one each, in the order they are
 * shown. Engine identifiers ("hi-in-x-hid-local") are unusable as choices and
 * a bare index ("Voice 3") is no easier to remember, so each slot gets a
 * short name instead.
 *
 * A name is only a handle: preview a few, remember the one you liked. It says
 * nothing about how that voice sounds, and in particular its own connotation
 * is not evidence of the voice's gender — Android reports no gender, so the
 * pairing is arbitrary. Where the engine's own identifier does reveal one,
 * [speakVoiceLabel] appends it, and that is the part to trust.
 */
private val VOICE_NAMES = listOf(
    "Aarav", "Diya", "Kabir", "Meera", "Rohan",
    "Saanvi", "Vikram", "Anaya", "Arjun", "Priya"
)

/** How many voices the picker offers. */
val MAX_SPEAK_VOICE_CHOICES: Int = VOICE_NAMES.size

/**
 * Google's engine writes a gender into some of its voice names
 * (`en-us-x-sfg#male_1-local`) and only an opaque variant code into others
 * (`hi-in-x-hid-local`). Android's Voice API carries no gender field at all,
 * so the name is the only source there is.
 */
private val VOICE_GENDER = Regex("""[#\-_](male|female)_?\d*""", RegexOption.IGNORE_CASE)

/**
 * "male" / "female" when [voiceName] reveals one, else null — for most
 * languages the engine simply does not say.
 */
fun speakVoiceGender(voiceName: String): String? =
    VOICE_GENDER.find(voiceName)?.groupValues?.get(1)?.lowercase()

/**
 * Label for one voice in the picker. [position] is the voice's 1-based place
 * in the list being shown, which is what picks the name; the list is sorted,
 * so a given voice keeps its name between openings. The gender is appended
 * only when the engine's own name revealed it.
 */
fun speakVoiceLabel(voiceName: String, position: Int): String {
    val name = VOICE_NAMES.getOrNull(position - 1) ?: "Voice $position"
    val gender = speakVoiceGender(voiceName) ?: return name
    return "$name ($gender)"
}
