package com.dhruw.autoflow.ui.diagnostics

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.core.utils.formatTimestamp

/**
 * Developer Tools: scheduler state, database counts and an opt-in event log.
 * Everything shown is read from this device; nothing is uploaded and the
 * event log records event kinds, never their content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()
    val recording by viewModel.recording.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
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
            SectionLabel("Scheduled automations")
            if (state.scheduled.isEmpty()) {
                Card {
                    Text(
                        text = "No time-triggered automations are scheduled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                state.scheduled.forEach { info ->
                    Card {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (info.healthy) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    Icons.Outlined.ReportProblem
                                },
                                contentDescription = null,
                                tint = if (info.healthy) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(info.name, style = MaterialTheme.typography.titleSmall)
                                DetailLine("Schedule", info.schedule)
                                DetailLine("Next window", info.nextExpectedWindow)
                                DetailLine("Work state", info.state)
                            }
                        }
                    }
                }
                Text(
                    text = "Android decides exactly when deferred work runs. AutoFlow " +
                        "asks for a time; the system may run it later to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Database")
            Card {
                DetailLine("Automations", state.automationCount.toString())
                DetailLine("Executions", state.executionCount.toString())
                DetailLine("Saved notifications", state.notificationRecordCount.toString())
                DetailLine("Schema version", state.schemaVersion.toString())
                DetailLine(
                    "Integrity",
                    if (state.orphanedExecutions == 0) {
                        "No orphaned rows"
                    } else {
                        "${state.orphanedExecutions} runs from deleted automations (kept on purpose)"
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Event log")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Record engine events", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "In memory, newest 200 entries, cleared when switched off. " +
                                "Records event kinds only — never notification or screen content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = recording, onCheckedChange = viewModel::setRecording)
                }
            }
            if (recording) {
                if (events.isEmpty()) {
                    Text(
                        text = "Waiting for events…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card {
                        events.forEach { event ->
                            Text(
                                text = "${formatTimestamp(event.timestamp)}  ${event.category}  ${event.message}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = viewModel::clearEvents) { Text("Clear log") }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
