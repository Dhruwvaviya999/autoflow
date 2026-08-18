package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Trigger

@Composable
fun NotificationActionDialog(
    initial: Action.ShowNotificationAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.ShowNotificationAction) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var message by rememberSaveable { mutableStateOf(initial?.message ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show notification") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(Action.ShowNotificationAction(title.trim(), message.trim())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DelayActionDialog(
    initial: Action.DelayAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.DelayAction) -> Unit
) {
    var secondsText by rememberSaveable {
        mutableStateOf(initial?.let { (it.durationMillis / 1000.0).toString().removeSuffix(".0") } ?: "2")
    }
    val seconds = secondsText.toDoubleOrNull()
    val valid = seconds != null && seconds > 0 && seconds <= 3600

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delay") },
        text = {
            Column {
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it },
                    label = { Text("Seconds") },
                    singleLine = true,
                    isError = secondsText.isNotEmpty() && !valid,
                    supportingText = {
                        if (secondsText.isNotEmpty() && !valid) {
                            Text("Enter a value between 0 and 3600")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(Action.DelayAction((seconds!! * 1000).toLong())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LogActionDialog(
    initial: Action.LogAction?,
    onDismiss: () -> Unit,
    onConfirm: (Action.LogAction) -> Unit
) {
    var message by rememberSaveable { mutableStateOf(initial?.message ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log") },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = message.isNotBlank(),
                onClick = { onConfirm(Action.LogAction(message.trim())) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTriggerDialog(
    initial: Trigger.TimeTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.TimeTrigger) -> Unit
) {
    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 8,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true
    )
    var repeat by remember {
        mutableStateOf(initial?.repeat ?: Trigger.TimeTrigger.Repeat.DAILY)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time trigger") },
        text = {
            Column {
                TimeInput(state = timeState)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeat == Trigger.TimeTrigger.Repeat.DAILY,
                        onClick = { repeat = Trigger.TimeTrigger.Repeat.DAILY },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = repeat == Trigger.TimeTrigger.Repeat.ONCE,
                        onClick = { repeat = Trigger.TimeTrigger.Repeat.ONCE },
                        label = { Text("Once") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Runs in the background near the set time. Android may delay it by a few minutes to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(Trigger.TimeTrigger(timeState.hour, timeState.minute, repeat))
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
