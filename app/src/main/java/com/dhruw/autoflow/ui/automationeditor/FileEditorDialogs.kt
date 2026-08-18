package com.dhruw.autoflow.ui.automationeditor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger

/** Folder row used by the file trigger and copy/move dialogs. */
@Composable
private fun FolderPickerField(
    label: String,
    folderLabel: String?,
    onPick: (uri: String, label: String) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Keep access across restarts; the scanner and file actions rely on it.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Selected folder"
            onPick(uri.toString(), name)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { launcher.launch(null) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = folderLabel ?: "Tap to choose a folder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (folderLabel != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun FileTriggerDialog(
    initial: Trigger.FileTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.FileTrigger) -> Unit
) {
    var folderUri by rememberSaveable { mutableStateOf(initial?.folderUri) }
    var folderLabel by rememberSaveable { mutableStateOf(initial?.folderLabel) }
    var namePattern by rememberSaveable { mutableStateOf(initial?.namePattern ?: "") }
    var extension by rememberSaveable { mutableStateOf(initial?.extension ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File trigger") },
        text = {
            Column {
                FolderPickerField(
                    label = "Watched folder",
                    folderLabel = folderLabel,
                    onPick = { uri, label -> folderUri = uri; folderLabel = label }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text("Extension (optional, e.g. zip)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = { namePattern = it },
                    label = { Text("Name contains (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "The folder is checked when you open AutoFlow and " +
                        "about every 15 minutes in the background. Only files " +
                        "added after the automation is created will trigger it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = folderUri != null,
                onClick = {
                    onConfirm(
                        Trigger.FileTrigger(
                            folderUri = folderUri.orEmpty(),
                            folderLabel = folderLabel.orEmpty(),
                            namePattern = namePattern.trim(),
                            extension = extension.trim()
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FileExtensionConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.FileExtensionCondition) -> Unit
) {
    var extension by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File extension") },
        text = {
            OutlinedTextField(
                value = extension,
                onValueChange = { extension = it },
                label = { Text("Extension (e.g. zip)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = extension.trim().removePrefix(".").isNotBlank(),
                onClick = { onConfirm(Condition.FileExtensionCondition(extension.trim())) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FileNameContainsConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.FileNameContainsCondition) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File name contains") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text (e.g. instagram)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(Condition.FileNameContainsCondition(text.trim())) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FileSizeConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.FileSizeCondition) -> Unit
) {
    var comparison by remember {
        mutableStateOf(Condition.FileSizeCondition.Comparison.LESS_THAN)
    }
    var mbText by rememberSaveable { mutableStateOf("100") }
    val mb = mbText.toDoubleOrNull()
    val valid = mb != null && mb > 0 && mb <= 100_000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File size") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = comparison == Condition.FileSizeCondition.Comparison.LESS_THAN,
                        onClick = { comparison = Condition.FileSizeCondition.Comparison.LESS_THAN },
                        label = { Text("Smaller than") }
                    )
                    FilterChip(
                        selected = comparison == Condition.FileSizeCondition.Comparison.GREATER_THAN,
                        onClick = { comparison = Condition.FileSizeCondition.Comparison.GREATER_THAN },
                        label = { Text("Larger than") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = mbText,
                    onValueChange = { mbText = it },
                    label = { Text("Size in MB") },
                    singleLine = true,
                    isError = mbText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        Condition.FileSizeCondition(
                            comparison = comparison,
                            sizeBytes = (mb!! * 1024 * 1024).toLong()
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shared dialog for copy/move — both only need a destination folder. */
@Composable
fun FileDestinationActionDialog(
    isMove: Boolean,
    initial: Action?,
    onDismiss: () -> Unit,
    onConfirm: (Action) -> Unit
) {
    val initialUri: String?
    val initialLabel: String?
    when (initial) {
        is Action.CopyFileAction -> { initialUri = initial.destinationFolderUri; initialLabel = initial.destinationLabel }
        is Action.MoveFileAction -> { initialUri = initial.destinationFolderUri; initialLabel = initial.destinationLabel }
        else -> { initialUri = null; initialLabel = null }
    }
    var folderUri by rememberSaveable { mutableStateOf(initialUri) }
    var folderLabel by rememberSaveable { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMove) "Move file" else "Copy file") },
        text = {
            Column {
                FolderPickerField(
                    label = "Destination folder",
                    folderLabel = folderLabel,
                    onPick = { uri, label -> folderUri = uri; folderLabel = label }
                )
                if (isMove) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Moving removes the file from its original folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = folderUri != null,
                onClick = {
                    val uri = folderUri.orEmpty()
                    val label = folderLabel.orEmpty()
                    onConfirm(
                        if (isMove) Action.MoveFileAction(uri, label)
                        else Action.CopyFileAction(uri, label)
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RenameFileActionDialog(
    initial: Action.RenameFileAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.RenameFileAction) -> Unit
) {
    var newName by rememberSaveable { mutableStateOf(initial?.newName ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename file") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New file name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = { onConfirm(Action.RenameFileAction(newName.trim())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
