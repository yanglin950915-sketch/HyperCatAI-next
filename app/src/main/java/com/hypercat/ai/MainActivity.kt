package com.hypercat.ai

import android.content.Context
import android.app.DownloadManager
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Base64
import android.os.Bundle
import android.os.Environment
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.webkit.URLUtil
import com.hypercat.ai.rear.PetStateStore
import com.hypercat.ai.rear.RearAgentInteractionDispatcher
import com.hypercat.ai.rear.RearProjectionController
import kotlin.random.Random

private var appDarkState by mutableStateOf(true)
private val Bg get() = if (appDarkState) Color(0xFF000000) else Color(0xFFF1F5FA)
private val Panel get() = if (appDarkState) Color(0xFF101010) else Color(0xFFFFFFFF)
private val Panel2 get() = if (appDarkState) Color(0xFF1B1B1B) else Color(0xFFE4EDF6)
private val Cyan get() = if (appDarkState) Color(0xFFE6E6E6) else Color(0xFF168DA6)
private val Muted get() = if (appDarkState) Color(0xFFA0A0A0) else Color(0xFF65758A)
private val GlassBorder get() = if (appDarkState) Color.White.copy(alpha = .12f) else Color(0xFF8AA4BA).copy(alpha = .35f)
private val MainText get() = if (appDarkState) Color(0xFFF5F5F5) else Color(0xFF142235)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val updateCheck = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "hypercat-release-check", ExistingPeriodicWorkPolicy.KEEP, updateCheck
        )
        setContent { HyperCatApp() }
    }
}

private fun createCallSpeechRecognizer(context: Context): SpeechRecognizer =
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
        SpeechRecognizer.createSpeechRecognizer(context)
    } else if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

private fun createCallRecognitionIntent(context: Context) =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }

private enum class Page { CHAT, SETTINGS }

private data class ModelReply(val text: String, val reasoning: String = "")

private fun requestModelReply(context: Context, endpoint: String, model: String, apiKey: String, history: List<ChatLine>, memories: List<String> = emptyList()): ModelReply {
    val base = endpoint.trim().trimEnd('/')
    val apiUrl = when { base.endsWith("/chat/completions") -> base; base.endsWith("/v1") -> "$base/chat/completions"; else -> "$base/v1/chat/completions" }
    val requestMessages = org.json.JSONArray()
    val baseSystemPrompt = "请根据问题复杂度组织答复：简单问题（如问候、常识、简单事实或直接操作指引）直接给出简洁结论，不要输出思路说明、分析过程或推理过程；只有复杂问题确有必要时，才在最终答复前提供2-4条简短、可核验的方法或依据，不输出隐式思维链、私人内心独白或冗长逐字推理。语音朗读只朗读最终答复内容，不朗读思路说明或分析内容。代码请使用标准 Markdown 三反引号代码块，并标注语言。"
    val memoryContext = memories.filter { it.isNotBlank() }.take(40).joinToString("\n") { "- ${it.take(500)}" }
    val systemPrompt = if (memoryContext.isBlank()) baseSystemPrompt else "$baseSystemPrompt\n\n以下是用户明确保存的长期记忆，仅在与当前问题相关时参考；若用户当前指令与记忆冲突，以当前指令为准：\n$memoryContext"
    requestMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))
    history.forEach { line ->
        if (line.text.isNotBlank() || line.photos.isNotEmpty() || line.imageUris.isNotEmpty()) {
            val role = if (line.fromUser) "user" else "assistant"
            if (line.fromUser && (line.photos.isNotEmpty() || line.imageUris.isNotEmpty())) {
                val content = org.json.JSONArray()
                if (line.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", line.text))
                line.photos.forEach { bitmap ->
                    val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
                    content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(bytes)}")))
                }
                line.imageUris.forEach { uri ->
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    if (mime.startsWith("image/")) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}")))
                    }
                }
                requestMessages.put(JSONObject().put("role", role).put("content", content))
            } else if (line.text.isNotBlank()) requestMessages.put(JSONObject().put("role", role).put("content", line.text))
        }
    }
    val payload = JSONObject().put("model", model).put("messages", requestMessages).put("stream", false)
    val connection = URL(apiUrl).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 90000; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }.getOrNull()
            throw IllegalStateException("模型请求失败（HTTP $code）${detail?.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}")
        }
        val message = JSONObject(responseText).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: throw IllegalStateException("模型返回内容格式不正确")
        val value = message.opt("content")
        var answer = when (value) {
            is String -> value
            is org.json.JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it)?.optString("text") }.filter { it.isNotBlank() }.joinToString("\n")
            else -> "模型已返回，但没有可显示的文字内容。"
        }.ifBlank { "模型返回了空内容，请检查模型服务配置。" }
        var reasoning = listOf("reasoning_content", "reasoning", "analysis")
            .firstNotNullOfOrNull { key -> message.optString(key).takeIf { it.isNotBlank() && it != "null" } }.orEmpty()
        val tagged = Regex("(?s)<思路说明>(.*?)</思路说明>").find(answer)
        if (reasoning.isBlank() && tagged != null) reasoning = tagged.groupValues[1].trim()
        if (tagged != null) answer = answer.removeRange(tagged.range).trim()
        return ModelReply(answer, reasoning)
    } finally { connection.disconnect() }
}


private fun requestSpeechAudio(endpoint: String, apiKey: String, model: String, voice: String, input: String, speed: Float): ByteArray {
    val rawBase = endpoint.trim()
    require(rawBase.isNotBlank()) { "TTS 服务地址不能为空" }
    val parsedBase = runCatching { URL(rawBase) }.getOrElse {
        throw IllegalArgumentException("TTS 服务地址格式无效，请填写 https:// 开头的 API 基础地址", it)
    }
    require(parsedBase.protocol.equals("https", true) || parsedBase.protocol.equals("http", true)) {
        "TTS 服务地址必须是有效的 HTTP/HTTPS 地址"
    }
    // Detect MiMo by official host as well as model name, so a saved profile with an unexpected model label cannot fall through to /audio/speech.
    val isXiaomiMiMo = parsedBase.host.equals("api.xiaomimimo.com", ignoreCase = true) ||
        model.trim().startsWith("mimo-v2.5-tts", ignoreCase = true)
    val endpointPath = parsedBase.path.trimEnd('/')
        .removeSuffix("/chat/completions")
        .removeSuffix("/audio/speech")
    val apiUrl = if (isXiaomiMiMo) {
        // Xiaomi's official host has one canonical endpoint. Ignore accidental
        // path suffixes in the saved profile so /v1 is never duplicated.
        if (parsedBase.host.equals("api.xiaomimimo.com", ignoreCase = true)) {
            "https://api.xiaomimimo.com/v1/chat/completions"
        } else {
            val versionedPath = if (endpointPath.endsWith("/v1")) endpointPath else "${endpointPath}/v1"
            URL(parsedBase.protocol, parsedBase.host, parsedBase.port, "${versionedPath}/chat/completions").toString()
        }
    } else {
        val baseUrl = URL(parsedBase.protocol, parsedBase.host, parsedBase.port, endpointPath).toString().trimEnd('/')
        when {
            parsedBase.path.endsWith("/audio/speech") -> URL(parsedBase.protocol, parsedBase.host, parsedBase.port, parsedBase.path).toString()
            endpointPath.endsWith("/v1") -> "$baseUrl/audio/speech"
            else -> "$baseUrl/v1/audio/speech"
        }
    }
    val payload = if (isXiaomiMiMo) {
        val supportedVoices = setOf("mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")
        val selectedVoice = voice.trim().takeIf { it in supportedVoices } ?: "mimo_default"
        val messages = org.json.JSONArray()
        val rate = speed.coerceIn(0.6f, 1.6f)
        val style = if (kotlin.math.abs(rate - 1.0f) < 0.05f) {
            "请使用自然、清晰的语气播报。"
        } else {
            "请使用自然、清晰的语气，并以约${String.format(Locale.US, "%.1f", rate)}倍语速播报。"
        }
        messages.put(JSONObject().put("role", "user").put("content", style))
        messages.put(JSONObject().put("role", "assistant").put("content", input))
        JSONObject().put("model", model.trim()).put("messages", messages)
            .put("audio", JSONObject().put("format", "wav").put("voice", selectedVoice))
            .put("stream", false)
    } else {
        JSONObject().put("model", model.trim()).put("voice", voice.trim().ifBlank { "alloy" })
            .put("input", input).put("response_format", "mp3")
            .put("speed", speed.coerceIn(0.6f, 1.6f).toDouble())
    }
    val connection = URL(apiUrl).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"; connection.connectTimeout = 20000; connection.readTimeout = 90000; connection.doOutput = true
        connection.setRequestProperty("Connection", "close")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", if (isXiaomiMiMo) "application/json" else "audio/mpeg, audio/*")
        if (apiKey.isNotBlank()) {
            if (isXiaomiMiMo) connection.setRequestProperty("api-key", apiKey)
            else connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val contentType = connection.getHeaderField("Content-Type").orEmpty()
            val server = connection.getHeaderField("Server").orEmpty()
            val message = runCatching {
                val json = JSONObject(detail)
                json.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("message").takeIf { it.isNotBlank() && it != "null" }
            }.getOrNull()
            val safeDetail = message ?: detail
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(1600)
            val hostPath = runCatching { URL(apiUrl).let { "${it.host}${it.path}" } }.getOrDefault("未知地址")
            val diagnostics = buildString {
                append("请求：").append(hostPath)
                if (contentType.isNotBlank()) append("；类型：").append(contentType.take(80))
                if (server.isNotBlank()) append("；服务器：").append(server.take(60))
                if (safeDetail.isNotBlank()) append("；详情：").append(safeDetail)
            }
            throw IllegalStateException("云端 TTS 请求失败（HTTP $code）：$diagnostics")
        }
        val responseBytes = connection.inputStream.use { it.readBytes() }
        if (responseBytes.isEmpty()) throw IllegalStateException("TTS 服务返回了空音频")
        val bytes = if (isXiaomiMiMo) {
            val response = JSONObject(String(responseBytes, Charsets.UTF_8))
            val audioData = response.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optJSONObject("audio")?.optString("data").orEmpty()
            if (audioData.isBlank() || audioData == "null") {
                val detail = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?.optString("content").orEmpty()
                throw IllegalStateException("MiMo TTS 未返回音频数据${detail.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}")
            }
            try { Base64.getDecoder().decode(audioData) }
            catch (error: IllegalArgumentException) { throw IllegalStateException("MiMo TTS 返回的音频编码无法解析", error) }
        } else responseBytes
        if (bytes.isEmpty()) throw IllegalStateException("TTS 服务返回了空音频")
        return bytes
    } finally { connection.disconnect() }
}

private suspend fun playSpeechAudio(context: Context, bytes: ByteArray, usage: Int = AudioAttributes.USAGE_MEDIA) {
    val extension = if (bytes.size >= 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) ".wav" else ".mp3"
    val file = withContext(Dispatchers.IO) { File.createTempFile("hypercat-tts-", extension, context.cacheDir).apply { writeBytes(bytes) } }
    try {
        suspendCancellableCoroutine<Unit> { continuation ->
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setVolume(1.0f, 1.0f)
            fun release() { runCatching { player.reset() }; runCatching { player.release() } }
            player.setOnPreparedListener { media -> runCatching { media.start() }.onFailure {
                release(); if (continuation.isActive) continuation.resumeWithException(it)
            } }
            player.setOnCompletionListener {
                release(); if (continuation.isActive) continuation.resume(Unit)
            }
            player.setOnErrorListener { _, what, extra ->
                release(); if (continuation.isActive) continuation.resumeWithException(IllegalStateException("音频播放失败（$what/$extra）")); true
            }
            continuation.invokeOnCancellation { release(); file.delete() }
            try { player.setDataSource(file.absolutePath); player.prepareAsync() }
            catch (error: Exception) { release(); if (continuation.isActive) continuation.resumeWithException(error) }
        }
    } finally { withContext(Dispatchers.IO) { file.delete() } }
}

private suspend fun speakWithCloudTts(context: Context, profile: TtsProfile, model: String, voice: String, text: String, speed: Float, usage: Int = AudioAttributes.USAGE_MEDIA) {
    val audio = withContext(Dispatchers.IO) { requestSpeechAudio(profile.endpoint, profile.apiKey, model, voice, text, speed) }
    playSpeechAudio(context, audio, usage)
}

private fun safeTtsDiagnostic(error: Exception, profile: TtsProfile, model: String): String {
    val raw = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    val redacted = (if (profile.apiKey.isNotBlank()) raw.replace(profile.apiKey, "[API Key 已隐藏]") else raw)
        .replace(Regex("(?i)(api[-_ ]?key|authorization)(\\s*[:=]\\s*)[^\\s,;]+"), "$1$2[已隐藏]")
    val safeEndpoint = (if (profile.apiKey.isNotBlank()) profile.endpoint.replace(profile.apiKey, "[API Key 已隐藏]") else profile.endpoint)
        .replace(Regex("(?i)([?&](?:api[-_]?key|token|access_token)=)[^&]+"), "$1[已隐藏]")
    return buildString {
        appendLine("模型档案：").appendLine(profile.name)
        appendLine("TTS 模型：").appendLine(model)
        appendLine("配置的 API Base URL：").appendLine(safeEndpoint)
        appendLine()
        append(redacted)
    }
}

private fun speechText(source: String): String {
    return source
        .replace(Regex("```[\\w+-]*"), "代码段：")
        .replace("```", "。")
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~\\x60]"), "")
        .replace(Regex("^\\s*[-+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*\\d+[.)、]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun splitSpeechText(text: String, maxLength: Int = 2800): List<String> {
    if (text.length <= maxLength) return listOf(text)
    val chunks = mutableListOf<String>()
    var remaining = text
    while (remaining.isNotEmpty()) {
        if (remaining.length <= maxLength) { chunks += remaining.trim(); break }
        var cut = remaining.lastIndexOfAny(charArrayOf('。', '！', '？', '；', '\n'), maxLength)
        if (cut < maxLength / 2) cut = maxLength else cut += 1
        chunks += remaining.substring(0, cut).trim()
        remaining = remaining.substring(cut).trimStart()
    }
    return chunks.filter { it.isNotBlank() }
}

private fun looksLikeAgentEcho(recognized: String, agentText: String): Boolean {
    val heard = recognized.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    val spoken = agentText.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    if (heard.length < 4 || spoken.length < 4) return false
    if (spoken.contains(heard) || heard == spoken) return true
    val heardBigrams = (0 until heard.length - 1).map { heard.substring(it, it + 2) }.toSet()
    val spokenBigrams = (0 until spoken.length - 1).map { spoken.substring(it, it + 2) }.toSet()
    if (heardBigrams.isEmpty()) return false
    return heardBigrams.intersect(spokenBigrams).size.toFloat() / heardBigrams.size >= 0.78f && heard.length >= 6
}

private fun inferMood(reply: String): String = when {
    listOf("开心", "高兴", "太棒", "喜欢", "成功").any { reply.contains(it) } -> "happy"
    listOf("难过", "抱歉", "遗憾", "失败").any { reply.contains(it) } -> "sad"
    listOf("什么", "竟然", "惊讶", "哇").any { reply.contains(it) } -> "surprised"
    listOf("思考", "考虑", "分析", "让我想想").any { reply.contains(it) } -> "thinking"
    else -> "normal"
}
private fun exportRearWidgetPackage(context: Context) {
    runCatching {
        val directory = File(context.cacheDir, "rear-widget").apply { mkdirs() }
        val archive = File(directory, "HyperCatAI-RearWidget.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            listOf("manifest.xml", "web/index.html", "REAREye_IMPORT_PROMPT.md").forEach { assetPath ->
                zip.putNextEntry(ZipEntry(assetPath))
                context.assets.open(assetPath).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", archive
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HyperCat AI 背屏萌宠组件")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "将背屏组件包分享给 REAREye"))
    }.onFailure {
        android.widget.Toast.makeText(context, "组件包导出失败：${it.message ?: "未知错误"}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private data class ChatLine(val text: String, val fromUser: Boolean, val attachments: List<String> = emptyList(), val photos: List<Bitmap> = emptyList(), val imageUris: List<Uri> = emptyList(), val reasoning: String = "")

private fun serializeChatHistory(messages: List<ChatLine>): String = org.json.JSONArray().apply {
    messages.forEach { line ->
        put(JSONObject().put("text", line.text).put("fromUser", line.fromUser).put("reasoning", line.reasoning)
            .put("attachments", org.json.JSONArray(line.attachments)))
    }
}.toString()

private fun loadChatHistory(context: Context, title: String): List<ChatLine> {
    if (title.isBlank()) return emptyList()
    val raw = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        .getString("chat_session_$title", null) ?: return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val files = item.optJSONArray("attachments") ?: org.json.JSONArray()
            ChatLine(item.optString("text"), item.optBoolean("fromUser"),
                attachments = (0 until files.length()).map { files.optString(it) },
                reasoning = item.optString("reasoning"))
        }
    }.getOrDefault(emptyList())
}

private fun formatChatForSharing(title: String, messages: List<ChatLine>): String = buildString {
    append("HyperCat AI 对话记录")
    if (title.isNotBlank()) append("：").append(title)
    append("\n\n")
    messages.forEach { line ->
        if (line.text.isNotBlank()) {
            append(if (line.fromUser) "我：" else "Agent：").append(line.text).append("\n\n")
        }
        if (line.attachments.isNotEmpty()) append("附件：").append(line.attachments.joinToString("、")).append("\n\n")
    }
}.trim()

private fun shareChatText(context: Context, text: String, chooserTitle: String = "分享对话") {
    if (text.isBlank()) {
        Toast.makeText(context, "没有可分享的对话内容", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(intent, chooserTitle)) }
}



private data class GeneratedDownloadLink(val url: String, val isImage: Boolean)

private fun extractGeneratedDownloadLinks(text: String): List<GeneratedDownloadLink> {
    val markdownImages = Regex("!\\[[^]]*]\\((https?://[^)\\s]+)\\)")
        .findAll(text).map { it.groupValues[1].trimEnd('.', ',', ';', ')', ']') }.toSet()
    val urls = Regex("https?://[^\\s<>\\\"']+").findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ')', ']', '，', '。') }.distinct().toList()
    return urls.map { url ->
        val extension = Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
        GeneratedDownloadLink(url, url in markdownImages || extension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif"))
    }
}

private fun saveGeneratedDownload(context: Context, link: GeneratedDownloadLink) {
    runCatching {
        val uri = Uri.parse(link.url)
        require(uri.scheme == "https" || uri.scheme == "http") { "仅支持保存 HTTP/HTTPS 链接" }
        val rawName = URLUtil.guessFileName(link.url, null, if (link.isImage) "image/*" else "application/octet-stream")
        val safeName = rawName.replace(Regex("[\\\\/:*?\\\"<>|]"), "_").take(120)
            .ifBlank { if (link.isImage) "HyperCat_AI_image.jpg" else "HyperCat_AI_file" }
        val fileName = "HyperCatAI_" + System.currentTimeMillis() + "_" + safeName
        val request = DownloadManager.Request(uri)
            .setTitle(safeName)
            .setDescription(if (link.isImage) "保存到图片目录" else "保存到下载目录")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                if (link.isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(context, if (link.isImage) "已开始保存图片到相册目录" else "已开始保存文件到下载目录", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "保存失败：" + (it.message ?: "无法下载该文件"), Toast.LENGTH_LONG).show()
    }
}

private fun appendAgentReply(context: Context, messages: MutableList<ChatLine>, text: String, reasoning: String = "") {
    messages.add(ChatLine(text, false, reasoning = reasoning))
    AgentReplyNotifications.showIfBackground(context, text)
}
private data class ModelProfile(val name: String, val provider: String, val endpoint: String, val model: String, val apiKey: String)
private data class TtsProfile(val name: String, val endpoint: String, val apiKey: String)
private data class SkillProfile(val name: String, val description: String, val instructions: String)
private data class ThirdPartyPlugin(val name: String, val description: String, val endpoint: String, val method: String, val apiKey: String, val enabled: Boolean = false)
private data class ReleaseInfo(val version: String, val notes: String, val url: String)
private fun versionIsNewer(latest: String, current: String): Boolean {
    fun parts(value: String) = value.trim().removePrefix("v").split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
    val a = parts(latest); val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val av = a.getOrElse(i) { 0 }; val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av > bv
    }
    return false
}
private fun fetchLatestRelease(): ReleaseInfo {
    val apiConnection = (URL("https://api.github.com/repos/yanglin950915-sketch/HyperCatAI-next/releases/latest").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "HyperCatAI-Android")
    }
    try {
        val code = apiConnection.responseCode
        if (code in 200..299) {
            val json = JSONObject(apiConnection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name", "").ifBlank { json.optString("name", "") }
            if (tag.isBlank()) throw IllegalStateException("GitHub Release 未设置版本标签")
            return ReleaseInfo(tag, json.optString("body", "").take(1200), json.optString("html_url", "https://github.com/yanglin950915-sketch/HyperCatAI-next/releases"))
        }
        if (code != 403 && code != 429) {
            val detail = apiConnection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(240)
            throw IllegalStateException("GitHub Releases 请求失败（HTTP $code）${detail.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}")
        }
    } finally { apiConnection.disconnect() }

    // GitHub API may return 403 when its unauthenticated rate limit is exhausted.
    // The public latest-release page redirects to the current tag and does not use the API quota.
    val webConnection = (URL("https://github.com/yanglin950915-sketch/HyperCatAI-next/releases/latest").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "HyperCatAI-Android")
    }
    try {
        val code = webConnection.responseCode
        if (code !in 200..299) throw IllegalStateException("GitHub 更新服务暂不可用（API 限流，网页回退 HTTP $code）")
        val tag = webConnection.url.path.substringAfterLast('/').takeIf { it.matches(Regex("v?\\d+\\.\\d+\\.\\d+.*")) }
            ?: throw IllegalStateException("无法从 GitHub 最新 Release 页面识别版本号")
        return ReleaseInfo(tag, "", "https://github.com/yanglin950915-sketch/HyperCatAI-next/releases/tag/$tag")
    } finally { webConnection.disconnect() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HyperCatApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> HyperCatAppVisibility.isForeground = true
                Lifecycle.Event.ON_STOP -> HyperCatAppVisibility.isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !context.getSharedPreferences("hypercat_update_state", Context.MODE_PRIVATE).getBoolean("agent_notification_permission_asked_v099", false)
        ) {
            context.getSharedPreferences("hypercat_update_state", Context.MODE_PRIVATE).edit().putBoolean("agent_notification_permission_asked_v099", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val initialRearState = remember { PetStateStore.read(context) }
    val rearPetState by PetStateStore.state.collectAsState()
    var page by remember { mutableStateOf(Page.CHAT) }
    var openSettingsDrawer by remember { mutableStateOf(false) }
    var chatDrawerExpanded by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf("OpenAI 兼容接口") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var ttsLanguage by remember { mutableStateOf("zh-CN") }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }
    var ttsPitch by remember { mutableFloatStateOf(1.0f) }
    var ttsMode by remember { mutableStateOf("system") }
    var ttsProfileName by remember { mutableStateOf("") }
    var ttsEndpoint by remember { mutableStateOf("") }
    var ttsApiKey by remember { mutableStateOf("") }
    var ttsModel by remember { mutableStateOf("tts-1") }
    var ttsVoice by remember { mutableStateOf("alloy") }
    var saved by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var petName by remember { mutableStateOf(initialRearState.petName) }
    var petMood by remember { mutableStateOf(initialRearState.mood) }
    var petReply by remember { mutableStateOf(initialRearState.reply) }
    var rearPetEnabled by remember { mutableStateOf(initialRearState.enabled) }
    var rearProjectionStatus by remember { mutableStateOf("尚未连接背屏") }
    var themeMode by remember { mutableStateOf("system") }
    var timeRefresh by remember { mutableIntStateOf(0) }
    var updateStatus by remember { mutableStateOf("尚未检查") }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showUpdatePrompt by remember { mutableStateOf(false) }
    val currentVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0" }.getOrDefault("0.0.0") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(themeMode) {
        if (themeMode == "time") {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                timeRefresh++
            }
        }
    }
    val systemDark = isSystemInDarkTheme()
    val darkNow = when (themeMode) {
        "light" -> false
        "dark" -> true
        "time" -> {
            val hour = timeRefresh.let { java.time.LocalTime.now().hour }
            hour < 7 || hour >= 19
        }
        else -> systemDark
    }
    SideEffect { appDarkState = darkNow }
    fun setRearProjectionEnabled(enabled: Boolean) {
        if (!enabled) {
            rearPetEnabled = false
            PetStateStore.setEnabled(context, false)
            rearProjectionStatus = "正在关闭背屏萌宠…"
            scope.launch {
                rearProjectionStatus = withContext(Dispatchers.IO) {
                    RearProjectionController.hide(context)
                }
            }
            return
        }
        val host = context as? ComponentActivity
        if (host == null) {
            rearProjectionStatus = "无法获取应用界面上下文，请返回设置页重试"
            return
        }
        RearProjectionController.requestPermission(host) { granted, message ->
            if (!granted) {
                rearProjectionStatus = message
                return@requestPermission
            }
            scope.launch {
                rearProjectionStatus = "正在检测背屏并启动萌宠…"
                val result = withContext(Dispatchers.IO) {
                    RearProjectionController.connect(context)
                }
                rearProjectionStatus = result
                val connected = result.startsWith("已部署背屏卡片")
                rearPetEnabled = connected
                PetStateStore.setEnabled(context, connected)
            }
        }
    }
    val messages = remember { mutableStateListOf<ChatLine>() }
    var isThinking by remember { mutableStateOf(false) }
    val weatherPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        scope.launch {
            isThinking = true
            try {
                val hasLocation = AgentDeviceTools.hasLocationPermission(context)
                val precise = AgentDeviceTools.hasPreciseLocationPermission(context)
                val result = if (hasLocation) withContext(Dispatchers.IO) { AgentDeviceTools.currentWeather(context) }
                    else "未授予位置权限，无法获取当地天气。请在系统权限弹窗中允许位置访问，或直接告诉我城市名称。"
                val display = if (hasLocation && !precise) "提示：目前只获得大致位置权限，天气区域可能不够准确。请在应用权限中开启“精确位置”。\\n$result" else result
                appendAgentReply(context, messages, display)
                petReply = display
                petMood = "happy"
            } catch (error: Exception) {
                val failure = "天气查询失败：${error.message ?: "请稍后重试"}"
                appendAgentReply(context, messages, failure)
                petReply = failure
                petMood = "sad"
            } finally { isThinking = false }
        }
    }
    val modelProfiles = remember { mutableStateListOf<ModelProfile>() }
    val ttsProfiles = remember { mutableStateListOf<TtsProfile>() }
    var savedNotes by remember { mutableStateOf(emptyList<String>()) }
    var autoReadReplies by remember { mutableStateOf(false) }
    val chatPrefs = remember { context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE) }
    var recentPrompts by remember {
        mutableStateOf(chatPrefs.getString("recent_chat_titles", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList())
    }
    var currentSessionTitle by remember { mutableStateOf("") }
    LaunchedEffect(messages.size, currentSessionTitle) {
        if (currentSessionTitle.isNotBlank() && messages.isNotEmpty()) {
            chatPrefs.edit().putString("chat_session_$currentSessionTitle", serializeChatHistory(messages)).apply()
        }
    }
    LaunchedEffect(recentPrompts) {
        if (messages.isEmpty() && currentSessionTitle.isBlank()) {
            val latest = recentPrompts.firstOrNull().orEmpty()
            if (latest.isNotBlank()) {
                val restored = loadChatHistory(context, latest)
                if (restored.isNotEmpty()) {
                    messages.addAll(restored)
                    currentSessionTitle = latest
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        provider = prefs.getString("provider", "OpenAI 兼容接口") ?: "OpenAI 兼容接口"
        endpoint = prefs.getString("endpoint", "") ?: ""
        model = prefs.getString("model", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""
        ttsLanguage = prefs.getString("tts_language", "zh-CN") ?: "zh-CN"
        ttsRate = prefs.getFloat("tts_rate", 1.0f).coerceIn(0.6f, 1.6f)
        ttsPitch = prefs.getFloat("tts_pitch", 1.0f).coerceIn(0.7f, 1.4f)
        ttsMode = prefs.getString("tts_mode", "system") ?: "system"
        ttsProfileName = prefs.getString("tts_profile_name", "") ?: ""
        ttsEndpoint = prefs.getString("tts_endpoint", "") ?: ""
        ttsApiKey = prefs.getString("tts_api_key", "") ?: ""
        ttsModel = prefs.getString("tts_model", "tts-1") ?: "tts-1"
        ttsVoice = prefs.getString("tts_voice", "alloy") ?: "alloy"
        saved = endpoint.isNotBlank() && model.isNotBlank()
        runCatching { val array = org.json.JSONArray(prefs.getString("model_profiles", "[]") ?: "[]"); for (i in 0 until array.length()) { val item = array.getJSONObject(i); modelProfiles.add(ModelProfile(item.optString("name"), item.optString("provider"), item.optString("endpoint"), item.optString("model"), item.optString("apiKey"))) } }
        runCatching { val array = org.json.JSONArray(prefs.getString("tts_profiles", "[]") ?: "[]"); for (i in 0 until array.length()) { val item = array.getJSONObject(i); ttsProfiles.add(TtsProfile(item.optString("name"), item.optString("endpoint"), item.optString("apiKey"))) } }
        if (ttsProfiles.isEmpty()) modelProfiles.firstOrNull { it.name == ttsProfileName }?.let { old -> val p=TtsProfile(old.name,old.endpoint,old.apiKey); ttsProfiles.add(p); ttsEndpoint=p.endpoint; ttsApiKey=p.apiKey; prefs.edit().putString("tts_profiles",org.json.JSONArray().put(JSONObject().put("name",p.name).put("endpoint",p.endpoint).put("apiKey",p.apiKey)).toString()).putString("tts_endpoint",p.endpoint).putString("tts_api_key",p.apiKey).apply() }
        savedNotes = prefs.getString("saved_notes", "")?.split("\n--NOTE--\n")?.filter { it.isNotBlank() } ?: emptyList()
        autoReadReplies = prefs.getBoolean("auto_read_replies", false)
        petName = prefs.getString("pet_name", "小橘")?.takeIf { it.isNotBlank() } ?: "小橘"
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
    }

    LaunchedEffect(currentVersion) {
        try {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            latestRelease = release
            if (versionIsNewer(release.version, currentVersion)) {
                updateStatus = "发现新版本：${release.version}"
                showUpdatePrompt = true
            } else {
                updateStatus = "当前已是最新版本（${currentVersion}）"
            }
        } catch (_: Exception) {
            updateStatus = "自动检查暂不可用，可稍后手动检查"
        }
    }

    LaunchedEffect(rearPetState.updatedAt) {
        if (rearPetState.updatedAt > 0L) {
            petName = rearPetState.petName
            petMood = rearPetState.mood
            val transientRearFeedback = setOf("让我想一想……", "我准备休息啦。", "我在听，你想和我聊什么？", "嘿嘿，你摸到我啦！")
            if (rearPetState.reply.isNotBlank() &&
                rearPetState.reply != petReply &&
                rearPetState.reply !in transientRearFeedback
            ) {
                messages.add(ChatLine("（背屏互动）用户通过背屏和萌宠进行了互动。", true))
                messages.add(ChatLine(rearPetState.reply, false))
            }
            petReply = rearPetState.reply
        }
    }

    LaunchedEffect(isThinking) {
        if (isThinking) petMood = "thinking"
    }

    LaunchedEffect(petName, petMood, petReply) {
        PetStateStore.update(context, petName, "🤖", petMood, petReply)
    }

    MaterialTheme(colorScheme = if (darkNow) darkColorScheme(
        primary = Cyan, secondary = Color(0xFFB9A4FF), background = Bg, surface = Panel,
        onPrimary = Color(0xFF06131B), onSurface = Color(0xFFEAF4FF)
    ) else lightColorScheme(
        primary = Color(0xFF168DA6), secondary = Color(0xFF7461C8), background = Bg, surface = Panel,
        onPrimary = Color.White, onSurface = Color(0xFF142235)
    )) {
        Scaffold(containerColor = Bg) { padding ->
            when (page) {
                Page.CHAT -> ChatPage(
                    modifier = Modifier,
                    contentPadding = padding,
                    onDrawerVisibility = { chatDrawerExpanded = it },
                    messages = messages,
                    isThinking = isThinking,
                    recentPrompts = recentPrompts,
                    onDeleteRecentPrompt = { prompt -> recentPrompts = recentPrompts.filterNot { it == prompt }; chatPrefs.edit().putString("recent_chat_titles", recentPrompts.joinToString("||")).remove("chat_session_$prompt").apply() },
                    onOpenRecentSession = { prompt -> messages.clear(); messages.addAll(loadChatHistory(context, prompt)); currentSessionTitle = prompt },
                    onShareRecentSession = { prompt -> shareChatText(context, formatChatForSharing(prompt, loadChatHistory(context, prompt))) },
                    onShareCurrentSession = { shareChatText(context, formatChatForSharing(currentSessionTitle, messages)) },
                    onClearCurrentSession = { currentSessionTitle = "" },
                    currentSessionTitle = currentSessionTitle,
                    onPetInteraction = { action -> RearAgentInteractionDispatcher.enqueue(context, action) },
                    onPetNameChange = { value -> val clean = value.trim().take(16).ifBlank { "小橘" }; petName = clean; chatPrefs.edit().putString("pet_name", clean).apply() },
                    modelProfiles = modelProfiles.toList(), ttsProfiles = ttsProfiles.toList(), activeModel = model.ifBlank { "未选择模型" },
                    onSelectModel = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                    savedNotes = savedNotes, onSaveNote = { note -> savedNotes=(listOf(note)+savedNotes).distinct().take(100); chatPrefs.edit().putString("saved_notes",savedNotes.joinToString("\n--NOTE--\n")).apply() },
                    onDeleteNote = { note -> savedNotes = savedNotes.filterNot { it == note }; chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply() },
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = { value, attachments, photos ->
                        if (value.isNotBlank() || attachments.isNotEmpty() || photos.isNotEmpty()) {
                            if (currentSessionTitle.isBlank() && value.isNotBlank()) currentSessionTitle = value.trim().take(42)
                            val userLine = ChatLine(value, true, attachments.map { it.lastPathSegment ?: "附件" }, photos, attachments.filter { (context.contentResolver.getType(it) ?: "").startsWith("image/") })
                            messages.add(userLine)
                            if (value.isNotBlank()) {
                                if (currentSessionTitle == value.trim().take(42)) recentPrompts = (listOf(currentSessionTitle) + recentPrompts).distinct().take(100)
                                chatPrefs.edit().putString("recent_chat_titles", recentPrompts.joinToString("||")).apply()
                                val explicitMemory = Regex("^(?:请)?记住[：:，, ]*(.+)$", RegexOption.DOT_MATCHES_ALL).find(value.trim())?.groupValues?.getOrNull(1)?.trim()
                                if (!explicitMemory.isNullOrBlank()) {
                                    savedNotes = (listOf(explicitMemory.take(500)) + savedNotes).distinct().take(100)
                                    chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply()
                                }
                            }
                            draft = ""
                            if (value.contains("天气") || value.contains("气温") || value.contains("温度")) {
                                val requestedPlace = AgentDeviceTools.extractWeatherPlace(value)
                                if (requestedPlace.isNotBlank() || AgentDeviceTools.hasPreciseLocationPermission(context)) {
                                    scope.launch {
                                        isThinking = true
                                        try {
                                            val weatherReply = withContext(Dispatchers.IO) { AgentDeviceTools.currentWeather(context, requestedPlace.ifBlank { null }) }
                                            appendAgentReply(context, messages, weatherReply)
                                            petReply = weatherReply
                                            petMood = "happy"
                                        } catch (error: Exception) {
                                            val failure = "天气查询失败：${error.message ?: "请稍后重试"}"
                                            appendAgentReply(context, messages, failure)
                                            petReply = failure
                                            petMood = "sad"
                                        }
                                        finally { isThinking = false }
                                    }
                                } else weatherPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                val localAction = AgentDeviceTools.handleSystemAction(context, value)
                                if (localAction != null) {
                                    appendAgentReply(context, messages, localAction)
                                    petReply = localAction
                                    petMood = "happy"
                                } else if (!saved || endpoint.isBlank() || model.isBlank()) {
                                    val setupHint = "请先在设置中完成模型配置，再发送消息。"
                                    messages.add(ChatLine(setupHint, false))
                                    petReply = setupHint
                                    petMood = "sad"
                                }
                                else {
                                    val requestHistory = messages.toList()
                                    scope.launch {
                                        isThinking = true
                                        try {
                                            val reply = withContext(Dispatchers.IO) { requestModelReply(context, endpoint, model, apiKey, requestHistory, savedNotes) }
                                            appendAgentReply(context, messages, reply.text, reply.reasoning)
                                            petReply = reply.text
                                            petMood = inferMood(reply.text)
                                        } catch (error: Exception) {
                                            val failure = "模型请求未成功：${error.message ?: "网络或服务异常"}"
                                            appendAgentReply(context, messages, failure)
                                            petReply = failure
                                            petMood = "sad"
                                        } finally { isThinking = false }
                                    }
                                }
                            }
                        }
                    },
                    onCallInitiative = { prompt ->
                        if (saved && endpoint.isNotBlank() && model.isNotBlank() && !isThinking) {
                            val requestHistory = messages.toList() + ChatLine(prompt, true)
                            scope.launch {
                                isThinking = true
                                try {
                                    val reply = withContext(Dispatchers.IO) { requestModelReply(context, endpoint, model, apiKey, requestHistory, savedNotes) }
                                    appendAgentReply(context, messages, reply.text, reply.reasoning)
                                    petReply = reply.text
                                    petMood = inferMood(reply.text)
                                } catch (error: Exception) {
                                    val failure = "通话主动对话暂时不可用：${error.message ?: "网络或模型服务异常"}"
                                    appendAgentReply(context, messages, failure)
                                    petReply = failure
                                    petMood = "sad"
                                } finally { isThinking = false }
                            }
                        }
                    },
                    openSettings = { page = Page.CHAT; openSettingsDrawer = true },
                    configured = saved,
                    petName = petName,
                    petMood = petMood,
                    ttsLanguage = ttsLanguage, ttsRate = ttsRate, ttsPitch = ttsPitch,
                    ttsMode = ttsMode, ttsProfileName = ttsProfileName, ttsModel = ttsModel, ttsVoice = ttsVoice,
                    autoReadReplies = autoReadReplies, onAutoReadReplies = { enabled -> autoReadReplies = enabled; chatPrefs.edit().putBoolean("auto_read_replies", enabled).apply() },
                    onMood = { mood -> petMood = mood },
                    showSettingsOnEnter = openSettingsDrawer,
                    onSettingsDrawerShown = { openSettingsDrawer = false },
                    petSettingsContent = { drawerModifier ->
                        PetSettingsPage(
                            modifier = drawerModifier,
                            petName = petName,
                            petMood = petMood,
                            rearPetEnabled = rearPetEnabled,
                            rearProjectionStatus = rearProjectionStatus,
                            onRearPetEnabledChange = { enabled -> setRearProjectionEnabled(enabled) },
                            onRearPetRestore = {
                                rearPetEnabled = false
                                scope.launch {
                                    rearProjectionStatus = withContext(Dispatchers.IO) { RearProjectionController.restore(context) }
                                }
                            },
                            onPetInteraction = { action -> RearAgentInteractionDispatcher.enqueue(context, action) },
                            onPetNameChange = { value ->
                                val clean = value.trim().take(16).ifBlank { "小橘" }
                                petName = clean
                                chatPrefs.edit().putString("pet_name", clean).apply()
                            }
                        )
                    },
                    settingsContent = { drawerModifier ->
                        SettingsPage(
                            modifier = drawerModifier,
                            savedNotes = savedNotes,
                            onSaveNote = { note -> savedNotes = (listOf(note) + savedNotes).distinct().take(100); chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply() },
                            onDeleteNote = { note -> savedNotes = savedNotes.filterNot { it == note }; chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply() },
                            autoReadReplies = autoReadReplies,
                            onAutoReadReplies = { enabled -> autoReadReplies = enabled; chatPrefs.edit().putBoolean("auto_read_replies", enabled).apply() },
                             petName = petName,
                             petMood = petMood,
                             rearPetEnabled = rearPetEnabled,
                             onRearPetEnabledChange = { enabled -> setRearProjectionEnabled(enabled) },
                             onRearPetRestore = {
                                 rearPetEnabled = false
                                 scope.launch {
                                     rearProjectionStatus = withContext(Dispatchers.IO) { RearProjectionController.restore(context) }
                                 }
                             },
                             onPetInteraction = { action -> RearAgentInteractionDispatcher.enqueue(context, action) },
                             rearProjectionStatus = rearProjectionStatus,
                             onPetNameChange = { value ->
                                 val clean = value.trim().take(16).ifBlank { "小橘" }
                                 petName = clean
                                 chatPrefs.edit().putString("pet_name", clean).apply()
                             },
                            provider = provider, onProvider = { provider = it },
                            modelProfiles = modelProfiles.toList(),
                            ttsProfiles = ttsProfiles.toList(),
                            onAddProfile = { p -> modelProfiles.removeAll { it.name == p.name }; modelProfiles.add(0,p); val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                            onSelectProfile = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                            onDeleteProfile = { p -> modelProfiles.removeAll { it.name == p.name }; val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                            onAddTtsProfile = { p -> ttsProfiles.removeAll { it.name == p.name }; ttsProfiles.add(0,p); val a=org.json.JSONArray(); ttsProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("endpoint",x.endpoint).put("apiKey",x.apiKey)) }; ttsProfileName=p.name; ttsEndpoint=p.endpoint; ttsApiKey=p.apiKey; chatPrefs.edit().putString("tts_profiles",a.toString()).putString("tts_profile_name",p.name).putString("tts_endpoint",p.endpoint).putString("tts_api_key",p.apiKey).apply() },
                            onSelectTtsProfile = { p -> ttsProfileName=p.name; ttsEndpoint=p.endpoint; ttsApiKey=p.apiKey; chatPrefs.edit().putString("tts_profile_name",p.name).putString("tts_endpoint",p.endpoint).putString("tts_api_key",p.apiKey).apply() },
                            onDeleteTtsProfile = { p -> ttsProfiles.removeAll { it.name == p.name }; val a=org.json.JSONArray(); ttsProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("endpoint",x.endpoint).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("tts_profiles",a.toString()).apply(); if (ttsProfileName==p.name) { ttsProfileName=""; ttsMode="system"; chatPrefs.edit().putString("tts_profile_name","").putString("tts_mode","system").apply() } },
                            endpoint = endpoint, onEndpoint = { endpoint = it; saved = false },
                            model = model, onModel = { model = it; saved = false },
                            apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
                            ttsLanguage = ttsLanguage, onTtsLanguage = { ttsLanguage = it; chatPrefs.edit().putString("tts_language", it).apply() },
                            ttsRate = ttsRate, onTtsRate = { ttsRate = it; chatPrefs.edit().putFloat("tts_rate", it).apply() },
                            ttsPitch = ttsPitch, onTtsPitch = { ttsPitch = it; chatPrefs.edit().putFloat("tts_pitch", it).apply() },
                    ttsMode = ttsMode, onTtsMode = { ttsMode = it; chatPrefs.edit().putString("tts_mode", it).apply() },
                    ttsProfileName = ttsProfileName, onTtsProfileName = { ttsProfileName = it; chatPrefs.edit().putString("tts_profile_name", it).apply() },
                    ttsEndpoint = ttsEndpoint, onTtsEndpoint = { ttsEndpoint=it; chatPrefs.edit().putString("tts_endpoint",it).apply() },
                    ttsApiKey = ttsApiKey, onTtsApiKey = { ttsApiKey=it; chatPrefs.edit().putString("tts_api_key",it).apply() },
                    ttsModel = ttsModel, onTtsModel = { ttsModel = it; chatPrefs.edit().putString("tts_model", it).apply() },
                    ttsVoice = ttsVoice, onTtsVoice = { ttsVoice = it; chatPrefs.edit().putString("tts_voice", it).apply() },
                            onSave = {
                                chatPrefs.edit().putString("provider", provider).putString("endpoint", endpoint.trim())
                                    .putString("model", model.trim()).putString("api_key", apiKey).apply()
                                saved = endpoint.isNotBlank() && model.isNotBlank()
                            },
                            saved = saved, themeMode = themeMode,
                            onThemeMode = { mode -> themeMode = mode; chatPrefs.edit().putString("theme_mode", mode).apply() },
                            currentVersion = currentVersion, updateStatus = updateStatus, latestRelease = latestRelease,
                            onCheckUpdates = {
                                scope.launch {
                                    updateStatus = "正在检查 GitHub Releases…"
                                    try {
                                        val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                                        latestRelease = release
                                        updateStatus = if (versionIsNewer(release.version, currentVersion)) "发现新版本：${release.version}" else "当前已是最新版本（${currentVersion}）"
                                    } catch (error: Exception) { updateStatus = error.message ?: "检查失败，请稍后重试" }
                                }
                            },
                            openUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                        )
                    }
                )
                Page.SETTINGS -> SettingsPage(
                    modifier = Modifier.padding(padding),
                            savedNotes = savedNotes,
                            onSaveNote = { note -> savedNotes = (listOf(note) + savedNotes).distinct().take(100); chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply() },
                            onDeleteNote = { note -> savedNotes = savedNotes.filterNot { it == note }; chatPrefs.edit().putString("saved_notes", savedNotes.joinToString("\n--NOTE--\n")).apply() },
                            autoReadReplies = autoReadReplies,
                            onAutoReadReplies = { enabled -> autoReadReplies = enabled; chatPrefs.edit().putBoolean("auto_read_replies", enabled).apply() },
                    petName = petName,
                    petMood = petMood,
                    rearPetEnabled = rearPetEnabled,
                    onRearPetEnabledChange = { enabled -> setRearProjectionEnabled(enabled) },
                    onRearPetRestore = {
                        rearPetEnabled = false
                        scope.launch {
                            rearProjectionStatus = withContext(Dispatchers.IO) { RearProjectionController.restore(context) }
                        }
                    },
                    onPetInteraction = { action -> RearAgentInteractionDispatcher.enqueue(context, action) },
                    rearProjectionStatus = rearProjectionStatus,
                    onPetNameChange = { value ->
                        val clean = value.trim().take(16).ifBlank { "小橘" }
                        petName = clean
                        chatPrefs.edit().putString("pet_name", clean).apply()
                    },
                    provider = provider, onProvider = { provider = it },
                    modelProfiles = modelProfiles.toList(),
                    ttsProfiles = ttsProfiles.toList(),
                    onAddProfile = { p -> modelProfiles.removeAll { it.name == p.name }; modelProfiles.add(0,p); val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                    onSelectProfile = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                    onDeleteProfile = { p -> modelProfiles.removeAll { it.name == p.name }; val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                    onAddTtsProfile = { p -> ttsProfiles.removeAll { it.name == p.name }; ttsProfiles.add(0,p); val a=org.json.JSONArray(); ttsProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("endpoint",x.endpoint).put("apiKey",x.apiKey)) }; ttsProfileName=p.name; ttsEndpoint=p.endpoint; ttsApiKey=p.apiKey; chatPrefs.edit().putString("tts_profiles",a.toString()).putString("tts_profile_name",p.name).putString("tts_endpoint",p.endpoint).putString("tts_api_key",p.apiKey).apply() },
                    onSelectTtsProfile = { p -> ttsProfileName=p.name; ttsEndpoint=p.endpoint; ttsApiKey=p.apiKey; chatPrefs.edit().putString("tts_profile_name",p.name).putString("tts_endpoint",p.endpoint).putString("tts_api_key",p.apiKey).apply() },
                    onDeleteTtsProfile = { p -> ttsProfiles.removeAll { it.name == p.name }; val a=org.json.JSONArray(); ttsProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("endpoint",x.endpoint).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("tts_profiles",a.toString()).apply(); if (ttsProfileName==p.name) { ttsProfileName=""; ttsMode="system"; chatPrefs.edit().putString("tts_profile_name","").putString("tts_mode","system").apply() } },
                    endpoint = endpoint, onEndpoint = { endpoint = it; saved = false },
                    model = model, onModel = { model = it; saved = false },
                    apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
                    ttsLanguage = ttsLanguage, onTtsLanguage = { ttsLanguage = it; chatPrefs.edit().putString("tts_language", it).apply() },
                    ttsRate = ttsRate, onTtsRate = { ttsRate = it; chatPrefs.edit().putFloat("tts_rate", it).apply() },
                    ttsPitch = ttsPitch, onTtsPitch = { ttsPitch = it; chatPrefs.edit().putFloat("tts_pitch", it).apply() },
                    ttsMode = ttsMode, onTtsMode = { ttsMode = it; chatPrefs.edit().putString("tts_mode", it).apply() },
                    ttsProfileName = ttsProfileName, onTtsProfileName = { ttsProfileName = it; chatPrefs.edit().putString("tts_profile_name", it).apply() },
                    ttsEndpoint = ttsEndpoint, onTtsEndpoint = { ttsEndpoint=it; chatPrefs.edit().putString("tts_endpoint",it).apply() },
                    ttsApiKey = ttsApiKey, onTtsApiKey = { ttsApiKey=it; chatPrefs.edit().putString("tts_api_key",it).apply() },
                    ttsModel = ttsModel, onTtsModel = { ttsModel = it; chatPrefs.edit().putString("tts_model", it).apply() },
                    ttsVoice = ttsVoice, onTtsVoice = { ttsVoice = it; chatPrefs.edit().putString("tts_voice", it).apply() },
                    onSave = {
                        context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE).edit()
                            .putString("provider", provider).putString("endpoint", endpoint.trim())
                            .putString("model", model.trim()).putString("api_key", apiKey).apply()
                        saved = endpoint.isNotBlank() && model.isNotBlank()
                    },
                    saved = saved,
                    themeMode = themeMode,
                    onThemeMode = { mode ->
                        themeMode = mode
                        context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE).edit().putString("theme_mode", mode).apply()
                    },
                    currentVersion = currentVersion,
                    updateStatus = updateStatus,
                    latestRelease = latestRelease,
                    onCheckUpdates = {
                        scope.launch {
                            updateStatus = "正在检查 GitHub Releases…"
                            try {
                                val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                                latestRelease = release
                                updateStatus = if (versionIsNewer(release.version, currentVersion)) "发现新版本：${release.version}" else "当前已是最新版本（${currentVersion}）"
                            } catch (error: Exception) {
                                updateStatus = error.message ?: "检查失败，请稍后重试"
                            }
                        }
                    },
                    openUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                )
            }
        }
        if (showUpdatePrompt && latestRelease != null) {
            AlertDialog(
                onDismissRequest = { showUpdatePrompt = false },
                title = { Text("发现 HyperCat AI 新版本") },
                text = { Text("最新版本：${latestRelease!!.version}\n建议前往 GitHub 下载并安装更新。") },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdatePrompt = false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestRelease!!.url))) }
                    }) { Text("立即更新") }
                },
                dismissButton = { TextButton(onClick = { showUpdatePrompt = false }) { Text("稍后") } }
            )
        }
    }
}

@Composable
private fun CatFace(modifier: Modifier = Modifier, mood: String = "normal") {
    var gazeX by remember { mutableStateOf(0f) }
    var gazeY by remember { mutableStateOf(0f) }
    var blinking by remember { mutableStateOf(false) }
    var touchBlinkTrigger by remember { mutableIntStateOf(0) }
    var lastTouchAt by remember { mutableStateOf(0L) }
    val pupilX by animateFloatAsState(gazeX, animationSpec = tween(260), label = "pupilX")
    val pupilY by animateFloatAsState(gazeY, animationSpec = tween(260), label = "pupilY")
    val eyelid by animateFloatAsState(if (blinking) 0.06f else 1f, animationSpec = tween(70), label = "eyelid")
    val idle = rememberInfiniteTransition(label = "petIdle")
    val breathe by idle.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "breathe"
    )
    val floatY by idle.animateFloat(
        initialValue = -3.5f, targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "floatY"
    )
    val eyeSparkle by idle.animateFloat(
        initialValue = 0.72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "eyeSparkle"
    )

    LaunchedEffect(touchBlinkTrigger) {
        if (touchBlinkTrigger > 0) {
            blinking = true
            delay(85L)
            blinking = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2300L, 4301L))
            if (System.currentTimeMillis() - lastTouchAt > 3200L) {
                gazeX = Random.nextFloat() * 0.7f - 0.35f
                gazeY = Random.nextFloat() * 0.36f - 0.18f
            }
            delay(100L)
            blinking = true
            delay(85L)
            blinking = false
            if (Random.nextInt(5) == 0) {
                delay(120L)
                blinking = true
                delay(75L)
                blinking = false
            }
        }
    }

    Canvas(modifier
        .graphicsLayer {
            translationY = floatY.dp.toPx()
            scaleX = 1f + breathe * 0.008f
            scaleY = 1f - breathe * 0.006f
        }
        .pointerInput(Unit) {
            detectTapGestures { point ->
                lastTouchAt = System.currentTimeMillis()
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                gazeX = (((point.x / w) - 0.5f) * 2.4f).coerceIn(-1f, 1f)
                gazeY = (((point.y / h) - 0.47f) * 2.8f).coerceIn(-1f, 1f)
                touchBlinkTrigger++
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { point ->
                    lastTouchAt = System.currentTimeMillis()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    gazeX = (((point.x / w) - 0.5f) * 2.4f).coerceIn(-1f, 1f)
                    gazeY = (((point.y / h) - 0.47f) * 2.8f).coerceIn(-1f, 1f)
                },
                onDrag = { change, _ ->
                    lastTouchAt = System.currentTimeMillis()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    gazeX = (((change.position.x / w) - 0.5f) * 2.4f).coerceIn(-1f, 1f)
                    gazeY = (((change.position.y / h) - 0.47f) * 2.8f).coerceIn(-1f, 1f)
                    change.consume()
                }
            )
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height * .47f
        val eyeW = size.width * .205f
        val eyeH = size.height * .31f
        val gap = size.width * .17f
        val eyeWhite = Color(0xFFF4F1EA)
        listOf(cx - gap, cx + gap).forEachIndexed { index, x ->
            val wink = if (mood == "happy") 0.82f else 1f
            val currentEyeH = eyeH * eyelid * wink * (0.985f + breathe * 0.015f)
            val eyeTop = cy - currentEyeH / 2
            drawOval(brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), eyeWhite, Color(0xFFE5E1DA)), startY = eyeTop, endY = eyeTop + currentEyeH), topLeft = Offset(x - eyeW / 2, eyeTop), size = Size(eyeW, currentEyeH))
            if (eyelid > .22f) {
                val px = (pupilX * eyeW * .30f).coerceIn(-eyeW * .30f, eyeW * .30f)
                val py = (pupilY * eyeH * .22f).coerceIn(-eyeH * .22f, eyeH * .22f)
                val irisCenter = Offset(x + px, cy + py)
                drawCircle(Color(0xFF9B7960), radius = eyeW * .305f, center = irisCenter)
                drawCircle(Color(0xFF382B25), radius = eyeW * .255f, center = irisCenter)
                drawCircle(Color(0xFF101010), radius = eyeW * .19f, center = irisCenter)
                drawCircle(Color.White.copy(alpha = eyeSparkle), radius = eyeW * .085f, center = Offset(x + px - eyeW * .095f, cy - eyeH * .105f + py))
                drawCircle(Color.White.copy(alpha = .55f), radius = eyeW * .038f, center = Offset(x + px + eyeW * .105f, cy + eyeH * .075f + py))
            }
            val lash = Path().apply {
                moveTo(x - eyeW * .49f, cy - currentEyeH * .12f)
                quadraticTo(x, eyeTop - 2.dp.toPx(), x + eyeW * .49f, cy - currentEyeH * .12f)
            }
            drawPath(lash, Color(0xFF403631).copy(alpha = .72f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        val noseY = cy + eyeH * .72f + breathe * 1.2f.dp.toPx()
        val nose = Path().apply {
            moveTo(cx - 7.dp.toPx(), noseY)
            quadraticTo(cx, noseY - 8.dp.toPx(), cx + 7.dp.toPx(), noseY)
            quadraticTo(cx, noseY + 9.dp.toPx(), cx - 7.dp.toPx(), noseY)
            close()
        }
        drawPath(nose, Color(0xFFB9A5A0))
        val mouthY = noseY + 12.dp.toPx()
        val mouth = Path().apply {
            when (mood) {
                "happy" -> { moveTo(cx - 17.dp.toPx(), mouthY); quadraticTo(cx, mouthY + 24.dp.toPx(), cx + 17.dp.toPx(), mouthY) }
                "sad" -> { moveTo(cx - 15.dp.toPx(), mouthY + 9.dp.toPx()); quadraticTo(cx, mouthY - 7.dp.toPx(), cx + 15.dp.toPx(), mouthY + 9.dp.toPx()) }
                "surprised" -> addOval(androidx.compose.ui.geometry.Rect(cx - 6.dp.toPx(), mouthY, cx + 6.dp.toPx(), mouthY + 15.dp.toPx()))
                "thinking" -> { moveTo(cx - 10.dp.toPx(), mouthY + 4.dp.toPx()); lineTo(cx + 10.dp.toPx(), mouthY + 4.dp.toPx()) }
                else -> {
                    moveTo(cx, mouthY)
                    cubicTo(cx - 2.dp.toPx(), mouthY + 10.dp.toPx(), cx - 13.dp.toPx(), mouthY + 11.dp.toPx(), cx - 16.dp.toPx(), mouthY + 5.dp.toPx())
                    moveTo(cx, mouthY)
                    cubicTo(cx + 2.dp.toPx(), mouthY + 10.dp.toPx(), cx + 13.dp.toPx(), mouthY + 11.dp.toPx(), cx + 16.dp.toPx(), mouthY + 5.dp.toPx())
                }
            }
        }
        drawPath(mouth, Color(0xFFB9A5A0), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}


@Composable
private fun MarkdownMessage(text: String, textColor: Color, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    val fence = "```"
    val codePattern = Regex("(?s)" + fence + "([^\\n]*)\\n(.*?)" + fence)
    val matches = codePattern.findAll(text).toList()
    if (matches.isEmpty()) {
        Text(text, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var cursor = 0
        matches.forEach { match ->
            val prose = text.substring(cursor, match.range.first).trim()
            if (prose.isNotBlank()) Text(prose, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
            val language = match.groupValues[1].trim().ifBlank { "CODE" }
            val code = match.groupValues[2].trimEnd()
            Surface(color = if (appDarkState) Color(0xFF090D12) else Color(0xFFF0F3F7), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(language.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(code)) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制代码", tint = Cyan, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制代码", color = Cyan, fontSize = 10.sp)
                        }
                    }
                    Text(code, color = if (appDarkState) Color(0xFFE2EAF2) else Color(0xFF1E293B), fontSize = 12.sp, lineHeight = 17.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            cursor = match.range.last + 1
        }
        val tail = text.substring(cursor).trim()
        if (tail.isNotBlank()) Text(tail, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatPage(
    modifier: Modifier, contentPadding: PaddingValues, onDrawerVisibility: (Boolean) -> Unit, messages: List<ChatLine>, isThinking: Boolean, recentPrompts: List<String>, onDeleteRecentPrompt: (String) -> Unit, onOpenRecentSession: (String) -> Unit, onShareRecentSession: (String) -> Unit, onShareCurrentSession: () -> Unit, onClearCurrentSession: () -> Unit, currentSessionTitle: String, modelProfiles: List<ModelProfile>, ttsProfiles: List<TtsProfile>, activeModel: String, onSelectModel: (ModelProfile) -> Unit, savedNotes: List<String>, onSaveNote: (String) -> Unit, onDeleteNote: (String) -> Unit, draft: String, onDraft: (String) -> Unit,
    onSend: (String, List<Uri>, List<Bitmap>) -> Unit, onCallInitiative: (String) -> Unit, openSettings: () -> Unit, configured: Boolean, petName: String,
    petMood: String, ttsLanguage: String, ttsRate: Float, ttsPitch: Float,
    ttsMode: String, ttsProfileName: String, ttsModel: String, ttsVoice: String, autoReadReplies: Boolean, onAutoReadReplies: (Boolean) -> Unit, onMood: (String) -> Unit, onPetInteraction: (String) -> Unit, onPetNameChange: (String) -> Unit,
    showSettingsOnEnter: Boolean,
    onSettingsDrawerShown: () -> Unit,
    settingsContent: @Composable (Modifier) -> Unit,
    petSettingsContent: @Composable (Modifier) -> Unit
) {
    val context: Context = LocalContext.current
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var userMovedChat by remember(currentSessionTitle) { mutableStateOf(false) }
    LaunchedEffect(chatListState) {
        chatListState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) userMovedChat = true
        }
    }
    LaunchedEffect(currentSessionTitle) {
        userMovedChat = false
        if (messages.isNotEmpty()) chatListState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(messages.size) {
        if (!userMovedChat && messages.isNotEmpty()) chatListState.animateScrollToItem(messages.lastIndex)
    }
    var drawerSection by remember { mutableStateOf("pet") }
    var showPetNameDialog by remember { mutableStateOf(false) }
    var petNameDraft by remember(petName) { mutableStateOf(petName) }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPhotos by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var previewPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> if (bitmap != null) selectedPhotos = selectedPhotos + bitmap }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var selectedMessageIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showModelInfo by remember { mutableStateOf(false) }
    var showCallInfo by remember { mutableStateOf(false) }
    var callActive by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(true) }
    var videoEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var lastCallSpokenText by remember { mutableStateOf("") }
    var agentPlaybackUntilMs by remember { mutableLongStateOf(0L) }
    var micRmsDb by remember { mutableFloatStateOf(-2f) }
    var previousAudioMode by remember { mutableIntStateOf(AudioManager.MODE_NORMAL) }
    var previousSpeakerphoneOn by remember { mutableStateOf(false) }
    var recognitionStartJob by remember { mutableStateOf<Job?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var cloudSpeechJob by remember { mutableStateOf<Job?>(null) }
    val speechScope = rememberCoroutineScope()
    val selectedTtsProfile = ttsProfiles.firstOrNull { it.name == ttsProfileName }
    val canSpeak = if (ttsMode == "cloud") selectedTtsProfile != null && ttsModel.isNotBlank() else ttsReady
    var speechStatus by remember { mutableStateOf("语音引擎初始化中") }
    var ttsDiagnostic by remember { mutableStateOf<String?>(null) }
    val speechEngine = remember(context) {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) speechStatus = "语音引擎初始化失败，请检查系统文字转语音服务"
        }
    }
    LaunchedEffect(ttsMode, ttsProfileName, ttsModel) {
        if (ttsMode == "cloud") speechStatus = if (selectedTtsProfile != null && ttsModel.isNotBlank()) "云端 TTS 已配置，可测试朗读" else "请在设置中选择 TTS 模型档案并填写模型名称"
    }
    LaunchedEffect(ttsReady, ttsLanguage, ttsRate, ttsPitch) {
        if (ttsReady) {
            val requestedLocale = Locale.forLanguageTag(ttsLanguage)
            val selected = when {
                speechEngine.isLanguageAvailable(requestedLocale) >= TextToSpeech.LANG_AVAILABLE -> requestedLocale
                speechEngine.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE -> Locale.SIMPLIFIED_CHINESE
                speechEngine.isLanguageAvailable(Locale.CHINESE) >= TextToSpeech.LANG_AVAILABLE -> Locale.CHINESE
                else -> null
            }
            if (selected == null || speechEngine.setLanguage(selected) < TextToSpeech.LANG_AVAILABLE) {
                speechStatus = "系统未安装中文语音包，请在系统文字转语音设置中下载中文语音"
                ttsReady = false
            } else {
                speechEngine.setSpeechRate(ttsRate.coerceIn(0.6f, 1.6f))
                speechEngine.setPitch(ttsPitch.coerceIn(0.7f, 1.4f))
                speechEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true; agentPlaybackUntilMs = Long.MAX_VALUE }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("-last") == true) {
                            isSpeaking = false
                            agentPlaybackUntilMs = SystemClock.elapsedRealtime() + 2200L
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        agentPlaybackUntilMs = SystemClock.elapsedRealtime() + 1200L
                        speechStatus = "朗读失败，请检查系统语音引擎或语音数据"
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                        agentPlaybackUntilMs = SystemClock.elapsedRealtime() + 1200L
                        speechStatus = "朗读失败（错误码 $errorCode），请检查系统语音引擎"
                    }
                })
                speechStatus = if (selected.language == "zh") "中文朗读已就绪" else "语音朗读已就绪"
            }
        }
    }
    DisposableEffect(speechEngine) { onDispose { speechEngine.stop(); speechEngine.shutdown() } }
    var showRemember by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var recentQuery by remember { mutableStateOf("") }
    var recentSort by remember { mutableStateOf("最新") }
    var showModelPicker by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> selectedFiles = (selectedFiles + uris).distinct() }
    val latestOnSend by rememberUpdatedState(onSend)
    val latestOnDraft by rememberUpdatedState(onDraft)
    val latestDraft by rememberUpdatedState(draft)
    val latestCallActive by rememberUpdatedState(callActive)
    val latestMicEnabled by rememberUpdatedState(micEnabled)
    val latestOnCallInitiative by rememberUpdatedState(onCallInitiative)
    var pendingRecognitionStart by remember { mutableStateOf(false) }
    var pendingMicPermissionRequest by remember { mutableStateOf(false) }
    var speechRecognizer by remember(context) { mutableStateOf(createCallSpeechRecognizer(context)) }
    DisposableEffect(speechRecognizer) {
        val activeRecognizer = speechRecognizer
        activeRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; speechStatus = "麦克风已启动，正在聆听…" }
            override fun onBeginningOfSpeech() { isListening = true; speechStatus = "已检测到你的声音，正在识别…" }
            override fun onRmsChanged(rmsdB: Float) { micRmsDb = rmsdB.coerceIn(-2f, 10f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false; if (latestCallActive) speechStatus = "检测到语音结束，正在识别…" }
            override fun onError(error: Int) {
                isListening = false
                val reason = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "麦克风音频采集失败，请检查系统麦克风隐私开关或其他应用占用"
                    SpeechRecognizer.ERROR_CLIENT -> "语音识别服务被中断"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络不可用"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请靠近麦克风再说一次"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务正忙，请稍后重试"
                    SpeechRecognizer.ERROR_SERVER -> "语音识别服务器暂时不可用"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音，请再次说话"
                    else -> "语音识别异常（$error）"
                }
                if (latestCallActive) {
                    speechStatus = reason
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        speechStatus = "系统未授予录音权限，正在请求麦克风权限…"
                        pendingMicPermissionRequest = true
                    }
                    if (latestMicEnabled && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        speechScope.launch {
                            delay(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1400L else if (error == SpeechRecognizer.ERROR_CLIENT) 900L else 700L)
                            if (latestCallActive && latestMicEnabled && !isListening && speechRecognizer === activeRecognizer) {
                                runCatching {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        speechStatus = "录音权限已失效，请重新授权麦克风"
                                        pendingMicPermissionRequest = true
                                        return@launch
                                    }
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    if (audioManager.isMicrophoneMute) {
                                        speechStatus = "系统麦克风隐私开关已关闭，请开启麦克风访问"
                                        return@launch
                                    }
                                    val target = if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER) {
                                        speechStatus = "语音识别服务已断开，正在重建识别会话…"
                                        val replacement = createCallSpeechRecognizer(context)
                                        speechRecognizer = replacement
                                        delay(500L)
                                        replacement
                                    } else {
                                        // onError already terminates the current recognition session.
                                        // Calling cancel() here can itself trigger ERROR_CLIENT on some OEM services.
                                        delay(350L)
                                        activeRecognizer
                                    }
                                    if (!latestCallActive || !latestMicEnabled) return@launch
                                    speechStatus = "正在重新连接语音识别服务…"
                                    target.startListening(createCallRecognitionIntent(context))
                                }.onFailure { speechStatus = "语音识别重试失败：${it.message ?: "请检查系统语音服务"}" }
                            }
                        }
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotBlank()) {
                    if (latestCallActive && latestMicEnabled) {
                        val playbackActive = isSpeaking || SystemClock.elapsedRealtime() <= agentPlaybackUntilMs
                        if (playbackActive && looksLikeAgentEcho(spoken, lastCallSpokenText)) {
                            speechStatus = "已识别并过滤 Agent 自己的播报回声，继续聆听…"
                        } else latestOnSend(spoken, emptyList(), emptyList())
                    } else if (!latestCallActive) {
                        latestOnDraft(if (latestDraft.isBlank()) spoken else "${latestDraft} ${spoken}")
                    }
                } else if (latestCallActive) speechStatus = "没有识别到清晰语音，请再说一次"
                if (latestCallActive && latestMicEnabled) pendingRecognitionStart = true
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank() && latestCallActive) {
                    val playbackActive = isSpeaking || SystemClock.elapsedRealtime() <= agentPlaybackUntilMs
                    speechStatus = if (playbackActive && looksLikeAgentEcho(partial, lastCallSpokenText)) "正在过滤 Agent 播报回声…" else "正在识别你的声音：$partial"
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { runCatching { activeRecognizer.cancel() }; runCatching { activeRecognizer.destroy() } }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Toast.makeText(context, "麦克风录音权限已开启", Toast.LENGTH_SHORT).show()
            if (callActive) pendingRecognitionStart = true
        } else {
            speechStatus = "麦克风权限被拒绝，请到系统设置中允许 HyperCat AI 使用麦克风"
            Toast.makeText(context, speechStatus, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(pendingMicPermissionRequest) {
        if (pendingMicPermissionRequest) {
            pendingMicPermissionRequest = false
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun startInAppRecognition() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speechStatus = "尚未获得麦克风录音权限，正在请求授权…"
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMicrophoneMute) {
            speechStatus = "系统麦克风隐私开关当前为关闭状态，请在快捷设置中开启麦克风访问"
            isListening = false
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context) &&
            !(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))) {
            speechStatus = "设备未安装可用的语音识别服务，请安装/启用系统语音识别服务后重试"
            Toast.makeText(context, speechStatus, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
        isListening = false
        if (callActive) {
            recognitionStartJob?.cancel()
            // The previous session has already delivered onResults/onError before this restart.
            // Avoid cancel() on an idle SpeechRecognizer: some OEM services report ERROR_CLIENT.
            speechStatus = "正在启动语音识别，请开始说话…"
            recognitionStartJob = speechScope.launch {
                delay(300L)
                if (!callActive || !micEnabled) return@launch
                runCatching { speechRecognizer.startListening(intent) }
                    .onFailure { speechStatus = "无法启动语音识别：${it.message ?: "系统语音服务异常"}" }
            }
        } else {
            runCatching { speechRecognizer.startListening(intent) }
                .onFailure { speechStatus = "无法启动语音识别：${it.message ?: "系统语音服务异常"}" }
        }
    }
    val callMicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            previousAudioMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            callActive = true; micEnabled = true; videoEnabled = false
            showCallInfo = true; lastCallSpokenText = ""; startInAppRecognition()
            latestOnCallInitiative("你现在正在与用户进行语音通话。请自然、简短地主动打个招呼，像真实伙伴一样开启交流，并邀请用户告诉你想聊什么。不要提及你收到了指令。")
        } else Toast.makeText(context, "AI 通话需要麦克风录音权限，请在系统设置中允许", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(pendingRecognitionStart) {
        if (pendingRecognitionStart) {
            pendingRecognitionStart = false
            delay(350L)
            if (callActive && micEnabled) startInAppRecognition()
        }
    }
    fun stopInAppRecognition() { if (isListening) runCatching { speechRecognizer.stopListening() } }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { cameraError = null; videoEnabled = true; speechStatus = "摄像头预览已启动" }
        else { videoEnabled = false; Toast.makeText(context, "未获得摄像头权限，语音通话仍可继续", Toast.LENGTH_SHORT).show() }
    }
    fun startUnifiedCall() {
        if (!configured) { Toast.makeText(context, "请先配置大模型", Toast.LENGTH_SHORT).show(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            previousAudioMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            callActive = true; micEnabled = true; videoEnabled = false
            showCallInfo = true; lastCallSpokenText = ""; startInAppRecognition()
            latestOnCallInitiative("你现在正在与用户进行语音通话。请自然、简短地主动打个招呼，像真实伙伴一样开启交流，并邀请用户告诉你想聊什么。不要提及你收到了指令。")
        }
    }
    fun toggleVideo() {
        if (videoEnabled) { videoEnabled = false; cameraError = null }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        else { cameraError = null; videoEnabled = true }
    }
    LaunchedEffect(callActive, messages.size, isThinking, ttsMode, ttsProfileName, ttsModel, ttsVoice, ttsRate, canSpeak) {
        if (callActive && !isThinking) {
            val reply = messages.lastOrNull { !it.fromUser }?.text.orEmpty()
            if (reply.isNotBlank() && reply != lastCallSpokenText) {
                lastCallSpokenText = reply
                val spokenText = speechText(reply)
                if (spokenText.isNotBlank() && canSpeak) {
                    // Do not pause recognition during Agent playback. Keep the microphone session
                    // alive and filter likely playback echo against the Agent's spoken text.
                    agentPlaybackUntilMs = Long.MAX_VALUE
                    if (ttsMode == "cloud") {
                        val profile = selectedTtsProfile
                        if (profile != null) {
                            isSpeaking = true; speechStatus = "正在请求云端语音…"
                            try { speakWithCloudTts(context, profile, ttsModel, ttsVoice, spokenText, ttsRate, AudioAttributes.USAGE_VOICE_COMMUNICATION); speechStatus = "云端语音播报完成" }
                            catch (cancelled: CancellationException) {
                                speechStatus = "已停止朗读"
                                throw cancelled
                            } catch (error: Exception) {
                                speechStatus = "云端 TTS 失败，点击查看完整诊断"
                                ttsDiagnostic = safeTtsDiagnostic(error, profile, ttsModel)
                            }
                            finally { isSpeaking = false; agentPlaybackUntilMs = SystemClock.elapsedRealtime() + 2200L }
                        }
                    } else {
                        isSpeaking = true
                        agentPlaybackUntilMs = Long.MAX_VALUE
                        val result = speechEngine.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "call-${messages.size}-last")
                        if (result == TextToSpeech.ERROR) { isSpeaking = false; agentPlaybackUntilMs = SystemClock.elapsedRealtime() + 1200L; speechStatus = "通话语音播报失败，请检查系统 TTS 语音数据" }
                    }
                }
            }
        }
    }
    LaunchedEffect(callActive, messages.size, isThinking, isSpeaking, isListening) {
        if (callActive && !isThinking && !isSpeaking && !isListening) {
            delay(Random.nextLong(22000L, 42000L))
            if (callActive && !isThinking && !isSpeaking && !isListening) {
                val followUps = listOf(
                    "通话中用户暂时没有说话。请结合刚才的聊天内容，自然地问一个轻松、相关的问题，避免催促。",
                    "用户可能正在思考。请用一句简短、友好的话自然延续刚才的话题，不要重复之前的开场白。",
                    "请像真实语音伙伴一样主动补充一个与当前话题相关的小建议或有趣信息，控制在两句话以内。"
                )
                latestOnCallInitiative(followUps.random())
            }
        }
    }
    var lastAutoReadText by remember { mutableStateOf("") }
    var autoReadPendingReply by remember { mutableStateOf(false) }
    LaunchedEffect(autoReadReplies, callActive, messages.size, isThinking, ttsMode, ttsProfileName, ttsModel, ttsVoice, ttsRate) {
        // Only auto-read a reply produced by a request in the active session.
        // Opening/restoring a conversation must never replay its historical last message.
        if (isThinking) {
            if (autoReadReplies && !callActive) autoReadPendingReply = true
            return@LaunchedEffect
        }
        if (!autoReadReplies || callActive) {
            autoReadPendingReply = false
            return@LaunchedEffect
        }
        if (!autoReadPendingReply) return@LaunchedEffect
        autoReadPendingReply = false
        val reply = messages.lastOrNull { !it.fromUser }?.text.orEmpty()
        if (reply.isNotBlank() && reply != lastAutoReadText) {
            lastAutoReadText = reply
            val spokenText = speechText(reply)
            if (spokenText.isNotBlank()) {
                if (ttsMode == "cloud") {
                    val profile = selectedTtsProfile
                    if (profile != null) {
                        cloudSpeechJob?.cancel()
                        cloudSpeechJob = speechScope.launch {
                            isSpeaking = true
                            try { speakWithCloudTts(context, profile, ttsModel, ttsVoice, spokenText, ttsRate); speechStatus = "自动朗读完成" }
                            catch (cancelled: CancellationException) { speechStatus = "已停止朗读"; throw cancelled }
                            catch (error: Exception) { speechStatus = "自动朗读失败：${error.message ?: "TTS 服务异常"}"; ttsDiagnostic = safeTtsDiagnostic(error, profile, ttsModel) }
                            finally { isSpeaking = false }
                        }
                    } else speechStatus = "自动朗读未启动：请先选择 TTS 模型"
                } else if (ttsReady) {
                    val chunks = splitSpeechText(spokenText)
                    val prefix = "hypercat-auto-${System.nanoTime()}"
                    isSpeaking = true
                    chunks.forEachIndexed { index, chunk ->
                        val id = prefix + if (index == chunks.lastIndex) "-last" else "-${index}"
                        speechEngine.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
                    }
                }
            }
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        if (drawerSection == "settings" || drawerSection == "pet_settings") {
            drawerSection = "pet"
        } else {
            drawerScope.launch { drawerState.close() }
        }
    }
    LaunchedEffect(drawerState) { androidx.compose.runtime.snapshotFlow { drawerState.isOpen }.collect { onDrawerVisibility(it) } }
    LaunchedEffect(showSettingsOnEnter) {
        if (showSettingsOnEnter) {
            drawerSection = "settings"
            drawerState.open()
            onSettingsDrawerShown()
        }
    }
    val filteredRecent = recentPrompts.filter { it.contains(recentQuery, true) }
    val orderedRecent = when (recentSort) { "最早" -> filteredRecent.reversed(); "A-Z" -> filteredRecent.sortedBy { it.lowercase() }; else -> filteredRecent }
    Box(
        Modifier.fillMaxSize().pointerInput(drawerState.isOpen) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onHorizontalDrag = { _, amount -> dragDistance += amount },
                onDragEnd = {
                    if (!drawerState.isOpen && dragDistance > 72f) drawerScope.launch { drawerState.open() }
                    else if (drawerState.isOpen && dragDistance < -72f) drawerScope.launch { drawerState.close() }
                }
            )
        }
    ) {
    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), bottom = if (WindowInsets.isImeVisible) 0.dp else contentPadding.calculateBottomPadding()).imePadding().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(40.dp).clickable { drawerScope.launch { drawerState.open() } }) {
                Box(contentAlignment = Alignment.Center) { Text("☰", color = MainText, fontSize = 18.sp) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Agent", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(if (configured) activeModel else "尚未连接模型", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Surface(shape = RoundedCornerShape(15.dp), color = Panel2, modifier = Modifier.clickable { showModelPicker = true }) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("模型", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(5.dp))
                    Text("⌄", color = Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(7.dp))
            Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(40.dp).clickable { onShareCurrentSession() }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Share, contentDescription = "分享当前对话", tint = Cyan, modifier = Modifier.size(19.dp)) }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(40.dp).clickable { showClear = true }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Delete, contentDescription = "清理当前对话", tint = MainText, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("长期记忆 ${savedNotes.size} 条", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { noteDraft = ""; showRemember = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("管理记忆", fontSize = 10.sp) }
            Spacer(Modifier.width(6.dp))
            Text("自动朗读", color = Muted, fontSize = 11.sp)
            Switch(checked = autoReadReplies, onCheckedChange = onAutoReadReplies)
        }
        if (!configured) {
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.fillMaxWidth().clickable(onClick = openSettings), color = Color(0xFF2B2A22), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("尚未配置模型 · 点击前往设置", color = Color(0xFFFFD58B), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("设置  ›", color = Cyan, fontSize = 12.sp)
                }
            }
        }
        if (messages.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
            Text("有什么可以帮你？", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("选择常用任务，或直接输入想法。", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 1.dp, bottom = 4.dp))
            val suggestions = listOf(
                "总结内容" to "请帮我总结以下内容，提炼重点：",
                "翻译文本" to "请将下面内容准确翻译成中文：",
                "润色写作" to "请帮我润色下面这段文字，保持原意：",
                "代码助手" to "请分析下面的代码并给出修复建议："
            )
            suggestions.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { (label, prompt) ->
                        Surface(
                            modifier = Modifier.weight(1f).padding(bottom = 4.dp).clickable { onDraft(if (draft.isBlank()) prompt else "$prompt\n$draft") },
                            color = Panel, shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("✦", color = Cyan, fontSize = 9.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(label, color = MainText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (selectedMessageIndices.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${selectedMessageIndices.size} 条", color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedMessageIndices = messages.indices.toSet() }) { Text("全选") }
                TextButton(onClick = { val chosen = selectedMessageIndices.sorted().mapNotNull { messages.getOrNull(it) }; shareChatText(context, formatChatForSharing(currentSessionTitle, chosen), "分享所选消息"); selectedMessageIndices = emptySet() }) { Text("分享") }
                TextButton(onClick = { selectedMessageIndices = emptySet() }) { Text("取消") }
            }
        }
        LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("开始与 Agent 对话", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("输入消息，或使用语音与 Agent 交流。", color = Muted, fontSize = 13.sp)
                }
            }
            items(messages.mapIndexed { index, message -> index to message }.filter { searchQuery.isBlank() || it.second.text.contains(searchQuery, ignoreCase = true) }) { (messageIndex, line) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                    if (!line.fromUser) {
                        Box(Modifier.padding(end = 8.dp).size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Cyan, Color(0xFF8C7BFF)))), contentAlignment = Alignment.Center) {
                            Text(petName.take(1), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (selectedMessageIndices.isNotEmpty()) Checkbox(checked = messageIndex in selectedMessageIndices, onCheckedChange = { checked -> selectedMessageIndices = if (checked) selectedMessageIndices + messageIndex else selectedMessageIndices - messageIndex }, modifier = Modifier.size(34.dp))
                    Surface(modifier = (if (line.fromUser) Modifier.widthIn(max = 276.dp) else Modifier.fillMaxWidth(0.96f)).combinedClickable(onClick = { if (selectedMessageIndices.isNotEmpty()) selectedMessageIndices = if (messageIndex in selectedMessageIndices) selectedMessageIndices - messageIndex else selectedMessageIndices + messageIndex }, onLongClick = { selectedMessageIndices = selectedMessageIndices + messageIndex }), color = if (line.fromUser) (if (appDarkState) Color(0xFF303030) else Color(0xFF1689C2)) else if (appDarkState) Color(0xFF1B1B1B) else Color.White, border = androidx.compose.foundation.BorderStroke(if (messageIndex in selectedMessageIndices) 2.dp else 1.dp, if (messageIndex in selectedMessageIndices) Cyan else GlassBorder), shape = RoundedCornerShape(topStart = if (line.fromUser) 18.dp else 5.dp, topEnd = if (line.fromUser) 5.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            if (line.reasoning.isNotBlank() && !line.fromUser) {
                                var reasoningExpanded by remember(line) { mutableStateOf(true) }
                                Surface(color = if (appDarkState) Color(0xFF24231F) else Color(0xFFFFF7E8), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(Modifier.fillMaxWidth().clickable { reasoningExpanded = !reasoningExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                            Text("✧ 思路说明", color = if (appDarkState) Color(0xFFE5C98D) else Color(0xFF8A6425), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Text(if (reasoningExpanded) "收起⌃" else "展开⌄", color = Muted, fontSize = 10.sp)
                                        }
                                        AnimatedVisibility(visible = reasoningExpanded) {
                                            Text(line.reasoning, color = if (appDarkState) Color(0xFFD8D1C3) else Color(0xFF5C503D), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(9.dp))
                            }
                            if (line.text.isNotBlank()) {
                                MarkdownMessage(line.text, if (line.fromUser || appDarkState) Color.White else Color(0xFF172638), clipboardManager)
                                if (!line.fromUser) {
                                    val downloadableLinks = remember(line.text) { extractGeneratedDownloadLinks(line.text) }
                                    if (downloadableLinks.isNotEmpty()) {
                                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            downloadableLinks.forEach { download ->
                                                TextButton(onClick = { saveGeneratedDownload(context, download) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                                    Icon(Icons.Outlined.Download, contentDescription = if (download.isImage) "保存图片" else "保存文件", tint = Cyan, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(3.dp))
                                                    Text(if (download.isImage) "保存图片" else "保存文件", color = Cyan, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    val actionTint = if (line.fromUser) Color.White.copy(alpha=.82f) else Cyan
                                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(line.text)) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制消息", modifier = Modifier.size(13.dp), tint = actionTint)
                                        Spacer(Modifier.width(3.dp)); Text("复制", color = actionTint, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, line.text)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(shareIntent, "分享消息")) }
                                    }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Icon(Icons.Outlined.Share, contentDescription = "分享消息", modifier = Modifier.size(13.dp), tint = actionTint)
                                        Spacer(Modifier.width(3.dp)); Text("分享", color = actionTint, fontSize = 10.sp)
                                    }
                                    if (!line.fromUser) {
                                        TextButton(enabled = true, onClick = {
                                            if (!canSpeak && !isSpeaking) {
                                                Toast.makeText(context, speechStatus, Toast.LENGTH_LONG).show()
                                            } else if (isSpeaking) {
                                                cloudSpeechJob?.cancel(); cloudSpeechJob = null
                                                speechEngine.stop(); isSpeaking = false; speechStatus = "已停止朗读"
                                            } else {
                                                val spokenText = speechText(line.text)
                                                if (spokenText.isBlank()) speechStatus = "这条回复没有可朗读的文字"
                                                else if (ttsMode == "cloud") {
                                                    val profile = selectedTtsProfile
                                                    if (profile == null) speechStatus = "请先在设置中选择 TTS 模型档案"
                                                    else {
                                                        cloudSpeechJob?.cancel()
                                                        cloudSpeechJob = speechScope.launch {
                                                            isSpeaking = true; speechStatus = "正在请求云端语音…"
                                                            try { speakWithCloudTts(context, profile, ttsModel, ttsVoice, spokenText, ttsRate); speechStatus = "云端语音播报完成" }
                                                            catch (cancelled: CancellationException) {
                                                                speechStatus = "已停止朗读"
                                                                throw cancelled
                                                            } catch (error: Exception) {
                                                                speechStatus = "云端 TTS 失败，点击查看完整诊断"
                                ttsDiagnostic = safeTtsDiagnostic(error, profile, ttsModel)
                            }
                                                            finally { isSpeaking = false }
                                                        }
                                                    }
                                                } else {
                                                    val chunks = splitSpeechText(spokenText)
                                                    val requestPrefix = "hypercat-${System.nanoTime()}"
                                                    speechEngine.stop(); isSpeaking = true; speechStatus = "正在朗读"
                                                    chunks.forEachIndexed { index, chunk ->
                                                        val utteranceId = requestPrefix + if (index == chunks.lastIndex) "-last" else "-${index}"
                                                        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                                                        val result = speechEngine.speak(chunk, queueMode, null, utteranceId)
                                                        if (result == TextToSpeech.ERROR) { isSpeaking = false; speechStatus = "朗读请求失败，请检查系统文字转语音引擎" }
                                                    }
                                                }
                                            }
                                        }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                            Icon(if (isSpeaking) Icons.Outlined.Stop else Icons.Outlined.VolumeUp, contentDescription = if (isSpeaking) "停止朗读" else "语音播放", modifier = Modifier.size(13.dp), tint = actionTint)
                                            Spacer(Modifier.width(3.dp)); Text(if (isSpeaking) "停止" else "朗读", color = actionTint, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            line.photos.forEach { photo -> Image(bitmap = photo.asImageBitmap(), contentDescription = "照片", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp))) }
                            line.attachments.forEach { name -> Text("📎 $name", color = if (line.fromUser) Color.White.copy(alpha=.8f) else Cyan, fontSize = 11.sp) }
                        }
                    }
                    if (line.fromUser) {
                        Box(Modifier.padding(start = 8.dp).size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF397D9D)), contentAlignment = Alignment.Center) {
                            Text("我", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (isThinking && searchQuery.isBlank()) item {
                Row(Modifier.fillMaxWidth().padding(start = 42.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Cyan)
                    Spacer(Modifier.width(9.dp))
                    Text("正在思考…", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (selectedPhotos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                selectedPhotos.forEach { photo -> Image(bitmap = photo.asImageBitmap(), contentDescription = "待发送照片", modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).clickable { previewPhoto = photo }) }
                Text("${selectedPhotos.size} 张照片", color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("清除", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { selectedPhotos = emptyList() })
            }
        }
        if (selectedFiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${selectedFiles.size} 个附件已添加", color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("清除", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { selectedFiles = emptyList() })
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(modifier = Modifier.fillMaxWidth(), color = if (appDarkState) Color.White.copy(alpha = .08f) else Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 15.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = draft, onValueChange = onDraft,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = 132.dp).padding(vertical = 10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp, lineHeight = 20.sp),
                        decorationBox = { inner -> if (draft.isEmpty()) Text("发送消息给$petName…", color = Muted, fontSize = 14.sp); inner() })
                    VoiceInputButton(isListening = isListening, onPressStart = { startInAppRecognition() }, onPressEnd = { stopInAppRecognition() })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(Modifier.requiredSize(40.dp).clickable { cameraCapture.launch(null) }, color = Panel2, shape = CircleShape) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhotoCamera, contentDescription = "拍照", tint = Cyan, modifier = Modifier.size(20.dp)) } }
                Surface(Modifier.requiredHeight(40.dp).defaultMinSize(minWidth = 72.dp).clickable { startUnifiedCall() }, color = Panel2, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Call, contentDescription = "AI 通话", tint = Cyan, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp)); Text("通话", color = MainText, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(Modifier.requiredSize(40.dp).clickable { filePicker.launch(arrayOf("*/*")) }, color = Panel2, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AttachFile, contentDescription = "添加附件", tint = Muted, modifier = Modifier.size(20.dp)) }
                }
                Surface(Modifier.requiredSize(42.dp).clickable(enabled = draft.isNotBlank() || selectedFiles.isNotEmpty() || selectedPhotos.isNotEmpty()) {
                    onSend(draft, selectedFiles, selectedPhotos); selectedFiles = emptyList(); selectedPhotos = emptyList()
                }, color = if (draft.isNotBlank() || selectedFiles.isNotEmpty() || selectedPhotos.isNotEmpty()) Cyan else Panel2, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Send, contentDescription = "发送", tint = if (draft.isNotBlank() || selectedFiles.isNotEmpty()) Color(0xFF06202A) else Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (isListening) Text("通话/语音输入：麦克风正在采集声音", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
        AnimatedVisibility(visible = drawerState.isOpen, modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
            Surface(modifier = Modifier.fillMaxSize(), color = Panel) {
                if (drawerSection == "pet_settings") {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { drawerSection = "pet" }) { Text("‹ 返回个人页", color = MainText) }
                            Spacer(Modifier.weight(1f)); Text("萌宠设置", color = MainText, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { drawerScope.launch { drawerState.close() } }) { Icon(Icons.Outlined.Close, contentDescription = "关闭萌宠设置", tint = Muted) }
                        }
                        petSettingsContent(Modifier.weight(1f).fillMaxWidth())
                    }
                } else if (drawerSection == "settings") {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { drawerSection = "pet" }) { Text("‹ 返回个人页", color = MainText) }
                            Spacer(Modifier.weight(1f)); Text("设置", color = MainText, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { drawerScope.launch { drawerState.close() } }) { Icon(Icons.Outlined.Close, contentDescription = "关闭设置", tint = Muted) }
                        }
                        settingsContent(Modifier.weight(1f).fillMaxWidth())
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("我的萌宠", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("你的 AI 伙伴与聊天空间", color = Muted, fontSize = 11.sp) }
                            IconButton(onClick = { drawerScope.launch { drawerState.close() } }) { Icon(Icons.Outlined.Close, contentDescription = "关闭侧栏", tint = Muted) }
                        }
                        Surface(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), color = Panel2, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                                    CatFace(Modifier.fillMaxSize().pointerInput(petMood) { detectTapGestures(onTap = { drawerSection = "pet_settings" }, onLongPress = { onPetInteraction("pet_long_press") }) }, petMood)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(petName, color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(when (petMood) { "happy" -> "心情愉快 · 陪伴中"; "thinking" -> "正在思考"; "sad" -> "需要一点安慰"; else -> "在线 · 随时可以聊天" }, color = Muted, fontSize = 11.sp)
                                    Text("点击头像管理萌宠与背屏", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("最近和历史对话", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("选择记录即可继续上次聊天", color = Muted, fontSize = 10.sp) }
                            Text(orderedRecent.size.toString() + " 条", color = Muted, fontSize = 10.sp)
                        }
                        OutlinedTextField(value = recentQuery, onValueChange = { recentQuery = it }, placeholder = { Text("搜索聊天记录") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 7.dp)) { listOf("最新", "最早", "A-Z").forEach { mode -> FilterChip(selected = recentSort == mode, onClick = { recentSort = mode }, label = { Text(mode, fontSize = 10.sp) }) } }
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                            if (orderedRecent.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("暂无历史对话", color = MainText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("开始聊天后，记录会显示在这里", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) } }
                            items(orderedRecent) { prompt ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable { onOpenRecentSession(prompt); drawerScope.launch { drawerState.close() } }.padding(vertical = 5.dp)) { Text(prompt, color = MainText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2); Text("与 " + petName + " 的对话", color = Muted, fontSize = 10.sp) }
                                    IconButton(onClick = { onShareRecentSession(prompt) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Share, contentDescription = "分享完整对话", tint = Cyan, modifier = Modifier.size(17.dp)) }
                                    IconButton(onClick = { onDeleteRecentPrompt(prompt) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, contentDescription = "删除对话", tint = Color(0xFFE07878), modifier = Modifier.size(17.dp)) }
                                }
                                Divider(color = GlassBorder.copy(alpha = .4f))
                            }
                        }
                        Surface(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), color = Panel2, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf("⌕" to "找一下", "✎" to "记一下", "⚙" to "设置").forEachIndexed { index, item ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                                        when (index) { 0 -> { showSearch = true; drawerScope.launch { drawerState.close() } }; 1 -> { noteDraft = draft; showRemember = true; drawerScope.launch { drawerState.close() } }; else -> drawerSection = "settings" }
                                    }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(item.first, color = Cyan, fontSize = 20.sp); Text(item.second, color = MainText, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (ttsDiagnostic != null) AlertDialog(
        onDismissRequest = { ttsDiagnostic = null },
        title = { Text("云端 TTS 完整诊断") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("以下信息已自动隐藏 API Key，可复制后发送用于排查。", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text(ttsDiagnostic.orEmpty(), color = MainText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(ttsDiagnostic.orEmpty()))
                ttsDiagnostic = null
            }) { Text("复制诊断信息") }
        },
        dismissButton = { TextButton(onClick = { ttsDiagnostic = null }) { Text("关闭") } }
    )
    if (previewPhoto != null) AlertDialog(onDismissRequest = { previewPhoto = null }, title = { Text("照片预览") }, text = { Image(bitmap = previewPhoto!!.asImageBitmap(), contentDescription = "照片预览", modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) }, confirmButton = { TextButton(onClick = { previewPhoto = null }) { Text("完成") } })
    if (showPetNameDialog) AlertDialog(
        onDismissRequest = { showPetNameDialog = false },
        title = { Text("编辑萌宠资料") },
        text = { OutlinedTextField(value = petNameDraft, onValueChange = { petNameDraft = it.take(16) }, singleLine = true, label = { Text("萌宠名称") }) },
        confirmButton = { TextButton(onClick = { onPetNameChange(petNameDraft); showPetNameDialog = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showPetNameDialog = false }) { Text("取消") } }
    )
    if (showSearch) AlertDialog(onDismissRequest = { showSearch = false; searchQuery = "" }, title = { Text("查找对话内容") },
        text = { Column { OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("输入关键词，聊天列表即时筛选") }, singleLine = true); Text("${messages.count { it.text.contains(searchQuery, true) }} 条匹配消息", color = Muted, fontSize = 12.sp) } },
        confirmButton = { TextButton(onClick = { showSearch = false; searchQuery = "" }) { Text("完成") } })
    if (showRemember) AlertDialog(
        onDismissRequest = { showRemember = false },
        title = { Text("Agent 长期记忆") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("保存的记忆会在后续对话中作为背景信息提供给模型。也可以在聊天中直接发送“记住：……”快速保存。", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it.take(500) }, label = { Text("添加一条长期记忆") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("已保存 ${savedNotes.size} 条", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                savedNotes.forEach { memory ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(memory, color = MainText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteNote(memory) }) { Text("删除", color = Color(0xFFE07878)) }
                    }
                    Divider(color = GlassBorder.copy(alpha = .4f))
                }
                if (savedNotes.isNotEmpty()) TextButton(onClick = { savedNotes.toList().forEach(onDeleteNote) }) { Text("清空全部记忆", color = Color(0xFFE07878)) }
            }
        },
        confirmButton = { TextButton(onClick = { if (noteDraft.isNotBlank()) onSaveNote(noteDraft.trim()); noteDraft = ""; showRemember = false }) { Text("保存记忆") } },
        dismissButton = { TextButton(onClick = { showRemember = false }) { Text("完成") } }
    )
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("清理当前对话") },
        text = { Text("确定清空当前页面的全部消息吗？此操作不会删除最近对话记录。", color = Muted) },
        confirmButton = { TextButton(onClick = { (messages as? androidx.compose.runtime.snapshots.SnapshotStateList<ChatLine>)?.clear(); onClearCurrentSession(); showClear = false }) { Text("清空消息", color = Color(0xFFE57373)) } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } })
    if (showModelPicker) AlertDialog(onDismissRequest={showModelPicker=false},title={Text("切换模型")},text={if(modelProfiles.isEmpty()) Text("请先在设置中添加模型档案",color=Muted) else Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())) { modelProfiles.forEach { p -> Text("${p.name} · ${p.model}",color=MainText,modifier=Modifier.fillMaxWidth().clickable { onSelectModel(p);showModelPicker=false }.padding(vertical=10.dp)) } }},confirmButton={TextButton(onClick={showModelPicker=false;openSettings()}) { Text("管理模型") }})
    if (showCallInfo) Dialog(
        onDismissRequest = {
            callActive = false; micEnabled = false; videoEnabled = false; isListening = false
            runCatching { speechRecognizer.cancel() }; cloudSpeechJob?.cancel(); speechEngine.stop(); showCallInfo = false
            agentPlaybackUntilMs = 0L
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
            audioManager.mode = previousAudioMode
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable {
                        callActive = false; micEnabled = false; videoEnabled = false; isListening = false
                        runCatching { speechRecognizer.cancel() }; cloudSpeechJob?.cancel(); speechEngine.stop(); showCallInfo = false
                        agentPlaybackUntilMs = 0L
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
                        audioManager.mode = previousAudioMode
                    })
                    Spacer(Modifier.weight(1f))
                    Text("HyperCat AI 通话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(if (callActive) "● 通话中" else "● 已结束", color = if (callActive) Color(0xFF55D69A) else Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF151C29))) {
                    if (videoEnabled) {
                        LiveCameraPreview(useFrontCamera = useFrontCamera, onError = { cameraError = it })
                        if (cameraError != null) Text(cameraError.orEmpty(), color = Color(0xFFFF9A9A), fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(8.dp))
                        Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(20.dp)) {
                            Text(if (useFrontCamera) "前置摄像头" else "后置摄像头", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("HyperCat AI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(if (isSpeaking) "AI 正在回答…" else if (isListening) "正在聆听…" else if (isThinking) "正在思考…" else "语音通话已连接", color = Color(0xFFADB8C8), fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val latestCallReply = messages.lastOrNull { !it.fromUser }?.text.orEmpty()
                val latestCallInput = messages.lastOrNull { it.fromUser }?.text.orEmpty()
                Surface(Modifier.fillMaxWidth().heightIn(max = 128.dp), color = Color(0xFF151C29), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (latestCallInput.isNotBlank()) Text("你：$latestCallInput", color = Color(0xFFADB8C8), fontSize = 11.sp)
                        if (latestCallReply.isNotBlank()) Text("AI：$latestCallReply", color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
                        if (latestCallReply.isBlank() && latestCallInput.isBlank()) Text("说话后，识别内容和 AI 回复会显示在这里", color = Color(0xFFADB8C8), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CallControl(if (micEnabled) "麦克风开启" else "已静音", if (micEnabled) "🎙" else "🔇") {
                        micEnabled = !micEnabled
                        if (!micEnabled) { isListening = false; runCatching { speechRecognizer.cancel() }; speechStatus = "麦克风已关闭" }
                        else if (callActive) { speechStatus = "正在重新启动语音识别…"; startInAppRecognition() }
                    }
                    CallControl(if (videoEnabled) "关闭视频" else "开启视频", if (videoEnabled) "📹" else "📷") { toggleVideo() }
                    if (videoEnabled) CallControl("翻转摄像头", "🔄") { useFrontCamera = !useFrontCamera }
                    CallControl("挂断", "☎", danger = true) {
                        callActive = false; micEnabled = false; videoEnabled = false; isListening = false
                        runCatching { speechRecognizer.cancel() }; cloudSpeechJob?.cancel(); speechEngine.stop(); showCallInfo = false
                        agentPlaybackUntilMs = 0L
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
                        audioManager.mode = previousAudioMode
                    }
                }
                Text(if (videoEnabled) "摄像头画面为设备本地实时预览；AI 语音通话持续工作。连续视频流理解需接入支持实时音视频的模型服务。" else "语音通话已启动。点击摄像头按钮可开启视频，点击麦克风按钮可静音。",
                    color = Color(0xFF8E9BAC), fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
                if (speechStatus.isNotBlank()) Text(speechStatus, color = Color(0xFF8E9BAC), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                if (callActive && micEnabled) Text("麦克风输入电平：${micRmsDb.toInt()} dB · 识别持续运行", color = Color(0xFF8E9BAC), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun LiveCameraPreview(useFrontCamera: Boolean, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnError by rememberUpdatedState(onError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val previewView = remember(context) { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    DisposableEffect(previewView, lifecycleOwner, useFrontCamera) {
        var boundProvider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                boundPreview = preview
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(selector)) error(if (useFrontCamera) "设备没有可用的前置摄像头" else "设备没有可用的后置摄像头")
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (error: Exception) {
                mainHandler.post { latestOnError("摄像头启动失败：${error.message ?: error.javaClass.simpleName}。请检查权限或关闭占用摄像头的应用。") }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching {
                val provider = boundProvider
                val preview = boundPreview
                if (provider != null && preview != null) provider.unbind(preview)
            }
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun CallControl(label: String, icon: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Surface(Modifier.size(62.dp).clickable(enabled = enabled, onClick = onClick), color = if (danger) Color(0xFFB83D4A) else if (enabled) Color(0xFF202A3A) else Color(0xFF30343B), shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontSize = 24.sp) }
        }
        Text(label, color = if (danger) Color(0xFFFF8E98) else Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun VoiceInputButton(isListening: Boolean, onPressStart: () -> Unit, onPressEnd: () -> Unit) {
    val pulseTransition = rememberInfiniteTransition(label = "voice-input-pulse")
    val pulse by pulseTransition.animateFloat(initialValue = 0.78f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(animation = tween(720), repeatMode = RepeatMode.Reverse), label = "voice-input-scale")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        if (isListening) Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Cyan.copy(alpha = 0.42f), radius = size.minDimension * 0.42f * pulse,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Surface(Modifier.size(40.dp).pointerInput(isListening) {
            detectTapGestures(onPress = {
                onPressStart()
                tryAwaitRelease()
                onPressEnd()
            })
        }, color = if (isListening) Cyan else Panel2, shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = if (isListening) "正在聆听" else "语音输入",
                    tint = if (isListening) Color(0xFF06202A) else MainText)
            }
        }
    }
}

@Composable
private fun PetSettingsPage(
    modifier: Modifier,
    petName: String,
    petMood: String,
    rearPetEnabled: Boolean,
    rearProjectionStatus: String,
    onRearPetEnabledChange: (Boolean) -> Unit,
    onRearPetRestore: () -> Unit,
    onPetInteraction: (String) -> Unit,
    onPetNameChange: (String) -> Unit
) {
    var nameDraft by remember(petName) { mutableStateOf(petName) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("萌宠设置", color = MainText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("管理伙伴资料、互动与背屏投放", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) {
                        CatFace(Modifier.fillMaxSize(), petMood)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(petName, color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(when (petMood) { "happy" -> "心情愉快 · 陪伴中"; "thinking" -> "正在思考"; "sad" -> "需要一点安慰"; else -> "在线 · 随时可以聊天" }, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(16) }, label = { Text("萌宠名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onPetNameChange(nameDraft) }, modifier = Modifier.fillMaxWidth()) { Text("保存萌宠资料") }
                OutlinedButton(onClick = { onPetInteraction("pet_touch") }, modifier = Modifier.fillMaxWidth()) { Text("和萌宠互动") }
            }
        }
        Text("背屏投放", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("萌宠投放到背屏", color = MainText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (rearPetEnabled) "已开启" else "关闭状态 · 不影响主屏萌宠", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = rearPetEnabled, onCheckedChange = onRearPetEnabledChange)
                }
                Text("仅支持具备背屏的 Xiaomi Pro 系列设备。需要先启动 Shizuku 并授权。首次部署后，还需在系统背屏卡片管理中重新应用卡片。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                Text(rearProjectionStatus, color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                OutlinedButton(onClick = onRearPetRestore, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("恢复原始背屏卡片") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier, petName: String, petMood: String, rearPetEnabled: Boolean, rearProjectionStatus: String, onRearPetEnabledChange: (Boolean) -> Unit, onRearPetRestore: () -> Unit, onPetInteraction: (String) -> Unit, onPetNameChange: (String) -> Unit,
    provider: String, onProvider: (String) -> Unit, modelProfiles: List<ModelProfile>, onAddProfile: (ModelProfile) -> Unit, onSelectProfile: (ModelProfile) -> Unit, onDeleteProfile: (ModelProfile) -> Unit,
    ttsProfiles: List<TtsProfile>, onAddTtsProfile: (TtsProfile) -> Unit, onSelectTtsProfile: (TtsProfile) -> Unit, onDeleteTtsProfile: (TtsProfile) -> Unit,
    endpoint: String, onEndpoint: (String) -> Unit, model: String, onModel: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit,
    ttsLanguage: String, onTtsLanguage: (String) -> Unit,
    ttsRate: Float, onTtsRate: (Float) -> Unit, ttsPitch: Float, onTtsPitch: (Float) -> Unit,
    ttsMode: String, onTtsMode: (String) -> Unit, ttsProfileName: String, onTtsProfileName: (String) -> Unit, ttsEndpoint: String, onTtsEndpoint: (String) -> Unit, ttsApiKey: String, onTtsApiKey: (String) -> Unit,
    ttsModel: String, onTtsModel: (String) -> Unit, ttsVoice: String, onTtsVoice: (String) -> Unit,
    onSave: () -> Unit, saved: Boolean,
    themeMode: String, onThemeMode: (String) -> Unit,
    currentVersion: String, updateStatus: String, latestRelease: ReleaseInfo?,
    savedNotes: List<String>, onSaveNote: (String) -> Unit, onDeleteNote: (String) -> Unit,
    autoReadReplies: Boolean, onAutoReadReplies: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit, openUrl: (String) -> Unit
) {
    var showPetNameDialog by remember { mutableStateOf(false) }
    var petNameDraft by remember(petName) { mutableStateOf(petName) }
    var showPluginDialog by remember { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDocsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var memoryDraft by remember { mutableStateOf("") }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showSecurityDetail by remember { mutableStateOf(false) }
    var showSkillDialog by remember { mutableStateOf(false) }
    var skillName by remember { mutableStateOf("") }
    var skillDescription by remember { mutableStateOf("") }
    var skillInstructions by remember { mutableStateOf("") }
    var skillStatus by remember { mutableStateOf("支持导入 JSON、Markdown 和 TXT 技能文件") }
    val skills = remember { mutableStateListOf<SkillProfile>() }
    var securityDetailTitle by remember { mutableStateOf("") }
    var securityDetailText by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var taskDelayMinutes by remember { mutableStateOf("30") }
    var profileName by remember { mutableStateOf("") }
    var pluginEnabled by remember { mutableStateOf(false) }
    var pluginName by remember { mutableStateOf("") }
    var pluginDescription by remember { mutableStateOf("") }
    var pluginEndpoint by remember { mutableStateOf("") }
    var pluginMethod by remember { mutableStateOf("POST") }
    var pluginApiKey by remember { mutableStateOf("") }
    var pluginStatus by remember { mutableStateOf("支持导入 JSON 插件清单或手动添加 REST API 插件") }
    val plugins = remember { mutableStateListOf<ThirdPartyPlugin>() }
    var mcpName by remember { mutableStateOf("") }
    var mcpEndpoint by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE) }
    var weatherProvider by remember { mutableStateOf(prefs.getString("weather_provider", "openmeteo") ?: "openmeteo") }
    var amapWeatherKey by remember { mutableStateOf(prefs.getString("amap_weather_key", "") ?: "") }
    var caiyunWeatherToken by remember { mutableStateOf(prefs.getString("caiyun_weather_token", "") ?: "") }
    val pluginImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取插件清单")
            val json = JSONObject(raw)
            val name = json.optString("name").trim()
            val endpoint = json.optString("endpoint").trim()
            require(name.isNotBlank() && endpoint.startsWith("https://")) { "插件名称不能为空，且远程地址必须使用 HTTPS" }
            val method = json.optString("method", "POST").uppercase().let { if (it in listOf("GET", "POST")) it else "POST" }
            val item = ThirdPartyPlugin(name.take(60), json.optString("description").take(300), endpoint, method, json.optString("apiKey"), false)
            plugins.removeAll { it.name.equals(item.name, true) }
            plugins.add(0, item)
            val savedPlugins = org.json.JSONArray()
            plugins.forEach { plugin -> savedPlugins.put(JSONObject().put("name", plugin.name).put("description", plugin.description).put("endpoint", plugin.endpoint).put("method", plugin.method).put("apiKey", plugin.apiKey).put("enabled", plugin.enabled)) }
            prefs.edit().putString("third_party_plugins", savedPlugins.toString()).apply()
            pluginStatus = "已导入插件：${item.name}（默认未启用）"
        }.onFailure { pluginStatus = "导入失败：${it.message ?: "文件格式错误"}" }
    }
    val skillImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取所选文件")
            val json = runCatching { JSONObject(raw) }.getOrNull()
            val importedName = json?.optString("name")?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { "导入技能" } ?: "导入技能"
            val description = json?.optString("description").orEmpty()
            val instructions = json?.optString("instructions")?.takeIf { it.isNotBlank() }
                ?: json?.optString("content")?.takeIf { it.isNotBlank() } ?: raw
            val skill = SkillProfile(importedName.take(60), description.take(300), instructions.take(30000))
            skills.removeAll { it.name.equals(skill.name, true) }
            skills.add(0, skill)
            val array = org.json.JSONArray()
            skills.forEach { item -> array.put(JSONObject().put("name", item.name).put("description", item.description).put("instructions", item.instructions)) }
            prefs.edit().putString("agent_skills", array.toString()).apply()
            skillStatus = "已导入：${skill.name}"
        }.onFailure { skillStatus = "导入失败：${it.message ?: "文件格式无法识别"}" }
    }
    LaunchedEffect(Unit) {
        pluginEnabled = prefs.getBoolean("plugins_enabled", false)
        weatherProvider = prefs.getString("weather_provider", "openmeteo") ?: "openmeteo"
        amapWeatherKey = prefs.getString("amap_weather_key", "") ?: ""
        caiyunWeatherToken = prefs.getString("caiyun_weather_token", "") ?: ""
        runCatching {
            val array = org.json.JSONArray(prefs.getString("third_party_plugins", "[]") ?: "[]")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                plugins.add(ThirdPartyPlugin(item.optString("name"), item.optString("description"), item.optString("endpoint"), item.optString("method", "POST"), item.optString("apiKey"), item.optBoolean("enabled", false)))
            }
        }
        mcpName = prefs.getString("mcp_name", "") ?: ""
        mcpEndpoint = prefs.getString("mcp_endpoint", "") ?: ""
        runCatching {
            val array = org.json.JSONArray(prefs.getString("agent_skills", "[]") ?: "[]")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                skills.add(SkillProfile(item.optString("name"), item.optString("description"), item.optString("instructions")))
            }
        }
    }
    fun persistSkills() {
        val array = org.json.JSONArray()
        skills.forEach { item -> array.put(JSONObject().put("name", item.name).put("description", item.description).put("instructions", item.instructions)) }
        prefs.edit().putString("agent_skills", array.toString()).apply()
    }
    fun persistPlugins() {
        val array = org.json.JSONArray()
        plugins.forEach { item -> array.put(JSONObject().put("name", item.name).put("description", item.description).put("endpoint", item.endpoint).put("method", item.method).put("apiKey", item.apiKey).put("enabled", item.enabled)) }
        prefs.edit().putString("third_party_plugins", array.toString()).apply()
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("设置", color = MainText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("外观、扩展能力与应用信息", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("模型与服务", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("先选择服务类型，再配置连接信息与模型档案", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("服务类型", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("本地模型", "OpenAI 兼容接口").forEach { item ->
                        FilterChip(selected = provider == item, onClick = { onProvider(item) }, label = { Text(item, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                ConfigField("API / 服务地址", endpoint, onEndpoint, "例如 http://127.0.0.1:1234/v1")
                ConfigField("模型名称", model, onModel, "例如 qwen2.5 或 gpt-4o-mini")
                ConfigField("API Key（本地服务可留空）", apiKey, onApiKey, "输入密钥", secret = true)
                Spacer(Modifier.height(14.dp))
                Text("TTS 语音配置", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("选择系统语音，或使用已保存模型档案连接兼容 OpenAI 的云端语音合成服务。", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = ttsMode == "system", onClick = { onTtsMode("system") }, label = { Text("系统语音") })
                    FilterChip(selected = ttsMode == "cloud", onClick = { onTtsMode("cloud") }, label = { Text("云端模型 TTS") })
                }
                if (ttsMode == "cloud") {
                    Text("TTS 服务连接独立于推理模型配置。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    ConfigField("TTS API Base URL", ttsEndpoint, onTtsEndpoint, "例如 https://api.xiaomimimo.com/v1")
                    ConfigField("TTS API Key", ttsApiKey, onTtsApiKey, "TTS 服务密钥", secret = true)
                    ttsProfiles.forEach { profile -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onSelectTtsProfile(profile) }.padding(7.dp)) { Text(profile.name, color = MainText, fontSize = 12.sp); Text(profile.endpoint, color = Muted, fontSize = 9.sp, maxLines = 1) }
                        RadioButton(selected = ttsProfileName == profile.name, onClick = { onSelectTtsProfile(profile) })
                        IconButton(onClick = { onDeleteTtsProfile(profile) }) { Icon(Icons.Outlined.Delete, contentDescription = "删除 TTS 模型", tint = Color(0xFFE07878)) }
                    } }
                    OutlinedButton(onClick = { val n = "TTS-${ttsProfiles.size + 1}"; if (ttsEndpoint.isNotBlank()) onAddTtsProfile(TtsProfile(n, ttsEndpoint.trim(), ttsApiKey)) }, enabled = ttsEndpoint.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("保存当前 TTS 配置") }
                    ConfigField("TTS 模型名称", ttsModel, onTtsModel, "例如 tts-1 或服务商提供的语音模型")
                    ConfigField("TTS 音色", ttsVoice, onTtsVoice, "例如 alloy、nova、shimmer")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("语速", color = MainText, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(value = ttsRate, onValueChange = onTtsRate, valueRange = 0.6f..1.6f, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1fx", ttsRate), color = Muted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                    }
                    Text("支持 OpenAI /v1/audio/speech；小米 MiMo-V2.5-TTS 自动使用 /v1/chat/completions，并解析 WAV Base64 音频。MiMo 音色可选 mimo_default、冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean。", color = Muted, fontSize = 10.sp)
                } else {
                    Text("朗读语言", color = Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("zh-CN" to "中文普通话", "en-US" to "英语").forEach { (code, label) ->
                            FilterChip(selected = ttsLanguage == code, onClick = { onTtsLanguage(code) }, label = { Text(label, fontSize = 11.sp) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("语速", color = MainText, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(value = ttsRate, onValueChange = onTtsRate, valueRange = 0.6f..1.6f, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1fx", ttsRate), color = Muted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("音调", color = MainText, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(value = ttsPitch, onValueChange = onTtsPitch, valueRange = 0.7f..1.4f, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f", ttsPitch), color = Muted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                    }
                    Text("参数自动保存并用于回复朗读与 AI 通话播报。", color = Muted, fontSize = 10.sp)
                }
                Text("模型档案 · ${modelProfiles.size}", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (modelProfiles.isEmpty()) Text("保存常用配置后，可在这里快速切换模型。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                else modelProfiles.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onSelectProfile(p) }.padding(vertical = 9.dp, horizontal = 4.dp)) { Text(p.name, color = MainText, fontSize = 13.sp); Text("${p.provider} · ${p.model}", color = Muted, fontSize = 10.sp) }
                        Text("设为当前", color = Cyan, fontSize = 11.sp)
                        IconButton(onClick = { onDeleteProfile(p) }) { Icon(Icons.Outlined.Delete, contentDescription = "删除推理模型", tint = Color(0xFFE07878)) }
                    }
                }
                OutlinedTextField(value = profileName, onValueChange = { profileName = it.take(24) }, label = { Text("档案名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (profileName.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()) { onAddProfile(ModelProfile(profileName.trim(), provider, endpoint.trim(), model.trim(), apiKey)); profileName = "" } },
                    enabled = profileName.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("保存为模型档案") }
                Spacer(Modifier.height(8.dp))
                Text("密钥目前保存在应用私有配置中；模型推理请求与密钥加密存储仍需后续接入。", color = Muted, fontSize = 10.sp)
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("保存模型配置", fontWeight = FontWeight.Bold) }
                if (saved) Text("配置已保存", color = Color(0xFF76E0B1), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("天气服务", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("三种天气服务共存，独立保存配置；选择后 Agent 会使用对应服务查询天气。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("openmeteo" to "原有天气", "amap" to "高德地图", "caiyun" to "彩云天气").forEach { (id, label) ->
                        FilterChip(selected = weatherProvider == id, onClick = {
                            weatherProvider = id
                            prefs.edit().putString("weather_provider", id).apply()
                        }, modifier = Modifier.weight(1f), label = { Text(label, fontSize = 11.sp, maxLines = 1) })
                    }
                }
                when (weatherProvider) {
                    "amap" -> {
                        ConfigField("高德地图 Web 服务 API Key", amapWeatherKey, {
                            amapWeatherKey = it
                            prefs.edit().putString("amap_weather_key", it.trim()).apply()
                        }, "粘贴高德开放平台创建的 Key", secret = true)
                        Text("申请入口：https://console.amap.com/dev/key/app", color = Cyan, fontSize = 10.sp)
                        Text("需要创建“Web 服务”类型 Key。仅保存在本机。", color = Muted, fontSize = 10.sp)
                    }
                    "caiyun" -> {
                        ConfigField("彩云天气 Token", caiyunWeatherToken, {
                            caiyunWeatherToken = it
                            prefs.edit().putString("caiyun_weather_token", it.trim()).apply()
                        }, "粘贴彩云天气 API Token", secret = true)
                        Text("申请入口：https://platform.caiyunapp.com/", color = Cyan, fontSize = 10.sp)
                        Text("使用彩云天气 v2.6 综合接口；分钟级降水、预警等数据是否返回取决于 Token 套餐权限。Token 仅保存在本机。", color = Muted, fontSize = 10.sp)
                    }
                    else -> Text("原有 Open-Meteo 服务，无需 API Key。保留原有定位天气查询能力。", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("外观与主题", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("按系统或时间自动切换，也可手动固定主题", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("system" to "系统", "time" to "定时").forEach { (mode, label) ->
                        FilterChip(selected = themeMode == mode, onClick = { onThemeMode(mode) }, modifier = Modifier.weight(1f), label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("light" to "☀ 浅色", "dark" to "☾ 深色").forEach { (mode, label) ->
                        FilterChip(selected = themeMode == mode, onClick = { onThemeMode(mode) }, modifier = Modifier.weight(1f), label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                    }
                }
                Text(if (themeMode == "time") "自动规则：07:00–18:59 浅色，19:00–06:59 深色" else if (themeMode == "system") "当前跟随手机系统外观" else if (themeMode == "light") "已固定为浅色外观" else "已固定为深色外观", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("安全防护", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("为 Agent、插件和技能设定操作边界。建议默认关闭高风险权限，按任务、目录和资源逐项授权。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        val securityItems = listOf(
            Triple("security_delete_files", "删除本地文件的授权", "控制 Agent、技能或工具是否可以发起文件删除。建议默认关闭；开启后也应逐次展示目标文件路径、文件名和影响范围，并要求用户二次确认。应用只能删除自身有权管理的文件，SAF 授权范围之外及 Android 受保护目录仍由系统拦截。"),
            Triple("security_sensitive_dirs", "敏感目录的授权", "为下载、文档、照片、项目资料等目录分别设置访问范围。应遵循最小授权原则，只选择任务必需的目录；目录访问通过 Android 系统文件选择器授予，用户可随时撤销。不要将整个内部存储或包含密钥的目录无差别开放给技能。"),
            Triple("security_system_protection", "系统安全保护", "作为高风险动作的总开关：对启动外部应用、修改系统设置、调用系统能力及执行批量操作进行风险提示和确认。应用不会静默获取设备管理员、无障碍、Root 或其他系统权限；任何系统能力都必须经过 Android 正规授权页面。"),
            Triple("security_file_protection", "文件安全保护", "保护文件读写、覆盖、移动和删除流程。建议在覆盖前保留原文件或提示备份，对批量修改显示清单，并拒绝路径越界和可疑文件操作。此项不等同于杀毒软件、云端备份或全盘实时防护。"),
            Triple("security_identity_delegate", "身份委托控制", "控制是否允许 Agent、插件或技能在用户明确授权的范围内代为执行操作。委托应限定具体任务、资源和有效范围；敏感操作再次确认，不应把 API Key、登录密码、短信验证码或系统账号凭据交给技能。"),
            Triple("security_resource_access", "应用和资源访问控制", "管理相机、麦克风、位置、附件、剪贴板及应用内数据等资源的调用边界。按功能逐项授权，只在任务需要时请求系统权限，并说明用途；用户可以关闭应用内授权或通过 Android 设置撤销系统权限。")
        )
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp)) {
                securityItems.forEach { (key, title, description) ->
                    var granted by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (granted) "应用内已授权" else "尚未授权", color = if (granted) Color(0xFF70C99A) else Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Switch(checked = granted, onCheckedChange = {
                            granted = it
                            prefs.edit().putBoolean(key, it).apply()
                        })
                        IconButton(onClick = {
                            securityDetailTitle = title
                            securityDetailText = description
                            showSecurityDetail = true
                        }) { Text("ⓘ", color = Muted, fontSize = 17.sp) }
                    }
                    if (key != securityItems.last().first) Divider(color = GlassBorder.copy(alpha = .45f))
                }
            }
        }
        Text("重要：当前开关仅保存应用内授权偏好，尚未接入对应的底层执行拦截器，因此不能视为已经具备完整安全防护。实际访问受保护目录、相机、麦克风等仍须 Android 系统授权；后续接入执行层时应再次校验授权状态。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        SettingsAction("⌘", "手机操作权限", "启用无障碍服务后，可执行返回、主页、通知栏、滑动和按文字点击") {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Text("无障碍权限由 Android 系统管理；HyperCat AI 仅在你主动启用后执行明确的简单操作，不会自动读取密码或验证码。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(22.dp))
        Text("Agent 偏好与数据", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动朗读回复", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("仅朗读 Agent 新生成的最终答复，不重播历史消息", color = Muted, fontSize = 10.sp)
                }
                Switch(checked = autoReadReplies, onCheckedChange = onAutoReadReplies)
            }
        }
        SettingsAction("▤", "长期记忆管理", "查看、添加、删除或清空已保存的记忆（${savedNotes.size} 条）") { memoryDraft = ""; showMemoryDialog = true }
        SettingsAction("⚙", "Android 系统权限", "管理位置、麦克风、相机、通知等系统权限") {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Spacer(Modifier.height(22.dp))
        Text("扩展能力", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsAction("◷", "定时任务", "创建一次性提醒，到时间通过系统通知提醒") { showTaskDialog = true }
        SettingsAction("◇", "插件", if (pluginEnabled) "已启用插件框架" else "管理插件扩展入口") { showPluginDialog = true }
        SettingsAction("✧", "技能（Skills）", "${skills.size} 个技能 · 创建自定义技能或从文件导入") { showSkillDialog = true }
        SettingsAction("⌁", "MCP 设置", if (mcpEndpoint.isBlank()) "配置 MCP 服务连接" else mcpName.ifBlank { mcpEndpoint }) { showMcpDialog = true }
        SettingsAction("✉", "帮助与反馈", "问题反馈与项目主页") { showHelpDialog = true }
        SettingsAction("▤", "项目文档", "README、版本更新说明和使用文档") { showDocsDialog = true }
        Spacer(Modifier.height(18.dp))
        Text("应用与帮助", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsAction("i", "版本号", "当前版本 $currentVersion") { }
        SettingsAction("↻", "检查更新", updateStatus) { onCheckUpdates() }
        if (latestRelease != null && versionIsNewer(latestRelease.version, currentVersion)) {
            Surface(color = if (appDarkState) Color(0xFF183A43) else Color(0xFFE1F5F4), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("发现新版本 ${latestRelease.version}", color = MainText, fontWeight = FontWeight.Bold)
                    if (latestRelease.notes.isNotBlank()) Text(latestRelease.notes, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Button(onClick = { openUrl(latestRelease.url) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("前往 GitHub 下载更新") }
                }
            }
        }

    }
    if (showPetNameDialog) AlertDialog(
        onDismissRequest = { showPetNameDialog = false },
        title = { Text("给萌宠取个名字") },
        text = { OutlinedTextField(value = petNameDraft, onValueChange = { petNameDraft = it.take(16) }, singleLine = true, label = { Text("萌宠名称") }) },
        confirmButton = { TextButton(onClick = { onPetNameChange(petNameDraft); showPetNameDialog = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showPetNameDialog = false }) { Text("取消") } }
    )
    if (showMemoryDialog) AlertDialog(
        onDismissRequest = { showMemoryDialog = false },
        title = { Text("Agent 长期记忆 · ${savedNotes.size}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("保存的记忆会作为后续对话的背景信息。你也可以在聊天中发送“记住：……”快速添加。", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = memoryDraft, onValueChange = { memoryDraft = it.take(500) }, label = { Text("添加一条长期记忆") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (memoryDraft.isNotBlank()) { onSaveNote(memoryDraft.trim()); memoryDraft = "" } }, enabled = memoryDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("保存记忆") }
                Text("已保存 ${savedNotes.size} 条", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                savedNotes.forEach { memory ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(memory, color = MainText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteNote(memory) }) { Text("删除", color = Color(0xFFE07878)) }
                    }
                    Divider(color = GlassBorder.copy(alpha = .4f))
                }
                if (savedNotes.isNotEmpty()) TextButton(onClick = { savedNotes.toList().forEach(onDeleteNote) }) { Text("清空全部记忆", color = Color(0xFFE07878)) }
            }
        },
        confirmButton = { TextButton(onClick = { showMemoryDialog = false }) { Text("完成") } }
    )
    if (showSkillDialog) AlertDialog(
        onDismissRequest = { showSkillDialog = false },
        title = { Text("技能库 · ${skills.size}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("技能由名称、简介和操作指令组成。可创建自己的技能，或导入 JSON、Markdown、TXT 文件。导入内容会作为技能指令保存；请勿导入不可信来源的脚本或密钥。", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = skillName, onValueChange = { skillName = it.take(60) }, label = { Text("技能名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = skillDescription, onValueChange = { skillDescription = it.take(300) }, label = { Text("技能简介（选填）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = skillInstructions, onValueChange = { skillInstructions = it.take(30000) }, label = { Text("技能指令 / 工作流程") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (skillName.isNotBlank() && skillInstructions.isNotBlank()) {
                        skills.removeAll { it.name.equals(skillName.trim(), true) }
                        skills.add(0, SkillProfile(skillName.trim(), skillDescription.trim(), skillInstructions.trim()))
                        persistSkills()
                        skillName = ""; skillDescription = ""; skillInstructions = ""
                        skillStatus = "技能已保存"
                    }
                }, enabled = skillName.isNotBlank() && skillInstructions.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("创建并保存技能") }
                OutlinedButton(onClick = { skillImporter.launch(arrayOf("application/json", "text/markdown", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("从文件导入技能") }
                Text(skillStatus, color = Muted, fontSize = 11.sp)
                if (skills.isNotEmpty()) {
                    Divider(color = GlassBorder)
                    Text("已保存的技能", color = MainText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    skills.forEach { skill ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(skill.description.ifBlank { skill.instructions.take(90) }, color = Muted, fontSize = 10.sp, maxLines = 2)
                            }
                            TextButton(onClick = {
                                skills.remove(skill)
                                persistSkills()
                                skillStatus = "已删除：${skill.name}"
                            }) { Text("删除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSkillDialog = false }) { Text("完成") } }
    )
    if (showSecurityDetail) AlertDialog(
        onDismissRequest = { showSecurityDetail = false },
        title = { Text(securityDetailTitle) },
        text = { Text(securityDetailText + "\\n\\n当前开关仅保存应用内授权状态；涉及系统级文件或设备权限时，仍会通过 Android 系统授权流程单独申请。", color = Muted) },
        confirmButton = { TextButton(onClick = { showSecurityDetail = false }) { Text("知道了") } }
    )
    if (showPluginDialog) AlertDialog(
        onDismissRequest = { showPluginDialog = false },
        title = { Text("第三方插件") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text("支持接入 HTTPS REST API 插件。可手动登记或导入 JSON 清单；插件默认关闭，启用前请确认服务来源与权限。当前提供插件登记、启停和配置持久化，Agent 自动调用/参数映射将在工具执行层接入后开放。", color = Muted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许启用第三方插件", modifier = Modifier.weight(1f), color = MainText, fontSize = 13.sp)
                Switch(checked = pluginEnabled, onCheckedChange = { pluginEnabled = it; prefs.edit().putBoolean("plugins_enabled", it).apply() })
            }
            Divider(color = GlassBorder)
            Text("添加插件", color = MainText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(value = pluginName, onValueChange = { pluginName = it.take(60) }, label = { Text("插件名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pluginDescription, onValueChange = { pluginDescription = it.take(300) }, label = { Text("功能说明") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pluginEndpoint, onValueChange = { pluginEndpoint = it.take(1000) }, label = { Text("HTTPS API 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = pluginMethod == "GET", onClick = { pluginMethod = "GET" }, label = { Text("GET") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = pluginMethod == "POST", onClick = { pluginMethod = "POST" }, label = { Text("POST") })
            }
            OutlinedTextField(value = pluginApiKey, onValueChange = { pluginApiKey = it.take(500) }, label = { Text("API Key（选填）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val url = pluginEndpoint.trim()
                if (pluginName.isNotBlank() && url.startsWith("https://")) {
                    val item = ThirdPartyPlugin(pluginName.trim(), pluginDescription.trim(), url, pluginMethod, pluginApiKey.trim(), false)
                    plugins.removeAll { it.name.equals(item.name, true) }
                    plugins.add(0, item)
                    persistPlugins()
                    pluginName = ""; pluginDescription = ""; pluginEndpoint = ""; pluginApiKey = ""
                    pluginStatus = "插件已添加，默认未启用"
                } else pluginStatus = "请填写名称，并使用 HTTPS API 地址"
            }, enabled = pluginName.isNotBlank() && pluginEndpoint.startsWith("https://"), modifier = Modifier.fillMaxWidth()) { Text("添加插件") }
            OutlinedButton(onClick = { pluginImporter.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("导入 JSON 插件清单") }
            Text(pluginStatus, color = Muted, fontSize = 10.sp)
            Divider(color = GlassBorder)
            Text("已接入插件 · \${plugins.size}", color = MainText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            if (plugins.isEmpty()) Text("尚未添加插件。", color = Muted, fontSize = 11.sp)
            plugins.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = MainText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("\${item.method} · \${item.endpoint}", color = Muted, fontSize = 9.sp, maxLines = 2)
                        if (item.description.isNotBlank()) Text(item.description, color = Muted, fontSize = 10.sp, maxLines = 2)
                    }
                    Switch(checked = item.enabled, enabled = pluginEnabled, onCheckedChange = { enabled ->
                        val index = plugins.indexOf(item)
                        if (index >= 0) plugins[index] = item.copy(enabled = enabled)
                        persistPlugins()
                    })
                    TextButton(onClick = { plugins.remove(item); persistPlugins(); pluginStatus = "已移除：\${item.name}" }) { Text("删除") }
                }
                Divider(color = GlassBorder.copy(alpha = .4f))
            }
            Text("清单格式：{ name, description, endpoint, method, apiKey }。仅接受 HTTPS 远程地址；导入后默认关闭。不要导入来源不明的 API Key。", color = Muted, fontSize = 10.sp)
        } },
        confirmButton = { TextButton(onClick = { showPluginDialog = false }) { Text("完成") } }
    )
    if (showMcpDialog) AlertDialog(
        onDismissRequest = { showMcpDialog = false },
        title = { Text("MCP 服务设置") },
        text = { Column {
            Text("配置 MCP 服务连接信息。当前版本保存配置，MCP 客户端连接能力将在后续接入。", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = mcpName, onValueChange = { mcpName = it }, label = { Text("服务名称") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = mcpEndpoint, onValueChange = { mcpEndpoint = it }, label = { Text("服务地址 / URL") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { prefs.edit().putString("mcp_name", mcpName.trim()).putString("mcp_endpoint", mcpEndpoint.trim()).apply(); showMcpDialog = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showMcpDialog = false }) { Text("取消") } }
    )
    if (showTaskDialog) AlertDialog(
        onDismissRequest = { showTaskDialog = false },
        title = { Text("创建定时任务") },
        text = { Column {
            Text("设置任务名称和提醒时间。当前支持一次性本地通知提醒。", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it.take(60) }, label = { Text("任务名称") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = taskDelayMinutes, onValueChange = { taskDelayMinutes = it.filter { ch -> ch.isDigit() }.take(5) }, label = { Text("多少分钟后提醒") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            val minutes = taskDelayMinutes.toLongOrNull()
            if (taskTitle.isNotBlank() && minutes != null && minutes > 0) {
                val data = androidx.work.workDataOf("task_title" to taskTitle.trim(), "task_note" to "你设置的定时任务时间到了。")
                val request = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(minutes, java.util.concurrent.TimeUnit.MINUTES)
                    .setInputData(data).build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)
                taskTitle = ""
                showTaskDialog = false
            }
        }) { Text("创建提醒") } },
        dismissButton = { TextButton(onClick = { showTaskDialog = false }) { Text("取消") } }
    )
    if (showHelpDialog) AlertDialog(
        onDismissRequest = { showHelpDialog = false },
        title = { Text("帮助与反馈") },
        text = { Text("HyperCat AI 项目主页、问题反馈和版本发布均通过 GitHub 提供。若遇到问题，请提交设备型号、系统版本和复现步骤。", color = Muted) },
        confirmButton = { TextButton(onClick = { showHelpDialog = false; openUrl("https://github.com/yanglin950915-sketch/HyperCatAI-next/issues") }) { Text("提交反馈") } },
        dismissButton = { TextButton(onClick = { showHelpDialog = false; openUrl("https://github.com/yanglin950915-sketch/HyperCatAI-next") }) { Text("项目主页") } }
    )
    if (showDocsDialog) AlertDialog(
        onDismissRequest = { showDocsDialog = false },
        title = { Text("项目文档") },
        text = { Text("选择要查看的项目资料。文档将在浏览器中打开，内容与 GitHub 仓库保持同步。", color = Muted) },
        confirmButton = { TextButton(onClick = { showDocsDialog = false; openUrl("https://github.com/yanglin950915-sketch/HyperCatAI-next/blob/main/README.md") }) { Text("项目 README") } },
        dismissButton = { Row {
            TextButton(onClick = { showDocsDialog = false; openUrl("https://github.com/yanglin950915-sketch/HyperCatAI-next/releases") }) { Text("版本更新") }
            TextButton(onClick = { showDocsDialog = false; openUrl("https://github.com/yanglin950915-sketch/HyperCatAI-next#agent-核心能力") }) { Text("使用文档") }
        } }
    )
}

@Composable
private fun SettingsAction(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        color = Panel, shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp, color = Cyan, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MainText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 2)
            }
            Text("›", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ConfigField(label: String, value: String, onValue: (String) -> Unit, hint: String, secret: Boolean = false) {
    Text(label, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp)) {
            if (secret) {
                BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp),
                    visualTransformation = PasswordVisualTransformation(), decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp); inner() })
            } else {
                BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp),
                    decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp); inner() })
            }
        }
    }
    Spacer(Modifier.height(13.dp))
}
