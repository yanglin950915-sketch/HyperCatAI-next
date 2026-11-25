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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.hypercat.ai.rear.PetStateStore

private val Bg = Color(0xFF080D18)
private val Panel = Color(0xFF111C2D)
private val Panel2 = Color(0xFF17263B)
private val Cyan = Color(0xFF78E5F4)
private val Muted = Color(0xFF91A3BA)

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

@Composable
private fun HyperCatApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf(Page.HOME) }
    var provider by remember { mutableStateOf("OpenAI 兼容接口") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var petName by remember { mutableStateOf("小橘") }
    var petMood by remember { mutableStateOf("normal") }
    val messages = remember { mutableStateListOf<ChatLine>() }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE)
        provider = prefs.getString("provider", "OpenAI 兼容接口") ?: "OpenAI 兼容接口"
        endpoint = prefs.getString("endpoint", "") ?: ""
        model = prefs.getString("model", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""
        saved = endpoint.isNotBlank() && model.isNotBlank()
        petName = prefs.getString("pet_name", "小橘")?.takeIf { it.isNotBlank() } ?: "小橘"
    }

    LaunchedEffect(petName, petMood) {
        val state = PetStateStore.read(context)
        PetStateStore.update(context, petName, "🤖", petMood, state.reply)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, secondary = Color(0xFFB9A4FF), background = Bg, surface = Panel,
        onPrimary = Color(0xFF06131B), onSurface = Color(0xFFEAF4FF)
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D1726), tonalElevation = 0.dp) {
                    NavigationBarItem(selected = page == Page.HOME, onClick = { page = Page.HOME },
                        icon = { Text("⌂", fontSize = 21.sp) }, label = { Text("萌宠") })
                    NavigationBarItem(selected = page == Page.CHAT, onClick = { page = Page.CHAT },
                        icon = { Text("◌", fontSize = 21.sp) }, label = { Text("Agent") })
                    NavigationBarItem(selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS },
                        icon = { Text("⚙", fontSize = 19.sp) }, label = { Text("模型配置") })
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
                    openSettings = { page = Page.SETTINGS }
                )
                Page.CHAT -> ChatPage(
                    modifier = Modifier.padding(padding),
                    messages = messages,
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = { value, attachments ->
                        if (value.isNotBlank() || attachments.isNotEmpty()) {
                            messages.add(ChatLine(value, true, attachments.map { it.lastPathSegment ?: "附件" }))
                            val reply = if (saved) "模型配置已保存，但当前版本尚未接入推理请求。附件已加入本条消息，下一步会接入模型多模态请求。" else "我还没有连接大模型。你可以先添加文字、图片或文件；到「模型配置」填写模型信息后，才能启用真正的 AI 对话。"
                            val mood = inferMood(reply)
                            petMood = mood
                            messages.add(ChatLine(reply, false))
                            draft = ""
                        }
                    },
                    openSettings = { page = Page.SETTINGS },
                    configured = saved,
                    petName = petName,
                    petMood = petMood
                )
                Page.SETTINGS -> SettingsPage(
                    modifier = Modifier.padding(padding),
                    provider = provider, onProvider = { provider = it },
                    endpoint = endpoint, onEndpoint = { endpoint = it; saved = false },
                    model = model, onModel = { model = it; saved = false },
                    apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
                    onSave = {
                        context.getSharedPreferences("hypercat_agent_config", Context.MODE_PRIVATE).edit()
                            .putString("provider", provider).putString("endpoint", endpoint.trim())
                            .putString("model", model.trim()).putString("api_key", apiKey).apply()
                        saved = endpoint.isNotBlank() && model.isNotBlank()
                    },
                    saved = saved
                )
            }
        }
    }
}

@Composable
private fun HomePage(modifier: Modifier, configured: Boolean, petName: String, petMood: String, onPetNameChange: (String) -> Unit, openChat: () -> Unit, openSettings: () -> Unit) {
    var showNameDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(petName) { mutableStateOf(petName) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Brush.verticalGradient(listOf(Color(0xFF13243A), Bg))).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HYPERCAT AI", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                Text("你的随身 AI 伙伴", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) {
                Text("HC", color = Cyan, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = Color(0xFF0A1422), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21364D))) {
            Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("MEET YOUR COMPANION", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                CatFace(Modifier.fillMaxWidth().height(220.dp), petMood)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(petName, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true)
                    Spacer(Modifier.width(8.dp))
                    Text("✎", color = Cyan, fontSize = 17.sp, modifier = Modifier.clip(CircleShape).clickable { nameDraft = petName; showNameDialog = true }.padding(5.dp))
                }
                Text("轻触表情，和我互动", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (configured) Color(0xFF6DE3B1) else Color(0xFFFFC46B)))
                    Spacer(Modifier.width(7.dp))
                    Text(if (configured) "模型配置已保存" else "尚未配置大模型", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = openChat, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), shape = RoundedCornerShape(18.dp)) {
            Spacer(Modifier.width(9.dp))
            Text("和${petName}开始对话", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = openSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
            Spacer(Modifier.width(8.dp))
            Text("配置本地 / 在线大模型", maxLines = 2, softWrap = true)
        }
        Spacer(Modifier.height(18.dp))
    }
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("给萌宠取个名字") },
            text = { OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(16) }, singleLine = true, label = { Text("萌宠名称") }) },
            confirmButton = { TextButton(onClick = { onPetNameChange(nameDraft); showNameDialog = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("取消") } }
        )
    }
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
        val eyeW = size.width * .235f
        val eyeH = size.height * .40f
        val gap = size.width * .155f
        val eyeBrush = Brush.verticalGradient(listOf(Color(0xFFE7FFFF), Color(0xFF65DFF5), Color(0xFF1489C7)))
        listOf(cx - gap, cx + gap).forEach { x ->
            val currentEyeH = eyeH * eyelid
            drawOval(eyeBrush, Offset(x - eyeW / 2, cy - currentEyeH / 2), Size(eyeW, currentEyeH))
            if (eyelid > 0.22f) {
                val px = (pupilX * eyeW * .38f).coerceIn(-eyeW * .38f, eyeW * .20f)
                val py = (pupilY * eyeH * .28f).coerceIn(-eyeH * .28f, eyeH * .16f)
                drawCircle(Color(0xFF06172A), radius = eyeW * .29f, center = Offset(x + px, cy + eyeH * .05f + py))
                drawCircle(Color.White, radius = eyeW * .085f, center = Offset(x + px - eyeW * .09f, cy - eyeH * .08f + py))
                drawCircle(Color(0xFFB8F7FF), radius = eyeW * .035f, center = Offset(x + px + eyeW * .11f, cy + eyeH * .16f + py))
            }
        }
        val noseY = cy + eyeH * .72f
        val nose = Path().apply {
            moveTo(cx - 7.dp.toPx(), noseY)
            quadraticTo(cx, noseY - 8.dp.toPx(), cx + 7.dp.toPx(), noseY)
            quadraticTo(cx, noseY + 9.dp.toPx(), cx - 7.dp.toPx(), noseY)
            close()
        }
        drawPath(nose, Color(0xFF8BEAFF))
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
        drawPath(mouth, Color(0xFF8BEAFF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2.dp.toPx()))
    }
}

@Composable
private fun ChatPage(
    modifier: Modifier, messages: List<ChatLine>, draft: String, onDraft: (String) -> Unit,
    onSend: (String, List<Uri>) -> Unit, openSettings: () -> Unit, configured: Boolean, petName: String,
    petMood: String, onMood: (String) -> Unit
) {
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> selectedFiles = (selectedFiles + uris).distinct() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedFiles = (selectedFiles + uris).distinct() }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onDraft(if (draft.isBlank()) spoken else "$draft $spoken")
    }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Agent 对话", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(if (configured) "$petName 已读取模型配置" else "先配置模型，才能启用真实 AI 回复", color = Muted, fontSize = 12.sp)
        if (!configured) {
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.fillMaxWidth().clickable(onClick = openSettings), color = Color(0xFF2B2A22), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("尚未配置模型 · 点击前往设置", color = Color(0xFFFFD58B), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("设置  ›", color = Cyan, fontSize = 12.sp)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatFace(Modifier.width(220.dp).height(170.dp), petMood)
                    Text("嗨，我是$petName。", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("你可以在这里和我聊天。", color = Muted, fontSize = 13.sp)
                }
            }
            items(messages) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (line.fromUser) Color(0xFF1D4560) else Panel2, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.widthIn(max = 290.dp).padding(horizontal = 14.dp, vertical = 11.dp)) {
                            if (line.text.isNotBlank()) Text(line.text, color = Color.White, fontSize = 14.sp)
                            line.attachments.forEach { name -> Text("📎 $name", color = Cyan, fontSize = 11.sp) }
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
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(18.dp)) {
                BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp).fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner -> if (draft.isEmpty()) Text("发送消息给萌宠…", color = Muted, fontSize = 14.sp, maxLines = 1); inner() })
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", color = Cyan, fontSize = 22.sp, modifier = Modifier.clickable { filePicker.launch(arrayOf("*/*")) }.padding(horizontal = 8.dp))
                Text("▧", color = Cyan, fontSize = 18.sp, modifier = Modifier.clickable { imagePicker.launch("image/*") }.padding(horizontal = 8.dp))
            }
            Text("🎙", fontSize = 20.sp, modifier = Modifier.clickable {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话")
                }
                runCatching { voiceInput.launch(intent) }
            }.padding(horizontal = 7.dp))
            Button(onClick = { onSend(draft, selectedFiles); selectedFiles = emptyList() }, enabled = draft.isNotBlank() || selectedFiles.isNotEmpty(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)) { Text("发送") }
        }
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier, provider: String, onProvider: (String) -> Unit,
    endpoint: String, onEndpoint: (String) -> Unit, model: String, onModel: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit, onSave: () -> Unit, saved: Boolean
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("模型配置", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("连接本地模型或在线大模型服务", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Text("服务类型", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("本地模型", "OpenAI 兼容接口").forEach { item ->
                FilterChip(selected = provider == item, onClick = { onProvider(item) }, label = { Text(item) })
            }
        }
        Spacer(Modifier.height(14.dp))
        ConfigField("API / 服务地址", endpoint, onEndpoint, "例如 http://127.0.0.1:1234/v1")
        ConfigField("模型名称", model, onModel, "例如 qwen2.5 或 gpt-4o-mini")
        ConfigField("API Key（本地服务可留空）", apiKey, onApiKey, "输入密钥", secret = true)
        Spacer(Modifier.height(8.dp))
        Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
            Text("提示：密钥目前保存在应用私有配置中。此版本已建立配置入口，模型请求与密钥加密存储仍需后续接入。", Modifier.padding(13.dp), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("保存模型配置", fontWeight = FontWeight.Bold) }
        if (saved) Text("配置已保存", color = Color(0xFF76E0B1), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ConfigField(label: String, value: String, onValue: (String) -> Unit, hint: String, secret: Boolean = false) {
    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
