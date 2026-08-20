package com.dhruw.autoflow.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.services.accessibility.AccessibilityAccessManager
import com.dhruw.autoflow.services.notification.NotificationAccessManager
import com.dhruw.autoflow.ui.components.SectionHeader
import com.dhruw.autoflow.ui.testlab.AccessibilityTestLabActivity

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenInspector: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenData: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }

    // Permission state is re-read on every resume so returning from system
    // settings reflects the real status without restarting the app.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }
    val notificationsAllowed = remember(refreshKey) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val container = remember {
        (context.applicationContext as AutoFlowApplication).container
    }
    val accessManager = container.notificationAccessManager
    val notificationAccess = remember(refreshKey) { accessManager.status() }
    val accessibilityManager = container.accessibilityAccessManager
    val accessibilityStatus = remember(refreshKey) { accessibilityManager.status() }
    // Bluetooth is a runtime permission only from Android 12.
    val bluetoothAllowed = remember(refreshKey) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationAllowed = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val ignoringBatteryOptimizations = remember(refreshKey) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    var deniedOnce by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshKey++
        if (!granted) deniedOnce = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Surfaces testTags as accessibility view IDs so ViewId
            // selectors can target this screen (same pattern as the Test Lab).
            .semantics { testTagsAsResourceId = true }
            .testTag("settings_list")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Permission center")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                description = if (notificationsAllowed) {
                    "Allowed — automations can post notifications"
                } else {
                    "Not allowed — notification actions will fail. Tap to allow."
                },
                statusColor = if (notificationsAllowed) StatusColor.OK else StatusColor.WARN,
                onClick = if (notificationsAllowed) null else {
                    {
                        // First ask via the system dialog; once it stops being
                        // shown (permanent denial or channel-level block), the
                        // app's notification settings are the only way back.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !deniedOnce) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.NotificationsActive,
                title = "Notification access",
                description = when (notificationAccess) {
                    NotificationAccessManager.Status.GRANTED ->
                        "Connected — notification triggers are active"
                    NotificationAccessManager.Status.NOT_GRANTED ->
                        "Not connected — needed only for notification triggers. Tap to grant."
                    NotificationAccessManager.Status.UNAVAILABLE ->
                        "Unavailable — this device blocks reading the setting"
                },
                statusColor = when (notificationAccess) {
                    NotificationAccessManager.Status.GRANTED -> StatusColor.OK
                    NotificationAccessManager.Status.NOT_GRANTED -> StatusColor.NEUTRAL
                    NotificationAccessManager.Status.UNAVAILABLE -> StatusColor.WARN
                },
                onClick = if (notificationAccess == NotificationAccessManager.Status.GRANTED) {
                    null
                } else {
                    { context.startActivity(accessManager.settingsIntent()) }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Bluetooth,
                title = "Bluetooth",
                description = if (bluetoothAllowed) {
                    "Allowed — Bluetooth automations can react to devices"
                } else {
                    "Not allowed — needed only for Bluetooth triggers. Tap to allow."
                },
                statusColor = if (bluetoothAllowed) StatusColor.OK else StatusColor.NEUTRAL,
                onClick = if (bluetoothAllowed) null else {
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Wifi,
                title = "Wi-Fi network names",
                description = if (locationAllowed) {
                    "Available — automations can match specific Wi-Fi names"
                } else {
                    "Android needs the Location permission to reveal Wi-Fi names. " +
                        "Only needed to match a specific network; \"any network\" " +
                        "works without it. Tap to allow."
                },
                statusColor = if (locationAllowed) StatusColor.OK else StatusColor.NEUTRAL,
                onClick = if (locationAllowed) null else {
                    { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "Background scheduling",
                description = "Available — scheduled automations run near their set " +
                    "time; Android may delay them slightly to save battery",
                statusColor = StatusColor.OK,
                onClick = null
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "Battery optimization",
                description = if (ignoringBatteryOptimizations) {
                    "Unrestricted — best reliability for scheduled automations"
                } else {
                    "Optimized — scheduled runs may be delayed. Tap to review."
                },
                statusColor = if (ignoringBatteryOptimizations) StatusColor.OK else StatusColor.NEUTRAL,
                onClick = if (ignoringBatteryOptimizations) null else {
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Accessibility,
                title = "Accessibility Automation",
                description = when (accessibilityStatus) {
                    AccessibilityAccessManager.Status.ENABLED ->
                        "Enabled — your UI automations can run"
                    AccessibilityAccessManager.Status.DISABLED ->
                        "Disabled — AutoFlow uses Android Accessibility only for " +
                            "UI automations you create. Tap to enable."
                    AccessibilityAccessManager.Status.UNAVAILABLE ->
                        "Unavailable — this device blocks reading the setting"
                },
                statusColor = when (accessibilityStatus) {
                    AccessibilityAccessManager.Status.ENABLED -> StatusColor.OK
                    AccessibilityAccessManager.Status.DISABLED -> StatusColor.NEUTRAL
                    AccessibilityAccessManager.Status.UNAVAILABLE -> StatusColor.WARN
                },
                onClick = if (accessibilityStatus == AccessibilityAccessManager.Status.ENABLED) {
                    null
                } else {
                    { context.startActivity(accessibilityManager.settingsIntent()) }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Privacy statement for notification access — must stay accurate:
        // notification content is processed on-device only, nothing is
        // uploaded. Revisit if a network action type is ever added.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Notification content is processed locally on this " +
                            "device by AutoFlow. AutoFlow does not upload " +
                            "notification content to a server. Notifications are " +
                            "only stored if an automation uses the Save " +
                            "notification action. AutoFlow can only read what " +
                            "apps put into their notifications — some apps hide " +
                            "or shorten content. Accessibility is used only while " +
                            "a UI automation you created is running or while the " +
                            "Inspect UI tool is on; screen content is never " +
                            "stored or sent anywhere, and AutoFlow never enters " +
                            "passwords or payment details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "System")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Security,
                title = "App permissions",
                description = "Review what AutoFlow can access",
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Notification settings",
                description = "Control how AutoFlow notifies you",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Automation")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Dashboard,
                title = "Templates",
                description = "Start from a ready-made workflow",
                onClick = onOpenTemplates
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Privacy")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Shield,
                title = "Privacy center",
                description = "What AutoFlow stores and what it can see",
                onClick = onOpenPrivacy
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Storage")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Data",
                description = "Back up, import, and clear local data",
                onClick = onOpenData
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Developer tools")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Search,
                title = "Inspect UI",
                description = "Capture element details (text, ID, class) by tapping " +
                    "them — for building selectors",
                onClick = onOpenInspector
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Science,
                title = "Accessibility Test Lab",
                description = "Safe screen with stable buttons and fields for " +
                    "testing UI automations",
                onClick = {
                    context.startActivity(
                        Intent(context, AccessibilityTestLabActivity::class.java)
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.MonitorHeart,
                title = "Diagnostics",
                description = "Scheduler state, database counts and an opt-in event log",
                onClick = onOpenDiagnostics
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Appearance")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = "Theme",
                description = "Dark",
                onClick = null
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "About")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "AutoFlow",
                description = "Version $versionName",
                onClick = null
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private enum class StatusColor { OK, WARN, NEUTRAL }

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    statusColor: StatusColor? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = when (statusColor) {
                StatusColor.OK -> Color(0xFF66BB6A)
                StatusColor.WARN -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
