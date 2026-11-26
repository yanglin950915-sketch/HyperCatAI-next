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

    fun extractWeatherPlace(text: String): String {
        var place = text.trim()
        listOf(
            "请问", "帮我查询", "帮我查一下", "帮我查", "查询一下", "查询", "查一下", "查下",
            "告诉我", "我想知道", "我想查", "看看", "现在", "今天", "明天", "后天",
            "天气预报", "天气", "气温", "温度", "多少度", "怎么样", "如何", "的天气", "的",
            "吧", "呢", "吗"
        ).sortedByDescending { it.length }.forEach { place = place.replace(it, "") }
        place = place.replace(Regex("[，。！？、,.!?：:;；\\s]"), "").trim()
        return place.takeIf { it.length >= 2 && it.any { c -> c.isLetter() } }.orEmpty()
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    suspend fun currentWeather(context: Context, requestedPlace: String? = null): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        val key = prefs.getString("amap_weather_key", "").orEmpty().trim()
        if (key.isBlank()) return@withContext "中国天气服务尚未配置：请打开 HyperCat AI 设置 → 天气服务，填写高德地图 Web 服务 API Key。可在 https://console.amap.com/dev/key/app 创建。"

        val placeName: String
        if (!requestedPlace.isNullOrBlank()) {
            placeName = requestedPlace.trim()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return@withContext "查询当前位置天气需要位置权限。请允许大致位置，或直接告诉我城市/区县名称。"
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = runCatching {
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                    .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            }.getOrNull() ?: return@withContext "暂时获取不到设备位置。请开启定位服务后重试，或直接告诉我城市/区县名称。"
            val geocoder = Geocoder(context, Locale.CHINA)
            val address = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }.getOrNull()
            placeName = listOf(address?.adminArea, address?.locality, address?.subLocality, address?.subAdminArea)
                .filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("")
                .ifBlank { return@withContext "无法识别当前位置所属的城市和区县。请开启定位权限，或直接告诉我城市/区县名称。" }
        }

        fun getJson(url: String): JSONObject {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IllegalStateException("天气服务 HTTP $status")
                return JSONObject(body)
            } finally { connection.disconnect() }
        }

        try {
            // Resolve the requested city/district to an AMap administrative code first.
            val geo = getJson("https://restapi.amap.com/v3/geocode/geo?address=${encode(placeName)}&key=${encode(key)}")
            if (geo.optString("status") != "1") {
                val info = geo.optString("info", "地点解析失败")
                return@withContext "无法识别天气查询区域“$placeName”：$info。请尝试补充省份或城市名称。"
            }
            val geocodes = geo.optJSONArray("geocodes")
            val resolved = geocodes?.optJSONObject(0)
                ?: return@withContext "没有找到“$placeName”对应的行政区域，请补充更完整的城市或区县名称。"
            val adcode = resolved.optString("adcode")
            if (adcode.isBlank() || adcode == "[]" || adcode == "100000")
                return@withContext "已识别地点“$placeName”，但没有取得有效的市/区县编码。请补充更具体的区域名称。"
            val province = resolved.optString("province")
            val city = resolved.optString("city").ifBlank { resolved.optString("district") }
            val district = resolved.optString("district")
            val fullPlace = listOf(province, city, district).filter { it.isNotBlank() }.distinct().joinToString("")

            val weather = getJson("https://restapi.amap.com/v3/weather/weatherInfo?city=${encode(adcode)}&extensions=all&key=${encode(key)}")
            if (weather.optString("status") != "1") {
                val info = weather.optString("info", "天气数据查询失败")
                return@withContext "已定位到$fullPlace，但天气服务暂时无法返回数据：$info。"
            }
            val live = weather.optJSONArray("lives")?.optJSONObject(0)
            val forecast = weather.optJSONArray("forecasts")?.optJSONObject(0)
            if (live == null && forecast == null) return@withContext "天气服务暂未返回${fullPlace}的天气数据，请稍后重试。"

            val result = StringBuilder("天气区域：$fullPlace（行政区划编码 $adcode）")
            if (live != null) {
                result.append("\\n当前天气：${live.optString("weather", "未知")}，气温 ${live.optString("temperature", "未知")}°C")
                result.append("，湿度 ${live.optString("humidity", "未知")}%")
                result.append("，风向 ${live.optString("winddirection", "未知")}，风力 ${live.optString("windpower", "未知")}级")
                result.append("\\n实况发布时间：${live.optString("reporttime", "未知")}")
            }
            val casts = forecast?.optJSONArray("casts")
            if (casts != null && casts.length() > 0) {
                result.append("\\n未来天气预报：")
                for (i in 0 until minOf(casts.length(), 4)) {
                    val day = casts.optJSONObject(i) ?: continue
                    result.append("\\n${day.optString("date")}：白天${day.optString("dayweather")} ${day.optString("daytemp")}°C；夜间${day.optString("nightweather")} ${day.optString("nighttemp")}°C；风向${day.optString("daywind")}，风力${day.optString("daypower")}级")
                }
                result.append("\\n预报发布时间：${forecast.optString("reporttime", "未知")}")
            }
            result.append("\\n数据来源：高德地图天气服务")
            result.toString()
        } catch (error: Exception) {
            "天气查询失败：${error.message ?: "网络连接异常"}。请检查网络和高德天气 API Key。"
        }
    }
}
