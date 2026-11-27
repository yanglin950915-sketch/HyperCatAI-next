package com.hypercat.ai.avatar

import android.content.*
import android.provider.Settings
import android.hardware.*
import android.media.session.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.notification.*
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

data class AvatarDeviceState(val battery: Int = -1, val charging: Boolean = false, val steps: Int = -1, val weather: String = "", val temperature: String = "", val music: Boolean = false, val track: String = "")
object AvatarDeviceStateStore : SensorEventListener {
    private val mutable = MutableStateFlow(AvatarDeviceState())
    val state = mutable.asStateFlow()
    private var app: Context? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var weatherStarted = false
    fun start(context: Context) {
        if (app != null) return
        app = context.applicationContext
        sensorManager = app!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                if (level >= 0 && scale > 0) mutable.value = mutable.value.copy(battery = level * 100 / scale, charging = status == 2 || status == 5)
            }
        }
        @Suppress("DEPRECATION")
        app!!.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        refreshWeather()
    }
    fun resume(context: Context) {
        start(context)
        if (sensor != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
    fun pause() { sensorManager?.unregisterListener(this) }
    private fun refreshWeather() {
        val ctx = app ?: return
        if (!com.hypercat.ai.AgentDeviceTools.hasLocationPermission(ctx)) {
            mutable.value = mutable.value.copy(weather = "开启定位显示天气", temperature = "")
        } else {
            scope.launch { updateWeather(ctx) }
        }
        if (weatherStarted) return
        weatherStarted = true
        scope.launch {
            while (isActive) {
                delay(10 * 60 * 1000L)
                val current = app ?: continue
                if (com.hypercat.ai.AgentDeviceTools.hasLocationPermission(current)) updateWeather(current)
                else mutable.value = mutable.value.copy(weather = "开启定位显示天气", temperature = "")
            }
        }
    }
    private suspend fun updateWeather(ctx: Context) {
        val result = runCatching { com.hypercat.ai.AgentDeviceTools.currentWeather(ctx) }
            .getOrElse { "天气获取失败：网络或定位异常" }
        val description = Regex("当前天气[：:]\\s*([^\\n，,；;]+)", RegexOption.IGNORE_CASE)
            .find(result)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val temp = Regex("(?:气温|temperature_2m)[：:\\s]*([+-]?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            .find(result)?.groupValues?.getOrNull(1).orEmpty()
        val failed = description.isBlank()
        mutable.value = mutable.value.copy(
            weather = if (!failed) description else if (result.contains("需要位置权限") || result.contains("位置权限")) "开启定位显示天气" else "天气暂不可用",
            temperature = temp
        )
    }
    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_STEP_COUNTER || e.values.isEmpty()) return
        val total = e.values[0].toInt()
        val p = app?.getSharedPreferences("hypercat_avatar_steps", Context.MODE_PRIVATE) ?: return
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (p.getString("day", "") != day) p.edit().putString("day", day).putInt("base", total).apply()
        mutable.value = mutable.value.copy(steps = (total - p.getInt("base", total)).coerceAtLeast(0))
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
    fun setMusic(playing: Boolean, title: String = "") { mutable.value = mutable.value.copy(music = playing, track = title.take(80)) }
}
class AvatarMediaNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 2000L) } }
    private fun refresh() {
        runCatching {
            val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = m.getActiveSessions(ComponentName(this, AvatarMediaNotificationListener::class.java))
            val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            AvatarDeviceStateStore.setMusic(c != null, c?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty())
        }.onFailure { AvatarDeviceStateStore.setMusic(false) }
    }
    override fun onListenerConnected() { super.onListenerConnected(); handler.removeCallbacks(poll); handler.post(poll) }
    override fun onNotificationPosted(s: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(s: StatusBarNotification?) { refresh() }
    override fun onListenerDisconnected() { handler.removeCallbacks(poll); AvatarDeviceStateStore.setMusic(false); super.onListenerDisconnected() }
    override fun onDestroy() { handler.removeCallbacks(poll); super.onDestroy() }
}
@Composable
fun AvatarStatusBadges(modifier: Modifier = Modifier) {
    val s by AvatarDeviceStateStore.state.collectAsState()
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarStatusBadge(if (s.battery >= 0) (if (s.charging) "⚡ " else "电量 ") + "${s.battery}%" else "电量读取中", Modifier.weight(1f))
            AvatarStatusBadge(if (s.weather.isNotBlank()) "☀ ${s.weather}${if (s.temperature.isNotBlank()) " ${s.temperature}°C" else ""}" else "天气获取中",
                Modifier.weight(1f).clickable {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarStatusBadge(if (s.steps >= 0) "👣 ${s.steps} 步" else "👣 步数未授权", Modifier.weight(1f))
            AvatarStatusBadge(if (s.music) "♫ ${s.track.ifBlank { "正在播放" }}" else "♫ 音乐同步设置",
                Modifier.weight(1f).clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
        }
    }
}
@Composable
private fun AvatarStatusBadge(t: String, modifier: Modifier = Modifier) {
    Text(t, color = Color.White, fontSize = 9.sp, maxLines = 1,
        modifier = modifier.background(Color(0xB8222B3B), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp))
}
