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

/**
 * Selected notification source: null package = "Any app". Labels are kept
 * for display; package names are what is stored and matched.
 */
private data class AppChoice(val packageName: String?, val label: String)

/** Row that shows the chosen app and opens the picker. */
@Composable
private fun AppPickerField(
    label: String,
    choice: AppChoice,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Lists launchable apps from PackageManager plus an "Any app" entry.
 * [allowAny] is false when picking for the app condition, which needs a
 * concrete application.
 */
@Composable
private fun AppPickerDialog(
    allowAny: Boolean,
    onDismiss: () -> Unit,
    onPick: (AppChoice) -> Unit
) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        val container = (context.applicationContext as AutoFlowApplication).container
        value = withContext(Dispatchers.Default) { container.installedApps.launchableApps() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            val loaded = apps
            if (loaded == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (allowAny) {
                        item {
                            AppRow(
                                label = "Any app",
                                detail = "React to notifications from every app",
                                onClick = { onPick(AppChoice(null, "Any app")) }
                            )
                        }
                    }
                    items(loaded, key = { it.packageName }) { app ->
                        AppRow(
                            label = app.label,
                            detail = app.packageName,
                            onClick = { onPick(AppChoice(app.packageName, app.label)) }
                        )
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AppRow(label: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
