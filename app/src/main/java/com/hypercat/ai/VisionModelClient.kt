package com.hypercat.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible vision request adapter.
 * The camera only stores a recent JPEG locally; this request is made when
 * the caller explicitly asks about the scene.
 */
internal data class RealtimeVisionObservation(
    val summary: String,
    val notableEvent: String? = null
)

internal object VisionModelClient {
    /**
     * Produces passive scene context and a conservative signal for events
     * that may merit one proactive voice response.
     */
    fun observeRealtimeFrame(
        endpoint: String,
        apiKey: String,
        model: String,
        jpeg: ByteArray
    ): RealtimeVisionObservation {
        val raw = describeFrame(
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            jpeg = jpeg,
            question = """你是视频通话中的视觉观察器。只输出 JSON，不要 Markdown。格式：{"summary":"80字以内的客观场景摘要","notable":false,"event":""}。只有出现明确且值得主动提醒用户的重大变化时 notable 才为 true，例如用户明显向镜头展示一个物品、场景发生显著变化、出现需要用户注意的即时情况。普通姿势变化、光线变化、画面抖动、持续静态场景、无法确认的动作一律 notable=false。不要推测身份、意图、情绪或不可见细节；event 只描述可见事实，最多40字。"""
        ).trim()
        val jsonText = raw.removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val parsed = runCatching { org.json.JSONObject(jsonText) }.getOrNull()
            ?: return RealtimeVisionObservation(raw.take(240))
        val summary = parsed.optString("summary").trim().take(240)
        val notable = parsed.optBoolean("notable", false)
        val event = parsed.optString("event").trim()
            .takeIf { notable && it.isNotBlank() }?.take(120)
        return RealtimeVisionObservation(summary.ifBlank { raw.take(240) }, event)
    }

    fun describeFrame(
        endpoint: String,
        apiKey: String,
        model: String,
        jpeg: ByteArray,
        question: String
    ): String {
        require(endpoint.isNotBlank()) { "视觉服务地址为空" }
        // API keys are opaque tokens; pasted values may contain line breaks or
        // spaces (including a wrapped "Bearer" prefix). HTTP headers reject CR/LF.
        val normalizedApiKey = apiKey.trim()
            .replace(Regex("(?i)^Bearer\\s+"), "")
            .filterNot { it.isWhitespace() }
        require(normalizedApiKey.isNotBlank()) { "视觉 API Key 为空" }
        require(model.isNotBlank()) { "视觉模型名称为空" }
        require(jpeg.isNotEmpty()) { "没有可用的摄像头画面" }

        val base = endpoint.trim().trimEnd('/')
        val apiUrl = when {
            base.endsWith("/chat/completions") -> base
            else -> "$base/chat/completions"
        }
        val imageData = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "请简洁回答用户关于这张图片的问题，优先给出直接结论；无法确认的内容请说明不确定。用户问题：$question"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageData)))
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 192)

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $normalizedApiKey")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                val hint = if (code == 401) "；请确认视觉模型 API Key 属于当前服务商、未过期且未重复填写 Bearer 前缀" else ""
                throw IllegalStateException("视觉模型请求失败（HTTP $code）" + detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty() + hint)
            }
            val message = JSONObject(responseText)
                .optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw IllegalStateException("视觉模型返回格式不正确")
            val answer = message.optString("content").trim()
            if (answer.isBlank()) throw IllegalStateException("视觉模型没有返回可读内容")
            return answer
        } finally {
            connection.disconnect()
        }
    }
}
