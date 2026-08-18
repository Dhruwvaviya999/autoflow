package com.dhruw.autoflow.automation.model

/**
 * What starts an automation. Extensible: future phases add
 * FileTrigger, NotificationTrigger, AppTrigger, etc.
 */
sealed interface Trigger {

    /** The user explicitly presses Run. */
    data object ManualTrigger : Trigger

    /**
     * A scheduled execution. Pure schedule description — actual
     * scheduling is done by [com.dhruw.autoflow.automation.engine.AutomationScheduler].
     */
    data class TimeTrigger(
        val hour: Int,
        val minute: Int,
        val repeat: Repeat
    ) : Trigger {
        enum class Repeat { ONCE, DAILY }
    }

    /**
     * Fires when a new file matching the filters appears in a user-selected
     * folder. [folderUri] is an opaque storage URI (SAF tree URI on Android);
     * the domain never assumes raw filesystem paths. Blank filter fields
     * match everything.
     */
    data class FileTrigger(
        val folderUri: String,
        val folderLabel: String,
        val namePattern: String = "",
        val extension: String = ""
    ) : Trigger {
        /** ".ZIP" / "zip" / " .Zip " all normalize to "zip". */
        val normalizedExtension: String
            get() = extension.trim().removePrefix(".").lowercase()
    }
}

val Trigger.displayName: String
    get() = when (this) {
        is Trigger.ManualTrigger -> "Manual"
        is Trigger.TimeTrigger -> "Time"
        is Trigger.FileTrigger -> "File"
    }

val Trigger.summary: String
    get() = when (this) {
        is Trigger.ManualTrigger -> "Run it yourself"
        is Trigger.TimeTrigger -> {
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val time = "%d:%02d %s".format(displayHour, minute, amPm)
            when (repeat) {
                Trigger.TimeTrigger.Repeat.ONCE -> "Once · $time"
                Trigger.TimeTrigger.Repeat.DAILY -> "Every day · $time"
            }
        }
        is Trigger.FileTrigger -> buildString {
            append("New file in ").append(folderLabel.ifBlank { "selected folder" })
            if (normalizedExtension.isNotEmpty()) append(" · .").append(normalizedExtension)
            if (namePattern.isNotBlank()) append(" · name has \"").append(namePattern.trim()).append("\"")
        }
    }
