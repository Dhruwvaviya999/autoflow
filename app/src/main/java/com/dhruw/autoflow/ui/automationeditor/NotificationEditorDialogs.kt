package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.label
import com.dhruw.autoflow.services.notification.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The app picker (AppChoice, AppPickerField, AppPickerDialog) is shared
// with the UI-automation editor — see AppPicker.kt in this package.

@Composable
fun NotificationTriggerDialog(
    initial: Trigger.NotificationTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.NotificationTrigger) -> Unit
) {
    var choice by remember {
        mutableStateOf(
            when {
                initial == null || initial.allowedPackages.isEmpty() -> AppChoice(null, "Any app")
                else -> AppChoice(
                    initial.allowedPackages.first(),
                    initial.appLabel.ifBlank { initial.allowedPackages.first() }
                )
            }
        )
    }
    var titlePattern by rememberSaveable { mutableStateOf(initial?.titlePattern ?: "") }
    var textPattern by rememberSaveable { mutableStateOf(initial?.textPattern ?: "") }
    var showAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification trigger") },
        text = {
            Column {
                AppPickerField(
                    label = "From",
                    choice = choice,
                    onClick = { showAppPicker = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titlePattern,
                    onValueChange = { titlePattern = it },
                    label = { Text("Title contains (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textPattern,
                    onValueChange = { textPattern = it },
                    label = { Text("Text contains (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Needs Notification Access (Settings → Permission center). " +
                        "AutoFlow only sees what an app puts into its notification — " +
                        "some apps hide or shorten their content. Ongoing status " +
                        "notifications (timers, downloads, music) are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Trigger.NotificationTrigger(
                            allowedPackages = choice.packageName?.let { setOf(it) } ?: emptySet(),
                            appLabel = if (choice.packageName == null) "" else choice.label,
                            titlePattern = titlePattern.trim(),
                            textPattern = textPattern.trim()
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAppPicker) {
        AppPickerDialog(
            allowAny = true,
            onDismiss = { showAppPicker = false },
            onPick = { picked ->
                choice = picked
                showAppPicker = false
            }
        )
    }
}

@Composable
fun NotificationAppConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.NotificationAppCondition) -> Unit
) {
    var choice by remember { mutableStateOf<AppChoice?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification app") },
        text = {
            Column {
                AppPickerField(
                    label = "Application",
                    choice = choice ?: AppChoice(null, "Tap to choose an app"),
                    onClick = { showAppPicker = true }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice?.packageName != null,
                onClick = {
                    val picked = choice ?: return@TextButton
                    onConfirm(
                        Condition.NotificationAppCondition(
                            packageName = picked.packageName.orEmpty(),
                            appLabel = picked.label
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAppPicker) {
        AppPickerDialog(
            allowAny = false,
            onDismiss = { showAppPicker = false },
            onPick = { picked ->
                choice = picked
                showAppPicker = false
            }
        )
    }
}

/** Shared dialog for the title and text conditions — same shape, different field. */
@Composable
fun NotificationTextMatchConditionDialog(
    isTitle: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Condition) -> Unit
) {
    var mode by remember { mutableStateOf(TextMatchMode.CONTAINS) }
    var value by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isTitle) "Notification title" else "Notification text") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextMatchMode.entries.forEach { candidate ->
                        FilterChip(
                            selected = mode == candidate,
                            onClick = { mode = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value (e.g. job opening)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Matching ignores upper/lower case.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = {
                    onConfirm(
                        if (isTitle) {
                            Condition.NotificationTitleCondition(value.trim(), mode)
                        } else {
                            Condition.NotificationTextCondition(value.trim(), mode)
                        }
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NotificationCategoryConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.NotificationCategoryCondition) -> Unit
) {
    var category by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification category") },
        text = {
            Column {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. msg, email, call)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android categories set by the sending app. Many apps " +
                        "don't set one — prefer app or text conditions when unsure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = category.isNotBlank(),
                onClick = { onConfirm(Condition.NotificationCategoryCondition(category.trim())) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
