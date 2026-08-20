package com.dhruw.autoflow.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.automation.engine.FailureDiagnostics
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.core.utils.formatDuration
import com.dhruw.autoflow.core.utils.formatTimestamp
import com.dhruw.autoflow.ui.components.EmptyState
import com.dhruw.autoflow.ui.components.color
import com.dhruw.autoflow.ui.components.icon
import com.dhruw.autoflow.ui.components.label

/**
 * Everything AutoFlow recorded about one run: outcome, timing, the ordered
 * step log, and — when it failed — an explanation of what to try.
 *
 * The log lines come from the engine and handlers, which record labels and
 * outcomes rather than content: typed text, notification bodies and screen
 * data are never written here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionDetailScreen(
    executionId: String,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val executionFlow = remember(executionId) { viewModel.execution(executionId) }
    val execution by executionFlow.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(execution?.automationName ?: "Run details") },
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
        val current = execution
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "Run not found",
                    message = "This run is no longer in history."
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            StatusHeader(current)

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Summary")
            DetailRow("Status", current.status.label)
            DetailRow("Started", formatTimestamp(current.startedAt))
            current.completedAt?.let { completedAt ->
                DetailRow("Duration", formatDuration(current.startedAt, completedAt))
            }
            DetailRow(
                "Actions",
                "${current.completedActions} of ${current.totalActions} completed"
            )
            current.message?.let { DetailRow("Result", it) }

            if (current.status == ExecutionStatus.FAILED) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("What went wrong")
                FailureCard(current.error ?: current.message)
            }

            if (current.logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Timeline")
                TimelineCard(current)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusHeader(execution: Execution) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = execution.status.icon,
            contentDescription = null,
            tint = execution.status.color(),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = execution.status.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = formatTimestamp(execution.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FailureCard(rawMessage: String?) {
    val explanation = remember(rawMessage) { FailureDiagnostics.explain(rawMessage) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = explanation.headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (explanation.detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = explanation.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (explanation.possibleReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Possible reasons",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                explanation.possibleReasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            explanation.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Try this",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(execution: Execution) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TimelineLine("Run started", formatTimestamp(execution.startedAt))
            execution.logs.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
            execution.completedAt?.let { completedAt ->
                TimelineLine("Run ${execution.status.label.lowercase()}", formatTimestamp(completedAt))
            }
        }
    }
}

@Composable
private fun TimelineLine(label: String, timestamp: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
