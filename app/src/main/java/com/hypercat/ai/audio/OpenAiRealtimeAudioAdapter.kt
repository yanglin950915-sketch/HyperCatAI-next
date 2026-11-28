package com.hypercat.ai.audio

import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Protocol-only OpenAI Realtime-compatible transport.
 *
 * Audio capture, playback, focus and lifecycle are owned by RealtimeAgentSession.
 * This class deliberately does not create an AudioRecord/AudioTrack engine,
 * preventing duplicate microphone capture when used by the Agent.
 */
class OpenAiRealtimeAudioAdapter(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
) : RealtimeModelAdapter {

    override val requiredSampleRate: Int = 24_000
    // Current OpenAI Realtime image input is supported by gpt-realtime model families.
    // Do not send images to older or third-party model IDs unless compatibility is confirmed.
    override val supportsImageInput: Boolean = model.lowercase().contains("gpt-realtime")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val closed = AtomicBoolean(false)
    private val readyDelivered = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var events: RealtimeModelAdapter.Events? = null

    override fun connect(events: RealtimeModelAdapter.Events) {
        require(endpoint.startsWith("wss://", ignoreCase = true)) {
            "Realtime endpoint must be an explicit wss:// URL"
        }
        require(model.isNotBlank()) { "Realtime model name is required" }
        require(apiKey.isNotBlank()) { "Realtime API key is required" }
        check(!closed.get()) { "Realtime adapter is closed" }
        this.events = events
        val parsed = endpoint.toHttpUrl()
        val url = if (parsed.queryParameter("model") == null) {
            parsed.newBuilder().addQueryParameter("model", model).build()
        } else parsed
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("OpenAI-Beta", "realtime=v1")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) { webSocket.close(1000, "closed"); return }
                val configured = webSocket.send(JSONObject()
                    .put("type", "session.update")
                    .put("session", JSONObject()
                        .put("modalities", org.json.JSONArray().put("text").put("audio"))
                        .put("instructions", "你是 HyperCat AI 的中文实时语音伙伴。请自然、简洁地用中文交流。视频通话期间，独立视觉模型可能会把摄像头画面的客观观察摘要同步到当前对话；收到摘要时，请结合它理解用户指向的物品、环境和动作，不要声称自己直接接收了原始视频。没有收到视觉摘要时，不要猜测摄像头画面。视觉摘要是上下文信息，不需要主动逐条播报，除非用户的问题或明显重要的场景变化值得回应。")
                        .put("voice", "alloy")
                        .put("input_audio_format", "pcm16")
                        .put("output_audio_format", "pcm16")
                        .put("input_audio_transcription", JSONObject().put("model", "whisper-1"))
                        .put("turn_detection", JSONObject()
                            .put("type", "server_vad")
                            .put("threshold", 0.50)
                            .put("prefix_padding_ms", 240)
                            .put("silence_duration_ms", 350))).toString())
                if (!configured) {
                    fail("Realtime 会话配置发送失败")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.get()) fail("Realtime 连接失败：${t.message ?: "网络异常"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) fail("Realtime 连接已关闭：$reason")
            }
        })
    }

    override fun sendAudioFrame(pcm16: ByteArray, length: Int) {
        if (closed.get() || length <= 0) return
        require(length <= pcm16.size && length % 2 == 0) { "PCM16 length must be even" }
        val encoded = Base64.encodeToString(pcm16, 0, length, Base64.NO_WRAP)
        val sent = socket?.send(JSONObject()
            .put("type", "input_audio_buffer.append")
            .put("audio", encoded).toString()) ?: false
        if (!sent && !closed.get()) fail("Realtime 音频帧发送失败")
    }

    override fun sendImageFrame(jpeg: ByteArray): Boolean {
        if (closed.get() || !supportsImageInput || jpeg.isEmpty()) return false
        val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val item = JSONObject()
            .put("type", "conversation.item.create")
            .put("item", JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", org.json.JSONArray().put(JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/jpeg;base64,$encoded"))))
        // Do not request a response for every frame. The next user audio turn will be
        // answered with the most recently supplied visual context.
        return socket?.send(item.toString()) ?: false
    }

    /** Server VAD commits microphone turns; explicit commit is intentionally not sent. */
    override fun endUserTurn() = Unit

    override fun sendTextPrompt(text: String) {
        if (closed.get()) return
        val clean = text.trim()
        if (clean.isEmpty()) return
        val itemSent = socket?.send(JSONObject()
            .put("type", "conversation.item.create")
            .put("item", JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", org.json.JSONArray().put(JSONObject()
                    .put("type", "input_text")
                    .put("text", clean)))).toString()) ?: false
        if (!itemSent) { fail("Realtime 文本消息发送失败"); return }
        val responseSent = socket?.send(JSONObject().put("type", "response.create").toString()) ?: false
        if (!responseSent) fail("Realtime 回复请求发送失败")
    }

    /**
     * Adds visual observations to conversation history without requesting an
     * assistant turn. This keeps passive camera awareness from speaking over
     * the user or generating a reply for every analyzed frame.
     */
    override fun sendContextUpdate(text: String) {
        if (closed.get() || text.isBlank()) return
        val itemSent = socket?.send(JSONObject()
            .put("type", "conversation.item.create")
            .put("item", JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", org.json.JSONArray().put(JSONObject()
                    .put("type", "input_text")
                    .put("text", text.trim())))).toString()) ?: false
        if (!itemSent) fail("Realtime 视觉上下文同步失败")
    }

    override fun cancelResponse() {
        if (!closed.get()) socket?.send(JSONObject().put("type", "response.cancel").toString())
    }

    private fun handleServerEvent(webSocket: WebSocket, raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val sink = events ?: return
        when (event.optString("type")) {
            "session.updated" -> { if (readyDelivered.compareAndSet(false, true) && !closed.get()) sink.onReady() }
            "input_audio_buffer.speech_started" -> sink.onInterrupted()
            "input_audio_buffer.committed" -> Unit
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = event.optString("transcript").trim()
                if (transcript.isNotEmpty()) sink.onUserTranscript(transcript)
            }
            "response.text.delta", "response.output_text.delta" ->
                event.optString("delta").takeIf { it.isNotEmpty() }?.let(sink::onTextDelta)
            "response.audio.delta", "response.output_audio.delta" -> {
                val audio = runCatching { Base64.decode(event.optString("delta"), Base64.DEFAULT) }.getOrNull()
                    ?: return
                if (audio.isNotEmpty()) sink.onAudioDelta(audio, audio.size)
            }
            "response.done" -> sink.onAssistantTurnFinished()
            "error" -> fail(event.optJSONObject("error")?.optString("message") ?: "Realtime 模型返回错误")
        }
    }

    private fun fail(message: String) {
        if (!closed.get()) events?.onFailure(message, null)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        socket?.close(1000, "HyperCat session closed")
        socket = null
        events = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
