package com.hypercat.ai.audio

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Agent-facing realtime session lifecycle; provider wire formats stay in adapters. */
class RealtimeAgentSession(private val audio: RealtimeAudioEngine, private val adapter: RealtimeModelAdapter, private val listener: Listener) : Closeable {
    interface Listener {
        fun onStateChanged(state: State)
        fun onTextDelta(text: String)
        fun onAssistantTurnFinished()
        fun onFailure(message: String, cause: Throwable? = null)
    }
    enum class State { IDLE, CONNECTING, LISTENING, RESPONDING, INTERRUPTED, CLOSING, CLOSED, FAILED }
    private val closed = AtomicBoolean(false)
    @Volatile private var state = State.IDLE
    fun currentState(): State = state
    fun start() {
        check(state == State.IDLE || state == State.CLOSED) { "Realtime session is already active" }
        closed.set(false)
        transition(State.CONNECTING)
        try {
            adapter.connect(object : RealtimeModelAdapter.Events {
                override fun onReady() {
                    if (closed.get()) return
                    try {
                        audio.setFrameConsumer { pcm, length -> adapter.sendAudioFrame(pcm, length) }
                        audio.start()
                        transition(State.LISTENING)
                    } catch (error: Throwable) { fail("无法启动实时音频引擎", error) }
                }
                override fun onTextDelta(text: String) { if (!closed.get()) listener.onTextDelta(text) }
                override fun onAudioDelta(pcm16: ByteArray, length: Int) {
                    if (closed.get()) return
                    transition(State.RESPONDING)
                    try { audio.writePlayback(pcm16, length) } catch (error: Throwable) { fail("播放 AI 实时语音失败", error) }
                }
                override fun onAssistantTurnFinished() {
                    if (!closed.get()) { transition(State.LISTENING); listener.onAssistantTurnFinished() }
                }
                override fun onInterrupted() {
                    if (!closed.get()) { audio.interruptPlayback(); transition(State.INTERRUPTED); transition(State.LISTENING) }
                }
                override fun onFailure(message: String, cause: Throwable?) { fail(message, cause) }
            })
        } catch (error: Throwable) { fail("实时语音模型连接失败", error) }
    }
    fun interruptAssistant() {
        if (closed.get()) return
        audio.interruptPlayback()
        adapter.cancelResponse()
        transition(State.INTERRUPTED)
    }
    fun endUserTurn() { if (!closed.get()) adapter.endUserTurn() }
    private fun fail(message: String, cause: Throwable?) {
        if (closed.get()) return
        transition(State.FAILED)
        listener.onFailure(message, cause)
        close()
    }
    private fun transition(next: State) { state = next; listener.onStateChanged(next) }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        transition(State.CLOSING)
        audio.setFrameConsumer(null)
        runCatching { audio.close() }
        runCatching { adapter.close() }
        transition(State.CLOSED)
    }
}

/** Implement this interface for any realtime model protocol; no vendor is hardcoded. */
interface RealtimeModelAdapter : Closeable {
    interface Events {
        fun onReady()
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
