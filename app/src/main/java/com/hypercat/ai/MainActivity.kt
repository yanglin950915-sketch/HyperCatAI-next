package com.hypercat.ai

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.hypercat.ai.rear.PetStateStore

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
        setContent { HyperCatApp() }
    }
}

private enum class Page { HOME, CHAT, SETTINGS }

private fun inferMood(reply: String): String = when {
    listOf("开心", "高兴", "太棒", "喜欢", "成功").any { reply.contains(it) } -> "happy"
    listOf("难过", "抱歉", "遗憾", "失败").any { reply.contains(it) } -> "sad"
    listOf("什么", "竟然", "惊讶", "哇").any { reply.contains(it) } -> "surprised"
    listOf("思考", "考虑", "分析", "让我想想").any { reply.contains(it) } -> "thinking"
    else -> "normal"
}
private data class ChatLine(val text: String, val fromUser: Boolean, val attachments: List<String> = emptyList())
private data class ModelProfile(val name: String, val provider: String, val endpoint: String, val model: String, val apiKey: String)
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
    val connection = URL("https://api.github.com/repos/yanglin950915-sketch/HyperCatAI-next/releases/latest").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "HyperCatAI-Android")
        if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub 暂无可用的正式 Release（HTTP ${connection.responseCode}）")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name", "").ifBlank { json.optString("name", "") }
        if (tag.isBlank()) throw IllegalStateException("Release 未设置版本标签")
        return ReleaseInfo(tag, json.optString("body", "").take(1200), json.optString("html_url", "https://github.com/yanglin950915-sketch/HyperCatAI-next/releases"))
    } finally { connection.disconnect() }
}

@Composable
private fun HyperCatApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf(Page.HOME) }
    var openSettingsDrawer by remember { mutableStateOf(false) }
    var chatDrawerExpanded by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf("OpenAI 兼容接口") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var petName by remember { mutableStateOf("小橘") }
    var petMood by remember { mutableStateOf("normal") }
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
    val messages = remember { mutableStateListOf<ChatLine>() }
    val modelProfiles = remember { mutableStateListOf<ModelProfile>() }
    var savedNotes by remember { mutableStateOf(emptyList<String>()) }
    val chatPrefs = remember { context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE) }
    var recentPrompts by remember {
        mutableStateOf(chatPrefs.getString("recent_chat_titles", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList())
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        provider = prefs.getString("provider", "OpenAI 兼容接口") ?: "OpenAI 兼容接口"
        endpoint = prefs.getString("endpoint", "") ?: ""
        model = prefs.getString("model", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""
        saved = endpoint.isNotBlank() && model.isNotBlank()
        runCatching { val array = org.json.JSONArray(prefs.getString("model_profiles", "[]") ?: "[]"); for (i in 0 until array.length()) { val item = array.getJSONObject(i); modelProfiles.add(ModelProfile(item.optString("name"), item.optString("provider"), item.optString("endpoint"), item.optString("model"), item.optString("apiKey"))) } }
        savedNotes = prefs.getString("saved_notes", "")?.split("\n--NOTE--\n")?.filter { it.isNotBlank() } ?: emptyList()
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

    LaunchedEffect(petName, petMood) {
        val state = PetStateStore.read(context)
        PetStateStore.update(context, petName, "🤖", petMood, state.reply)
    }

    MaterialTheme(colorScheme = if (darkNow) darkColorScheme(
        primary = Cyan, secondary = Color(0xFFB9A4FF), background = Bg, surface = Panel,
        onPrimary = Color(0xFF06131B), onSurface = Color(0xFFEAF4FF)
    ) else lightColorScheme(
        primary = Color(0xFF168DA6), secondary = Color(0xFF7461C8), background = Bg, surface = Panel,
        onPrimary = Color.White, onSurface = Color(0xFF142235)
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (!(page == Page.CHAT && chatDrawerExpanded)) {
                NavigationBar(containerColor = if (darkNow) Color(0xCC0D1726) else Color(0xDDF7FAFD), tonalElevation = 0.dp) {
                    NavigationBarItem(selected = page == Page.HOME, onClick = { page = Page.HOME },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = "萌宠") }, label = { Text("萌宠") })
                    NavigationBarItem(selected = page == Page.CHAT, onClick = { page = Page.CHAT },
                        icon = { Icon(Icons.Outlined.Forum, contentDescription = "Agent") }, label = { Text("Agent") })
                }
                }
            }
        ) { padding ->
            when (page) {
                Page.HOME -> HomePage(
                    modifier = Modifier.padding(padding),
                    configured = saved,
                    petName = petName,
                    petMood = petMood,
                    onPetNameChange = { value ->
                        val clean = value.trim().take(16).ifBlank { "小橘" }
                        petName = clean
                        context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE).edit().putString("pet_name", clean).apply()
                    },
                    openChat = { page = Page.CHAT },
                    openSettings = { page = Page.CHAT; openSettingsDrawer = true }
                )
                Page.CHAT -> ChatPage(
                    modifier = Modifier,
                    contentPadding = padding,
                    onDrawerVisibility = { chatDrawerExpanded = it },
                    messages = messages,
                    recentPrompts = recentPrompts,
                    modelProfiles = modelProfiles.toList(), activeModel = model.ifBlank { "未选择模型" },
                    onSelectModel = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                    savedNotes = savedNotes, onSaveNote = { note -> savedNotes=(listOf(note)+savedNotes).distinct().take(100); chatPrefs.edit().putString("saved_notes",savedNotes.joinToString("\n--NOTE--\n")).apply() },
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = { value, attachments ->
                        if (value.isNotBlank() || attachments.isNotEmpty()) {
                            messages.add(ChatLine(value, true, attachments.map { it.lastPathSegment ?: "附件" }))
                            if (value.isNotBlank()) {
                                recentPrompts = (listOf(value.trim().take(42)) + recentPrompts).distinct().take(12)
                                chatPrefs.edit().putString("recent_chat_titles", recentPrompts.joinToString("||")).apply()
                            }
                            val reply = if (saved) "模型配置已保存，但当前版本尚未接入推理请求。附件已加入本条消息，下一步会接入模型多模态请求。" else "我还没有连接大模型。你可以先添加文字、图片或文件；到「模型配置」填写模型信息后，才能启用真正的 AI 对话。"
                            val mood = inferMood(reply)
                            petMood = mood
                            messages.add(ChatLine(reply, false))
                            draft = ""
                        }
                    },
                    openSettings = { page = Page.CHAT; openSettingsDrawer = true },
                    configured = saved,
                    petName = petName,
                    petMood = petMood,
                    onMood = { mood -> petMood = mood },
                    showSettingsOnEnter = openSettingsDrawer,
                    onSettingsDrawerShown = { openSettingsDrawer = false },
                    settingsContent = { drawerModifier ->
                        SettingsPage(
                            modifier = drawerModifier,
                            provider = provider, onProvider = { provider = it },
                            modelProfiles = modelProfiles.toList(),
                            onAddProfile = { p -> modelProfiles.removeAll { it.name == p.name }; modelProfiles.add(0,p); val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                            onSelectProfile = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                            endpoint = endpoint, onEndpoint = { endpoint = it; saved = false },
                            model = model, onModel = { model = it; saved = false },
                            apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
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
                    provider = provider, onProvider = { provider = it },
                    modelProfiles = modelProfiles.toList(),
                    onAddProfile = { p -> modelProfiles.removeAll { it.name == p.name }; modelProfiles.add(0,p); val a=org.json.JSONArray(); modelProfiles.forEach { x -> a.put(JSONObject().put("name",x.name).put("provider",x.provider).put("endpoint",x.endpoint).put("model",x.model).put("apiKey",x.apiKey)) }; chatPrefs.edit().putString("model_profiles",a.toString()).apply() },
                    onSelectProfile = { p -> provider=p.provider; endpoint=p.endpoint; model=p.model; apiKey=p.apiKey; saved=true; chatPrefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).putString("api_key",apiKey).apply() },
                    endpoint = endpoint, onEndpoint = { endpoint = it; saved = false },
                    model = model, onModel = { model = it; saved = false },
                    apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
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
private fun HomePage(modifier: Modifier, configured: Boolean, petName: String, petMood: String, onPetNameChange: (String) -> Unit, openChat: () -> Unit, openSettings: () -> Unit) {
    var showNameDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(petName) { mutableStateOf(petName) }
    Column(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(if (appDarkState) Color(0xFF090909) else Color(0xFFEAF2F8), Bg)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 22.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HYPERCAT", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("你的 AI 伙伴", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
            Surface(shape = CircleShape, color = Panel2, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("✦", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = if (appDarkState) Color(0xFF111111) else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR COMPANION", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                        Text("今天也一起探索吧", color = MainText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("在线陪伴", color = if (configured) Color(0xFF65DDAE) else Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height * .48f)
                        drawCircle(Cyan.copy(alpha = if (appDarkState) .07f else .10f), size.minDimension * .43f, center)
                        drawCircle(Cyan.copy(alpha = .12f), size.minDimension * .34f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                        drawCircle(Color(0xFF8C7BFF).copy(alpha = .12f), size.minDimension * .27f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                    }
                    CatFace(Modifier.fillMaxWidth(.92f).fillMaxHeight(.92f), petMood)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(petName, color = MainText, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Surface(Modifier.size(30.dp).clickable { nameDraft = petName; showNameDialog = true }, color = Panel2, shape = CircleShape) {
                        Box(contentAlignment = Alignment.Center) { Text("✎", color = Cyan, fontSize = 14.sp) }
                    }
                }
                Text("轻触表情，看看我的反应", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (configured) Color(0xFF65DDAE) else Color(0xFFFFC46B)))
                    Spacer(Modifier.width(7.dp))
                    Text(if (configured) "模型已就绪" else "连接模型后即可开始使用", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = openChat, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("和 $petName 聊一聊", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("→", fontSize = 18.sp)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick = openSettings, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(17.dp)) {
            Text("模型与服务设置", fontSize = 13.sp)
        }
    }
    if (showNameDialog) AlertDialog(
        onDismissRequest = { showNameDialog = false },
        title = { Text("给萌宠取个名字") },
        text = { OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(16) }, singleLine = true, label = { Text("萌宠名称") }) },
        confirmButton = { TextButton(onClick = { onPetNameChange(nameDraft); showNameDialog = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("取消") } }
    )
}

@Composable
private fun CatFace(modifier: Modifier = Modifier, mood: String = "normal") {
    var gazeX by remember { mutableStateOf(0f) }
    var gazeY by remember { mutableStateOf(0f) }
    var blinking by remember { mutableStateOf(false) }
    val pupilX by animateFloatAsState(gazeX, animationSpec = tween(180), label = "pupilX")
    val pupilY by animateFloatAsState(gazeY, animationSpec = tween(180), label = "pupilY")
    val eyelid by animateFloatAsState(if (blinking) 0.10f else 1f, animationSpec = tween(85), label = "eyelid")

    LaunchedEffect(blinking) {
        if (blinking) {
            delay(150)
            blinking = false
        }
    }

    Canvas(modifier
        .pointerInput(Unit) {
            detectTapGestures { point ->
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                gazeX = (((point.x / w) - 0.5f) * 2.4f).coerceIn(-1f, 1f)
                gazeY = (((point.y / h) - 0.47f) * 2.8f).coerceIn(-1f, 1f)
                blinking = true
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { point ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    gazeX = (((point.x / w) - 0.5f) * 2.4f).coerceIn(-1f, 1f)
                    gazeY = (((point.y / h) - 0.47f) * 2.8f).coerceIn(-1f, 1f)
                },
                onDrag = { change, _ ->
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
        listOf(cx - gap, cx + gap).forEach { x ->
            val currentEyeH = eyeH * eyelid
            // Soft ivory eye whites with compact dark pupils: calmer, friendlier and less neon.
            drawOval(eyeWhite, Offset(x - eyeW / 2, cy - currentEyeH / 2), Size(eyeW, currentEyeH))
            if (eyelid > .22f) {
                val px = (pupilX * eyeW * .30f).coerceIn(-eyeW * .30f, eyeW * .30f)
                val py = (pupilY * eyeH * .22f).coerceIn(-eyeH * .22f, eyeH * .22f)
                val pupilCenter = Offset(x + px, cy + py)
                drawCircle(Color(0xFF242424), radius = eyeW * .285f, center = pupilCenter)
                drawCircle(Color.White, radius = eyeW * .075f, center = Offset(x + px - eyeW * .09f, cy - eyeH * .10f + py))
            }
        }
        val noseY = cy + eyeH * .72f
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
private fun ChatPage(
    modifier: Modifier, contentPadding: PaddingValues, onDrawerVisibility: (Boolean) -> Unit, messages: List<ChatLine>, recentPrompts: List<String>, modelProfiles: List<ModelProfile>, activeModel: String, onSelectModel: (ModelProfile) -> Unit, savedNotes: List<String>, onSaveNote: (String) -> Unit, draft: String, onDraft: (String) -> Unit,
    onSend: (String, List<Uri>) -> Unit, openSettings: () -> Unit, configured: Boolean, petName: String,
    petMood: String, onMood: (String) -> Unit,
    showSettingsOnEnter: Boolean,
    onSettingsDrawerShown: () -> Unit,
    settingsContent: @Composable (Modifier) -> Unit
) {
    var drawerSection by remember { mutableStateOf("recent") }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }
    var showCallInfo by remember { mutableStateOf(false) }
    var callMode by remember { mutableStateOf("语音") }
    var isListening by remember { mutableStateOf(false) }
    var showRemember by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var recentQuery by remember { mutableStateOf("") }
    var recentSort by remember { mutableStateOf("最新") }
    var showModelPicker by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> selectedFiles = (selectedFiles + uris).distinct() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedFiles = (selectedFiles + uris).distinct() }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onDraft(if (draft.isBlank()) spoken else "$draft $spoken")
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
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
    Column(modifier.fillMaxSize().padding(contentPadding).imePadding().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(40.dp).clickable { drawerScope.launch { drawerState.open() } }) {
                Box(contentAlignment = Alignment.Center) { Text("☰", color = MainText, fontSize = 18.sp) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Agent", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
            Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(40.dp).clickable { showClear = true }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Delete, contentDescription = "清理当前对话", tint = MainText, modifier = Modifier.size(20.dp)) }
            }
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
            Text("有什么可以帮你？", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("选择一个常用任务，或直接输入你的想法。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
            val suggestions = listOf(
                "总结内容" to "请帮我总结以下内容，提炼重点：",
                "翻译文本" to "请将下面内容准确翻译成中文：",
                "润色写作" to "请帮我润色下面这段文字，保持原意：",
                "代码助手" to "请分析下面的代码并给出修复建议："
            )
            suggestions.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowItems.forEach { (label, prompt) ->
                        Surface(
                            modifier = Modifier.weight(1f).padding(bottom = 8.dp).clickable { onDraft(if (draft.isBlank()) prompt else "$prompt\n$draft") },
                            color = Panel, shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Text("✦", color = Cyan, fontSize = 14.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(label, color = MainText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatFace(Modifier.width(220.dp).height(170.dp), petMood)
                    Text("嗨，我是$petName。", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("你可以在这里和我聊天。", color = Muted, fontSize = 13.sp)
                }
            }
            items(messages.filter { searchQuery.isBlank() || it.text.contains(searchQuery, ignoreCase = true) }) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                    if (!line.fromUser) {
                        Box(Modifier.padding(end = 8.dp).size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Cyan, Color(0xFF8C7BFF)))), contentAlignment = Alignment.Center) {
                            Text(petName.take(1), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(modifier = if (line.fromUser) Modifier.widthIn(max = 276.dp) else Modifier.fillMaxWidth(0.96f), color = if (line.fromUser) (if (appDarkState) Color(0xFF303030) else Color(0xFF1689C2)) else if (appDarkState) Color(0xFF1B1B1B) else Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), shape = RoundedCornerShape(topStart = if (line.fromUser) 18.dp else 5.dp, topEnd = if (line.fromUser) 5.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            if (line.text.isNotBlank()) Text(line.text, color = if (line.fromUser || appDarkState) Color.White else Color(0xFF172638), fontSize = 14.sp, lineHeight = 20.sp)
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
        }
        if (selectedFiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${selectedFiles.size} 个附件已添加", color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("清除", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { selectedFiles = emptyList() })
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(modifier = Modifier.fillMaxWidth(), color = if (appDarkState) Color.White.copy(alpha = .08f) else Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), shape = RoundedCornerShape(22.dp)) {
                BasicTextField(value = draft, onValueChange = onDraft,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 144.dp).padding(horizontal = 15.dp, vertical = 14.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp, lineHeight = 20.sp),
                    decorationBox = { inner -> if (draft.isEmpty()) Text("发送消息给$petName…", color = Muted, fontSize = 14.sp); inner() })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(Modifier.size(40.dp).clickable { filePicker.launch(arrayOf("*/*")) }, color = Panel2, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AttachFile, contentDescription = "添加附件", tint = Muted) }
                }
                Surface(Modifier.clip(RoundedCornerShape(14.dp)).clickable { callMode = "语音"; showCallInfo = true }, color = Panel2, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Call, contentDescription = null, tint = Cyan, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp)); Text("语音", color = MainText, fontSize = 11.sp)
                    }
                }
                Surface(Modifier.clip(RoundedCornerShape(14.dp)).clickable { callMode = "视频"; showCallInfo = true }, color = Panel2, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Cyan, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp)); Text("视频", color = MainText, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                VoiceInputButton(isListening = isListening) {
                    if (!isListening) {
                        isListening = true
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话")
                        }
                        runCatching { voiceInput.launch(intent) }.onFailure { isListening = false }
                    }
                }
                Surface(Modifier.size(42.dp).clickable(enabled = draft.isNotBlank() || selectedFiles.isNotEmpty()) {
                    onSend(draft, selectedFiles); selectedFiles = emptyList()
                }, color = if (draft.isNotBlank() || selectedFiles.isNotEmpty()) Cyan else Panel2, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Send, contentDescription = "发送", tint = if (draft.isNotBlank() || selectedFiles.isNotEmpty()) Color(0xFF06202A) else Muted)
                    }
                }
            }
            if (isListening) Text("正在聆听… 说完后系统会自动返回识别文字", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
        AnimatedVisibility(visible = drawerState.isOpen, modifier = Modifier.fillMaxSize().statusBarsPadding(),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Panel, shape = RoundedCornerShape(0.dp)) {
                        if (drawerSection == "settings") {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { drawerSection = "recent" }) { Text("‹ 最近对话", color = MainText) }
                                    Spacer(Modifier.weight(1f))
                                    Text("设置", color = MainText, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { drawerScope.launch { drawerState.close() } }) { Icon(Icons.Outlined.Close, contentDescription = "关闭设置", tint = Muted) }
                                }
                                settingsContent(Modifier.weight(1f).fillMaxWidth())
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("最近对话", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        Text("选择一条记录，将内容放回输入框继续编辑", color = Muted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { drawerScope.launch { drawerState.close() } }) { Icon(Icons.Outlined.Close, contentDescription = "关闭最近对话", tint = Muted) }
                                }
                                Spacer(Modifier.height(14.dp))
                                OutlinedTextField(value = recentQuery, onValueChange = { recentQuery = it }, placeholder = { Text("搜索聊天记录") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 10.dp)) { listOf("最新", "最早", "A-Z").forEach { mode -> FilterChip(selected = recentSort == mode, onClick = { recentSort = mode }, label = { Text(mode, fontSize = 11.sp) }) } }
                                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                                    if (orderedRecent.isEmpty()) item {
                                        Column(Modifier.fillMaxWidth().padding(top = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("暂无最近对话", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text("发送消息后，这里会显示最近使用的内容", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                                        }
                                    }
                                    items(orderedRecent) { prompt ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onDraft(prompt); drawerScope.launch { drawerState.close() } }.padding(vertical = 12.dp, horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Cyan, Color(0xFF8276D9)))), contentAlignment = Alignment.Center) { Text(petName.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) { Text(petName, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(prompt, color = Muted, fontSize = 11.sp, maxLines = 2) }
                                        }
                                        Divider(color = GlassBorder.copy(alpha = .4f))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                                    color = Panel2,
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("⌕" to "找一下", "✎" to "记一下", "⚙" to "设置").forEachIndexed { index, item ->
                                            Column(
                                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        when (index) {
                                                            0 -> { showSearch = true; drawerScope.launch { drawerState.close() } }
                                                            1 -> { noteDraft = draft; showRemember = true; drawerScope.launch { drawerState.close() } }
                                                            else -> drawerSection = "settings"
                                                        }
                                                    }.padding(vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(item.first, color = Cyan, fontSize = 20.sp)
                                                Text(item.second, color = MainText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
        }
    }
    if (showSearch) AlertDialog(onDismissRequest = { showSearch = false; searchQuery = "" }, title = { Text("查找对话内容") },
        text = { Column { OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("输入关键词，聊天列表即时筛选") }, singleLine = true); Text("${messages.count { it.text.contains(searchQuery, true) }} 条匹配消息", color = Muted, fontSize = 12.sp) } },
        confirmButton = { TextButton(onClick = { showSearch = false; searchQuery = "" }) { Text("完成") } })
    if (showRemember) AlertDialog(onDismissRequest = { showRemember = false }, title = { Text("记一下") }, text = { Column { OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it }, label = { Text("要记住的内容") }, minLines = 3); Text("已保存 ${savedNotes.size} 条", color = Muted, fontSize = 11.sp) } }, confirmButton = { TextButton(onClick = { if (noteDraft.isNotBlank()) onSaveNote(noteDraft.trim()); noteDraft=""; showRemember=false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { showRemember=false }) { Text("取消") } })
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("清理当前对话") },
        text = { Text("确定清空当前页面的全部消息吗？此操作不会删除最近对话记录。", color = Muted) },
        confirmButton = { TextButton(onClick = { (messages as? androidx.compose.runtime.snapshots.SnapshotStateList<ChatLine>)?.clear(); showClear = false }) { Text("清空消息", color = Color(0xFFE57373)) } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } })
    if (showModelPicker) AlertDialog(onDismissRequest={showModelPicker=false},title={Text("切换模型")},text={if(modelProfiles.isEmpty()) Text("请先在设置中添加模型档案",color=Muted) else Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())) { modelProfiles.forEach { p -> Text("${p.name} · ${p.model}",color=MainText,modifier=Modifier.fillMaxWidth().clickable { onSelectModel(p);showModelPicker=false }.padding(vertical=10.dp)) } }},confirmButton={TextButton(onClick={showModelPicker=false;openSettings()}) { Text("管理模型") }})
    if (showCallInfo) AlertDialog(onDismissRequest = { showCallInfo = false }, title = { Text("${callMode}通话") },
        text = { Text(if (callMode == "语音") "语音通话入口已恢复。实时双向通话需要接入支持实时音频的模型服务；当前版本暂未连接通话引擎。" else "视频通话入口已恢复。摄像头采集、实时视频传输与视频理解需要接入支持音视频的模型服务；当前版本暂未连接通话引擎。", color = Muted) },
        confirmButton = { TextButton(onClick = { showCallInfo = false }) { Text("知道了") } })
}

@Composable
private fun VoiceInputButton(isListening: Boolean, onClick: () -> Unit) {
    val pulseTransition = rememberInfiniteTransition(label = "voice-input-pulse")
    val pulse by pulseTransition.animateFloat(initialValue = 0.78f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(animation = tween(720), repeatMode = RepeatMode.Reverse), label = "voice-input-scale")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        if (isListening) Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Cyan.copy(alpha = 0.42f), radius = size.minDimension * 0.42f * pulse,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Surface(Modifier.size(40.dp).clickable(enabled = !isListening, onClick = onClick),
            color = if (isListening) Cyan else Panel2, shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = if (isListening) "正在聆听" else "语音输入",
                    tint = if (isListening) Color(0xFF06202A) else MainText)
            }
        }
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier, provider: String, onProvider: (String) -> Unit, modelProfiles: List<ModelProfile>, onAddProfile: (ModelProfile) -> Unit, onSelectProfile: (ModelProfile) -> Unit,
    endpoint: String, onEndpoint: (String) -> Unit, model: String, onModel: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit, onSave: () -> Unit, saved: Boolean,
    themeMode: String, onThemeMode: (String) -> Unit,
    currentVersion: String, updateStatus: String, latestRelease: ReleaseInfo?,
    onCheckUpdates: () -> Unit, openUrl: (String) -> Unit
) {
    var showPluginDialog by remember { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showSecurityDetail by remember { mutableStateOf(false) }
    var securityDetailTitle by remember { mutableStateOf("") }
    var securityDetailText by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var taskDelayMinutes by remember { mutableStateOf("30") }
    var profileName by remember { mutableStateOf("") }
    var pluginEnabled by remember { mutableStateOf(false) }
    var mcpName by remember { mutableStateOf("") }
    var mcpEndpoint by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        pluginEnabled = prefs.getBoolean("plugins_enabled", false)
        mcpName = prefs.getString("mcp_name", "") ?: ""
        mcpEndpoint = prefs.getString("mcp_endpoint", "") ?: ""
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("设置", color = MainText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("外观、扩展能力与应用信息", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
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
                Text("模型档案 · ${modelProfiles.size}", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (modelProfiles.isEmpty()) Text("保存常用配置后，可在这里快速切换模型。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                else modelProfiles.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelectProfile(p) }.padding(vertical = 9.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${p.provider} · ${p.model}", color = Muted, fontSize = 10.sp)
                        }
                        Text("设为当前  ›", color = Cyan, fontSize = 11.sp)
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
        Text("管理 HyperCat AI 在应用内可执行的敏感操作授权", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        val securityItems = listOf(
            Triple("security_delete_files", "删除本地文件的授权", "允许应用在明确确认后删除其管理的本地文件。不会授予任意目录的系统级删除权限。"),
            Triple("security_sensitive_dirs", "敏感目录的授权", "管理对用户指定目录的访问。访问 Android 受保护目录仍需通过系统文件选择器单独授权。"),
            Triple("security_system_protection", "系统安全保护", "限制高风险操作并提示权限变化；不代表获得设备管理员或系统安全中心权限。"),
            Triple("security_file_protection", "文件安全保护", "对应用管理的文件操作增加确认与保护提示；尚不等同于全盘防病毒或实时监控。"),
            Triple("security_identity_delegate", "身份委托控制", "控制是否允许应用内功能代表你执行已授权的操作；不会共享系统账号凭据。"),
            Triple("security_resource_access", "应用和资源访问控制", "管理应用内功能访问文件、附件及其他资源的授权偏好。")
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
        Text("说明：这里的开关记录应用内授权意愿，不会绕过 Android 权限机制。系统目录、设备管理等权限仍需系统正式授权。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(22.dp))
        Text("扩展能力", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsAction("◷", "定时任务", "创建一次性提醒，到时间通过系统通知提醒") { showTaskDialog = true }
        SettingsAction("◇", "插件", if (pluginEnabled) "已启用插件框架" else "管理插件扩展入口") { showPluginDialog = true }
        SettingsAction("⌁", "MCP 设置", if (mcpEndpoint.isBlank()) "配置 MCP 服务连接" else mcpName.ifBlank { mcpEndpoint }) { showMcpDialog = true }
        SettingsAction("✉", "帮助与反馈", "使用说明、问题反馈与项目主页") { showHelpDialog = true }
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
    if (showSecurityDetail) AlertDialog(
        onDismissRequest = { showSecurityDetail = false },
        title = { Text(securityDetailTitle) },
        text = { Text(securityDetailText + "\\n\\n当前开关仅保存应用内授权状态；涉及系统级文件或设备权限时，仍会通过 Android 系统授权流程单独申请。", color = Muted) },
        confirmButton = { TextButton(onClick = { showSecurityDetail = false }) { Text("知道了") } }
    )
    if (showPluginDialog) AlertDialog(
        onDismissRequest = { showPluginDialog = false },
        title = { Text("插件管理") },
        text = { Column {
            Text("插件扩展入口已建立。当前版本尚未接入第三方插件运行时，开关仅保存管理偏好。", color = Muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用插件功能", modifier = Modifier.weight(1f), color = MainText)
                Switch(checked = pluginEnabled, onCheckedChange = { pluginEnabled = it; prefs.edit().putBoolean("plugins_enabled", it).apply() })
            }
            Text("后续可在此安装、授权和管理工具插件。", color = Muted, fontSize = 11.sp)
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
                BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    visualTransformation = PasswordVisualTransformation(), decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp); inner() })
            } else {
                BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp); inner() })
            }
        }
    }
    Spacer(Modifier.height(13.dp))
}
