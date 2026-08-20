package com.dhruw.autoflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.UiSessionStatus

/**
 * In-app surface for the active UI automation session: a progress banner
 * with Cancel while it runs, and the confirmation dialog when a workflow
 * reaches a RequireUserConfirmation step while AutoFlow is in the
 * foreground. (In the background, the equivalent notification takes over.)
 */
@Composable
fun UiAutomationSessionHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember {
        (context.applicationContext as AutoFlowApplication).container.uiAutomationSessionManager
    }
    val session by manager.session.collectAsState()
    val active = session ?: return

    if (active.status == UiSessionStatus.WAITING_FOR_CONFIRMATION) {
        AlertDialog(
            onDismissRequest = { /* An explicit choice is required. */ },
            title = { Text("AutoFlow is ready to continue") },
            text = {
                Column {
                    Text(active.confirmationPrompt ?: "Continue the automation?")
                    active.nextActionLabel?.let { label ->
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        Text(
                            text = "Next action: $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { manager.respondToConfirmation(true) }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { manager.respondToConfirmation(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (active.status == UiSessionStatus.RUNNING ||
        active.status == UiSessionStatus.WAITING_FOR_CONFIRMATION
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UI automation running",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Step ${active.currentStep} of ${active.totalSteps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { manager.cancel() }) { Text("Cancel") }
            }
        }
    }
}
