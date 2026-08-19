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

    /**
     * Fires when another app posts a notification. Generic by design: no
     * application is special-cased. An empty [allowedPackages] means "any
     * app"; [appLabel] is only the display name captured when the user picked
     * an app (package names stay the stable identifier). Blank patterns
     * match everything; non-blank ones are compared with [matchMode],
     * case-insensitively.
     */
    data class NotificationTrigger(
        val allowedPackages: Set<String> = emptySet(),
        val appLabel: String = "",
        val titlePattern: String = "",
        val textPattern: String = "",
        val matchMode: TextMatchMode = TextMatchMode.CONTAINS
    ) : Trigger {

        /** Cheap first-level filter run for every incoming notification. */
        fun matches(event: TriggerPayload.NotificationEvent): Boolean {
            if (allowedPackages.isNotEmpty() && event.packageName !in allowedPackages) return false
            if (titlePattern.isNotBlank() && !matchMode.matches(event.title, titlePattern)) return false
            if (textPattern.isNotBlank() && !matchMode.matches(event.text, textPattern)) return false
            return true
        }
    }
}

val Trigger.displayName: String
    get() = when (this) {
        is Trigger.ManualTrigger -> "Manual"
        is Trigger.TimeTrigger -> "Time"
        is Trigger.FileTrigger -> "File"
        is Trigger.NotificationTrigger -> "Notification"
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
        is Trigger.NotificationTrigger -> buildString {
            append("Notification from ")
            append(
                when {
                    allowedPackages.isEmpty() -> "any app"
                    appLabel.isNotBlank() -> appLabel
                    else -> allowedPackages.first()
                }
            )
            if (titlePattern.isNotBlank()) append(" · title has \"").append(titlePattern.trim()).append("\"")
            if (textPattern.isNotBlank()) append(" · text has \"").append(textPattern.trim()).append("\"")
        }
    }
