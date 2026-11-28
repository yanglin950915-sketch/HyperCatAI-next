package com.hypercat.ai.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Continuous call microphone capture. Audio is segmented locally by VAD and
 * delivered as PCM16 utterances for the configured cloud ASR/AI pipeline.
 * This deliberately does not depend on Android SpeechRecognizer.
 */
class CallPcmCaptureController(
    context: Context,
    private val onUtterance: (ByteArray) -> Unit,
    private val onLevel: (Double) -> Unit = {},
    private val onError: (String, Throwable?) -> Unit,
) : Closeable {
    private val appContext = context.applicationContext
    private val coordinator = AudioSessionCoordinator.get(appContext)
    private val lock = Any()
    private val disposed = AtomicBoolean(false)
    @Volatile private var enabled = false
    @Volatile private var running = false
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var captureThread: Thread? = null
    @Volatile private var lease: AudioSessionCoordinator.Lease? = null

    fun start() {
        check(!disposed.get()) { "通话录音控制器已关闭" }
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "尚未获得麦克风录音权限"
        }
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(!manager.isMicrophoneMute) { "系统麦克风隐私开关已关闭，请先开启麦克风访问" }
        enabled = true
        startCapture()
    }

    fun setEnabled(value: Boolean) {
        if (disposed.get() || enabled == value) return
        enabled = value
        if (value) {
            val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (manager.isMicrophoneMute) {
                enabled = false
                onError("系统麦克风隐私开关已关闭，请先开启麦克风访问", null)
                return
            }
            runCatching { startCapture() }.onFailure { onError("恢复麦克风采集失败：${it.message}", it) }
        } else {
            stopCapture(releaseLease = false)
        }
    }

    private fun startCapture() = synchronized(lock) {
        if (running || disposed.get() || !enabled) return
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "麦克风录音权限已失效，请重新授权"
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "设备不支持 16kHz PCM 麦克风采集（$minBuffer）" }
        val acquired = lease ?: coordinator.acquire(AudioSessionCoordinator.Owner.REALTIME_CALL, speakerphone = true).also { lease = it }
        var input: AudioRecord? = null
        try {
            val startedRecorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, FRAME_BYTES * 8)
            )
            input = startedRecorder
            check(startedRecorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord 初始化失败，无法打开麦克风" }
            startedRecorder.startRecording()
            check(startedRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
            recorder = startedRecorder
            lease = acquired
            running = true
            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                captureLoop(startedRecorder)
            }, "HyperCat-CallMicCapture").apply { start() }
        } catch (error: Throwable) {
            running = false
            runCatching { input?.release() }
            if (lease === acquired) lease = null
            acquired.close()
            throw error
        }
    }

    private fun captureLoop(input: AudioRecord) {
        val vad = PcmVoiceActivityDetector(threshold = 0.018, onsetFrames = 2, releaseFrames = 25)
        val preRoll = ArrayDeque<ByteArray>()
        val utterance = ByteArrayOutputStream()
        var collecting = false
        var utteranceBytes = 0
        val frame = ByteArray(FRAME_BYTES)
        var levelFrameCounter = 0
        try {
            while (running && enabled && !Thread.currentThread().isInterrupted) {
                val count = input.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (running) onError("AudioRecord.read 失败：$count", null)
                    break
                }
                if (count == 0) continue
                val copy = frame.copyOf(count)
                val level = rms(copy, count)
                if (++levelFrameCounter % 5 == 0) onLevel(level)
                when (vad.process(copy, count)) {
                    PcmVoiceActivityDetector.Event.SPEECH_STARTED -> {
                        collecting = true
                        utterance.reset()
                        utteranceBytes = 0
                        while (preRoll.isNotEmpty()) {
                            val old = preRoll.removeFirst()
                            utterance.write(old)
                            utteranceBytes += old.size
                        }
                        utterance.write(copy, 0, count)
                        utteranceBytes += count
                    }
                    PcmVoiceActivityDetector.Event.SPEECH_CONTINUES -> if (collecting) {
                        utterance.write(copy, 0, count)
                        utteranceBytes += count
                    }
                    PcmVoiceActivityDetector.Event.SPEECH_ENDED -> if (collecting) {
                        utterance.write(copy, 0, count)
                        utteranceBytes += count
                        dispatchUtterance(utterance.toByteArray())
                        collecting = false
                        utterance.reset()
                        utteranceBytes = 0
                    }
                    PcmVoiceActivityDetector.Event.SILENCE -> Unit
                }
                if (!collecting) {
                    preRoll.addLast(copy)
                    while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                } else if (utteranceBytes >= MAX_UTTERANCE_BYTES) {
                    dispatchUtterance(utterance.toByteArray())
                    utterance.reset()
                    utteranceBytes = 0
                    collecting = false
                    vad.reset()
                    preRoll.clear()
                }
            }
        } catch (error: Throwable) {
            if (running && enabled) onError("麦克风实时采集异常：${error.message}", error)
        } finally {
            if (collecting && utteranceBytes >= MIN_UTTERANCE_BYTES) dispatchUtterance(utterance.toByteArray())
        }
    }

    private fun dispatchUtterance(pcm: ByteArray) {
        if (pcm.size >= MIN_UTTERANCE_BYTES && !disposed.get() && enabled) {
            runCatching { onUtterance(pcm) }.onFailure { onError("提交录音片段失败：${it.message}", it) }
        }
    }

    private fun rms(bytes: ByteArray, length: Int): Double {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
            val normalized = sample / 32768.0
            sum += normalized * normalized
            samples++
            i += 2
        }
        return if (samples == 0) 0.0 else sqrt(sum / samples)
    }

    private fun stopCapture(releaseLease: Boolean) {
        synchronized(lock) {
            if (!running && recorder == null) {
                if (releaseLease) { lease?.close(); lease = null }
                return
            }
            running = false
            runCatching { recorder?.stop() }
            val thread = captureThread
            if (thread != null && thread !== Thread.currentThread()) runCatching { thread.join(700) }
            captureThread = null
            runCatching { recorder?.release() }
            recorder = null
            if (releaseLease) { lease?.close(); lease = null }
        }
    }

    override fun close() {
        if (!disposed.compareAndSet(false, true)) return
        enabled = false
        stopCapture(releaseLease = true)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_BYTES = SAMPLE_RATE / 50 * 2
        private const val PRE_ROLL_FRAMES = 12
        private const val MIN_UTTERANCE_BYTES = SAMPLE_RATE
        private const val MAX_UTTERANCE_BYTES = SAMPLE_RATE * 2 * 18
    }
}
