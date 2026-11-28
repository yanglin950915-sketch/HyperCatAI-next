package com.hypercat.ai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Low-latency PCM engine for full-duplex voice sessions.
 *
 * This class deliberately does not perform speech recognition or call a model API.
 * It exposes microphone PCM frames and accepts PCM playback frames so a transport
 * adapter can connect it to a provider-specific realtime audio protocol.
 */
class RealtimeAudioEngine(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val frameDurationMs: Int = DEFAULT_FRAME_DURATION_MS
) : AutoCloseable {

    data class Effects(
        val echoCancelerAvailable: Boolean,
        val echoCancelerEnabled: Boolean,
        val noiseSuppressorAvailable: Boolean,
        val noiseSuppressorEnabled: Boolean,
        val automaticGainControlAvailable: Boolean,
        val automaticGainControlEnabled: Boolean
    )

    interface Listener {
        fun onPcmFrame(pcm16: ByteArray, rms: Float)
        fun onCaptureError(message: String)
    }

    private val running = AtomicBoolean(false)
    private val outputLock = Any()
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    @Volatile private var captureThread: Thread? = null
    @Volatile private var echoCanceler: AcousticEchoCanceler? = null
    @Volatile private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var automaticGainControl: AutomaticGainControl? = null

    @Volatile
    var effects: Effects = Effects(false, false, false, false, false, false)
        private set

    fun startCapture(listener: Listener) {
        check(running.compareAndSet(false, true)) { "Audio capture is already running" }
        try {
            val minBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBytes > 0) { "当前设备不支持所选的麦克风采样格式（$minBytes）" }
            val frameBytes = sampleRate * frameDurationMs / 1000 * BYTES_PER_SAMPLE
            val bufferBytes = maxOf(minBytes * 2, frameBytes * 4)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                "麦克风音频采集初始化失败"
            }
            recorder = audioRecord
            attachEffects(audioRecord.audioSessionId)
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "系统未能启动麦克风录音"
            }
            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val frame = ByteArray(frameBytes)
                try {
                    while (running.get()) {
                        val count = audioRecord.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                        if (count < 0) {
                            if (running.get()) listener.onCaptureError("麦克风读取失败（$count）")
                            break
                        }
                        if (count > 0 && running.get()) {
                            val packet = if (count == frame.size) frame.copyOf() else frame.copyOf(count)
                            listener.onPcmFrame(packet, calculateRms(packet, count))
                        }
                    }
                } catch (error: Exception) {
                    if (running.get()) listener.onCaptureError(error.message ?: "麦克风采集异常")
                } finally {
                    running.set(false)
                }
            }, "HyperCat-RealtimeCapture").apply { start() }
        } catch (error: Exception) {
            running.set(false)
            releaseCapture()
            throw error
        }
    }

    /** Start a streaming PCM16 mono output track. The caller supplies matching PCM frames. */
    fun startPlayback() {
        synchronized(outputLock) {
            if (player?.state == AudioTrack.STATE_INITIALIZED) return
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBytes > 0) { "当前设备不支持所选的音频播放格式（$minBytes）" }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes * 2, sampleRate * frameDurationMs / 1000 * BYTES_PER_SAMPLE * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED) {
                track.release()
                "实时语音播放初始化失败"
            }
            player = track
            track.play()
        }
    }

    /** Blocking write; call from a transport/audio worker, never from the UI thread. */
    fun writePlaybackFrame(pcm16: ByteArray) {
        val track = player ?: error("实时语音播放尚未启动")
        var offset = 0
        while (offset < pcm16.size) {
            val written = track.write(pcm16, offset, pcm16.size - offset, AudioTrack.WRITE_BLOCKING)
            check(written > 0) { "实时语音播放写入失败（$written）" }
            offset += written
        }
    }

    /** Immediately flushes queued speech, used when the user interrupts the assistant. */
    fun interruptPlayback() {
        synchronized(outputLock) {
            player?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.play() }
            }
        }
    }

    fun stopPlayback() {
        synchronized(outputLock) {
            player?.let { track ->
                runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
                runCatching { track.release() }
            }
            player = null
        }
    }

    fun stopCapture() {
        if (!running.getAndSet(false) && recorder == null) return
        runCatching { recorder?.stop() }
        captureThread?.let { thread ->
            if (thread !== Thread.currentThread()) runCatching { thread.join(1200L) }
        }
        releaseCapture()
    }

    private fun attachEffects(sessionId: Int) {
        val aecAvailable = AcousticEchoCanceler.isAvailable()
        val nsAvailable = NoiseSuppressor.isAvailable()
        val agcAvailable = AutomaticGainControl.isAvailable()
        echoCanceler = if (aecAvailable) runCatching {
            AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        noiseSuppressor = if (nsAvailable) runCatching {
            NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        automaticGainControl = if (agcAvailable) runCatching {
            AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        effects = Effects(
            aecAvailable, echoCanceler?.enabled == true,
            nsAvailable, noiseSuppressor?.enabled == true,
            agcAvailable, automaticGainControl?.enabled == true
        )
    }

    private fun releaseCapture() {
        listOf(echoCanceler, noiseSuppressor, automaticGainControl).forEach { effect ->
            runCatching { effect?.release() }
        }
        echoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null
        recorder?.let { audioRecord ->
            runCatching { if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop() }
            runCatching { audioRecord.release() }
        }
        recorder = null
        captureThread = null
    }

    override fun close() {
        stopCapture()
        stopPlayback()
    }

    private fun calculateRms(data: ByteArray, length: Int): Float {
        if (length < BYTES_PER_SAMPLE) return 0f
        var sum = 0.0
        var index = 0
        var samples = 0
        while (index + 1 < length) {
            val value = ((data[index + 1].toInt() shl 8) or (data[index].toInt() and 0xff)).toShort().toInt()
            sum += value.toDouble() * value.toDouble()
            samples++
            index += BYTES_PER_SAMPLE
        }
        return if (samples == 0) 0f else (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 24_000
        const val DEFAULT_FRAME_DURATION_MS = 20
        private const val BYTES_PER_SAMPLE = 2
    }
}
