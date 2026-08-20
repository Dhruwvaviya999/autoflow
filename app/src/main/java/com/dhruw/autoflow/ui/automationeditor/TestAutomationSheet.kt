package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.automation.engine.IssueSeverity
import com.dhruw.autoflow.automation.engine.ValidationIssue
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.label

/** Result of a dry run: validation output plus live capability checks. */
data class TestReport(
    val issues: List<ValidationIssue>,
    val missingCapabilities: Set<Capability>,
    val stepCount: Int
) {
    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.ERROR }
}

/**
 * Shows what "Test automation" found. Nothing is executed: the workflow is
 * validated, its variables and selectors are checked structurally, and the
 * permissions it needs are compared against what is granted right now.
 */
@Composable
fun TestAutomationDialog(
    report: TestReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report.hasErrors) "Test found problems" else "Test passed") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!report.hasErrors) {
                    ResultLine(
                        icon = Icons.Outlined.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        text = "Trigger, conditions and ${report.stepCount} steps are valid"
                    )
                }

                report.issues
                    .sortedBy { it.severity.ordinal }
                    .forEach { issue ->
                        ResultLine(
                            icon = issue.severity.icon,
                            tint = issue.severity.tint(),
                            text = issue.message
                        )
                    }

                report.missingCapabilities.forEach { capability ->
                    ResultLine(
                        icon = Icons.Outlined.ReportProblem,
                        tint = MaterialTheme.colorScheme.tertiary,
                        text = "${capability.label} is not enabled yet — this workflow needs it"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Nothing was run: no notification was posted, no file changed " +
                        "and no app was touched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ResultLine(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

private val IssueSeverity.icon: ImageVector
    get() = when (this) {
        IssueSeverity.ERROR -> Icons.Outlined.ErrorOutline
        IssueSeverity.WARNING -> Icons.Outlined.ReportProblem
        IssueSeverity.INFO -> Icons.Outlined.Info
    }

@Composable
private fun IssueSeverity.tint(): Color = when (this) {
    IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
    IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
    IssueSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}
