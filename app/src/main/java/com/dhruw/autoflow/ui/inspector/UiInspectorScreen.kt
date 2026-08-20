package com.dhruw.autoflow.ui.inspector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.services.accessibility.UiInspector

/**
 * User-assisted element inspector. While Inspect mode is on, tapping
 * elements in any app records their selector-relevant attributes here.
 * Explicitly started/stopped; auto-expires; in-memory only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiInspectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as AutoFlowApplication).container
    }
    val inspector = container.uiInspector
    val enabled by inspector.enabled.collectAsState()
    val elements by inspector.elements.collectAsState()
    val accessibilityEnabled = remember { container.accessibilityAccessManager.isEnabled() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Inspect UI") },
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
                .padding(horizontal = 20.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Inspect mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (enabled) {
                                "On — open any app and tap an element to capture it. " +
                                    "Turns itself off after 10 minutes."
                            } else {
                                "Off — nothing is captured"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = accessibilityEnabled,
                        onCheckedChange = { inspector.setEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (accessibilityEnabled) {
                    "Captured elements stay on this device and are cleared when " +
                        "you turn Inspect mode off. Password fields are captured " +
                        "without their contents."
                } else {
                    "Enable Accessibility for AutoFlow first (Settings → " +
                        "Permission center → Accessibility)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            if (elements.isEmpty()) {
                Text(
                    text = "No elements captured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(elements, key = { it.capturedAt }) { element ->
                        InspectedElementCard(element)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectedElementCard(element: UiInspector.InspectedElement) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = element.appLabel,
                style = MaterialTheme.typography.titleSmall
            )
            AttributeRow("Text", if (element.isPassword) "(protected)" else element.text)
            AttributeRow("Description", element.contentDescription)
            AttributeRow("View ID", element.viewId)
            AttributeRow("Class", element.className)
            AttributeRow(
                "Abilities",
                listOfNotNull(
                    "clickable".takeIf { element.isClickable },
                    "long-clickable".takeIf { element.isLongClickable },
                    "editable".takeIf { element.isEditable },
                    "scrollable".takeIf { element.isScrollable },
                    "password".takeIf { element.isPassword }
                ).joinToString(", ")
            )
        }
    }
}

@Composable
private fun AttributeRow(name: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(
            text = "$name: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
