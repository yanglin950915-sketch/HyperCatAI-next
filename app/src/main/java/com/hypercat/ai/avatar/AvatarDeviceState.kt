package com.hypercat.ai.avatar

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.media.MediaMetadata
import android.media.audiofx.Visualizer
import android.media.session.*
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

data class AvatarDeviceState(
    val battery: Int = -1, val charging: Boolean = false, val steps: Int = -1,
    val weather: String = "", val temperature: String = "", val music: Boolean = false,
    val track: String = "", val artist: String = "", val lyrics: String = "",
    val currentLyric: String = "", val musicBeat: Float = 0f
)

object AvatarDeviceStateStore : SensorEventListener {
    private val mutable = MutableStateFlow(AvatarDeviceState())
    val state = mutable.asStateFlow()
    private var app: Context? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    @Volatile private var sensorRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var weatherStarted = false
    @Volatile private var requestedLyricsKey = ""
    @Volatile private var lyricLines: List<Pair<Long, String>> = emptyList()

    fun start(context: Context) {
        if (app != null) return
        app = context.applicationContext
        sensorManager = app!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { updateBattery(i) }
        }
        @Suppress("DEPRECATION")
        val stickyBattery = app!!.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateBattery(stickyBattery)
        refreshWeather()
    }

    fun resume(context: Context) {
        start(context)
        refreshWeather()
        refreshBattery(context)
        if (!sensorRegistered && sensor != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensorRegistered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        }
    }

    private fun refreshBattery(context: Context) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateBattery(intent)
    }

    private fun updateBattery(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level >= 0 && scale > 0) mutable.value = mutable.value.copy(
            battery = level * 100 / scale,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        )
    }
    fun pause() {
        if (sensorRegistered) sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    private fun refreshWeather() {
        val ctx = app ?: return
        if (!com.hypercat.ai.AgentDeviceTools.hasLocationPermission(ctx)) {
            mutable.value = mutable.value.copy(weather = "开启定位显示天气", temperature = "")
        } else scope.launch { updateWeather(ctx) }
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
        val result = runCatching { com.hypercat.ai.AgentDeviceTools.currentWeather(ctx) }.getOrElse { "天气获取失败：网络或定位异常" }
        val description = Regex("当前天气[：:]\\s*([^\\n，,；;]+)", RegexOption.IGNORE_CASE).find(result)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val temp = Regex("(?:气温|temperature_2m)[：:\\s]*([+-]?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(result)?.groupValues?.getOrNull(1).orEmpty()
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

    fun setMusic(playing: Boolean, title: String = "", artist: String = "", metadataLyrics: String = "", positionMs: Long = 0L) {
        if (!playing) {
            requestedLyricsKey = ""
            lyricLines = emptyList()
            mutable.value = mutable.value.copy(music = false, track = "", artist = "", lyrics = "", currentLyric = "", musicBeat = 0f)
            return
        }
        val safeTitle = title.take(100)
        val safeArtist = artist.take(100)
        val key = safeTitle + "\u0000" + safeArtist
        val previous = mutable.value
        val changed = key != previous.track + "\u0000" + previous.artist
        if (changed) {
            requestedLyricsKey = key
            lyricLines = parseLrc(metadataLyrics)
            mutable.value = previous.copy(
                music = true, track = safeTitle, artist = safeArtist, lyrics = metadataLyrics.take(12000),
                currentLyric = lyricAt(metadataLyrics, positionMs).ifBlank { "正在获取歌词…" }
            )
            if (metadataLyrics.isBlank()) scope.launch {
                val found = fetchLyrics(safeTitle, safeArtist)
                if (requestedLyricsKey != key) return@launch
                lyricLines = parseLrc(found)
                mutable.value = mutable.value.copy(lyrics = found.take(12000), currentLyric = lyricAt(found, positionMs).ifBlank { "暂无可用歌词" })
            }
        } else {
            val lyrics = previous.lyrics
            val current = lyricAt(lyrics, positionMs).ifBlank { if (lyrics.isBlank()) previous.currentLyric.ifBlank { "正在获取歌词…" } else "暂无可用歌词" }
            mutable.value = previous.copy(music = true, track = safeTitle, artist = safeArtist, currentLyric = current)
        }
    }

    fun setMusicLevel(level: Float) { mutable.value = mutable.value.copy(musicBeat = level.coerceIn(0f, 1f)) }

    private fun parseLrc(value: String): List<Pair<Long, String>> {
        if (value.isBlank()) return emptyList()
        val pattern = Regex("^\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)$")
        return value.lineSequence().mapNotNull { line ->
            val match = pattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val text = match.groupValues[4].trim()
            if (text.isBlank()) null else (minutes * 60_000L + seconds * 1_000L + fraction) to text
        }.sortedBy { it.first }.toList()
    }

    private fun lyricAt(value: String, positionMs: Long): String {
        val lines = lyricLines.ifEmpty { parseLrc(value) }
        if (lines.isNotEmpty()) return lines.lastOrNull { it.first <= positionMs }?.second.orEmpty()
        return value.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("[") }.orEmpty()
    }

    private fun fetchLyrics(title: String, artist: String): String {
        if (title.isBlank()) return ""
        val query = URLEncoder.encode(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "), Charsets.UTF_8.name())
        val connection = (URL("https://lrclib.net/api/search?q=$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000; readTimeout = 9000; setRequestProperty("User-Agent", "HyperCatAI/0.9.88")
        }
        return try {
            if (connection.responseCode !in 200..299) return ""
            val results = org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            var fallback = ""
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                if (synced != null) return synced
                if (fallback.isBlank()) fallback = item.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            }
            fallback
        } catch (_: Exception) { "" } finally { connection.disconnect() }
    }

    fun handleMediaAction(context: Context, action: String) {
        if (action == "music_settings") {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(context, AvatarMediaNotificationListener::class.java))
            val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull() ?: return@runCatching
            when (action) {
                "media_toggle" -> if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause() else controller.transportControls.play()
                "media_next" -> controller.transportControls.skipToNext()
                "media_previous" -> controller.transportControls.skipToPrevious()
            }
        }
    }
}

class AvatarMediaNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var musicVisualizer: Visualizer? = null
    private val poll = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1200L) }
    }
    private fun refresh() {
        AvatarDeviceStateStore.resume(applicationContext)
        runCatching {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controller = manager.getActiveSessions(ComponentName(this, AvatarMediaNotificationListener::class.java))
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            if (controller == null) {
                AvatarDeviceStateStore.setMusic(false)
                stopMusicVisualizer()
                return
            }
            val metadata = controller.metadata
            val playback = controller.playbackState
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
            val position = playback?.let {
                if (it.state == PlaybackState.STATE_PLAYING && it.lastPositionUpdateTime > 0L)
                    it.position + ((SystemClock.elapsedRealtime() - it.lastPositionUpdateTime) * it.playbackSpeed).toLong()
                else it.position
            } ?: 0L
            val lyrics = metadata?.getString("android.media.metadata.LYRICS").orEmpty()
            AvatarDeviceStateStore.setMusic(true, title, artist, lyrics, position)
            startMusicVisualizer()
        }.onFailure { AvatarDeviceStateStore.setMusic(false); stopMusicVisualizer() }
    }
    private fun startMusicVisualizer() {
        if (musicVisualizer != null || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
                        if (waveform.isEmpty()) return
                        var energy = 0.0
                        waveform.forEach { sample ->
                            val normalized = ((sample.toInt() and 0xFF) - 128) / 128.0
                            energy += normalized * normalized
                        }
                        AvatarDeviceStateStore.setMusicLevel((kotlin.math.sqrt(energy / waveform.size) * 2.6).coerceIn(0.02, 1.0).toFloat())
                    }
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) = Unit
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
            }
        }.onSuccess { musicVisualizer = it }
    }
    private fun stopMusicVisualizer() {
        runCatching { musicVisualizer?.enabled = false }
        runCatching { musicVisualizer?.release() }
        musicVisualizer = null
        AvatarDeviceStateStore.setMusicLevel(0f)
    }
    override fun onListenerConnected() { super.onListenerConnected(); handler.removeCallbacks(poll); handler.post(poll) }
    override fun onNotificationPosted(s: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(s: StatusBarNotification?) { refresh() }
    override fun onListenerDisconnected() { handler.removeCallbacks(poll); stopMusicVisualizer(); AvatarDeviceStateStore.setMusic(false); super.onListenerDisconnected() }
    override fun onDestroy() { handler.removeCallbacks(poll); stopMusicVisualizer(); super.onDestroy() }
}

@Composable
fun AvatarStatusBadges(modifier: Modifier = Modifier, rearControls: Boolean = false) {
    val s by AvatarDeviceStateStore.state.collectAsState()
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarStatusBadge(if (s.battery >= 0) (if (s.charging) "⚡ " else "电量 ") + s.battery + "%" else "电量读取中", Modifier.weight(1f))
            AvatarStatusBadge(
                if (s.weather.isNotBlank()) "☀ " + s.weather + (if (s.temperature.isNotBlank()) " " + s.temperature + "°C" else "") else "天气获取中",
                Modifier.weight(1f).clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarStatusBadge(if (s.steps >= 0) "👣 " + s.steps + " 步" else "👣 步数未授权", Modifier.weight(1f))
            AvatarStatusBadge(
                if (s.music) "♫ " + s.track.ifBlank { "正在播放" } else "♫ 音乐同步设置",
                Modifier.weight(1f).clickable {
                    if (s.music) AvatarDeviceStateStore.handleMediaAction(context, "media_toggle")
                    else AvatarDeviceStateStore.handleMediaAction(context, "music_settings")
                }
            )
        }
        if (rearControls) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarStatusBadge("⏮ 上一首", Modifier.weight(1f).clickable { AvatarDeviceStateStore.handleMediaAction(context, "media_previous") })
                AvatarStatusBadge(if (s.music) "⏸ 暂停" else "▶ 播放", Modifier.weight(1f).clickable { AvatarDeviceStateStore.handleMediaAction(context, "media_toggle") })
                AvatarStatusBadge("⏭ 下一首", Modifier.weight(1f).clickable { AvatarDeviceStateStore.handleMediaAction(context, "media_next") })
            }
        }
    }
}
@Composable
private fun AvatarStatusBadge(t: String, modifier: Modifier = Modifier) {
    Text(t, color = Color.White, fontSize = 9.sp, maxLines = 1,
        modifier = modifier.background(Color(0xB8222B3B), RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
}
