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
