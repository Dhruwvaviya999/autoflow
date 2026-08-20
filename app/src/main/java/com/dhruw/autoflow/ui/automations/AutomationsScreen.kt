package com.dhruw.autoflow.ui.automations

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.automation.engine.HealthStatus
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.summary
import com.dhruw.autoflow.core.utils.formatTimestamp
import com.dhruw.autoflow.data.transfer.WorkflowFileCodec
import com.dhruw.autoflow.ui.components.EmptyState
import com.dhruw.autoflow.ui.components.HealthBadge
import com.dhruw.autoflow.ui.components.icon

@Composable
fun AutomationsScreen(
    onCreateAutomation: () -> Unit,
    onEditAutomation: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenPermissions: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AutomationsViewModel = viewModel(factory = AutomationsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    var searchVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Automation?>(null) }
    // Which automation the pending export writes; null means "all of them".
    var exportTarget by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WorkflowFileCodec.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            val target = exportTarget
            if (target == null) viewModel.exportAll(uri) else viewModel.export(target, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { onShowMessage(it) }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { intent ->
            context.startActivity(Intent.createChooser(intent, "Share workflow"))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automations",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.totalCount > 0) {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search automations")
                        }
                        IconButton(
                            onClick = {
                                exportTarget = null
                                exportLauncher.launch("autoflow-workflows.${WorkflowFileCodec.FILE_EXTENSION}")
                            }
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = "Export all automations")
                        }
                    }
                }

                AnimatedVisibility(visible = searchVisible && state.totalCount > 0) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            label = { Text("Search automations") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (state.totalCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutomationFilter.entries.forEach { candidate ->
                            FilterChip(
                                selected = state.filter == candidate,
                                onClick = { viewModel.setFilter(candidate) },
                                label = {
                                    Text(candidate.label, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            when {
                state.totalCount == 0 -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Bolt,
                        title = "No automations yet",
                        message = "Create an automation to make your phone work for you.",
                        action = {
                            Button(onClick = onCreateAutomation) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("New automation")
                            }
                        }
                    )
                }

                state.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Nothing matches",
                        message = "No automation matches this search or filter.",
                        action = {
                            TextButton(
                                onClick = {
                                    viewModel.setQuery("")
                                    viewModel.setFilter(AutomationFilter.ALL)
                                }
                            ) { Text("Clear filters") }
                        }
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.automation.id }) { item ->
                        AutomationCard(
                            item = item,
                            isRunning = item.automation.id in state.runningIds,
                            onClick = { onEditAutomation(item.automation.id) },
                            onRun = { viewModel.run(item.automation.id) },
                            onToggle = { viewModel.setEnabled(item.automation.id, it) },
                            onDelete = { pendingDelete = item.automation },
                            onDuplicate = { viewModel.duplicate(item.automation.id) },
                            onExport = {
                                exportTarget = item.automation.id
                                exportLauncher.launch(
                                    "${item.automation.name.toFileName()}.${WorkflowFileCodec.FILE_EXTENSION}"
                                )
                            },
                            onShare = { viewModel.share(item.automation.id) },
                            onHealthClick = onOpenPermissions
                        )
                    }
                }
            }
        }

        if (state.totalCount > 0) {
            FloatingActionButton(
                onClick = onCreateAutomation,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New automation")
            }
        }
    }

    pendingDelete?.let { automation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${automation.name}\"?") },
            text = {
                Text(
                    "The automation is removed from this device. Its run history is kept " +
                        "in History."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(automation.id)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun subtitle(state: AutomationsUiState): String = when {
    state.totalCount == 0 -> "No automations"
    state.isFiltered -> "${state.items.size} of ${state.totalCount} shown"
    state.needsAttentionCount > 0 ->
        "${state.activeCount} active · ${state.needsAttentionCount} need attention"
    state.totalCount == 1 -> "1 automation"
    else -> "${state.totalCount} automations · ${state.activeCount} active"
}

private fun String.toFileName(): String =
    trim().replace(Regex("""[^A-Za-z0-9 _-]"""), "").replace(' ', '-').ifBlank { "workflow" }

@Composable
private fun AutomationCard(
    item: AutomationListItem,
    isRunning: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onHealthClick: () -> Unit
) {
    val automation = item.automation
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Icon(
                        imageVector = automation.trigger.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = automation.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = triggerLine(automation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
            }

            if (item.health.status != HealthStatus.HEALTHY && item.health.detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HealthBadge(
                    health = item.health,
                    onClick = if (item.health.status == HealthStatus.NEEDS_PERMISSION) {
                        onHealthClick
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = automation.lastRunAt?.let { "Last run ${formatTimestamp(it)}" }
                        ?: "Never run",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export") },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onRun,
                    enabled = automation.enabled && !isRunning
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }
            }
        }
    }
}

private fun triggerLine(automation: Automation): String {
    val base = automation.trigger.summary
    return if (automation.trigger is Trigger.TimeTrigger) {
        if (automation.enabled) "$base · Scheduled" else "$base · Off"
    } else {
        base
    }
}
