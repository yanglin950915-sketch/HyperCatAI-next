package com.hypercat.ai.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Server-sent-events transport for OpenAI-compatible /chat/completions APIs.
 * It streams text deltas only; it does not pretend a text model is a native
 * realtime audio model. Speech recognition and TTS remain separate adapters.
 */
object OpenAiCompatibleStreamClient {
    suspend fun stream(
        endpoint: String,
        apiKey: String,
        payload: JSONObject,
        onDelta: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val base = endpoint.trim().trimEnd('/')
        require(base.isNotBlank()) { "模型 API 地址不能为空" }
        val apiUrl = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val request = JSONObject(payload.toString()).put("stream", true)
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val complete = StringBuilder()
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = (connection.errorStream ?: connection.inputStream).bufferedReader().use { it.readText() }
                val message = runCatching { JSONObject(detail).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("流式模型请求失败（HTTP $code）" +
                    (message?.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""))
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val event = runCatching { JSONObject(data) }.getOrElse {
                        // Ignore non-JSON SSE comments/keepalives, but surface malformed content events.
                        return@use
                    }
                    val error = event.optJSONObject("error")
                    if (error != null) throw IllegalStateException(error.optString("message", "模型流式响应出错"))
                    val delta = event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                    val content = when (val value = delta?.opt("content")) {
                        is String -> value
                        is org.json.JSONArray -> (0 until value.length()).mapNotNull { idx ->
                            value.optJSONObject(idx)?.optString("text")?.takeIf { it.isNotBlank() }
                        }.joinToString("")
                        else -> ""
                    }
                    if (content.isNotEmpty()) {
                        complete.append(content)
                        onDelta(content)
                    }
                }
            }
            complete.toString()
        } finally {
            connection.disconnect()
        }
    }
}
