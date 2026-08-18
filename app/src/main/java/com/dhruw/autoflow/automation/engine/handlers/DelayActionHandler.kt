package com.dhruw.autoflow.automation.engine.handlers

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action
import kotlinx.coroutines.delay

class DelayActionHandler : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.DelayAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.DelayAction
        if (action.durationMillis < 0) {
            throw ActionExecutionException("Delay duration must not be negative")
        }
        context.log("Waiting ${action.durationMillis} ms")
        delay(action.durationMillis)
    }
}
