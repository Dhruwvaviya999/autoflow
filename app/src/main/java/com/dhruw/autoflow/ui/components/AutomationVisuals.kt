package com.dhruw.autoflow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.Trigger

/** UI-layer mapping from domain types to icons/colors. */

val Trigger.icon: ImageVector
    get() = when (this) {
        is Trigger.ManualTrigger -> Icons.Outlined.TouchApp
        is Trigger.TimeTrigger -> Icons.Outlined.Schedule
        is Trigger.FileTrigger -> Icons.Outlined.FolderOpen
        is Trigger.NotificationTrigger -> Icons.Outlined.NotificationsActive
        is Trigger.BatteryLevelTrigger -> Icons.Outlined.BatteryAlert
        is Trigger.ChargingStateTrigger -> Icons.Outlined.Bolt
        is Trigger.WiFiConnectionTrigger -> Icons.Outlined.Wifi
        is Trigger.NetworkAvailabilityTrigger -> Icons.Outlined.NetworkCheck
        is Trigger.BluetoothConnectionTrigger -> Icons.Outlined.Bluetooth
        is Trigger.ScreenStateTrigger -> Icons.Outlined.Smartphone
        is Trigger.HeadsetConnectionTrigger -> Icons.Outlined.Headset
        is Trigger.DeviceBootTrigger -> Icons.Outlined.RestartAlt
    }

val Condition.icon: ImageVector
    get() = when (this) {
        is Condition.AlwaysCondition -> Icons.Outlined.CheckCircle
        is Condition.FileExtensionCondition -> Icons.Outlined.Extension
        is Condition.FileNameContainsCondition -> Icons.Outlined.Search
        is Condition.FileSizeCondition -> Icons.Outlined.Straighten
        is Condition.NotificationAppCondition -> Icons.Outlined.Apps
        is Condition.NotificationTitleCondition -> Icons.Outlined.Title
        is Condition.NotificationTextCondition -> Icons.Outlined.Notifications
        is Condition.NotificationCategoryCondition -> Icons.Outlined.Category
        is Condition.AndCondition -> Icons.Outlined.AccountTree
        is Condition.OrCondition -> Icons.Outlined.AccountTree
        is Condition.NotCondition -> Icons.Outlined.Block
        is Condition.BatteryLevelCondition -> Icons.Outlined.BatteryAlert
        is Condition.IsChargingCondition -> Icons.Outlined.Bolt
        is Condition.NetworkAvailableCondition -> Icons.Outlined.NetworkCheck
        is Condition.WiFiConnectedCondition -> Icons.Outlined.Wifi
        is Condition.ScreenOnCondition -> Icons.Outlined.Smartphone
        is Condition.BluetoothConnectedCondition -> Icons.Outlined.Bluetooth
    }

val Action.icon: ImageVector
    get() = when (this) {
        is Action.ShowNotificationAction -> Icons.Outlined.Notifications
        is Action.DelayAction -> Icons.Outlined.HourglassEmpty
        is Action.LogAction -> Icons.AutoMirrored.Outlined.Article
        is Action.CopyFileAction -> Icons.Outlined.FileCopy
        is Action.MoveFileAction -> Icons.AutoMirrored.Outlined.DriveFileMove
        is Action.RenameFileAction -> Icons.Outlined.DriveFileRenameOutline
        is Action.InstagramAnalysisAction -> Icons.Outlined.PersonSearch
        is Action.SaveNotificationAction -> Icons.Outlined.Save
        is Action.UiAutomationAction -> Icons.Outlined.TouchApp
        is Action.SetVariableAction -> Icons.Outlined.DataObject
        is Action.BranchAction -> Icons.Outlined.CallSplit
        is Action.DisabledAction -> wrapped.icon
        is Action.GroupMarker -> Icons.Outlined.Label
    }

val ExecutionStatus.icon: ImageVector
    get() = when (this) {
        ExecutionStatus.RUNNING -> Icons.Outlined.Sync
        ExecutionStatus.SUCCESS -> Icons.Outlined.CheckCircle
        ExecutionStatus.FAILED -> Icons.Outlined.ErrorOutline
        ExecutionStatus.CANCELLED -> Icons.Outlined.Cancel
        ExecutionStatus.SKIPPED -> Icons.Outlined.SkipNext
    }

val ExecutionStatus.label: String
    get() = when (this) {
        ExecutionStatus.RUNNING -> "Running"
        ExecutionStatus.SUCCESS -> "Success"
        ExecutionStatus.FAILED -> "Failed"
        ExecutionStatus.CANCELLED -> "Cancelled"
        ExecutionStatus.SKIPPED -> "Skipped"
    }

@Composable
fun ExecutionStatus.color(): Color = when (this) {
    ExecutionStatus.RUNNING -> MaterialTheme.colorScheme.primary
    ExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
    ExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
    ExecutionStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    ExecutionStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
}
