package com.dhruw.autoflow.automation.model

/** Overall outcome of one UI automation step. */
enum class UiStepStatus { SUCCESS, FAILED, TIMEOUT, CANCELLED, SKIPPED, WAITING_FOR_CONFIRMATION }

/**
 * Result of executing one step. [detail] is a short, non-sensitive
 * explanation for the history log — it must never contain text typed into a
 * field (SetText reports "Entered text", not the value).
 */
data class UiStepResult(
    val index: Int,
    val stepName: String,
    val status: UiStepStatus,
    val detail: String = ""
)

/** Lifecycle status of a whole UI automation session. */
enum class UiSessionStatus { IDLE, RUNNING, WAITING_FOR_CONFIRMATION, SUCCESS, FAILED, CANCELLED, BUSY }

/**
 * Runtime snapshot of one UI automation run. Holds selectors/configuration
 * only — never AccessibilityNodeInfo/UiNode references, which go stale.
 */
data class UiAutomationSession(
    val sessionId: String,
    val automationId: String?,
    val targetPackage: String,
    val startedAt: Long,
    val totalSteps: Int,
    val currentStep: Int = 0,
    val status: UiSessionStatus = UiSessionStatus.RUNNING,
    val confirmationPrompt: String? = null,
    val nextActionLabel: String? = null
)
