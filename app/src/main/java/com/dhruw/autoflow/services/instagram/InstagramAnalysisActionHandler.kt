package com.dhruw.autoflow.services.instagram

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.processor.ProcessingResult
import com.dhruw.autoflow.automation.processor.ProcessorInput
import com.dhruw.autoflow.instagram.InstagramAnalysisStore
import com.dhruw.autoflow.instagram.InstagramDataProcessor
import com.dhruw.autoflow.instagram.InstagramFollowAnalyzer
import com.dhruw.autoflow.services.files.FileAccess
import com.dhruw.autoflow.services.files.FileAccessException
import com.dhruw.autoflow.services.notification.AutomationNotifier

/**
 * Automation-side entry into the Instagram pipeline: triggering file →
 * InstagramDataProcessor → InstagramFollowAnalyzer → result published to
 * the analyzer screen. The engine knows nothing about Instagram formats —
 * everything specific lives behind this handler and the processor.
 */
class InstagramAnalysisActionHandler(
    private val fileAccess: FileAccess,
    private val processor: InstagramDataProcessor,
    private val analyzer: InstagramFollowAnalyzer,
    private val store: InstagramAnalysisStore,
    private val notifier: AutomationNotifier
) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.InstagramAnalysisAction

    override suspend fun execute(action: Action, context: ActionContext) {
        val file = context.fileEvent ?: throw ActionExecutionException(
            "No file in this run — use Instagram analysis with a File trigger"
        )

        val input = ProcessorInput(
            name = file.name,
            sizeBytes = file.sizeBytes,
            openStream = {
                try {
                    fileAccess.openInputStream(file.uri)
                } catch (e: FileAccessException) {
                    throw ActionExecutionException(e.message ?: "File is no longer accessible", e)
                }
            }
        )

        val result = when (val processed = processor.process(input)) {
            is ProcessingResult.Failure ->
                throw ActionExecutionException(processed.message, processed.cause)
            is ProcessingResult.Success -> analyzer.analyze(processed.data)
        }

        store.publish(result)
        context.variables["result.count"] = result.notFollowingBackCount.toString()
        context.variables["result.followers"] = result.followersCount.toString()
        context.variables["result.following"] = result.followingCount.toString()
        context.log(
            "Instagram analysis of \"${file.name}\": " +
                "${result.followersCount} followers, ${result.followingCount} following, " +
                "${result.notFollowingBackCount} don't follow back"
        )
        if (notifier.canNotify()) {
            notifier.notify(
                title = "Instagram analysis complete",
                message = "${result.notFollowingBackCount} accounts don't follow you back. " +
                    "Open AutoFlow to see the list."
            )
            context.log("Posted analysis summary notification")
        }
    }
}
