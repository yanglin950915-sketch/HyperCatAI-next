package com.hypercat.ai.rear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Routes rear touch and recognized speech into the configured Agent model. */
class RearAgentInteractionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val action = inputData.getString(KEY_ACTION).orEmpty()
        val userText = inputData.getString(KEY_USER_TEXT).orEmpty().trim()
        val eventPrompt = eventPrompt(action, userText) ?: return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "").orEmpty().trim()
        val model = prefs.getString("model", "").orEmpty().trim()
        val apiKey = prefs.getString("api_key", "").orEmpty()
        if (endpoint.isBlank() || model.isBlank()) {
            val current = PetStateStore.read(applicationContext)
            PetStateStore.update(applicationContext, current.petName, current.petEmoji, "sad", "请先在主屏配置 Agent 模型，我就能回答你啦。")
            return@withContext Result.success()
        }
        try {
            val recent = prefs.getString("recent_chat_titles", "").orEmpty().split("||").firstOrNull { it.isNotBlank() }
            val title = recent ?: "背屏互动"
            val sessionKey = "chat_session_$title"
            val history = runCatching { JSONArray(prefs.getString(sessionKey, "[]") ?: "[]") }.getOrElse { JSONArray() }
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content",
                "你是 HyperCat AI 的二次元虚拟形象伙伴。用户通过手机主屏或背屏与你交流，请结合近期对话自然回应。保持角色性格，回复简短亲切，不输出推理过程。"))
            val start = (history.length() - 10).coerceAtLeast(0)
            for (i in start until history.length()) {
                val item = history.optJSONObject(i) ?: continue
                val text = item.optString("text")
                if (text.isNotBlank()) messages.put(JSONObject().put("role", if (item.optBoolean("fromUser")) "user" else "assistant").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", eventPrompt))
            val base = endpoint.trimEnd('/')
            val apiUrl = when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            val reply = try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $apiKey")
                val payload = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("模型请求失败（HTTP $code）")
                val message = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?: throw IllegalStateException("模型返回内容格式不正确")
                val raw = message.opt("content")
                when (raw) {
                    is String -> raw.trim()
                    is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it)?.optString("text") }.filter { it.isNotBlank() }.joinToString("\n").trim()
                    else -> ""
                }.ifBlank { "我收到你的互动啦！再和我说说话吧。" }
            } finally { connection.disconnect() }
            history.put(JSONObject().put("text", if (action == "pet_voice") userText else eventPrompt).put("fromUser", true).put("attachments", JSONArray()))
            history.put(JSONObject().put("text", reply).put("fromUser", false).put("attachments", JSONArray()))
            prefs.edit()
                .putString(sessionKey, history.toString())
                .putString("recent_chat_titles", (listOf(title) + prefs.getString("recent_chat_titles", "").orEmpty().split("||").filter { it.isNotBlank() && it != title }).take(12).joinToString("||"))
                .apply()
            val mood = when {
                listOf("开心", "高兴", "喜欢", "舒服", "嘿嘿", "好耶").any { reply.contains(it) } -> "happy"
                listOf("休息", "晚安", "睡觉").any { reply.contains(it) } -> "sleepy"
                listOf("惊讶", "哇", "竟然").any { reply.contains(it) } -> "surprised"
                else -> "normal"
            }
            val current = PetStateStore.read(applicationContext)
            PetStateStore.update(applicationContext, current.petName, current.petEmoji, mood, reply, userText = if (action == "pet_voice") userText else eventPrompt)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 2) return@withContext Result.retry()
            val current = PetStateStore.read(applicationContext)
            PetStateStore.update(applicationContext, current.petName, current.petEmoji, "sad", "刚才没能连接到 Agent，请稍后再试。")
            Result.success()
        }
    }

    private fun eventPrompt(action: String, text: String): String? = when (action) {
        "pet_touch", "pet_happy" -> "[背屏互动：用户摸了摸虚拟形象，请自然回应。]"
        "pet_long_press" -> "[背屏互动：用户长按虚拟形象，想开始聊天，请主动邀请用户交流。]"
        "pet_drag" -> "[背屏互动：用户在背屏上拖动了虚拟形象，请用轻松可爱的语气回应这次互动。]"
        "pet_thinking" -> "[背屏互动：用户点击思考，请给出一句简短回应。]"
        "pet_rest" -> "[背屏互动：用户让你休息，请温柔回应。]"
        "pet_voice" -> text.takeIf { it.isNotBlank() }
        else -> null
    }

    companion object {
        const val KEY_ACTION = "rear_interaction_action"
        const val KEY_USER_TEXT = "rear_interaction_user_text"
    }
}
