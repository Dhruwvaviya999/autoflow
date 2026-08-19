package com.dhruw.autoflow.data.local.converters

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.NotificationRecord
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import com.dhruw.autoflow.data.local.entity.ExecutionEntity
import com.dhruw.autoflow.data.local.entity.NotificationRecordEntity

fun Automation.toEntity(): AutomationEntity = AutomationEntity(
    id = id,
    name = name,
    description = description,
    enabled = enabled,
    triggerJson = WorkflowJson.encodeTrigger(trigger),
    conditionsJson = WorkflowJson.encodeConditions(conditions),
    actionsJson = WorkflowJson.encodeActions(actions),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRunAt = lastRunAt
)

/** @throws WorkflowJsonException when the stored workflow JSON is malformed. */
fun AutomationEntity.toDomain(): Automation = Automation(
    id = id,
    name = name,
    description = description,
    enabled = enabled,
    trigger = WorkflowJson.decodeTrigger(triggerJson),
    conditions = WorkflowJson.decodeConditions(conditionsJson),
    actions = WorkflowJson.decodeActions(actionsJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRunAt = lastRunAt
)

fun Execution.toEntity(): ExecutionEntity = ExecutionEntity(
    id = id,
    automationId = automationId,
    automationName = automationName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status.name,
    totalActions = totalActions,
    completedActions = completedActions,
    currentAction = currentAction,
    message = message,
    error = error,
    logsJson = WorkflowJson.encodeLogs(logs)
)

/** @throws WorkflowJsonException when the stored row cannot be decoded. */
fun ExecutionEntity.toDomain(): Execution = Execution(
    id = id,
    automationId = automationId,
    automationName = automationName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = try {
        ExecutionStatus.valueOf(status)
    } catch (e: IllegalArgumentException) {
        throw WorkflowJsonException("Unknown execution status: $status", e)
    },
    totalActions = totalActions,
    completedActions = completedActions,
    currentAction = currentAction,
    message = message,
    error = error,
    logs = WorkflowJson.decodeLogs(logsJson)
)

fun NotificationRecord.toEntity(): NotificationRecordEntity = NotificationRecordEntity(
    id = id,
    packageName = packageName,
    appName = appName,
    title = title,
    text = text,
    timestamp = timestamp,
    automationId = automationId
)

fun NotificationRecordEntity.toDomain(): NotificationRecord = NotificationRecord(
    id = id,
    packageName = packageName,
    appName = appName,
    title = title,
    text = text,
    timestamp = timestamp,
    automationId = automationId
)
