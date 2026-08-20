package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.unwrapped

/**
 * Decides which steps may be retried after a transient failure.
 *
 * The rule is idempotence, not convenience: a step is retryable only when
 * running it twice has the same effect as running it once. Anything that acts
 * on the outside world in a way the user would notice twice — moving or
 * renaming a file, driving another app's UI — is never retried automatically,
 * because a "failure" there may have partially succeeded.
 */
object RetryPolicy {

    fun isRetryable(action: Action): Boolean = when (action.unwrapped) {
        // Safe: re-posting a notification, re-logging, re-reading a file, or
        // re-storing the same notification record produces the same result.
        is Action.ShowNotificationAction,
        is Action.LogAction,
        is Action.SaveNotificationAction,
        is Action.InstagramAnalysisAction,
        is Action.SetVariableAction,
        is Action.DelayAction -> true

        // A copy that failed may have left a partial file; the handler names
        // copies uniquely, so a retry would create a second one.
        is Action.CopyFileAction -> false

        // Consequential or stateful — never retried.
        is Action.MoveFileAction,
        is Action.RenameFileAction,
        is Action.UiAutomationAction,
        is Action.BranchAction,
        is Action.GroupMarker,
        is Action.DisabledAction -> false
    }

    /** Attempts remaining for [action] beyond the first try. */
    fun retriesFor(action: Action): Int =
        if (isRetryable(action)) EngineLimits.MAX_RETRIES else 0
}
