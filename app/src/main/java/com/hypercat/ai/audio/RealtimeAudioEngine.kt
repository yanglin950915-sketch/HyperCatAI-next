package com.hypercat.ai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Process
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device-side full-duplex PCM transport foundation.
 *
 * Captures mono PCM16 at 16 kHz in 20 ms frames while an AudioTrack can
 * play streamed PCM16 output concurrently. Network/model protocol is
 * deliberately injected by the caller; no vendor endpoint or credential
 * is embedded here.
 */
class RealtimeAudioEngine(
    private val listener: Listener,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : Closeable {

    interface Listener {
        /** Called on the capture thread. Copy the bytes if retaining them. */
        fun onAudioFrame(pcm16: ByteArray, length: Int)
        fun onError(message: String, cause: Throwable? = null)
    }

    data class Effects(
        val echoCancellationAvailable: Boolean,
        val noiseSuppressionAvailable: Boolean,
        val automaticGainControlAvailable: Boolean,
    )

    private val running = AtomicBoolean(false)
    @Volatile private var frameConsumer: ((ByteArray, Int) -> Unit)? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    @Volatile private var captureThread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile var effects: Effects = Effects(false, false, false)
        private set

    val isRunning: Boolean get() = running.get()

    fun setFrameConsumer(consumer: ((ByteArray, Int) -> Unit)?) { frameConsumer = consumer }

    @Synchronized
    fun start() {
        if (running.get()) return
        check(sampleRate == DEFAULT_SAMPLE_RATE || sampleRate == 24_000) {
            "Only 16 kHz and 24 kHz PCM are supported"
        }
        val inputMin = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(inputMin > 0) { "This device does not support the requested microphone format ($inputMin)" }
        val frameBytes = sampleRate / 50 * 2
        val input = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(inputMin, frameBytes * 4)
        )
        check(input.state == AudioRecord.STATE_INITIALIZED) {
            input.release()
            "Unable to initialize microphone capture"
        }

        val outputMin = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(outputMin > 0) {
            input.release()
            "This device does not support the requested playback format ($outputMin)"
        }
        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(outputMin, frameBytes * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(output.state == AudioTrack.STATE_INITIALIZED) {
            input.release()
            output.release()
            "Unable to initialize audio playback"
        }

        recorder = input
        player = output
        val sessionId = input.audioSessionId
        val aec = if (AcousticEchoCanceler.isAvailable()) runCatching {
            AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        val ns = if (NoiseSuppressor.isAvailable()) runCatching {
            NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        val agc = if (AutomaticGainControl.isAvailable()) runCatching {
            AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        }.getOrNull() else null
        echoCanceler = aec
        noiseSuppressor = ns
        gainControl = agc
        effects = Effects(aec?.enabled == true, ns?.enabled == true, agc?.enabled == true)

        try {
            output.play()
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone did not enter recording state"
            }
            running.set(true)
            captureThread = Thread({ captureLoop(input, frameBytes) }, "HyperCat-AudioCapture").apply {
                priority = Process.THREAD_PRIORITY_AUDIO
                start()
            }
        } catch (error: Throwable) {
            running.set(false)
            runCatching { input.release() }
            runCatching { output.release() }
            recorder = null
            player = null
            releaseEffects()
            listener.onError("Unable to start real-time audio", error)
            throw error
        }
    }

    /** Queue one mono PCM16 output chunk. Safe to call while capture is active. */
    fun writePlayback(pcm16: ByteArray, length: Int = pcm16.size) {
        require(length in 0..pcm16.size && length % 2 == 0) { "PCM16 length must be even" }
        val output = player ?: throw IllegalStateException("Audio engine is not started")
        check(running.get()) { "Audio engine is not running" }
        var offset = 0
        while (offset < length && running.get()) {
            val written = output.write(pcm16, offset, length - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) throw IllegalStateException("AudioTrack write failed: $written")
            if (written == 0) break
            offset += written
        }
    }

    /** Immediately flush queued AI audio, for barge-in / response cancellation. */
    fun interruptPlayback() {
        player?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.play()
            }.onFailure { listener.onError("Unable to interrupt audio playback", it) }
        }
    }

    private fun captureLoop(input: AudioRecord, frameBytes: Int) {
        val frame = ByteArray(frameBytes)
        while (running.get()) {
            val count = input.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
            if (count > 0) {
                try {
                    val consumer = frameConsumer
                    if (consumer != null) consumer(frame, count) else listener.onAudioFrame(frame, count)
                } catch (error: Throwable) {
                    listener.onError("Audio frame consumer failed", error)
                }
            } else if (count < 0) {
                if (running.get()) listener.onError("Microphone read failed: $count")
                break
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false) && recorder == null && player == null) return
        runCatching { recorder?.stop() }
        runCatching { player?.pause(); player?.flush(); player?.stop() }
        captureThread?.let { thread ->
            if (thread !== Thread.currentThread()) runCatching { thread.join(500) }
        }
        captureThread = null
        frameConsumer = null
        runCatching { recorder?.release() }
        runCatching { player?.release() }
        recorder = null
        player = null
        releaseEffects()
    }

    private fun releaseEffects() {
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { gainControl?.release() }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        effects = Effects(false, false, false)
    }

    override fun close() = stop()

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val FRAME_DURATION_MS = 20
    }
}
