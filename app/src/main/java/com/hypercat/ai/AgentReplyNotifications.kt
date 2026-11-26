package com.hypercat.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether the activity is visible so Agent replies only interrupt the user
 * with a notification after the app has moved to the background.
 */
object HyperCatAppVisibility {
    @Volatile
    var isForeground: Boolean = true
}

object AgentReplyNotifications {
    private const val CHANNEL_ID = "agent_replies"
    private val notificationIds = AtomicInteger(4100)

    fun showIfBackground(context: Context, reply: String) {
        if (HyperCatAppVisibility.isForeground || reply.isBlank()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Agent 对话消息", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "应用在后台时显示 HyperCat AI 的回复"
                    enableVibration(true)
                }
            )
        }

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("HyperCat AI · Agent 回复")
            .setContentText(reply.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply.take(3500)))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        runCatching {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(notificationIds.incrementAndGet(), notification)
            }
        }
    }
}
