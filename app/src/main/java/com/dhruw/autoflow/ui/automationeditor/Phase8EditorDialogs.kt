package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.automation.engine.TemplateResolver
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.label
import com.dhruw.autoflow.automation.model.summary

/** Editor for [Action.SetVariableAction]: name = value, value may use templates. */
@Composable
fun SetVariableActionDialog(
    initial: Action.SetVariableAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.SetVariableAction) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var value by rememberSaveable { mutableStateOf(initial?.value ?: "") }
    val nameValid = TemplateResolver.VALID_LOCAL_NAME.matches(name.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set variable") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. jobType)") },
                    singleLine = true,
                    isError = name.isNotBlank() && !nameValid,
                    supportingText = {
                        if (name.isNotBlank() && !nameValid) {
                            Text("Letters, digits and _ only, starting with a letter")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The value may use {{variable}} placeholders, e.g. " +
                        "{{notification.text}} or an earlier variable. Later steps " +
                        "can then use {{${name.trim().ifBlank { "name" }}}}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValid,
                onClick = { onConfirm(Action.SetVariableAction(name.trim(), value)) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Editor for [Action.GroupMarker]: a purely organizational header. */
@Composable
fun GroupMarkerDialog(
    initial: Action.GroupMarker?,
    onDismiss: () -> Unit,
    onConfirm: (Action.GroupMarker) -> Unit
) {
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group label") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Preparation)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Groups only organize the step list — they don't change what runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onConfirm(Action.GroupMarker(label.trim())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- Branch (If / else) -----------------------------------------------------

/** Condition kinds the branch editor can build directly. */
private enum class BranchConditionKind(val label: String) {
    NOTIFICATION_TEXT("Notification text"),
    NOTIFICATION_TITLE("Notification title"),
    BATTERY_LEVEL("Battery level"),
    CHARGING("Charging"),
    NETWORK("Network"),
    SCREEN("Screen")
}

/** Simple nested actions the branch editor can add directly. */
private enum class BranchInnerKind { NOTIFICATION, DELAY, LOG, SET_VARIABLE }

/**
 * Editor for [Action.BranchAction]. First iteration by design: one condition
 * (text/title match, battery, charging, network, screen) and simple nested
 * actions (notification, delay, log, set variable). Branches built elsewhere
 * (import) with richer contents still display and run — this editor replaces
 * the whole branch when saved.
 */
@Composable
fun BranchActionDialog(
    initial: Action.BranchAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.BranchAction) -> Unit
) {
    var condition by remember { mutableStateOf(initial?.condition) }
    var thenActions by remember { mutableStateOf(initial?.thenActions ?: emptyList()) }
    var elseActions by remember { mutableStateOf(initial?.elseActions ?: emptyList()) }

    var kind by remember { mutableStateOf<BranchConditionKind?>(null) }
    // Which nested list an inner action dialog adds to: true = THEN.
    var innerTarget by remember { mutableStateOf(true) }
    var innerKind by remember { mutableStateOf<BranchInnerKind?>(null) }

    // Condition sub-editors, one at a time on top of the branch dialog.
    when (kind) {
        BranchConditionKind.NOTIFICATION_TEXT, BranchConditionKind.NOTIFICATION_TITLE ->
            NotificationTextMatchConditionDialog(
                isTitle = kind == BranchConditionKind.NOTIFICATION_TITLE,
                onDismiss = { kind = null },
                onConfirm = { condition = it; kind = null }
            )
        BranchConditionKind.BATTERY_LEVEL -> BranchBatteryConditionDialog(
            onDismiss = { kind = null },
            onConfirm = { condition = it; kind = null }
        )
        BranchConditionKind.CHARGING -> BranchToggleConditionDialog(
            title = "Charging",
            optionOn = "Phone is charging",
            optionOff = "Phone is not charging",
            onDismiss = { kind = null },
            onConfirm = { on -> condition = Condition.IsChargingCondition(on); kind = null }
        )
        BranchConditionKind.NETWORK -> BranchToggleConditionDialog(
            title = "Network",
            optionOn = "Network is available",
            optionOff = "Network is unavailable",
            onDismiss = { kind = null },
            onConfirm = { on -> condition = Condition.NetworkAvailableCondition(on); kind = null }
        )
        BranchConditionKind.SCREEN -> BranchToggleConditionDialog(
            title = "Screen",
            optionOn = "Screen is on",
            optionOff = "Screen is off",
            onDismiss = { kind = null },
            onConfirm = { on -> condition = Condition.ScreenOnCondition(on); kind = null }
        )
        null -> Unit
    }

    // Inner action editors.
    when (innerKind) {
        BranchInnerKind.NOTIFICATION -> NotificationActionDialog(
            initial = null,
            onDismiss = { innerKind = null },
            onConfirm = { a ->
                if (innerTarget) thenActions = thenActions + a else elseActions = elseActions + a
                innerKind = null
            }
        )
        BranchInnerKind.DELAY -> DelayActionDialog(
            initial = null,
            onDismiss = { innerKind = null },
            onConfirm = { a ->
                if (innerTarget) thenActions = thenActions + a else elseActions = elseActions + a
                innerKind = null
            }
        )
        BranchInnerKind.LOG -> LogActionDialog(
            initial = null,
            onDismiss = { innerKind = null },
            onConfirm = { a ->
                if (innerTarget) thenActions = thenActions + a else elseActions = elseActions + a
                innerKind = null
            }
        )
        BranchInnerKind.SET_VARIABLE -> SetVariableActionDialog(
            initial = null,
            onDismiss = { innerKind = null },
            onConfirm = { a ->
                if (innerTarget) thenActions = thenActions + a else elseActions = elseActions + a
                innerKind = null
            }
        )
        null -> Unit
    }

    if (kind != null || innerKind != null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("If / else") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("IF", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = condition?.summary ?: "No condition yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (condition == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BranchConditionKind.entries.take(3).forEach { candidate ->
                        FilterChip(
                            selected = false,
                            onClick = { kind = candidate },
                            label = { Text(candidate.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BranchConditionKind.entries.drop(3).forEach { candidate ->
                        FilterChip(
                            selected = false,
                            onClick = { kind = candidate },
                            label = { Text(candidate.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                BranchActionList(
                    header = "THEN",
                    actions = thenActions,
                    onAdd = { innerTarget = true; innerKind = it },
                    onRemove = { i -> thenActions = thenActions.filterIndexed { j, _ -> j != i } }
                )

                Spacer(modifier = Modifier.height(16.dp))
                BranchActionList(
                    header = "ELSE (optional)",
                    actions = elseActions,
                    onAdd = { innerTarget = false; innerKind = it },
                    onRemove = { i -> elseActions = elseActions.filterIndexed { j, _ -> j != i } }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = condition != null && thenActions.isNotEmpty(),
                onClick = { onConfirm(Action.BranchAction(condition!!, thenActions, elseActions)) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BranchActionList(
    header: String,
    actions: List<Action>,
    onAdd: (BranchInnerKind) -> Unit,
    onRemove: (Int) -> Unit
) {
    Text(header, style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    actions.forEachIndexed { index, action ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${action.displayName} · ${action.summary}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onRemove(index) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = false, onClick = { onAdd(BranchInnerKind.NOTIFICATION) },
            label = { Text("+ Notify", style = MaterialTheme.typography.labelSmall) })
        FilterChip(selected = false, onClick = { onAdd(BranchInnerKind.DELAY) },
            label = { Text("+ Delay", style = MaterialTheme.typography.labelSmall) })
        FilterChip(selected = false, onClick = { onAdd(BranchInnerKind.LOG) },
            label = { Text("+ Log", style = MaterialTheme.typography.labelSmall) })
        FilterChip(selected = false, onClick = { onAdd(BranchInnerKind.SET_VARIABLE) },
            label = { Text("+ Variable", style = MaterialTheme.typography.labelSmall) })
    }
}

@Composable
private fun BranchBatteryConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.BatteryLevelCondition) -> Unit
) {
    var comparison by remember { mutableStateOf(LevelComparison.LESS_OR_EQUAL) }
    var levelText by rememberSaveable { mutableStateOf("20") }
    val level = levelText.toIntOrNull()
    val valid = level != null && level in 0..100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery level") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(LevelComparison.LESS_OR_EQUAL, LevelComparison.GREATER_OR_EQUAL).forEach { c ->
                        FilterChip(
                            selected = comparison == c,
                            onClick = { comparison = c },
                            label = { Text(c.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it },
                    label = { Text("Percent (0–100)") },
                    singleLine = true,
                    isError = levelText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(Condition.BatteryLevelCondition(comparison, level!!)) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BranchToggleConditionDialog(
    title: String,
    optionOn: String,
    optionOff: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var on by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (on) optionOn else optionOff,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = on, onCheckedChange = { on = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(on) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
