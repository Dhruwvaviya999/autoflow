package com.dhruw.autoflow.automation.engine.handlers

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.engine.TemplateResolver
import com.dhruw.autoflow.automation.model.Action

/**
 * Stores a workflow-local variable in the run's context. The value template
 * is resolved first, so a variable can be built from payload data or earlier
 * variables. The stored value is logged; if that ever carries user-sensitive
 * content it is because the user explicitly put it there.
 */
class SetVariableActionHandler : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.SetVariableAction

    override suspend fun execute(action: Action, context: ActionContext) {
        val set = action as Action.SetVariableAction
        val name = set.name.trim()
        if (!TemplateResolver.VALID_LOCAL_NAME.matches(name)) {
            throw ActionExecutionException(
                "\"$name\" is not a valid variable name — use letters, digits and _, starting with a letter"
            )
        }
        val value = when (val r = context.resolveTemplate(set.value)) {
            is TemplateResolver.Result.Ok -> r.text
            is TemplateResolver.Result.UnknownVariable ->
                throw ActionExecutionException("Unknown variable {{${r.variable}}}")
            is TemplateResolver.Result.Unavailable ->
                throw ActionExecutionException("Variable {{${r.variable}}} not available for this trigger")
        }
        context.variables[name] = value
        context.log("$name = $value")
    }
}
