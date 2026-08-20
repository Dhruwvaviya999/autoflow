package com.dhruw.autoflow.ui.data

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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.automation.model.label
import com.dhruw.autoflow.data.transfer.WorkflowFileCodec

/** Destructive actions that need an explicit confirmation. */
private enum class DangerAction(
    val title: String,
    val body: String,
    val confirmLabel: String
) {
    CLEAR_HISTORY(
        title = "Clear execution history?",
        body = "Every recorded run is removed. Your automations are not affected.",
        confirmLabel = "Clear history"
    ),
    CLEAR_NOTIFICATIONS(
        title = "Clear saved notifications?",
        body = "Notifications kept by the Save notification action are deleted from this device.",
        confirmLabel = "Clear notifications"
    ),
    RESET_AUTOMATIONS(
        title = "Delete all automations?",
        body = "Every automation is deleted and its scheduled work cancelled. " +
            "This cannot be undone — back up first if you want to keep them.",
        confirmLabel = "Delete everything"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: DataManagementViewModel = viewModel(factory = DataManagementViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDanger by remember { mutableStateOf<DangerAction?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WorkflowFileCodec.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::backup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::openFile) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { onShowMessage(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                text = "${state.automationCount} automations · ${state.executionCount} runs · " +
                    "${state.notificationRecordCount} saved notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Backup")
            ActionRow(
                icon = Icons.Outlined.Backup,
                title = "Back up automations",
                subtitle = "Writes a .autoflow file you choose. Contains only workflow " +
                    "definitions — no history, notifications or personal data.",
                onClick = {
                    backupLauncher.launch("autoflow-backup.${WorkflowFileCodec.FILE_EXTENSION}")
                }
            )
            ActionRow(
                icon = Icons.Outlined.Restore,
                title = "Restore or import",
                subtitle = "Reads a .autoflow file. Imported workflows are added as new " +
                    "automations and stay switched off until you review them.",
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Clear local data")
            ActionRow(
                icon = Icons.Outlined.DeleteSweep,
                title = "Clear execution history",
                subtitle = "${state.executionCount} runs recorded",
                onClick = { pendingDanger = DangerAction.CLEAR_HISTORY }
            )
            ActionRow(
                icon = Icons.Outlined.NotificationsOff,
                title = "Clear saved notifications",
                subtitle = "${state.notificationRecordCount} records stored",
                onClick = { pendingDanger = DangerAction.CLEAR_NOTIFICATIONS }
            )
            ActionRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete all automations",
                subtitle = "Removes every automation from this device",
                destructive = true,
                onClick = { pendingDanger = DangerAction.RESET_AUTOMATIONS }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    pendingDanger?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text(action.title) },
            text = { Text(action.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            DangerAction.CLEAR_HISTORY -> viewModel.clearHistory()
                            DangerAction.CLEAR_NOTIFICATIONS -> viewModel.clearNotificationRecords()
                            DangerAction.RESET_AUTOMATIONS -> viewModel.resetAutomations()
                        }
                        pendingDanger = null
                    }
                ) { Text(action.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDanger = null }) { Text("Cancel") }
            }
        )
    }

    state.pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Import ${preview.importableCount} workflows?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    preview.candidates.forEach { candidate ->
                        Text(
                            text = candidate.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (candidate.errors.isNotEmpty()) {
                            Text(
                                text = "Cannot import: ${candidate.errors.first()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val needs = candidate.capabilities
                            Text(
                                text = if (needs.isEmpty()) {
                                    "No special permissions needed"
                                } else {
                                    "Needs " + needs.joinToString(", ") { it.label }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        candidate.warnings.forEach { warning ->
                            Text(
                                text = "⚠ $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = "Imported workflows arrive switched off. AutoFlow does not " +
                            "grant any permission — you enable what you want yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.importableCount > 0,
                    onClick = viewModel::confirmImport
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
