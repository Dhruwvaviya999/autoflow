package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.summary
import com.dhruw.autoflow.automation.model.unwrapped

/** One line of a simulation trace. */
data class SimulationStep(
    val label: String,
    val passed: Boolean,
    val detail: String = ""
)

/**
 * What a simulated event would have done. [wouldRun] is true only when the
 * trigger matched and every condition passed. No action is ever executed and
 * no device state is changed — this is a dry evaluation of the matching logic.
 */
data class SimulationResult(
    val steps: List<SimulationStep>,
    val wouldRun: Boolean,
    val plannedActions: List<String>
)

/**
 * Evaluates an automation against a synthetic payload so the user can test
 * conditions without waiting for a real notification, file or battery change.
 *
 * Strictly read-only: it runs the same trigger matching and the same
 * [ConditionEvaluator] the engine uses, then lists which steps would have
 * run. Actions are never invoked, so nothing is sent, moved, typed or posted.
 */
class SimulationEngine(
    private val conditionEvaluator: ConditionEvaluator = ConditionEvaluator()
) {

    fun simulate(automation: Automation, payload: TriggerPayload?): SimulationResult {
        val steps = mutableListOf<SimulationStep>()

        val triggerMatched = matchesTrigger(automation.trigger, payload)
        steps += SimulationStep(
            label = "${automation.trigger.displayName} trigger",
            passed = triggerMatched,
            detail = if (triggerMatched) {
                automation.trigger.summary
            } else {
                "This event does not match the trigger"
            }
        )

        var allPassed = triggerMatched
        automation.conditions.forEach { condition ->
            val passed = if (triggerMatched) {
                conditionEvaluator.evaluate(condition, payload)
            } else {
                false
            }
            allPassed = allPassed && passed
            steps += SimulationStep(
                label = condition.displayName,
                passed = passed,
                detail = condition.summary
            )
        }

        val planned = if (allPassed) plannedActions(automation.actions, payload) else emptyList()

        return SimulationResult(
            steps = steps,
            wouldRun = allPassed,
            plannedActions = planned
        )
    }

    /**
     * The steps that would run, resolving branches with the same condition
     * evaluation the engine performs. Disabled steps and group labels are
     * reported as such rather than silently dropped.
     */
    private fun plannedActions(
        actions: List<Action>,
        payload: TriggerPayload?,
        prefix: String = ""
    ): List<String> = buildList {
        actions.forEachIndexed { index, action ->
            val number = "$prefix${index + 1}"
            when (action) {
                is Action.DisabledAction ->
                    add("$number. ${action.displayName} — skipped (disabled)")
                is Action.GroupMarker ->
                    add("— ${action.label} —")
                is Action.BranchAction -> {
                    val passed = conditionEvaluator.evaluate(action.condition, payload)
                    add("$number. If ${action.condition.summary} → ${if (passed) "THEN" else "ELSE"}")
                    val chosen = if (passed) action.thenActions else action.elseActions
                    addAll(plannedActions(chosen, payload, prefix = "$number."))
                }
                else -> add("$number. ${action.displayName} · ${action.summary}")
            }
        }
    }

    /** Mirrors the dispatcher's first-level matching for each trigger kind. */
    private fun matchesTrigger(trigger: Trigger, payload: TriggerPayload?): Boolean = when (trigger) {
        is Trigger.ManualTrigger -> payload == null
        is Trigger.TimeTrigger -> payload == null
        is Trigger.DeviceBootTrigger -> payload is SystemEvent.DeviceBooted
        is Trigger.NotificationTrigger ->
            (payload as? TriggerPayload.NotificationEvent)?.let { trigger.matches(it) } == true
        is Trigger.FileTrigger -> (payload as? TriggerPayload.FileEvent)?.let { file ->
            val extensionOk = trigger.normalizedExtension.isEmpty() ||
                file.extension.trim().removePrefix(".").lowercase() == trigger.normalizedExtension
            val nameOk = trigger.namePattern.isBlank() ||
                file.name.contains(trigger.namePattern.trim(), ignoreCase = true)
            extensionOk && nameOk
        } == true
        is Trigger.BatteryLevelTrigger ->
            (payload as? SystemEvent.BatteryChanged)?.let { trigger.matches(it) } == true
        is Trigger.ChargingStateTrigger ->
            (payload as? SystemEvent.ChargingChanged)?.let { trigger.matches(it) } == true
        is Trigger.WiFiConnectionTrigger ->
            (payload as? SystemEvent.WiFiChanged)?.let { trigger.matches(it) } == true
        is Trigger.NetworkAvailabilityTrigger ->
            (payload as? SystemEvent.NetworkChanged)?.let { trigger.matches(it) } == true
        is Trigger.BluetoothConnectionTrigger ->
            (payload as? SystemEvent.BluetoothChanged)?.let { trigger.matches(it) } == true
        is Trigger.ScreenStateTrigger ->
            (payload as? SystemEvent.ScreenChanged)?.let { trigger.matches(it) } == true
        is Trigger.HeadsetConnectionTrigger ->
            (payload as? SystemEvent.HeadsetChanged)?.let { trigger.matches(it) } == true
    }
}

/** Ready-made synthetic payloads for the simulation screen. */
object SimulationPayloads {

    fun notification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        timestamp: Long
    ): TriggerPayload.NotificationEvent = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = appName.ifBlank { packageName },
        title = title,
        text = text,
        subText = "",
        timestamp = timestamp,
        notificationKey = "simulation",
        category = ""
    )

    fun file(name: String, sizeBytes: Long, timestamp: Long): TriggerPayload.FileEvent =
        TriggerPayload.FileEvent(
            uri = "",
            name = name,
            extension = name.substringAfterLast('.', ""),
            sizeBytes = sizeBytes,
            detectedAt = timestamp
        )

    fun battery(level: Int, previousLevel: Int, charging: Boolean, timestamp: Long): SystemEvent.BatteryChanged =
        SystemEvent.BatteryChanged(
            level = level,
            previousLevel = previousLevel,
            isCharging = charging,
            timestamp = timestamp
        )

    fun charging(charging: Boolean, batteryLevel: Int, timestamp: Long): SystemEvent.ChargingChanged =
        SystemEvent.ChargingChanged(
            isCharging = charging,
            batteryLevel = batteryLevel,
            timestamp = timestamp
        )

    fun screen(on: Boolean, timestamp: Long): SystemEvent.ScreenChanged =
        SystemEvent.ScreenChanged(on = on, timestamp = timestamp)

    fun network(available: Boolean, timestamp: Long): SystemEvent.NetworkChanged =
        SystemEvent.NetworkChanged(available = available, timestamp = timestamp)
}
