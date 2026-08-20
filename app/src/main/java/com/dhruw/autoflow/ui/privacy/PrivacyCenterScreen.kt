package com.dhruw.autoflow.ui.privacy

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Plain-language account of what AutoFlow stores and what it can see. Every
 * claim here is checked against the code in the Phase 8 privacy audit — the
 * app has no INTERNET permission, so the "stays on this device" statements
 * are structural, not policy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
            SectionLabel("How AutoFlow works")
            GuaranteeCard(
                listOf(
                    "No internet permission — AutoFlow cannot make network requests",
                    "No cloud database and no account",
                    "No analytics or telemetry libraries",
                    "No automatic uploads or backups",
                    "No remote control of this device"
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("What is stored on this device")
            InfoCard(
                title = "Automations",
                body = "The triggers, conditions and steps you create, in AutoFlow's " +
                    "local database."
            )
            InfoCard(
                title = "Execution history",
                body = "One entry per run with its outcome and step labels. Newest " +
                    "200 runs are kept; older ones are removed automatically."
            )
            InfoCard(
                title = "Saved notifications",
                body = "Only when an automation uses the Save notification action. " +
                    "Newest 500 records are kept."
            )
            InfoCard(
                title = "Imported workflows",
                body = "Workflow files you import become ordinary automations. They " +
                    "arrive switched off until you review them."
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("Features that can see sensitive data")
            InfoCard(
                title = "Notification access",
                body = "Lets notification triggers read what other apps put into their " +
                    "notifications. Content is matched in memory and is not stored " +
                    "unless you add the Save notification action."
            )
            InfoCard(
                title = "Accessibility",
                body = "Lets UI automations read the screen and tap elements — only " +
                    "while one of your automations is running or while the Inspect UI " +
                    "tool is on. Screen content is never stored or sent. AutoFlow " +
                    "refuses to type into password, PIN, OTP and payment fields, and " +
                    "pauses for your confirmation before consequential steps."
            )
            InfoCard(
                title = "Folder access",
                body = "File triggers and file actions work only inside folders you " +
                    "pick yourself, through Android's storage picker."
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("Sharing a workflow")
            InfoCard(
                title = "What an exported file contains",
                body = "Only the workflow definition: its trigger, conditions and " +
                    "steps. Execution history, saved notifications, folder contents, " +
                    "screen data and device identifiers are never included."
            )

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
private fun GuaranteeCard(points: List<String>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            points.forEachIndexed { index, point ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
