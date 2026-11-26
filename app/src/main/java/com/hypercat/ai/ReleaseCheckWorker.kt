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
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReleaseCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HyperCatAI-Android")
            }
            if (connection.responseCode !in 200..299) return Result.retry()
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name").ifBlank { release.optString("name") }
            if (tag.isBlank()) return Result.retry()
            val current = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0).versionName ?: "0.0.0"
            if (!isNewer(tag, current)) return Result.success()

            val prefs = applicationContext.getSharedPreferences("hypercat_update_state", Context.MODE_PRIVATE)
            if (prefs.getString("notified_version", "") == tag) return Result.success()

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "HyperCat AI 新版本提醒"
                    }
                )
            }
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 7301, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notes = release.optString("body").replace(Regex("\\s+"), " ").take(110)
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("HyperCat AI 发现新版本 $tag")
                .setContentText(notes.ifBlank { "点击打开应用查看更新详情。" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(notes.ifBlank { "点击打开应用查看更新详情。" }))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString("notified_version", tag).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(value: String) = value.trim().removePrefix("v").split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/yanglin950915-sketch/HyperCatAI-next/releases/latest"
        private const val CHANNEL_ID = "hypercat_app_updates"
        private const val NOTIFICATION_ID = 7301
    }
}
