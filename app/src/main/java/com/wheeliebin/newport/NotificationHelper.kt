package com.wheeliebin.newport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ID = "bin_reminders"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Bin day reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminds you the evening before your bins are collected"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showBinReminder(context: Context, binTypes: List<String>) {
        ensureChannel(context)

        val title = if (binTypes.size == 1) {
            "Bins out: ${binTypes.first()}"
        } else {
            "Bins out: ${binTypes.joinToString(", ")}"
        }
        val text = "Collection is due tomorrow — don't forget to put it out tonight."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            manager.areNotificationsEnabled()
        ) {
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}
