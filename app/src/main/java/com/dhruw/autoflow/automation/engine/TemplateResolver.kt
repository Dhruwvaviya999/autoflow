package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.TriggerPayload

/**
 * Resolves a tiny, closed template language used by SetText, notification
 * text, log messages and Set variable: literal text with `{{variable}}`
 * placeholders. Only an explicit allow-list plus the run's own variables are
 * supported — there is no code evaluation, no arbitrary property access.
 * Unknown or unavailable variables are reported as validation errors rather
 * than silently blanked, so a broken workflow fails loudly at edit time.
 *
 * Variable namespaces:
 *   Payload (depend on the run's [TriggerPayload]):
 *     notification.title / .text / .appName / .packageName
 *     file.name / .extension
 *     battery.level / battery.charging
 *   Run:
 *     automation.name
 *   Workflow-local (created by Set variable, e.g. {{jobType}}) and action
 *   outputs (produced by earlier actions, e.g. {{result.count}}) — both are
 *   run-scoped strings carried in the ActionContext, never persisted.
 */
object TemplateResolver {

    private val TOKEN = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")

    /** Names Set variable may define: simple identifiers, no dots (namespaces are reserved). */
    val VALID_LOCAL_NAME = Regex("""[a-zA-Z][a-zA-Z0-9_]*""")

    sealed interface Result {
        data class Ok(val text: String) : Result
        /** [variable] is not in the allow-list and not a known run variable. */
        data class UnknownVariable(val variable: String) : Result
        /** Known variable, but the current run does not carry a value for it. */
        data class Unavailable(val variable: String) : Result
    }

    /** Every payload variable the language recognizes, for editor hints/validation. */
    val supportedVariables: List<String> = listOf(
        "notification.title", "notification.text", "notification.appName", "notification.packageName",
        "file.name", "file.extension",
        "battery.level", "battery.charging"
    )

    private const val AUTOMATION_NAME = "automation.name"
    private const val RESULT_PREFIX = "result."

    /** All placeholder names appearing in [template], in order. */
    fun variablesIn(template: String): List<String> =
        TOKEN.findAll(template).map { it.groupValues[1] }.toList()

    /**
     * Validate against the allow-list plus [knownRunVariables] (locals defined
     * by earlier Set variable steps and result.* keys earlier actions can
     * produce) — used by the editor and the workflow validator so a template
     * is checked before it can ever run. `result.*` names are accepted
     * structurally: which keys exist depends on the actions that ran.
     */
    fun validate(template: String, knownRunVariables: Set<String> = emptySet()): Result {
        for (name in variablesIn(template)) {
            val known = name in supportedVariables ||
                name == AUTOMATION_NAME ||
                name.startsWith(RESULT_PREFIX) ||
                name in knownRunVariables
            if (!known) return Result.UnknownVariable(name)
        }
        return Result.Ok(template)
    }

    /**
     * Resolve against a concrete run. [runVariables] carries workflow-locals
     * and result.* outputs; [automationName] feeds {{automation.name}}.
     * A null [payload] means a manual/timer run — payload variables are then
     * unavailable, not unknown.
     */
    fun resolve(
        template: String,
        payload: TriggerPayload?,
        runVariables: Map<String, String> = emptyMap(),
        automationName: String? = null
    ): Result {
        val payloadValues = valuesFor(payload)
        val out = StringBuilder()
        var last = 0
        for (match in TOKEN.findAll(template)) {
            val name = match.groupValues[1]
            val value = runVariables[name]
                ?: payloadValues[name]
                ?: (if (name == AUTOMATION_NAME) automationName else null)
            if (value == null) {
                val known = name in supportedVariables ||
                    name == AUTOMATION_NAME ||
                    name.startsWith(RESULT_PREFIX)
                return if (known) Result.Unavailable(name) else Result.UnknownVariable(name)
            }
            out.append(template, last, match.range.first)
            out.append(value)
            last = match.range.last + 1
        }
        out.append(template, last, template.length)
        return Result.Ok(out.toString())
    }

    private fun valuesFor(payload: TriggerPayload?): Map<String, String> = when (payload) {
        is TriggerPayload.NotificationEvent -> mapOf(
            "notification.title" to payload.title,
            "notification.text" to payload.text,
            "notification.appName" to payload.appName,
            "notification.packageName" to payload.packageName
        )
        is TriggerPayload.FileEvent -> mapOf(
            "file.name" to payload.name,
            "file.extension" to payload.extension
        )
        is com.dhruw.autoflow.automation.model.SystemEvent.BatteryChanged -> mapOf(
            "battery.level" to payload.level.toString(),
            "battery.charging" to payload.isCharging.toString()
        )
        is com.dhruw.autoflow.automation.model.SystemEvent.ChargingChanged -> mapOf(
            "battery.level" to payload.batteryLevel.toString(),
            "battery.charging" to payload.isCharging.toString()
        )
        else -> emptyMap()
    }
}
