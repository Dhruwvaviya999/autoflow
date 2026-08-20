package com.dhruw.autoflow.services.accessibility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dhruw.autoflow.MainActivity
import com.dhruw.autoflow.R

/**
 * Session UI for a running UI automation, notification-based by design —
 * Phase 7 deliberately avoids the SYSTEM_ALERT_WINDOW overlay permission.
 *
 * Two surfaces, one notification id (they replace each other):
 * - Progress: "Step 3 of 6 — Waiting for …" with a Cancel action (silent).
 * - Confirmation: "AutoFlow is ready to continue" with Continue/Cancel
 *   (high importance so it appears over the target app).
 *
 * Content is limited to step labels and selector summaries — never text
 * entered into fields, never screen content. Posting failures (permission
 * revoked) are swallowed: the in-app session banner remains, and an
 * unanswerable confirmation times out to a safe stop.
 */
class UiAutomationNotifications(private val context: Context) {

    fun showProgress(stepNumber: Int, totalSteps: Int, stepLabel: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AutoFlow automation running")
            .setContentText("Step $stepNumber of $totalSteps — $stepLabel")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", controlIntent(UiAutomationControlReceiver.ACTION_CANCEL, 1))
            .build()
        post(notification)
    }

    fun showConfirmation(prompt: String, nextActionLabel: String) {
        ensureChannels()
        val text = if (nextActionLabel.isBlank()) prompt else "$prompt\nNext action: $nextActionLabel"
        val notification = NotificationCompat.Builder(context, CONFIRM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AutoFlow is ready to continue")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .addAction(0, "Continue", controlIntent(UiAutomationControlReceiver.ACTION_CONTINUE, 2))
            .addAction(0, "Cancel", controlIntent(UiAutomationControlReceiver.ACTION_DECLINE, 3))
            .build()
        post(notification)
    }

    fun clear() {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun post(notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked — session banner in-app still works.
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun controlIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, UiAutomationControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "UI automation progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress of running UI automations, with Cancel" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CONFIRM_CHANNEL_ID,
                "UI automation confirmations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Asks before AutoFlow performs a consequential step" }
        )
    }

    private companion object {
        const val PROGRESS_CHANNEL_ID = "ui_automation_progress"
        const val CONFIRM_CHANNEL_ID = "ui_automation_confirm"
        const val NOTIFICATION_ID = 9100
    }
}
