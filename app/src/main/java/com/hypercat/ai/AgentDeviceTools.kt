package com.hypercat.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object AgentDeviceTools {
    fun handleSystemAction(context: Context, text: String): String? {
        val value = text.trim()
        val alarm = Regex("(?:设置|添加|帮我)?(?:一个)?(?:闹钟|提醒我)(?:在|到|为)?\\s*(上午|下午|晚上)?\\s*(\\d{1,2})(?:[:：点时](\\d{1,2})分?)?").find(value)
        if (alarm != null) {
            var hour = alarm.groupValues[2].toIntOrNull() ?: return "没有识别出闹钟时间，请说例如：明天早上 7:30 叫我起床。"
            val minute = alarm.groupValues[3].toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) return "闹钟时间无效，请重新说出小时和分钟。"
            when (alarm.groupValues[1]) {
                "下午", "晚上" -> if (hour in 1..11) hour += 12
                "上午" -> if (hour == 12) hour = 0
            }
            val label = value.substringAfter("闹钟", "").substringAfter("叫我", "HyperCat AI 闹钟").trim().ifBlank { "HyperCat AI 闹钟" }.take(40)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                "已打开系统闹钟确认页面：${hour}:${minute.toString().padStart(2, '0')}。请在系统页面确认保存。"
            } catch (_: Exception) { "无法打开系统闹钟应用，请确认设备已安装闹钟应用。" }
        }

        if (listOf("日历", "日程", "会议", "日历事件").any { value.contains(it) } &&
            listOf("创建", "添加", "安排", "设置", "新建").any { value.contains(it) }) {
            val title = value.substringAfter("日历", "").substringAfter("日程", "").trim()
                .ifBlank { value.removePrefix("帮我").take(60) }.take(80)
            val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, "由 HyperCat AI 创建，请确认时间和日历后保存。")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                "已打开系统日历新建事件页面，并填入标题。请核对日期、时间和日历后保存。"
            } catch (_: Exception) { "无法打开日历应用，请先安装或启用系统日历。" }
        }

        val deviceCommand = when {
            value.contains("返回上一页") || value == "返回" || value == "后退" -> "返回"
            value.contains("回到桌面") || value.contains("返回桌面") -> "回到桌面"
            value.contains("最近任务") -> "最近任务"
            value.contains("下拉通知栏") || value == "打开通知栏" -> "下拉通知栏"
            value.contains("快捷设置") || value.contains("控制中心") -> "打开快速设置"
            value.contains("向上滑动") || value == "上滑" -> "向上滑动"
            value.contains("向下滑动") || value == "下滑" -> "向下滑动"
            value.startsWith("点击") -> value
            else -> null
        }
        if (deviceCommand != null) {
            return if (HyperCatAccessibilityService.executeCommand(deviceCommand)) "已执行手机操作：$deviceCommand"
            else "尚未执行手机操作。请先在系统设置中启用 HyperCat AI 无障碍服务，并确保当前页面允许该操作。"
        }
        return null
    }

    suspend fun currentWeather(context: Context): String = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return@withContext "查询当地天气需要位置权限。请允许大致位置，或直接告诉我城市名称。"
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        }.getOrNull() ?: return@withContext "暂时获取不到设备位置。请开启定位服务后重试，或告诉我城市名称。"
        val geocoder = Geocoder(context, Locale.getDefault())
        val place = runCatching {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }.getOrNull()
        val city = listOfNotNull(place?.locality, place?.subAdminArea, place?.adminArea).firstOrNull().orEmpty().ifBlank { "当前位置" }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) return@withContext "天气服务暂时不可用（HTTP $code），请稍后重试。"
            val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONObject("current")
                ?: return@withContext "天气服务暂未返回当前天气。"
            val temp = current.optDouble("temperature_2m", Double.NaN)
            val feels = current.optDouble("apparent_temperature", Double.NaN)
            val humidity = current.optInt("relative_humidity_2m", -1)
            val wind = current.optDouble("wind_speed_10m", Double.NaN)
            val rain = current.optDouble("precipitation", Double.NaN)
            val codeValue = current.optInt("weather_code", -1)
            val condition = when (codeValue) {
                0 -> "晴朗"; 1, 2 -> "大致晴朗/多云"; 3 -> "阴天"; 45, 48 -> "有雾"
                51, 53, 55 -> "毛毛雨"; 61, 63, 65 -> "下雨"; 71, 73, 75, 77 -> "降雪"
                80, 81, 82 -> "阵雨"; 85, 86 -> "阵雪"; 95, 96, 99 -> "雷暴"; else -> "天气情况未知"
            }
            "当前位置：$city\n当前天气：$condition\n气温：${if (temp.isNaN()) "未知" else "$temp°C"}；体感：${if (feels.isNaN()) "未知" else "$feels°C"}\n湿度：${if (humidity < 0) "未知" else "$humidity%"}；风速：${if (wind.isNaN()) "未知" else "$wind km/h"}；降水：${if (rain.isNaN()) "未知" else "$rain mm"}\n数据来源：Open-Meteo"
        } finally { connection.disconnect() }
    }
}
