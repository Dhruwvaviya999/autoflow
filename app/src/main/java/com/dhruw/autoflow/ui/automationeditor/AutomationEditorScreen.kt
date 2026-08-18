package com.dhruw.autoflow.ui.automationeditor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.summary
import com.dhruw.autoflow.ui.components.icon

private enum class EditorSheet { TRIGGER, CONDITION, ACTION }

private sealed interface EditorDialog {
    data class Notification(val index: Int?) : EditorDialog
    data class Delay(val index: Int?) : EditorDialog
    data class Log(val index: Int?) : EditorDialog
    data object Time : EditorDialog
    data object File : EditorDialog
    data object FileExtension : EditorDialog
    data object FileNameContains : EditorDialog
    data object FileSize : EditorDialog
    data class CopyFile(val index: Int?) : EditorDialog
    data class MoveFile(val index: Int?) : EditorDialog
    data class RenameFile(val index: Int?) : EditorDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditorScreen(
    onBack: () -> Unit,
    viewModel: AutomationEditorViewModel = viewModel(factory = AutomationEditorViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    // Notification permission is requested only when the user adds a
    // notification action — the functionality that actually needs it.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handlers re-check on every run and report a clear error if missing. */ }
    val requestNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit automation" else "New automation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(enabled = state.canSave, onClick = viewModel::save) {
                        Text("Save")
                    }
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enabled",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            SectionLabel("WHEN")
            val trigger = state.trigger
            if (trigger == null) {
                AddSlotCard(label = "Choose a trigger", onClick = { sheet = EditorSheet.TRIGGER })
            } else {
                EditorItemCard(
                    icon = trigger.icon,
                    title = trigger.displayName,
                    subtitle = trigger.summary,
                    onClick = {
                        when (trigger) {
                            is Trigger.TimeTrigger -> dialog = EditorDialog.Time
                            is Trigger.FileTrigger -> dialog = EditorDialog.File
                            else -> sheet = EditorSheet.TRIGGER
                        }
                    },
                    onDelete = { viewModel.setTrigger(null) }
                )
            }

            SectionLabel("IF")
            state.conditions.forEachIndexed { index, condition ->
                EditorItemCard(
                    icon = condition.icon,
                    title = condition.displayName,
                    subtitle = condition.summary,
                    onClick = null,
                    onDelete = { viewModel.removeCondition(index) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            AddSlotCard(
                label = if (state.conditions.isEmpty()) "Add a condition (optional)" else "Add condition",
                onClick = { sheet = EditorSheet.CONDITION }
            )

            SectionLabel("THEN")
            state.actions.forEachIndexed { index, action ->
                EditorItemCard(
                    icon = action.icon,
                    title = "${index + 1}.  ${action.displayName}",
                    subtitle = action.summary,
                    onClick = {
                        dialog = when (action) {
                            is Action.ShowNotificationAction -> EditorDialog.Notification(index)
                            is Action.DelayAction -> EditorDialog.Delay(index)
                            is Action.LogAction -> EditorDialog.Log(index)
                            is Action.CopyFileAction -> EditorDialog.CopyFile(index)
                            is Action.MoveFileAction -> EditorDialog.MoveFile(index)
                            is Action.RenameFileAction -> EditorDialog.RenameFile(index)
                            is Action.InstagramAnalysisAction -> null
                        }
                    },
                    onDelete = { viewModel.removeAction(index) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            AddSlotCard(label = "Add action", onClick = { sheet = EditorSheet.ACTION })
            if (state.actions.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "At least one action is required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    when (sheet) {
        EditorSheet.TRIGGER -> PickerSheet(
            title = "Choose trigger",
            options = listOf(
                PickerOption(Icons.Outlined.TouchApp, "Manual", "Run it yourself with the Run button") {
                    viewModel.setTrigger(Trigger.ManualTrigger)
                },
                PickerOption(Icons.Outlined.Schedule, "Time", "Runs at a scheduled time") {
                    dialog = EditorDialog.Time
                },
                PickerOption(Icons.Outlined.FolderOpen, "File", "Runs when a matching file appears in a folder") {
                    dialog = EditorDialog.File
                }
            ),
            onDismiss = { sheet = null }
        )

        EditorSheet.CONDITION -> PickerSheet(
            title = "Add condition",
            options = listOf(
                PickerOption(Icons.Outlined.CheckCircle, "Always", "Run every time") {
                    viewModel.addCondition(Condition.AlwaysCondition)
                },
                PickerOption(Icons.Outlined.Extension, "File extension", "Only files with a given extension") {
                    dialog = EditorDialog.FileExtension
                },
                PickerOption(Icons.Outlined.Search, "File name contains", "Only files whose name has a text") {
                    dialog = EditorDialog.FileNameContains
                },
                PickerOption(Icons.Outlined.Straighten, "File size", "Only files above/below a size") {
                    dialog = EditorDialog.FileSize
                }
            ),
            onDismiss = { sheet = null }
        )

        EditorSheet.ACTION -> PickerSheet(
            title = "Add action",
            options = listOf(
                PickerOption(Icons.Outlined.Notifications, "Show notification", "Post a notification") {
                    dialog = EditorDialog.Notification(index = null)
                },
                PickerOption(Icons.Outlined.HourglassEmpty, "Delay", "Wait before the next action") {
                    dialog = EditorDialog.Delay(index = null)
                },
                PickerOption(Icons.AutoMirrored.Outlined.Article, "Log", "Write a message to the execution log") {
                    dialog = EditorDialog.Log(index = null)
                },
                PickerOption(Icons.Outlined.FileCopy, "Copy file", "Copy the triggering file to a folder") {
                    dialog = EditorDialog.CopyFile(index = null)
                },
                PickerOption(Icons.AutoMirrored.Outlined.DriveFileMove, "Move file", "Move the triggering file to a folder") {
                    dialog = EditorDialog.MoveFile(index = null)
                },
                PickerOption(Icons.Outlined.DriveFileRenameOutline, "Rename file", "Rename the triggering file") {
                    dialog = EditorDialog.RenameFile(index = null)
                },
                PickerOption(Icons.Outlined.PersonSearch, "Instagram analysis", "Find who doesn't follow back from an export file") {
                    viewModel.addAction(Action.InstagramAnalysisAction)
                }
            ),
            onDismiss = { sheet = null }
        )

        null -> Unit
    }

    when (val d = dialog) {
        is EditorDialog.Notification -> NotificationActionDialog(
            initial = d.index?.let { state.actions[it] as Action.ShowNotificationAction },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
                requestNotificationPermissionIfNeeded()
            }
        )

        is EditorDialog.Delay -> DelayActionDialog(
            initial = d.index?.let { state.actions[it] as Action.DelayAction },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
            }
        )

        is EditorDialog.Log -> LogActionDialog(
            initial = d.index?.let { state.actions[it] as Action.LogAction },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
            }
        )

        is EditorDialog.Time -> TimeTriggerDialog(
            initial = state.trigger as? Trigger.TimeTrigger,
            onDismiss = { dialog = null },
            onConfirm = { trigger ->
                viewModel.setTrigger(trigger)
                dialog = null
            }
        )

        is EditorDialog.File -> FileTriggerDialog(
            initial = state.trigger as? Trigger.FileTrigger,
            onDismiss = { dialog = null },
            onConfirm = { trigger ->
                viewModel.setTrigger(trigger)
                dialog = null
            }
        )

        is EditorDialog.FileExtension -> FileExtensionConditionDialog(
            onDismiss = { dialog = null },
            onConfirm = { condition ->
                viewModel.addCondition(condition)
                dialog = null
            }
        )

        is EditorDialog.FileNameContains -> FileNameContainsConditionDialog(
            onDismiss = { dialog = null },
            onConfirm = { condition ->
                viewModel.addCondition(condition)
                dialog = null
            }
        )

        is EditorDialog.FileSize -> FileSizeConditionDialog(
            onDismiss = { dialog = null },
            onConfirm = { condition ->
                viewModel.addCondition(condition)
                dialog = null
            }
        )

        is EditorDialog.CopyFile -> FileDestinationActionDialog(
            isMove = false,
            initial = d.index?.let { state.actions[it] },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
            }
        )

        is EditorDialog.MoveFile -> FileDestinationActionDialog(
            isMove = true,
            initial = d.index?.let { state.actions[it] },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
            }
        )

        is EditorDialog.RenameFile -> RenameFileActionDialog(
            initial = d.index?.let { state.actions[it] as Action.RenameFileAction },
            onDismiss = { dialog = null },
            onConfirm = { action ->
                if (d.index == null) viewModel.addAction(action)
                else viewModel.updateAction(d.index, action)
                dialog = null
            }
        )

        null -> Unit
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun EditorItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
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
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AddSlotCard(label: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
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
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class PickerOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onSelect: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    options: List<PickerOption>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            option.onSelect()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = option.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
