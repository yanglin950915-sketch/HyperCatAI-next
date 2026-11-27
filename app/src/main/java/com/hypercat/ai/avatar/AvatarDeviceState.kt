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
        if (weatherStarted) return
        weatherStarted = true
        scope.launch {
            while (isActive) {
                val ctx = app
                if (ctx != null && com.hypercat.ai.AgentDeviceTools.hasLocationPermission(ctx)) {
                    val result = runCatching { com.hypercat.ai.AgentDeviceTools.currentWeather(ctx) }.getOrNull().orEmpty()
                    val line = result.lineSequence().firstOrNull { it.startsWith("当前天气：") }.orEmpty()
                    if (line.isNotBlank()) {
                        val description = line.substringAfter("当前天气：").substringBefore("，").trim()
                        val temp = Regex("气温\\s*[:：]?\\s*([0-9.]+)").find(line)?.groupValues?.getOrNull(1).orEmpty()
                        mutable.value = mutable.value.copy(weather = description, temperature = temp)
                    }
                }
                delay(30 * 60 * 1000L)
            }
        }
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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (s.battery >= 0) Badge(if (s.charging) "⚡${s.battery}%" else "电量 ${s.battery}%")
        if (s.weather.isNotBlank()) Badge("☀ ${s.weather} ${s.temperature}°")
        if (s.steps >= 0) Badge("👣 ${s.steps} 步")
        if (s.music) Badge("♫ ${s.track.ifBlank { "正在播放" }}")
        else Badge("♫ 音乐同步设置", Modifier.clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
    }
}
@Composable
private fun Badge(t: String, modifier: Modifier = Modifier) { Text(t, color = Color.White, fontSize = 9.sp, maxLines = 1, modifier = modifier.background(Color(0xB8222B3B), RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) }
