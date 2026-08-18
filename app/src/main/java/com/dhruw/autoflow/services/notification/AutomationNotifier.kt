package com.dhruw.autoflow.services.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dhruw.autoflow.MainActivity
import com.dhruw.autoflow.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts notifications produced by automations on the "Automations" channel.
 * Callers must handle [SecurityException]/failure — permission can be
 * revoked at any time.
 */
class AutomationNotifier(private val context: Context) {

    private val nextId = AtomicInteger(1000)

    fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notify(title: String, message: String) {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(nextId.getAndIncrement(), notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Automations",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications posted by your automations"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "automations"
    }
}
