package com.hypercat.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object AgentDeviceTools {
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPreciseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun freshestRecentLocation(context: Context, precise: Boolean): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val now = System.currentTimeMillis()
        val providers = if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            else listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { it.latitude.isFinite() && it.longitude.isFinite() && now - it.time in 0L..600_000L }
            .maxByOrNull { it.time }
    }

    private suspend fun requestSingleLocation(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                        runCatching { manager.removeUpdates(this) }
                    }
                    override fun onProviderDisabled(name: String) {
                        if (continuation.isActive) continuation.resume(null)
                        runCatching { manager.removeUpdates(this) }
                    }
                    override fun onProviderEnabled(name: String) = Unit
                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(providerName: String?, status: Int, extras: android.os.Bundle?) = Unit
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private suspend fun currentDeviceLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val precise = hasPreciseLocationPermission(context)
        val providers = if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            else listOf(LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) continue
            val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        try {
                            manager.getCurrentLocation(provider, null, context.mainExecutor) { result ->
                                if (continuation.isActive) continuation.resume(result)
                            }
                        } catch (_: SecurityException) {
                            if (continuation.isActive) continuation.resume(null)
                        } catch (_: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                }
            } else requestSingleLocation(manager, provider)
            if (location != null && location.latitude.isFinite() && location.longitude.isFinite()) return location
        }
        // Only fall back to a recent cache entry; never silently reuse stale coordinates.
        return freshestRecentLocation(context, precise)?.takeIf { System.currentTimeMillis() - it.time <= 120_000L }
    }

    private fun resolveNamedAddress(geocoder: Geocoder, requestedPlace: String): android.location.Address? {
        val query = requestedPlace.trim().replace(Regex("\\s+"), "")
        if (query.isBlank()) return null
        @Suppress("DEPRECATION")
        val candidates = geocoder.getFromLocationName(requestedPlace.trim(), 8).orEmpty()
        fun normalize(value: String) = value.replace(Regex("[\\s,，省市区县特别行政区]"), "")
        val target = normalize(query)
        return candidates.mapNotNull { address ->
            val parts = buildList {
                address.featureName?.let(::add)
                address.subLocality?.let(::add)
                address.locality?.let(::add)
                address.subAdminArea?.let(::add)
                address.adminArea?.let(::add)
                address.countryName?.let(::add)
                for (i in 0 until address.maxAddressLineIndex + 1) address.getAddressLine(i)?.let(::add)
            }
            val combined = normalize(parts.joinToString(""))
            val score = when {
                parts.any { normalize(it) == target } -> 100
                combined.contains(target) -> 90
                parts.any { target.contains(normalize(it)) && normalize(it).length >= 2 } -> 65
                else -> 0
            }
            if (score == 0) null else score to address
        }.maxByOrNull { it.first }?.second
    }

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
        // Remove conversational greetings only at the beginning, before extracting the place.
        // This prevents phrases such as "你好，今天新加坡天气怎么样" from becoming "你好新加坡".
        place = place.replace(
            Regex("(?i)^\\s*(?:(?:你好|您好|嗨|哈喽|hello|hi|早上好|下午好|晚上好)[呀啊喂，,。.!！?？\\s]*)+"),
            ""
        )
        place = place.replace(
            Regex("^\\s*(?:(?:请问|麻烦问一下|麻烦帮我|请帮我|帮我|我想知道|我想查询|我想查)[，,。.!！?？\\s]*)+"),
            ""
        )
        listOf(
            "帮我查询", "帮我查一下", "帮我查", "查询一下", "查询", "查一下", "查下",
            "告诉我", "我想知道", "我想查", "看看", "现在", "今天", "明天", "后天",
            "天气预报", "天气", "气温", "温度", "多少度", "怎么样", "如何", "的天气", "的",
            "附近", "当地", "这边", "吧", "呢", "吗"
        ).sortedByDescending { it.length }.forEach { place = place.replace(it, "") }
        place = place.replace(Regex("[，。！？、,.!?：:;；\\s]"), "").trim()
        return place.takeIf { it.length >= 2 && it.any { c -> c.isLetter() } }.orEmpty()
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    suspend fun currentWeather(context: Context, requestedPlace: String? = null): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        val provider = prefs.getString("weather_provider", "openmeteo") ?: "openmeteo"
        val amapKey = prefs.getString("amap_weather_key", "").orEmpty().trim()
        val caiyunToken = prefs.getString("caiyun_weather_token", "").orEmpty().trim()
        // A user-confirmed city takes precedence over device coordinates; this avoids stale or mis-geocoded locations.
        val weatherPlace = requestedPlace?.trim()?.takeIf { it.isNotBlank() }
            ?: prefs.getString("weather_default_place", "西安市")?.trim()?.takeIf { it.isNotBlank() }
        fun json(url: String): JSONObject {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Accept", "application/json")
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP " + code + ": " + body.take(160))
                return JSONObject(body)
            } finally { c.disconnect() }
        }
        if (provider == "amap") {
            if (amapKey.isBlank()) return@withContext "高德天气尚未配置 API Key，请在设置 → 天气服务中填写。"
            val place = if (!weatherPlace.isNullOrBlank()) weatherPlace.trim() else {
                if (!hasLocationPermission(context))
                    return@withContext "查询当前位置天气需要位置权限，或直接告诉我城市/区县名称。"
                val loc = currentDeviceLocation(context)
                    ?: return@withContext "无法获取当前实时位置。请开启系统定位并稍后重试，或直接告诉我城市/区县名称。"
                val a = runCatching { @Suppress("DEPRECATION") Geocoder(context, Locale.CHINA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull() }.getOrNull()
                listOf(a?.adminArea, a?.locality, a?.subLocality, a?.subAdminArea).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("")
                    .ifBlank { return@withContext "无法识别当前位置区域，请直接告诉我城市/区县名称。" }
            }
            return@withContext try {
                val g = json("https://restapi.amap.com/v3/geocode/geo?address=" + encode(place) + "&key=" + encode(amapKey))
                if (g.optString("status") != "1") return@withContext "高德无法识别区域：" + g.optString("info")
                val geo = g.optJSONArray("geocodes")?.optJSONObject(0) ?: return@withContext "没有找到区域：" + place
                val adcode = geo.optString("adcode")
                if (adcode.isBlank() || adcode == "100000") return@withContext "未取得有效市/区县编码，请补充更具体的区域名称。"
                val name = listOf(geo.optString("province"), geo.optString("city").ifBlank { geo.optString("district") }, geo.optString("district")).filter { it.isNotBlank() }.distinct().joinToString("")
                val w = json("https://restapi.amap.com/v3/weather/weatherInfo?city=" + encode(adcode) + "&extensions=all&key=" + encode(amapKey))
                if (w.optString("status") != "1") return@withContext "高德天气查询失败：" + w.optString("info")
                val live = w.optJSONArray("lives")?.optJSONObject(0)
                val casts = w.optJSONArray("forecasts")?.optJSONObject(0)?.optJSONArray("casts")
                val out = StringBuilder("天气区域：" + name)
                if (live != null) out.append("\n当前天气：" + live.optString("weather") + "，气温 " + live.optString("temperature") + "°C，湿度 " + live.optString("humidity") + "%")
                if (casts != null) {
                    out.append("\n未来天气预报：")
                    for (i in 0 until minOf(4, casts.length())) {
                        val d = casts.optJSONObject(i) ?: continue
                        out.append("\n" + d.optString("date") + "：白天" + d.optString("dayweather") + " " + d.optString("daytemp") + "°C；夜间" + d.optString("nightweather") + " " + d.optString("nighttemp") + "°C")
                    }
                }
                out.append("\n数据来源：高德地图天气服务").toString()
            } catch (e: Exception) { "高德天气查询失败：" + (e.message ?: "网络异常") }
        }
        if (provider == "caiyun" && caiyunToken.isBlank()) return@withContext "彩云天气尚未配置 Token，请在设置 → 天气服务中填写。"
        if (weatherPlace.isNullOrBlank() && !hasLocationPermission(context))
            return@withContext "查询当前位置天气需要位置权限，或直接告诉我城市/区县名称。"
        val geocoder = Geocoder(context, Locale.CHINA)
        val address = try {
            if (!weatherPlace.isNullOrBlank()) {
                resolveNamedAddress(geocoder, weatherPlace.trim())
            } else {
                val loc = currentDeviceLocation(context)
                    ?: return@withContext "无法获取当前实时位置。请开启系统定位并稍后重试，或直接告诉我城市/区县名称。"
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(loc.latitude, loc.longitude, 5)
                    ?.maxByOrNull { a ->
                        (if (!a.locality.isNullOrBlank()) 4 else 0) +
                        (if (!a.subLocality.isNullOrBlank()) 3 else 0) +
                        (if (!a.adminArea.isNullOrBlank()) 2 else 0) +
                        (if (!a.countryName.isNullOrBlank()) 1 else 0)
                    }
                    ?: android.location.Address(Locale.CHINA).apply { latitude = loc.latitude; longitude = loc.longitude }
            }
        } catch (e: Exception) { return@withContext "地点解析失败：" + (e.message ?: "系统地理编码不可用") }
            ?: return@withContext "无法可靠匹配地点“" + (weatherPlace ?: "当前位置") + "”。请补充省份、城市和区县名称，避免使用同名地区。"
        val lat = address.latitude
        val lon = address.longitude
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0)
            return@withContext "地点坐标无效，请重新提供城市或区县名称。"
        val placeName = listOf(address.adminArea, address.locality, address.subLocality, address.subAdminArea).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("").ifBlank { weatherPlace ?: "当前位置" }
        if (provider == "caiyun") {
            // WGS84 -> GCJ-02 conversion for mainland China, as required by Caiyun v2.x.
            fun transform(x: Double, y: Double, latitude: Boolean): Double {
                var v = if (latitude) -100.0 + 2*x + 3*y + 0.2*y*y + 0.1*x*y + 0.2*kotlin.math.sqrt(kotlin.math.abs(x)) else 300.0 + x + 2*y + 0.1*x*x + 0.1*x*y + 0.1*kotlin.math.sqrt(kotlin.math.abs(x))
                v += (20*kotlin.math.sin(6*x*Math.PI) + 20*kotlin.math.sin(2*x*Math.PI))*2/3
                v += (20*kotlin.math.sin(y*Math.PI) + 40*kotlin.math.sin(y/3*Math.PI))*2/3
                v += (if (latitude) 160*kotlin.math.sin(y/12*Math.PI) + 320*kotlin.math.sin(y*Math.PI/30) else 150*kotlin.math.sin(x/12*Math.PI) + 300*kotlin.math.sin(x/30*Math.PI))*2/3
                return v
            }
            val inChina = lon in 72.004..137.8347 && lat in 0.8293..55.8271
            val pair = if (!inChina) lon to lat else {
                val a = 6378245.0; val ee = 0.00669342162296594323
                val x = lon - 105.0; val y = lat - 35.0
                var dLat = transform(x,y,true); var dLon = transform(x,y,false)
                val rad = lat / 180 * Math.PI
                val magic = 1 - ee*kotlin.math.sin(rad)*kotlin.math.sin(rad)
                val root = kotlin.math.sqrt(magic)
                dLat = dLat*180/((a*(1-ee))/(magic*root)*Math.PI)
                dLon = dLon*180/(a/root*kotlin.math.cos(rad)*Math.PI)
                (lon+dLon) to (lat+dLat)
            }
            return@withContext try {
                val data = json("https://api.caiyunapp.com/v2.6/" + encode(caiyunToken) + "/" + pair.first + "," + pair.second + "/weather?alert=true&dailysteps=5&lang=zh_CN")
                if (data.optString("status") != "ok") return@withContext "彩云天气返回失败：" + data.optString("error", "请检查 Token 或套餐权限")
                val result = data.optJSONObject("result") ?: return@withContext "彩云天气没有返回有效数据。"
                val live = result.optJSONObject("realtime") ?: return@withContext "彩云天气暂未返回实时天气。"
                val names = mapOf("CLEAR_DAY" to "晴", "CLEAR_NIGHT" to "晴", "PARTLY_CLOUDY_DAY" to "多云", "PARTLY_CLOUDY_NIGHT" to "多云", "CLOUDY" to "阴", "LIGHT_RAIN" to "小雨", "MODERATE_RAIN" to "中雨", "HEAVY_RAIN" to "大雨", "STORM_RAIN" to "暴雨", "FOG" to "雾", "LIGHT_SNOW" to "小雪", "MODERATE_SNOW" to "中雪", "HEAVY_SNOW" to "大雪", "STORM_SNOW" to "暴雪", "WIND" to "大风", "LIGHT_HAZE" to "轻度霾", "MODERATE_HAZE" to "中度霾", "HEAVY_HAZE" to "重度霾")
                val sky = live.optString("skycon")
                val temp = live.optDouble("temperature", Double.NaN)
                val out = StringBuilder("天气区域：" + placeName + "\n当前天气：" + (names[sky] ?: sky) + "，气温 " + if (temp.isNaN()) "未知" else "%.1f".format(Locale.ROOT,temp) + "°C")
                val feels = live.optDouble("apparent_temperature", Double.NaN)
                if (!feels.isNaN()) out.append("，体感 " + "%.1f".format(Locale.ROOT,feels) + "°C")
                val humidity = live.optDouble("humidity", Double.NaN)
                if (!humidity.isNaN()) out.append("，湿度 " + (humidity*100).toInt() + "%")
                val wind = live.optJSONObject("wind")
                if (wind != null) out.append("，风速 " + wind.optDouble("speed",0.0) + " m/s")
                result.optString("forecast_keypoint").takeIf { it.isNotBlank() }?.let { out.append("\n天气提示：" + it) }
                val daily = result.optJSONObject("daily")
                val skyArray = daily?.optJSONArray("skycon")
                val temps = daily?.optJSONArray("temperature")
                if (skyArray != null) {
                    out.append("\n未来天气预报：")
                    for (i in 0 until minOf(5,skyArray.length())) {
                        val day = skyArray.optJSONObject(i) ?: continue
                        val t = temps?.optJSONObject(i)
                        out.append("\n" + day.optString("date") + "：" + (names[day.optString("value")] ?: day.optString("value")) + "，" + (t?.optDouble("min")?.toInt()?.toString() ?: "?") + "～" + (t?.optDouble("max")?.toInt()?.toString() ?: "?") + "°C")
                    }
                }
                val alerts = result.optJSONObject("alert")?.optJSONArray("content")
                if (alerts != null && alerts.length() > 0) {
                    out.append("\n天气预警：")
                    for (i in 0 until minOf(3,alerts.length())) { val a = alerts.optJSONObject(i) ?: continue; out.append("\n" + a.optString("title") + "：" + a.optString("description")) }
                }
                out.append("\n数据来源：彩云天气").toString()
            } catch (e: Exception) { "彩云天气查询失败：" + (e.message ?: "网络异常") + "。请检查 Token。" }
        }
        // Original Open-Meteo service is retained and requires no API key.
        try {
            val data = json("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=auto")
            val current = data.optJSONObject("current") ?: return@withContext "Open-Meteo 暂未返回当前天气。"
            val code = current.optInt("weather_code",-1)
            val desc = when (code) { 0 -> "晴朗"; 1,2 -> "多云"; 3 -> "阴"; 45,48 -> "有雾"; 51,53,55 -> "毛毛雨"; 61,63,65,80,81,82 -> "降雨"; 71,73,75,77,85,86 -> "降雪"; 95,96,99 -> "雷暴"; else -> "未知" }
            val out = StringBuilder("天气区域：" + placeName + "\n当前天气：" + desc + "\n气温：" + current.optString("temperature_2m") + "°C；体感：" + current.optString("apparent_temperature") + "°C\n湿度：" + current.optString("relative_humidity_2m") + "%；风速：" + current.optString("wind_speed_10m") + " km/h；降水：" + current.optString("precipitation") + " mm")
            val daily = data.optJSONObject("daily")
            val dates = daily?.optJSONArray("time"); val highs = daily?.optJSONArray("temperature_2m_max"); val lows = daily?.optJSONArray("temperature_2m_min"); val codes = daily?.optJSONArray("weather_code")
            if (dates != null) {
                out.append("\n未来天气预报：")
                for (i in 0 until minOf(5,dates.length())) {
                    val c = codes?.optInt(i,-1) ?: -1
                    val d = when (c) { 0 -> "晴"; 1,2 -> "多云"; 3 -> "阴"; 45,48 -> "雾"; 51,53,55 -> "毛毛雨"; 61,63,65,80,81,82 -> "降雨"; 71,73,75,77,85,86 -> "降雪"; 95,96,99 -> "雷暴"; else -> "未知" }
                    out.append("\n" + dates.optString(i) + "：" + d + "，" + lows?.optDouble(i)?.toInt() + "～" + highs?.optDouble(i)?.toInt() + "°C")
                }
            }
            out.append("\n数据来源：Open-Meteo").toString()
        } catch (e: Exception) { "Open-Meteo 天气查询失败：" + (e.message ?: "网络异常") }
    }}
