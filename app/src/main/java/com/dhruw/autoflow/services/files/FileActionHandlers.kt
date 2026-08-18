package com.dhruw.autoflow.services.files

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.TriggerPayload

/**
 * Handlers for the file actions. They act on the run's triggering file and
 * convert every storage failure into an [ActionExecutionException], so the
 * engine records a clean FAILED execution instead of crashing.
 */

private fun ActionContext.requireFile(): TriggerPayload.FileEvent =
    fileEvent ?: throw ActionExecutionException(
        "No file in this run — file actions need a File trigger"
    )

class CopyFileActionHandler(private val fileAccess: FileAccess) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.CopyFileAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.CopyFileAction
        val file = context.requireFile()
        try {
            val newName = fileAccess.copyTo(file.uri, file.name, action.destinationFolderUri)
            context.log("Copied \"${file.name}\" to ${action.destinationLabel} as \"$newName\"")
        } catch (e: FileAccessException) {
            throw ActionExecutionException(e.message ?: "Copy failed", e)
        }
    }
}

class MoveFileActionHandler(private val fileAccess: FileAccess) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.MoveFileAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.MoveFileAction
        val file = context.requireFile()
        try {
            val newName = fileAccess.moveTo(file.uri, file.name, action.destinationFolderUri)
            context.log("Moved \"${file.name}\" to ${action.destinationLabel} as \"$newName\"")
        } catch (e: FileAccessException) {
            throw ActionExecutionException(e.message ?: "Move failed", e)
        }
    }
}

class RenameFileActionHandler(private val fileAccess: FileAccess) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.RenameFileAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.RenameFileAction
        val file = context.requireFile()
        try {
            val finalName = fileAccess.rename(file.uri, action.newName)
            context.log("Renamed \"${file.name}\" to \"$finalName\"")
        } catch (e: FileAccessException) {
            throw ActionExecutionException(e.message ?: "Rename failed", e)
        }
    }
}
