package com.dhruw.autoflow.automation.engine

/**
 * A failure explained for the person who built the workflow: what happened,
 * why it might have happened, and what to try next. Nothing here exposes
 * stack traces or internals — Developer Tools is where raw detail belongs.
 */
data class FailureExplanation(
    val headline: String,
    val detail: String,
    val possibleReasons: List<String>,
    val suggestedAction: String?
)

/**
 * Maps the failure messages the engine and handlers produce onto actionable
 * explanations. Matching is on the stable phrases those components emit; an
 * unrecognized message still produces a useful, honest explanation rather
 * than a guess.
 */
object FailureDiagnostics {

    fun explain(rawMessage: String?): FailureExplanation {
        val message = rawMessage?.trim().orEmpty()
        val lower = message.lowercase()

        return when {
            message.isBlank() -> FailureExplanation(
                headline = "The run failed",
                detail = "AutoFlow did not record a reason for this failure.",
                possibleReasons = emptyList(),
                suggestedAction = "Run the automation again to see whether it repeats."
            )

            lower.startsWith("could not find") || lower.startsWith("timed out waiting for") -> {
                FailureExplanation(
                    headline = "An element could not be found",
                    detail = message,
                    possibleReasons = listOf(
                        "The target app's screen changed or is in another language",
                        "The element was not on screen yet — or needed scrolling first",
                        "The selector is too specific"
                    ),
                    suggestedAction = "Open the step and use Test selector to check it against the live screen."
                )
            }

            lower.contains("elements match") -> FailureExplanation(
                headline = "The selector matched more than one element",
                detail = message,
                possibleReasons = listOf(
                    "Several items on screen share the same text or description",
                    "The selector uses only text where a view ID would be unique"
                ),
                suggestedAction = "Add a view ID or pick a specific occurrence in the step's advanced options."
            )

            lower.contains("foreground app changed") -> FailureExplanation(
                headline = "Another app took over the screen",
                detail = message,
                possibleReasons = listOf(
                    "A dialog, call or another app appeared during the run",
                    "The target app closed itself"
                ),
                suggestedAction = "Run it again with the target app in the foreground and the screen unlocked."
            )

            lower.contains("accessibility is not enabled") -> FailureExplanation(
                headline = "Accessibility is off",
                detail = message,
                possibleReasons = listOf(
                    "Accessibility was never enabled for AutoFlow",
                    "Android switched the service off after an app update or restart"
                ),
                suggestedAction = "Open Settings → Permission center → Accessibility Automation and enable it."
            )

            lower.contains("notifications are not allowed") ||
                lower.contains("blocked by the system") -> FailureExplanation(
                headline = "AutoFlow cannot post notifications",
                detail = message,
                possibleReasons = listOf(
                    "The notification permission is denied",
                    "AutoFlow's notifications are turned off in Android settings"
                ),
                suggestedAction = "Allow notifications in Settings → Permission center."
            )

            lower.contains("no file in this run") -> FailureExplanation(
                headline = "This step needed a file",
                detail = message,
                possibleReasons = listOf(
                    "The automation was run manually instead of by its file trigger",
                    "The trigger is not a File trigger"
                ),
                suggestedAction = "Use a File trigger, or remove the file step from this workflow."
            )

            lower.contains("unknown variable") -> FailureExplanation(
                headline = "A variable is not defined",
                detail = message,
                possibleReasons = listOf(
                    "The variable is spelled differently from the Set variable step",
                    "The Set variable step runs after the step that uses it, or inside a branch that did not run"
                ),
                suggestedAction = "Add a Set variable step before this one, or fix the name."
            )

            lower.contains("not available for this trigger") -> FailureExplanation(
                headline = "A variable had no value",
                detail = message,
                possibleReasons = listOf(
                    "The variable belongs to a different trigger type (for example a notification variable in a battery automation)",
                    "The app that sent the notification left that field empty"
                ),
                suggestedAction = "Use a variable this trigger provides, or set a default with a Set variable step."
            )

            lower.contains("already running") -> FailureExplanation(
                headline = "A UI automation was already running",
                detail = message,
                possibleReasons = listOf(
                    "Two automations tried to drive the screen at the same time"
                ),
                suggestedAction = "Wait for the current run to finish, then try again."
            )

            lower.contains("refused") && lower.contains("password") -> FailureExplanation(
                headline = "AutoFlow refused to type into a protected field",
                detail = message,
                possibleReasons = listOf(
                    "The target field is a password, PIN, OTP or payment field"
                ),
                suggestedAction = "This is a safety rule and cannot be turned off. Remove that step."
            )

            lower.contains("exceeded the") && lower.contains("limit") -> FailureExplanation(
                headline = "The run hit a safety limit",
                detail = message,
                possibleReasons = listOf(
                    "The workflow waits for something that never happened",
                    "The workflow has more steps or longer waits than the limit allows"
                ),
                suggestedAction = "Shorten the workflow or reduce its timeouts."
            )

            else -> FailureExplanation(
                headline = "The run failed",
                detail = message,
                possibleReasons = emptyList(),
                suggestedAction = "Use Test automation in the editor to check the configuration."
            )
        }
    }
}
