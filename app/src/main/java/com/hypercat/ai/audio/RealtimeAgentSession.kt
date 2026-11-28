package com.hypercat.ai.audio

import android.content.Context
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

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
    @Volatile private var state = State.IDLE
    @Volatile private var audioLease: AudioSessionCoordinator.Lease? = null

    fun currentState(): State = state

    @Synchronized
    fun start() {
        check(state == State.IDLE || state == State.CLOSED) { "Realtime session is already active" }
        closed.set(false)
        transition(State.CONNECTING)
        try {
            audioLease = AudioSessionCoordinator.get(appContext)
                .acquire(AudioSessionCoordinator.Owner.REALTIME_CALL, speakerphone = true)
            adapter.connect(object : RealtimeModelAdapter.Events {
                override fun onReady() {
                    if (closed.get()) return
                    try {
                        audio.setFrameConsumer { pcm, length -> adapter.sendAudioFrame(pcm, length) }
                        audio.start()
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
                    transition(State.RESPONDING)
                    try {
                        audio.writePlayback(pcm16, length)
                    } catch (error: Throwable) {
                        fail("播放 AI 实时语音失败", error)
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
        audio.interruptPlayback()
        adapter.cancelResponse()
        transition(State.INTERRUPTED)
    }

    fun endUserTurn() {
        if (!closed.get()) adapter.endUserTurn()
    }

    private fun fail(message: String, cause: Throwable?) {
        if (closed.get()) return
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
        audio.setFrameConsumer(null)
        runCatching { audio.close() }
        runCatching { adapter.close() }
        audioLease?.close()
        audioLease = null
        transition(State.CLOSED)
    }
}

/** Provider-specific wire protocols implement this interface. */
interface RealtimeModelAdapter : Closeable {
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
    fun endUserTurn()
    fun cancelResponse()
}
