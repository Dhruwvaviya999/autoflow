package com.dhruw.autoflow.ui.automationeditor

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ConnectionEvent
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.label

/** Two-option chip row used by most system trigger/condition dialogs. */
@Composable
private fun TwoChoiceChips(
    first: String,
    second: String,
    firstSelected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = firstSelected,
            onClick = { onSelect(true) },
            label = { Text(first) }
        )
        FilterChip(
            selected = !firstSelected,
            onClick = { onSelect(false) },
            label = { Text(second) }
        )
    }
}

@Composable
private fun ComparisonChips(
    selected: LevelComparison,
    onSelect: (LevelComparison) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LevelComparison.LESS_OR_EQUAL, LevelComparison.LESS_THAN).forEach { c ->
                FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c.label) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LevelComparison.GREATER_OR_EQUAL, LevelComparison.GREATER_THAN).forEach { c ->
                FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c.label) })
            }
        }
    }
}

@Composable
private fun DialogNote(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// --- Battery ---

@Composable
fun BatteryLevelTriggerDialog(
    initial: Trigger.BatteryLevelTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.BatteryLevelTrigger) -> Unit
) {
    var comparison by remember { mutableStateOf(initial?.comparison ?: LevelComparison.LESS_OR_EQUAL) }
    var levelText by rememberSaveable { mutableStateOf(initial?.level?.toString() ?: "20") }
    val level = levelText.toIntOrNull()
    val valid = level != null && level in 1..100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery level") },
        text = {
            Column {
                ComparisonChips(comparison) { comparison = it }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it },
                    label = { Text("Level (%)") },
                    singleLine = true,
                    isError = levelText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                DialogNote(
                    "Runs once when the level crosses into this zone — not " +
                        "again on every percent while it stays there."
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(Trigger.BatteryLevelTrigger(comparison, level!!)) }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChargingTriggerDialog(
    initial: Trigger.ChargingStateTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.ChargingStateTrigger) -> Unit
) {
    var onCharging by rememberSaveable { mutableStateOf(initial?.onCharging ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Charging") },
        text = {
            Column {
                TwoChoiceChips("Starts", "Stops", onCharging) { onCharging = it }
                DialogNote("Runs once per plug/unplug, not repeatedly while charging.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Trigger.ChargingStateTrigger(onCharging)) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- Wi-Fi / Network ---

@Composable
fun WiFiTriggerDialog(
    initial: Trigger.WiFiConnectionTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.WiFiConnectionTrigger) -> Unit
) {
    var connected by rememberSaveable {
        mutableStateOf(initial?.event != ConnectionEvent.DISCONNECTED)
    }
    var ssid by rememberSaveable { mutableStateOf(initial?.ssid ?: "") }
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Status also visible in the Permission center. */ }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi") },
        text = {
            Column {
                TwoChoiceChips("Connects", "Disconnects", connected) { connected = it }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network name (blank = any)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DialogNote(
                    "Android only reveals Wi-Fi names to apps with the Location " +
                        "permission. “Any network” works without it; a specific " +
                        "name needs it. AutoFlow never uses your location."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (ssid.isNotBlank() &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    onConfirm(
                        Trigger.WiFiConnectionTrigger(
                            event = if (connected) ConnectionEvent.CONNECTED else ConnectionEvent.DISCONNECTED,
                            ssid = ssid.trim()
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NetworkTriggerDialog(
    initial: Trigger.NetworkAvailabilityTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.NetworkAvailabilityTrigger) -> Unit
) {
    var onAvailable by rememberSaveable { mutableStateOf(initial?.onAvailable ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network") },
        text = {
            Column {
                TwoChoiceChips("Becomes available", "Becomes unavailable", onAvailable) {
                    onAvailable = it
                }
                DialogNote("Any connection type — Wi-Fi or mobile data.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Trigger.NetworkAvailabilityTrigger(onAvailable)) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- Bluetooth ---

private data class BondedDevice(val address: String, val name: String)

private fun bondedDevices(context: android.content.Context): List<BondedDevice> = try {
    context.getSystemService(BluetoothManager::class.java)
        ?.adapter?.bondedDevices.orEmpty()
        .mapNotNull { device ->
            val address = device.address ?: return@mapNotNull null
            BondedDevice(address, runCatching { device.name }.getOrNull().orEmpty().ifBlank { address })
        }
        .sortedBy { it.name.lowercase() }
} catch (e: SecurityException) {
    emptyList()
}

@Composable
fun BluetoothTriggerDialog(
    initial: Trigger.BluetoothConnectionTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.BluetoothConnectionTrigger) -> Unit
) {
    val context = LocalContext.current
    var connected by rememberSaveable {
        mutableStateOf(initial?.event != ConnectionEvent.DISCONNECTED)
    }
    var deviceAddress by rememberSaveable { mutableStateOf(initial?.deviceAddress ?: "") }
    var deviceName by rememberSaveable { mutableStateOf(initial?.deviceName ?: "") }
    var devices by remember { mutableStateOf<List<BondedDevice>>(emptyList()) }
    var permissionAsked by remember { mutableStateOf(false) }

    fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionAsked = true
        if (granted) devices = bondedDevices(context)
    }

    // Load paired devices; ask for the Bluetooth permission the first time.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasBtPermission()) {
            devices = bondedDevices(context)
        } else if (!permissionAsked) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth device") },
        text = {
            Column {
                TwoChoiceChips("Connects", "Disconnects", connected) { connected = it }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Device", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    item {
                        DeviceRow(
                            label = "Any device",
                            selected = deviceAddress.isBlank(),
                            onClick = { deviceAddress = ""; deviceName = "" }
                        )
                    }
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            label = device.name,
                            selected = deviceAddress.equals(device.address, ignoreCase = true),
                            onClick = { deviceAddress = device.address; deviceName = device.name }
                        )
                    }
                }
                if (devices.isEmpty()) {
                    DialogNote(
                        if (hasBtPermission()) {
                            "No paired devices found. Pair the device in Android " +
                                "Bluetooth settings first, or use “Any device”."
                        } else {
                            "Bluetooth permission is needed to list paired devices " +
                                "and react to connections. Grant it in the Permission center."
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Trigger.BluetoothConnectionTrigger(
                            event = if (connected) ConnectionEvent.CONNECTED else ConnectionEvent.DISCONNECTED,
                            deviceAddress = deviceAddress,
                            deviceName = deviceName
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeviceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Screen / Headset ---

@Composable
fun ScreenTriggerDialog(
    initial: Trigger.ScreenStateTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.ScreenStateTrigger) -> Unit
) {
    var onScreenOn by rememberSaveable { mutableStateOf(initial?.onScreenOn ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screen") },
        text = {
            Column {
                TwoChoiceChips("Turns on", "Turns off", onScreenOn) { onScreenOn = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Trigger.ScreenStateTrigger(onScreenOn)) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun HeadsetTriggerDialog(
    initial: Trigger.HeadsetConnectionTrigger?,
    onDismiss: () -> Unit,
    onConfirm: (Trigger.HeadsetConnectionTrigger) -> Unit
) {
    var connected by rememberSaveable {
        mutableStateOf(initial?.event != ConnectionEvent.DISCONNECTED)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Headset") },
        text = {
            Column {
                TwoChoiceChips("Connects", "Disconnects", connected) { connected = it }
                DialogNote("Wired and Bluetooth audio devices count as headsets.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Trigger.HeadsetConnectionTrigger(
                            if (connected) ConnectionEvent.CONNECTED else ConnectionEvent.DISCONNECTED
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- System condition dialogs ---

@Composable
fun BatteryLevelConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.BatteryLevelCondition) -> Unit
) {
    var comparison by remember { mutableStateOf(LevelComparison.LESS_THAN) }
    var levelText by rememberSaveable { mutableStateOf("30") }
    val level = levelText.toIntOrNull()
    val valid = level != null && level in 1..100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery level") },
        text = {
            Column {
                ComparisonChips(comparison) { comparison = it }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it },
                    label = { Text("Level (%)") },
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
fun ChargingConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.IsChargingCondition) -> Unit
) {
    var charging by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Charging") },
        text = { TwoChoiceChips("Is charging", "Is not charging", charging) { charging = it } },
        confirmButton = {
            TextButton(onClick = { onConfirm(Condition.IsChargingCondition(charging)) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NetworkConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.NetworkAvailableCondition) -> Unit
) {
    var available by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network") },
        text = { TwoChoiceChips("Available", "Unavailable", available) { available = it } },
        confirmButton = {
            TextButton(onClick = { onConfirm(Condition.NetworkAvailableCondition(available)) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WiFiConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.WiFiConnectedCondition) -> Unit
) {
    var ssid by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connected to Wi-Fi") },
        text = {
            Column {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network name (blank = any)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DialogNote("Checking a specific name needs the Location permission (see Wi-Fi trigger).")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Condition.WiFiConnectedCondition(ssid.trim())) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScreenConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.ScreenOnCondition) -> Unit
) {
    var on by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screen") },
        text = { TwoChoiceChips("Is on", "Is off", on) { on = it } },
        confirmButton = {
            TextButton(onClick = { onConfirm(Condition.ScreenOnCondition(on)) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BluetoothConditionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Condition.BluetoothConnectedCondition) -> Unit
) {
    val context = LocalContext.current
    var deviceAddress by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<BondedDevice>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        devices = bondedDevices(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth device connected") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    item {
                        DeviceRow(
                            label = "Any device",
                            selected = deviceAddress.isBlank(),
                            onClick = { deviceAddress = ""; deviceName = "" }
                        )
                    }
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            label = device.name,
                            selected = deviceAddress.equals(device.address, ignoreCase = true),
                            onClick = { deviceAddress = device.address; deviceName = device.name }
                        )
                    }
                }
                DialogNote(
                    "Passes while the device is connected. Connections are " +
                        "tracked from Bluetooth events, so it may read as not " +
                        "connected right after a restart until the next event."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(Condition.BluetoothConnectedCondition(deviceAddress, deviceName)) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
