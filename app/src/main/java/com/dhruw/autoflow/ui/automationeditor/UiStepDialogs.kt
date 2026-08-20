package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState as rememberHScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.TemplateResolver
import com.dhruw.autoflow.automation.model.ScrollDirection
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.label
import kotlinx.coroutines.launch

/** Which single attribute the simple selector editor is matching on. */
internal enum class SelectorField(val label: String) {
    TEXT("Text"),
    DESCRIPTION("Description"),
    VIEW_ID("View ID"),
    CLASS("Class")
}

/** Editable form state behind [SelectorEditor]; converts to/from [UiSelector]. */
internal data class SelectorDraft(
    val field: SelectorField = SelectorField.TEXT,
    val value: String = "",
    val matchMode: TextMatchMode = TextMatchMode.EQUALS,
    val extraClassName: String = "",
    val occurrenceText: String = ""
) {
    val occurrence: Int?
        get() = occurrenceText.trim().toIntOrNull()?.takeIf { it >= 1 }?.minus(1)

    val isValid: Boolean
        get() = value.isNotBlank() &&
            (occurrenceText.isBlank() || occurrenceText.trim().toIntOrNull()?.let { it >= 1 } == true)

    fun toSelector(): UiSelector = UiSelector(
        viewId = if (field == SelectorField.VIEW_ID) value.trim() else "",
        contentDescription = if (field == SelectorField.DESCRIPTION) value.trim() else "",
        text = if (field == SelectorField.TEXT) value.trim() else "",
        className = when {
            field == SelectorField.CLASS -> value.trim()
            else -> extraClassName.trim()
        },
        matchMode = matchMode,
        occurrence = occurrence
    )

    companion object {
        fun from(selector: UiSelector): SelectorDraft = when {
            selector.viewId.isNotBlank() -> SelectorDraft(
                SelectorField.VIEW_ID, selector.viewId, selector.matchMode,
                selector.className, occurrenceString(selector)
            )
            selector.contentDescription.isNotBlank() -> SelectorDraft(
                SelectorField.DESCRIPTION, selector.contentDescription, selector.matchMode,
                selector.className, occurrenceString(selector)
            )
            selector.text.isNotBlank() -> SelectorDraft(
                SelectorField.TEXT, selector.text, selector.matchMode,
                selector.className, occurrenceString(selector)
            )
            else -> SelectorDraft(
                SelectorField.CLASS, selector.className, selector.matchMode,
                "", occurrenceString(selector)
            )
        }

        private fun occurrenceString(selector: UiSelector): String =
            selector.occurrence?.let { (it + 1).toString() } ?: ""
    }
}

/**
 * Selector form: find-by chips, value, match mode, and collapsed advanced
 * options (extra class constraint, occurrence, selector test).
 * [targetPackage] enables the Test button when known.
 */
@Composable
internal fun SelectorEditor(
    draft: SelectorDraft,
    onDraftChange: (SelectorDraft) -> Unit,
    targetPackage: String
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(text = "Find element by", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberHScrollState())
        ) {
            SelectorField.entries.forEach { candidate ->
                FilterChip(
                    selected = draft.field == candidate,
                    onClick = { onDraftChange(draft.copy(field = candidate)) },
                    label = { Text(candidate.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.value,
            onValueChange = { onDraftChange(draft.copy(value = it)) },
            label = {
                Text(
                    when (draft.field) {
                        SelectorField.TEXT -> "Visible text (e.g. Send)"
                        SelectorField.DESCRIPTION -> "Content description"
                        SelectorField.VIEW_ID -> "View ID (e.g. send_button)"
                        SelectorField.CLASS -> "Class name (e.g. android.widget.Button)"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (draft.field == SelectorField.TEXT || draft.field == SelectorField.DESCRIPTION) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextMatchMode.entries.forEach { candidate ->
                    FilterChip(
                        selected = draft.matchMode == candidate,
                        onClick = { onDraftChange(draft.copy(matchMode = candidate)) },
                        label = { Text(candidate.label) }
                    )
                }
            }
        }

        if (draft.field == SelectorField.TEXT) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Text selectors can stop matching when the app changes its " +
                    "language, wording or layout. Use the Inspect UI tool " +
                    "(Settings → Developer tools) to find a stable View ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced options" else "Advanced options")
        }
        if (showAdvanced) {
            if (draft.field != SelectorField.CLASS) {
                OutlinedTextField(
                    value = draft.extraClassName,
                    onValueChange = { onDraftChange(draft.copy(extraClassName = it)) },
                    label = { Text("Also require class (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = draft.occurrenceText,
                onValueChange = { onDraftChange(draft.copy(occurrenceText = it)) },
                label = { Text("Occurrence — 1 = first match (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Without an occurrence, the selector must match exactly one " +
                    "element or the step fails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SelectorTester(draft = draft, targetPackage = targetPackage)
        }
    }
}

/**
 * "Test selector": opens the target app, waits ~3 s, counts matches — never
 * performs an action. The result is shown here once the user returns.
 */
@Composable
private fun SelectorTester(draft: SelectorDraft, targetPackage: String) {
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as AutoFlowApplication).container
    }
    val manager = container.uiAutomationSessionManager
    val lastTest by manager.lastSelectorTest.collectAsState()
    val scope = rememberCoroutineScope()
    var testStartedAt by remember { mutableStateOf<Long?>(null) }

    val accessibilityReady = container.accessibilityAccessManager.isEnabled()

    TextButton(
        enabled = draft.isValid && targetPackage.isNotBlank() && accessibilityReady,
        onClick = {
            testStartedAt = System.currentTimeMillis()
            scope.launch { manager.testSelector(targetPackage, draft.toSelector()) }
        }
    ) { Text("Test selector in target app") }

    val started = testStartedAt
    val result = lastTest
    when {
        !accessibilityReady -> Text(
            text = "Testing needs Accessibility enabled for AutoFlow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        started != null && (result == null || result.testedAt < started) -> Text(
            text = "Testing — the target app opens briefly, then come back here…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        result != null -> Text(
            text = result.detail,
            style = MaterialTheme.typography.bodySmall,
            color = when (result.matchCount) {
                1 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Shared dialog shape for the selector-only steps (tap, long press, wait-for). */
@Composable
internal fun SelectorStepDialog(
    title: String,
    initial: UiSelector?,
    targetPackage: String,
    extraContent: (@Composable () -> Unit)? = null,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (UiSelector) -> Unit
) {
    var draft by remember { mutableStateOf(initial?.let(SelectorDraft::from) ?: SelectorDraft()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectorEditor(
                    draft = draft,
                    onDraftChange = { draft = it },
                    targetPackage = targetPackage
                )
                extraContent?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    it()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isValid && confirmEnabled,
                onClick = { onConfirm(draft.toSelector()) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun WaitForElementStepDialog(
    initial: UiStep.WaitForElement?,
    targetPackage: String,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.WaitForElement) -> Unit
) {
    var timeoutText by rememberSaveable {
        mutableStateOf(initial?.let { (it.timeoutMillis / 1000).toString() } ?: "10")
    }
    val timeoutSeconds = timeoutText.trim().toLongOrNull()
    SelectorStepDialog(
        title = "Wait for element",
        initial = initial?.selector,
        targetPackage = targetPackage,
        confirmEnabled = timeoutSeconds != null && timeoutSeconds in 1..120,
        extraContent = {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { timeoutText = it },
                label = { Text("Give up after (seconds, 1–120)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        onDismiss = onDismiss,
        onConfirm = { selector ->
            onConfirm(
                UiStep.WaitForElement(
                    selector = selector,
                    timeoutMillis = (timeoutSeconds ?: 10) * 1000
                )
            )
        }
    )
}

@Composable
internal fun SetTextStepDialog(
    initial: UiStep.SetText?,
    targetPackage: String,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.SetText) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial?.text ?: "") }
    val templateProblem = when (val r = TemplateResolver.validate(text)) {
        is TemplateResolver.Result.UnknownVariable -> "Unknown variable {{${r.variable}}}"
        else -> null
    }
    SelectorStepDialog(
        title = "Enter text",
        initial = initial?.selector,
        targetPackage = targetPackage,
        confirmEnabled = text.isNotEmpty() && templateProblem == null,
        extraContent = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to enter") },
                modifier = Modifier.fillMaxWidth(),
                isError = templateProblem != null,
                supportingText = {
                    Text(
                        templateProblem
                            ?: "Insert trigger values with {{variable}}, e.g. " +
                            TemplateResolver.supportedVariables
                                .take(3).joinToString(", ") { "{{$it}}" }
                    )
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AutoFlow never enters text into password, PIN, OTP or " +
                    "payment fields — those steps fail safely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onDismiss = onDismiss,
        onConfirm = { selector -> onConfirm(UiStep.SetText(selector = selector, text = text)) }
    )
}

@Composable
internal fun ScrollStepDialog(
    initial: UiStep.Scroll?,
    targetPackage: String,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.Scroll) -> Unit
) {
    var direction by rememberSaveable {
        mutableStateOf(initial?.direction ?: ScrollDirection.FORWARD)
    }
    SelectorStepDialog(
        title = "Scroll",
        initial = initial?.selector,
        targetPackage = targetPackage,
        extraContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == ScrollDirection.FORWARD,
                    onClick = { direction = ScrollDirection.FORWARD },
                    label = { Text("Forward") }
                )
                FilterChip(
                    selected = direction == ScrollDirection.BACKWARD,
                    onClick = { direction = ScrollDirection.BACKWARD },
                    label = { Text("Backward") }
                )
            }
        },
        onDismiss = onDismiss,
        onConfirm = { selector ->
            onConfirm(UiStep.Scroll(selector = selector, direction = direction))
        }
    )
}

@Composable
internal fun WaitStepDialog(
    initial: UiStep.Wait?,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.Wait) -> Unit
) {
    var secondsText by rememberSaveable {
        mutableStateOf(
            initial?.let { (it.durationMillis / 1000.0).toString().removeSuffix(".0") } ?: "2"
        )
    }
    val seconds = secondsText.trim().toDoubleOrNull()
    val valid = seconds != null && seconds > 0 && seconds <= 120

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wait") },
        text = {
            Column {
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it },
                    label = { Text("Seconds (max 120)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(UiStep.Wait(((seconds ?: 2.0) * 1000).toLong())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ConfirmationStepDialog(
    initial: UiStep.RequireUserConfirmation?,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.RequireUserConfirmation) -> Unit
) {
    var prompt by rememberSaveable { mutableStateOf(initial?.prompt ?: "") }
    var nextLabel by rememberSaveable { mutableStateOf(initial?.nextActionLabel ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Require confirmation") },
        text = {
            Column {
                Text(
                    text = "The automation pauses here and continues only after " +
                        "you approve — put this before anything that sends, " +
                        "submits or confirms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Question (e.g. Send this message?)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nextLabel,
                    onValueChange = { nextLabel = it },
                    label = { Text("Next action shown (e.g. Tap \"Send\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        UiStep.RequireUserConfirmation(
                            prompt = prompt.trim(),
                            nextActionLabel = nextLabel.trim()
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun LaunchAppStepDialog(
    initial: UiStep.LaunchApp?,
    defaultChoice: AppChoice?,
    onDismiss: () -> Unit,
    onConfirm: (UiStep.LaunchApp) -> Unit
) {
    var choice by remember {
        mutableStateOf(
            initial?.let { AppChoice(it.packageName, it.appLabel.ifBlank { it.packageName }) }
                ?: defaultChoice
        )
    }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Launch app") },
        text = {
            Column {
                AppPickerField(
                    label = "Application",
                    choice = choice ?: AppChoice(null, "Tap to choose an app"),
                    onClick = { showPicker = true }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice?.packageName != null,
                onClick = {
                    val picked = choice ?: return@TextButton
                    onConfirm(
                        UiStep.LaunchApp(
                            packageName = picked.packageName.orEmpty(),
                            appLabel = picked.label
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showPicker) {
        AppPickerDialog(
            allowAny = false,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                choice = picked
                showPicker = false
            }
        )
    }
}
