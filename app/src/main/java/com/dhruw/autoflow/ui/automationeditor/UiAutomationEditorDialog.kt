package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dhruw.autoflow.automation.engine.UiWorkflowValidator
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.summary

/** Which step dialog is open, and whether it edits an existing step. */
private sealed interface StepDialog {
    val editIndex: Int?

    data class LaunchApp(override val editIndex: Int?) : StepDialog
    data class Wait(override val editIndex: Int?) : StepDialog
    data class WaitForElement(override val editIndex: Int?) : StepDialog
    data class Click(override val editIndex: Int?) : StepDialog
    data class LongClick(override val editIndex: Int?) : StepDialog
    data class SetText(override val editIndex: Int?) : StepDialog
    data class Scroll(override val editIndex: Int?) : StepDialog
    data class Confirmation(override val editIndex: Int?) : StepDialog
}

/**
 * Full-screen editor for one [Action.UiAutomationAction]: target app,
 * ordered step list (add / edit / reorder / delete), overall timeout, and
 * validation + safety warnings from [UiWorkflowValidator]. Invalid
 * workflows cannot be saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiAutomationEditorDialog(
    initial: Action.UiAutomationAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.UiAutomationAction) -> Unit
) {
    var target by remember {
        mutableStateOf(
            initial?.takeIf { it.targetPackage.isNotBlank() }?.let {
                AppChoice(it.targetPackage, it.targetLabel.ifBlank { it.targetPackage })
            }
        )
    }
    var steps by remember { mutableStateOf(initial?.steps ?: emptyList()) }
    var timeoutText by rememberSaveable {
        mutableStateOf(((initial?.overallTimeoutMillis ?: 60_000) / 1000).toString())
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var stepDialog by remember { mutableStateOf<StepDialog?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val targetPackage = target?.packageName.orEmpty()

    fun buildAction() = Action.UiAutomationAction(
        targetPackage = targetPackage,
        targetLabel = target?.label.orEmpty(),
        steps = steps,
        overallTimeoutMillis = (timeoutText.trim().toLongOrNull() ?: 0) * 1000
    )

    val warnings = remember(steps, targetPackage) {
        if (steps.isEmpty()) emptyList() else UiWorkflowValidator.warnings(buildAction())
    }

    fun putStep(index: Int?, step: UiStep) {
        steps = if (index == null) {
            steps + step
        } else {
            steps.mapIndexed { i, s -> if (i == index) step else s }
        }
        stepDialog = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("UI Automation") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = target != null && steps.isNotEmpty(),
                            onClick = {
                                val action = buildAction()
                                when (val result = UiWorkflowValidator.validate(action)) {
                                    is UiWorkflowValidator.Result.Invalid ->
                                        validationError = result.message
                                    UiWorkflowValidator.Result.Ok -> onConfirm(action)
                                }
                            }
                        ) { Text("Save") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "TARGET APP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                )
                AppPickerField(
                    label = "Runs inside",
                    choice = target ?: AppChoice(null, "Select application"),
                    onClick = { showAppPicker = true }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "The automation only acts while this app is in the " +
                        "foreground. If another app or dialog takes over, it stops.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "STEPS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
                )
                steps.forEachIndexed { index, step ->
                    StepCard(
                        index = index,
                        step = step,
                        isFirst = index == 0,
                        isLast = index == steps.lastIndex,
                        onClick = { stepDialog = dialogFor(step, index) },
                        onMoveUp = {
                            steps = steps.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                        },
                        onMoveDown = {
                            steps = steps.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                        },
                        onDelete = {
                            steps = steps.filterIndexed { i, _ -> i != index }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { showAddSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add step",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "LIMITS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
                )
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { timeoutText = it },
                    label = { Text("Stop everything after (seconds, 1–300)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                warnings.forEach { warning ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                validationError?.let { error ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "UI automations may stop working if the target app " +
                        "changes its layout or language. Runs need Accessibility " +
                        "enabled for AutoFlow and pause for your confirmation " +
                        "before consequential steps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            allowAny = false,
            onDismiss = { showAppPicker = false },
            onPick = { picked ->
                target = picked
                showAppPicker = false
            }
        )
    }

    if (showAddSheet) {
        AddStepSheet(
            onDismiss = { showAddSheet = false },
            onPick = { dialogOrStep ->
                showAddSheet = false
                when (dialogOrStep) {
                    is AddStepChoice.OpenDialog -> stepDialog = dialogOrStep.dialog
                    is AddStepChoice.Immediate -> steps = steps + dialogOrStep.step
                }
            },
            defaultTarget = target
        )
    }

    when (val d = stepDialog) {
        is StepDialog.LaunchApp -> LaunchAppStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.LaunchApp },
            defaultChoice = target,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        is StepDialog.Wait -> WaitStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.Wait },
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        is StepDialog.WaitForElement -> WaitForElementStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.WaitForElement },
            targetPackage = targetPackage,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        is StepDialog.Click -> SelectorStepDialog(
            title = "Tap element",
            initial = d.editIndex?.let { (steps[it] as UiStep.ClickElement).selector },
            targetPackage = targetPackage,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, UiStep.ClickElement(it)) }
        )

        is StepDialog.LongClick -> SelectorStepDialog(
            title = "Long press",
            initial = d.editIndex?.let { (steps[it] as UiStep.LongClickElement).selector },
            targetPackage = targetPackage,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, UiStep.LongClickElement(it)) }
        )

        is StepDialog.SetText -> SetTextStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.SetText },
            targetPackage = targetPackage,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        is StepDialog.Scroll -> ScrollStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.Scroll },
            targetPackage = targetPackage,
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        is StepDialog.Confirmation -> ConfirmationStepDialog(
            initial = d.editIndex?.let { steps[it] as UiStep.RequireUserConfirmation },
            onDismiss = { stepDialog = null },
            onConfirm = { putStep(d.editIndex, it) }
        )

        null -> Unit
    }
}

private fun dialogFor(step: UiStep, index: Int): StepDialog? = when (step) {
    is UiStep.LaunchApp -> StepDialog.LaunchApp(index)
    is UiStep.Wait -> StepDialog.Wait(index)
    is UiStep.WaitForElement -> StepDialog.WaitForElement(index)
    is UiStep.ClickElement -> StepDialog.Click(index)
    is UiStep.LongClickElement -> StepDialog.LongClick(index)
    is UiStep.SetText -> StepDialog.SetText(index)
    is UiStep.Scroll -> StepDialog.Scroll(index)
    is UiStep.RequireUserConfirmation -> StepDialog.Confirmation(index)
    is UiStep.GlobalBack -> null // nothing to configure
}

@Composable
private fun StepCard(
    index: Int,
    step: UiStep,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = step.editorIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}.  ${step.displayName}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = step.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move up",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Move down",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private sealed interface AddStepChoice {
    data class OpenDialog(val dialog: StepDialog) : AddStepChoice
    data class Immediate(val step: UiStep) : AddStepChoice
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStepSheet(
    defaultTarget: AppChoice?,
    onDismiss: () -> Unit,
    onPick: (AddStepChoice) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Add step",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            StepSectionLabel("Navigation")
            StepOption(Icons.Outlined.RocketLaunch, "Launch app", "Bring the target app to the front") {
                onPick(AddStepChoice.OpenDialog(StepDialog.LaunchApp(null)))
            }
            StepOption(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Go back", "Press the Back button") {
                onPick(AddStepChoice.Immediate(UiStep.GlobalBack))
            }
            StepSectionLabel("Find & interact")
            StepOption(Icons.Outlined.Search, "Wait for element", "Pause until an element appears") {
                onPick(AddStepChoice.OpenDialog(StepDialog.WaitForElement(null)))
            }
            StepOption(Icons.Outlined.TouchApp, "Tap element", "Tap a button or item") {
                onPick(AddStepChoice.OpenDialog(StepDialog.Click(null)))
            }
            StepOption(Icons.Outlined.PanTool, "Long press", "Press and hold an element") {
                onPick(AddStepChoice.OpenDialog(StepDialog.LongClick(null)))
            }
            StepOption(Icons.Outlined.Keyboard, "Enter text", "Type into a text field") {
                onPick(AddStepChoice.OpenDialog(StepDialog.SetText(null)))
            }
            StepOption(Icons.Outlined.SwapVert, "Scroll", "Scroll a list or page") {
                onPick(AddStepChoice.OpenDialog(StepDialog.Scroll(null)))
            }
            StepSectionLabel("Flow")
            StepOption(Icons.Outlined.HourglassEmpty, "Wait", "Pause for a fixed time") {
                onPick(AddStepChoice.OpenDialog(StepDialog.Wait(null)))
            }
            StepOption(Icons.Outlined.QuestionMark, "Require confirmation", "Ask you before continuing") {
                onPick(AddStepChoice.OpenDialog(StepDialog.Confirmation(null)))
            }
        }
    }
}

@Composable
private fun StepSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun StepOption(
    icon: ImageVector,
    title: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val UiStep.editorIcon: ImageVector
    get() = when (this) {
        is UiStep.LaunchApp -> Icons.Outlined.RocketLaunch
        is UiStep.Wait -> Icons.Outlined.HourglassEmpty
        is UiStep.WaitForElement -> Icons.Outlined.Search
        is UiStep.ClickElement -> Icons.Outlined.TouchApp
        is UiStep.LongClickElement -> Icons.Outlined.PanTool
        is UiStep.SetText -> Icons.Outlined.Keyboard
        is UiStep.Scroll -> Icons.Outlined.SwapVert
        is UiStep.GlobalBack -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft
        is UiStep.RequireUserConfirmation -> Icons.Outlined.QuestionMark
    }
