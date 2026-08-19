package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.TriggerPayload

/**
 * Resolves a tiny, closed template language used by SetText and notification
 * text: literal text with `{{variable}}` placeholders. Only an explicit
 * allow-list of variables is supported — there is no code evaluation, no
 * arbitrary property access. Unknown or unavailable variables are reported as
 * validation errors rather than silently blanked, so a broken workflow fails
 * loudly at edit time.
 *
 * Available variables depend on the run's [TriggerPayload]:
 *   notification.title / .text / .appName / .packageName
 *   file.name / .extension
 *   battery.level / battery.charging
 */
object TemplateResolver {

    private val TOKEN = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*}}""")

    sealed interface Result {
        data class Ok(val text: String) : Result
        /** [variable] is not in the allow-list at all. */
        data class UnknownVariable(val variable: String) : Result
        /** Known variable, but the current payload does not carry it. */
        data class Unavailable(val variable: String) : Result
    }

    /** Every variable name the language recognizes, for editor hints/validation. */
    val supportedVariables: List<String> = listOf(
        "notification.title", "notification.text", "notification.appName", "notification.packageName",
        "file.name", "file.extension",
        "battery.level", "battery.charging"
    )

    /**
     * Validate against the allow-list only (no payload needed) — used by the
     * editor so a template is checked before it can ever run.
     */
    fun validate(template: String): Result {
        for (match in TOKEN.findAll(template)) {
            val name = match.groupValues[1]
            if (name !in supportedVariables) return Result.UnknownVariable(name)
        }
        return Result.Ok(template)
    }

    /** Resolve against a concrete payload; empty [payload] means a manual/timer run. */
    fun resolve(template: String, payload: TriggerPayload?): Result {
        val values = valuesFor(payload)
        val out = StringBuilder()
        var last = 0
        for (match in TOKEN.findAll(template)) {
            val name = match.groupValues[1]
            if (name !in supportedVariables) return Result.UnknownVariable(name)
            val value = values[name] ?: return Result.Unavailable(name)
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
