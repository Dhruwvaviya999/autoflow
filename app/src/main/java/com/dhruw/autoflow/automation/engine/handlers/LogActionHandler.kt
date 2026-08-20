package com.dhruw.autoflow.automation.engine.handlers

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action

/**
 * Writes the message to the execution log. [sink] lets the app layer also
 * mirror it to Logcat without the handler depending on Android.
 */
class LogActionHandler(
    private val sink: (String) -> Unit = {}
) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.LogAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.LogAction
        val message = context.resolveTemplateOrFail(action.message)
        context.log(message)
        sink(message)
    }
}
