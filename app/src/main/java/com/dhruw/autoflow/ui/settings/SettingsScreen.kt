package com.dhruw.autoflow.ui.settings

import android.Manifest
import android.content.Intent
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dhruw.autoflow.ui.components.SectionHeader

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
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
                title = "Accessibility",
                description = "Not required yet",
                statusColor = StatusColor.NEUTRAL,
                onClick = null
            )
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
