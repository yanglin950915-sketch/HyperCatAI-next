package com.hypercat.ai.audio

import android.content.Context
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Owns the complete device-side realtime audio lifecycle.
 * Exactly one engine and one coordinator lease are held for a session.
 */
class RealtimeAgentSession(
    context: Context,
    private val audio: RealtimeAudioEngine,
    private val adapter: RealtimeModelAdapter,
    private val listener: Listener,
) : Closeable {
    interface Listener {
        fun onStateChanged(state: State)
        fun onUserTranscript(text: String)
        fun onTextDelta(text: String)
        fun onAssistantTurnFinished()
        fun onFailure(message: String, cause: Throwable? = null)
    }

    enum class State { IDLE, CONNECTING, LISTENING, RESPONDING, INTERRUPTED, CLOSING, CLOSED, FAILED }

    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    @Volatile private var state = State.IDLE
    @Volatile private var audioLease: AudioSessionCoordinator.Lease? = null
    private val playbackQueue = ArrayBlockingQueue<ByteArray>(16)
    @Volatile private var playbackThread: Thread? = null
    @Volatile private var microphoneEnabled = true
    private val localVad = PcmVoiceActivityDetector()
    @Volatile private var localSpeechInterruptActive = false
    @Volatile private var userSpeechActive = false

    fun currentState(): State = state
    fun isUserSpeaking(): Boolean = userSpeechActive
    fun supportsImageInput(): Boolean = adapter.supportsImageInput

    private fun handleMicrophoneFrame(pcm: ByteArray, length: Int) {
        if (closed.get() || !microphoneEnabled) return
        when (localVad.process(pcm, length)) {
            PcmVoiceActivityDetector.Event.SPEECH_STARTED -> {
                userSpeechActive = true
                if (state == State.RESPONDING && !localSpeechInterruptActive) {
                    localSpeechInterruptActive = true
                    playbackQueue.clear()
                    audio.interruptPlayback()
                    adapter.cancelResponse()
                    transition(State.INTERRUPTED)
                }
            }
            PcmVoiceActivityDetector.Event.SPEECH_ENDED -> { localSpeechInterruptActive = false; userSpeechActive = false }
            else -> Unit
        }
        adapter.sendAudioFrame(pcm, length)
    }

    @Synchronized
    fun start() {
        check(state == State.IDLE) { "Realtime session is already active" }
        closed.set(false)
        transition(State.CONNECTING)
        try {
            adapter.requiredSampleRate?.let { expected ->
                require(audio.sampleRate == expected) { "当前音频引擎为 ${audio.sampleRate} Hz，但所选 Realtime 协议要求 ${expected} Hz PCM；请使用匹配采样率的引擎" }
            }
            val coordinator = AudioSessionCoordinator.get(appContext)
            audioLease = coordinator.acquire(AudioSessionCoordinator.Owner.REALTIME_CALL, speakerphone = true)
            coordinator.onFocusLost = { _ ->
                if (!closed.get()) {
                    playbackQueue.clear()
                    audio.interruptPlayback()
                    adapter.cancelResponse()
                    transition(State.INTERRUPTED)
                }
            }
            adapter.connect(object : RealtimeModelAdapter.Events {
                override fun onReady() {
                    if (closed.get()) return
                    try {
                        audio.setFrameConsumer(if (microphoneEnabled) ({ pcm, length -> handleMicrophoneFrame(pcm, length) }) else null)
                        audio.start()
                        startPlaybackWorker()
                        transition(State.LISTENING)
                    } catch (error: Throwable) {
                        fail("无法启动实时音频引擎", error)
                    }
                }

                override fun onUserTranscript(text: String) {
                    if (!closed.get() && text.isNotEmpty()) listener.onUserTranscript(text)
                }

                override fun onTextDelta(text: String) {
                    if (!closed.get() && text.isNotEmpty()) listener.onTextDelta(text)
                }

                override fun onAudioDelta(pcm16: ByteArray, length: Int) {
                    if (closed.get()) return
                    if (length <= 0 || length > pcm16.size || length % 2 != 0) {
                        fail("Realtime 服务返回了无效的 PCM16 音频帧", null)
                        return
                    }
                    transition(State.RESPONDING)
                    val chunk = pcm16.copyOf(length)
                    if (!playbackQueue.offer(chunk)) {
                        // Keep playback close to the live edge under network jitter.
                        // Discard the oldest queued audio instead of playing stale audio.
                        playbackQueue.poll()
                        playbackQueue.offer(chunk)
                    }
                }

                override fun onAssistantTurnFinished() {
                    if (!closed.get()) {
                        transition(State.LISTENING)
                        listener.onAssistantTurnFinished()
                    }
                }

                override fun onInterrupted() {
                    if (!closed.get()) {
                        playbackQueue.clear()
                        audio.interruptPlayback()
                        transition(State.INTERRUPTED)
                        transition(State.LISTENING)
                    }
                }

                override fun onFailure(message: String, cause: Throwable?) {
                    fail(message, cause)
                }
            })
        } catch (error: Throwable) {
            fail("实时语音会话启动失败", error)
        }
    }

    fun interruptAssistant() {
        if (closed.get()) return
        playbackQueue.clear()
        audio.interruptPlayback()
        adapter.cancelResponse()
        transition(State.INTERRUPTED)
    }

    fun endUserTurn() {
        if (!closed.get()) adapter.endUserTurn()
    }

    fun sendTextPrompt(text: String) {
        if (!closed.get() && text.isNotBlank()) adapter.sendTextPrompt(text.trim())
    }

    /**
     * Adds non-interrupting context to the conversation. Unlike sendTextPrompt,
     * this must not call response.create; the next user turn consumes the update.
     */
    fun sendContextUpdate(text: String) {
        if (!closed.get() && text.isNotBlank()) adapter.sendContextUpdate(text.trim())
    }

    /** Sends a throttled camera still into the active conversation when the model supports vision. */
    fun sendImageFrame(jpeg: ByteArray): Boolean {
        if (closed.get() || jpeg.isEmpty() || !adapter.supportsImageInput) return false
        return adapter.sendImageFrame(jpeg)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        if (closed.get()) return
        microphoneEnabled = enabled
        if (!enabled) { localVad.reset(); localSpeechInterruptActive = false; userSpeechActive = false }
        audio.setFrameConsumer(if (enabled) ({ pcm, length -> handleMicrophoneFrame(pcm, length) }) else null)
    }

    fun reportAudioFailure(message: String, cause: Throwable? = null) {
        fail(message, cause)
    }


    private fun startPlaybackWorker() {
        if (playbackThread?.isAlive == true) return
        playbackThread = Thread({
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val chunk = playbackQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    audio.writePlayback(chunk, chunk.size)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Throwable) {
                    if (!closed.get()) fail("播放 AI 实时语音失败", error)
                    break
                }
            }
        }, "HyperCat-RealtimePlayback").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun fail(message: String, cause: Throwable?) {
        if (closed.get() || !failureReported.compareAndSet(false, true)) return
        transition(State.FAILED)
        listener.onFailure(message, cause)
        close()
    }

    private fun transition(next: State) {
        state = next
        listener.onStateChanged(next)
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        transition(State.CLOSING)
        userSpeechActive = false
        audio.setFrameConsumer(null)
        playbackQueue.clear()
        playbackThread?.interrupt()
        playbackThread?.let { if (it !== Thread.currentThread()) runCatching { it.join(500) } }
        playbackThread = null
        runCatching { audio.close() }
        runCatching { adapter.close() }
        AudioSessionCoordinator.get(appContext).onFocusLost = null
        audioLease?.close()
        audioLease = null
        transition(State.CLOSED)
    }
}

/** Provider-specific wire protocols implement this interface. */
interface RealtimeModelAdapter : Closeable {
    /** Null when the provider accepts a negotiated or implementation-defined PCM rate. */
    val requiredSampleRate: Int? get() = null
    val supportsImageInput: Boolean get() = false
    interface Events {
        fun onReady()
        fun onUserTranscript(text: String)
        /** Incremental assistant text output. */
        fun onTextDelta(text: String)
        fun onAudioDelta(pcm16: ByteArray, length: Int)
        fun onAssistantTurnFinished()
        fun onInterrupted()
        fun onFailure(message: String, cause: Throwable? = null)
    }

    fun connect(events: Events)
    fun sendAudioFrame(pcm16: ByteArray, length: Int)
    fun sendImageFrame(jpeg: ByteArray): Boolean = false
    fun endUserTurn()
    fun sendTextPrompt(text: String) { }
    fun sendContextUpdate(text: String) { }
    fun cancelResponse()
}
