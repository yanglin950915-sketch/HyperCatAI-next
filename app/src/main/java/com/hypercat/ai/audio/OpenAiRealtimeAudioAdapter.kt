package com.hypercat.ai.audio

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl\nimport okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * OpenAI Realtime-compatible WebSocket transport.
 *
 * This adapter is opt-in: callers must explicitly select a provider/model
 * that documents the Realtime API. A chat-completions URL is never upgraded
 * or guessed to be a realtime endpoint.
 */
class OpenAiRealtimeAudioAdapter(
    context: Context,
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val listener: Listener,
) : Closeable, RealtimeAudioEngine.Listener {

    interface Listener {
        fun onState(state: State)
        fun onTranscriptDelta(text: String)
        fun onAssistantTextDelta(text: String)
        fun onAssistantAudio(pcm16: ByteArray)
        fun onError(message: String)
    }

    enum class State { CONNECTING, LISTENING, RESPONDING, INTERRUPTED, CLOSED, FAILED }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val closed = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)
    private val engine = RealtimeAudioEngine(this)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var speechOnsetAt = 0L
    @Volatile private var lastSpeechAt = 0L

    fun connect() {
        require(endpoint.startsWith("wss://", ignoreCase = true)) {
            "Realtime endpoint must be an explicit wss:// URL"
        }
        require(model.isNotBlank()) { "Realtime model name is required" }
        require(apiKey.isNotBlank()) { "Realtime API key is required" }
        listener.onState(State.CONNECTING)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("OpenAI-Beta", "realtime=v1")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) { webSocket.close(1000, "closed"); return }
                webSocket.send(JSONObject()
                    .put("type", "session.update")
                    .put("session", JSONObject()
                        .put("modalities", org.json.JSONArray().put("text").put("audio"))
                        .put("instructions", "你是 HyperCat AI 的中文语音伙伴。自然、简洁地回答。")
                        .put("voice", "alloy")
                        .put("input_audio_format", "pcm16")
                        .put("output_audio_format", "pcm16")
                        .put("input_audio_transcription", JSONObject().put("model", "whisper-1"))
                        .put("turn_detection", JSONObject()
                            .put("type", "server_vad")
                            .put("threshold", 0.55)
                            .put("prefix_padding_ms", 300)
                            .put("silence_duration_ms", 500))))
                try {
                    engine.start()
                    listener.onState(State.LISTENING)
                } catch (error: Exception) {
                    fail("启动麦克风失败：${error.message ?: "设备不支持"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.get()) fail("Realtime 连接失败：${t.message ?: "网络异常"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) listener.onState(State.CLOSED)
            }
        })
    }

    override fun onAudioFrame(pcm16: ByteArray, length: Int) {
        val activeSocket = socket ?: return
        if (closed.get()) return
        val encoded = Base64.encodeToString(pcm16.copyOf(length), Base64.NO_WRAP)
        activeSocket.send(JSONObject()
            .put("type", "input_audio_buffer.append")
            .put("audio", encoded).toString())

        // Local speech-onset detection provides immediate barge-in while the
        // server VAD remains responsible for committing complete user turns.
        val rms = pcmRms(pcm16, length)
        val now = System.nanoTime() / 1_000_000L
        if (rms >= 0.035) {
            lastSpeechAt = now
            if (now - speechOnsetAt > 450L) {
                speechOnsetAt = now
                if (speaking.getAndSet(false)) {
                    engine.interruptPlayback()
                    activeSocket.send(JSONObject().put("type", "response.cancel").toString())
                    listener.onState(State.INTERRUPTED)
                }
            }
        } else if (now - lastSpeechAt > 900L) {
            speechOnsetAt = 0L
        }
    }

    override fun onError(message: String, cause: Throwable?) {
        if (!closed.get()) fail(message)
    }

    private fun handleServerEvent(webSocket: WebSocket, raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (event.optString("type")) {
            "input_audio_buffer.speech_started" -> {
                if (speaking.getAndSet(false)) {
                    engine.interruptPlayback()
                    webSocket.send(JSONObject().put("type", "response.cancel").toString())
                    listener.onState(State.INTERRUPTED)
                }
            }
            "input_audio_buffer.committed" -> listener.onState(State.RESPONDING)
            "conversation.item.input_audio_transcription.delta" ->
                listener.onTranscriptDelta(event.optString("delta"))
            "response.text.delta", "response.output_text.delta" -> {
                listener.onState(State.RESPONDING)
                listener.onAssistantTextDelta(event.optString("delta"))
            }
            "response.audio.delta", "response.output_audio.delta" -> {
                val audio = runCatching {
                    Base64.decode(event.optString("delta"), Base64.DEFAULT)
                }.getOrNull() ?: return
                speaking.set(true)
                scope.launch(Dispatchers.IO) {
                    runCatching { engine.writePlayback(audio) }
                        .onFailure { if (!closed.get()) fail("音频播放失败：${it.message}") }
                }
                listener.onState(State.RESPONDING)
            }
            "response.audio.done", "response.done" -> {
                speaking.set(false)
                listener.onState(State.LISTENING)
            }
            "error" -> fail(event.optJSONObject("error")?.optString("message") ?: "Realtime 模型返回错误")
        }
    }

    private fun pcmRms(bytes: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
            val normalized = sample / 32768.0
            sum += normalized * normalized
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    private fun fail(message: String) {
        if (closed.get()) return
        listener.onState(State.FAILED)
        listener.onError(message)
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        engine.stop()
        socket?.close(1000, "HyperCat session closed")
        socket = null
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        listener.onState(State.CLOSED)
    }
}
