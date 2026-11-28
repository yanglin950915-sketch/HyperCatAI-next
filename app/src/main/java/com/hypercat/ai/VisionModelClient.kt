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
internal object VisionModelClient {
    fun describeFrame(
        endpoint: String,
        apiKey: String,
        model: String,
        jpeg: ByteArray,
        question: String
    ): String {
        require(endpoint.isNotBlank()) { "视觉服务地址为空" }
        val normalizedApiKey = apiKey.trim()
            .removePrefix("Bearer ").removePrefix("bearer ").trim()
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
            .put(JSONObject().put("type", "text").put("text", "请根据摄像头最新画面回答用户的问题。若画面无法确认，请明确说明不确定。用户问题：$question"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageData)))
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 700)

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 45_000
            connection.doOutput = true
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
