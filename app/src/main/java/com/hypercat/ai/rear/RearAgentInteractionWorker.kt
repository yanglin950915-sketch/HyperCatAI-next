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

/**
 * Runs rear-pet interactions through the same configured Agent model even when
 * the main activity is not visible. The interaction is persisted in the active
 * chat session so it can be restored when HyperCat AI is opened again.
 */
class RearAgentInteractionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val action = inputData.getString(KEY_ACTION).orEmpty()
        val eventPrompt = eventPrompt(action) ?: return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "").orEmpty().trim()
        val model = prefs.getString("model", "").orEmpty().trim()
        val apiKey = prefs.getString("api_key", "").orEmpty()
        if (endpoint.isBlank() || model.isBlank()) {
            PetStateStore.update(applicationContext, PetStateStore.read(applicationContext).petName, "🤖", "sad", "先在 HyperCat AI 设置中配置模型，我就能和你聊天啦。")
            return@withContext Result.success()
        }

        try {
            val recent = prefs.getString("recent_chat_titles", "").orEmpty()
                .split("||").firstOrNull { it.isNotBlank() }
            val title = recent ?: "背屏互动"
            val sessionKey = "chat_session_$title"
            val history = runCatching { JSONArray(prefs.getString(sessionKey, "[]") ?: "[]") }.getOrElse { JSONArray() }
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put(
                "content",
                "你是 HyperCat AI 的桌面式 AI 萌宠。用户正在手机背屏上直接触摸和互动，请结合互动动作与近期对话自然回应，像有性格的陪伴机器人。回复简短、亲切、有真实互动感；不要声称你感知到了未提供的现实信息，不要输出推理过程。若用户摸摸你，可以表达开心；长按表示想和你深入交流；思考按钮表示请你主动思考并问候；休息按钮表示进入休息状态。"
            ))
            val start = (history.length() - 10).coerceAtLeast(0)
            for (i in start until history.length()) {
                val item = history.optJSONObject(i) ?: continue
                val text = item.optString("text")
                if (text.isNotBlank()) {
                    messages.put(JSONObject()
                        .put("role", if (item.optBoolean("fromUser")) "user" else "assistant")
                        .put("content", text))
                }
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
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("模型请求失败（HTTP $code）")
                val message = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?: throw IllegalStateException("模型返回内容格式不正确")
                val raw = message.opt("content")
                when (raw) {
                    is String -> raw.trim()
                    is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it)?.optString("text") }
                        .filter { it.isNotBlank() }.joinToString("\n").trim()
                    else -> ""
                }.ifBlank { "我收到你的互动啦！再和我说说话吧。" }
            } finally {
                connection.disconnect()
            }

            history.put(JSONObject().put("text", eventPrompt).put("fromUser", true).put("attachments", JSONArray()))
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
            PetStateStore.update(applicationContext, current.petName, current.petEmoji, mood, reply)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 2) return@withContext Result.retry()
            val current = PetStateStore.read(applicationContext)
            PetStateStore.update(applicationContext, current.petName, current.petEmoji, "sad", "刚才没能连接到 Agent，请稍后再摸摸我。")
            Result.success()
        }
    }

    private fun eventPrompt(action: String): String? = when (action) {
        "pet_touch", "pet_happy" -> "[背屏互动事件：用户轻轻摸了摸萌宠。请自然回应这次抚摸。]"
        "pet_long_press" -> "[背屏互动事件：用户长按了萌宠，表示想和你深入交流。请主动开启一段简短对话，并邀请用户继续说。]"
        "pet_thinking" -> "[背屏互动事件：用户点了思考按钮。请像桌面机器人一样主动思考并说一句有个性的回应。]"
        "pet_rest" -> "[背屏互动事件：用户点了休息按钮。请用温柔简短的话回应，并表达进入休息状态。]"
        else -> null
    }

    companion object {
        const val KEY_ACTION = "rear_interaction_action"
    }
}
