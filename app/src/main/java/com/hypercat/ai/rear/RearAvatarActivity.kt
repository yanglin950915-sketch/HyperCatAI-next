package com.hypercat.ai.rear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import com.hypercat.ai.avatar.AvatarDeviceStateStore
import com.hypercat.ai.avatar.AvatarStatusBadges
import com.hypercat.ai.avatar.Live2DModelStore
import com.hypercat.ai.avatar.Live2DView
import com.hypercat.ai.avatar.Live2DControllerHub

/** Direct rear-display host. Uses the same Live2D renderer and Agent state as the main screen. */
class RearAvatarActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening by mutableStateOf(false)
    private var live2dSpeaking by mutableStateOf(false)
    private var lastSpokenReply = ""
    private var pendingPermission = false
    private var rearWebView: WebView? = null
    // Measured from the active rear display's real Android DisplayCutout data.
    // No model-name or device-specific pixel constants are used.
    private var rearSafeTopRatio by mutableFloatStateOf(0f)
    private var rearSafeLeftRatio by mutableFloatStateOf(0f)
    private var rearSafeRightRatio by mutableFloatStateOf(0f)
    private var rearSafeBottomRatio by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AvatarStateStore.read(applicationContext)
        AvatarDeviceStateStore.start(applicationContext)
        AvatarDeviceStateStore.resume(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fun updateRearSafeArea() {
            val rearDisplay = window.decorView.display
                ?: getSystemService(DisplayManager::class.java).getDisplay(display?.displayId ?: Display.DEFAULT_DISPLAY)
            if (rearDisplay == null) return
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { rearDisplay.getRealMetrics(it) }
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            if (width <= 0 || height <= 0) return

            val cutout = rearDisplay.cutout
            val rects = cutout?.boundingRects.orEmpty()
            val left = maxOf(
                cutout?.safeInsetLeft ?: 0,
                rects.filter { it.left <= width * 0.15f }.maxOfOrNull { it.right } ?: 0
            )
            val top = maxOf(
                cutout?.safeInsetTop ?: 0,
                rects.filter { it.top <= height * 0.15f }.maxOfOrNull { it.bottom } ?: 0
            )
            val right = maxOf(
                cutout?.safeInsetRight ?: 0,
                rects.filter { it.right >= width * 0.85f }.maxOfOrNull { width - it.left } ?: 0
            )
            val bottom = maxOf(
                cutout?.safeInsetBottom ?: 0,
                rects.filter { it.bottom >= height * 0.85f }.maxOfOrNull { height - it.top } ?: 0
            )

            // Xiaomi 17 Pro Max community projects document a left-side camera island
            // occupying roughly 30% of the rear panel; layouts start at 32%.
            // Use the measured DisplayCutout when HyperOS exposes it. Fall back only
            // for the positively identified 17 Pro / 17 Pro Max rear panel.
            val model = android.os.Build.MODEL.orEmpty().uppercase(Locale.ROOT)
            val device = android.os.Build.DEVICE.orEmpty().lowercase(Locale.ROOT)
            val is17ProMax = device == "popsicle" || model.contains("2509FPN0BC")
            val is17Pro = device == "pandora"
            // Xiaomi 18 Pro rear panel: 912x596; 18 Pro Max: 976x596.
            // Prefer model/codename identification, with the documented panel dimensions
            // as a fallback for regional model strings that do not contain the marketing name.
            val is18ProMax = model.contains("18 PRO MAX") ||
                (width >= height && width in 960..992 && height in 580..612)
            val is18Pro = (model.contains("18 PRO") && !is18ProMax) ||
                (width >= height && width in 896..928 && height in 580..612)
            val isKnownRear = is17ProMax || is17Pro || is18ProMax || is18Pro
            val landscape = width >= height

            var safeLeft = left
            var safeTop = top
            var safeRight = right
            var safeBottom = bottom
            if (isKnownRear && left == 0 && top == 0 && right == 0 && bottom == 0) {
                when (rearDisplay.rotation) {
                    Surface.ROTATION_0 -> if (landscape) safeLeft = (width * 0.38f).toInt()
                    Surface.ROTATION_90 -> if (landscape) safeBottom = (height * 0.38f).toInt()
                    Surface.ROTATION_180 -> if (landscape) safeRight = (width * 0.38f).toInt()
                    Surface.ROTATION_270 -> if (landscape) safeTop = (height * 0.38f).toInt()
                }
            }

            // Add a visual buffer beyond the reported cutout edge so the character
            // stays clear of the camera island and is centered in the remaining panel.
            if (isKnownRear && (left > 0 || top > 0 || right > 0 || bottom > 0)) {
                val cameraBuffer = (if (landscape) width else height) * 0.05f
                when (rearDisplay.rotation) {
                    Surface.ROTATION_0 -> if (landscape) safeLeft += cameraBuffer.toInt()
                    Surface.ROTATION_90 -> if (landscape) safeBottom += cameraBuffer.toInt()
                    Surface.ROTATION_180 -> if (landscape) safeRight += cameraBuffer.toInt()
                    Surface.ROTATION_270 -> if (landscape) safeTop += cameraBuffer.toInt()
                }
            }

            rearSafeLeftRatio = (safeLeft.toFloat() / width).coerceIn(0f, 0.95f)
            rearSafeTopRatio = (safeTop.toFloat() / height).coerceIn(0f, 0.95f)
            rearSafeRightRatio = (safeRight.toFloat() / width).coerceIn(0f, 0.95f)
            rearSafeBottomRatio = (safeBottom.toFloat() / height).coerceIn(0f, 0.95f)
            android.util.Log.i(
                "HyperCatRear",
                "displayId=${rearDisplay.displayId}, size=${width}x${height}, rotation=${rearDisplay.rotation}, " +
                    "model=${android.os.Build.MODEL}, device=${android.os.Build.DEVICE}, " +
                    "cutout=${cutout?.boundingRects}, safeInsets=${left},${top},${right},${bottom}, " +
                    "applied=${safeLeft},${safeTop},${safeRight},${safeBottom}"
            )
        }
        window.decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateRearSafeArea() }
        updateRearSafeArea()
        activeActivity = this
        tts = TextToSpeech(this, this)
        setContent {
            val avatar by AvatarStateStore.state.collectAsState()
            val deviceState by AvatarDeviceStateStore.state.collectAsState()
            val live2dModel = androidx.compose.runtime.remember { Live2DModelStore.currentRear(this@RearAvatarActivity) }
            Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
                if (live2dModel != null) {
                    Live2DView(
                        model = live2dModel,
                        modifier = Modifier.fillMaxSize().zIndex(0f),
                        mood = avatar.mood,
                        speaking = live2dSpeaking,
                        musicPlaying = deviceState.music,
                        halfBody = false,
                        safeTopRatio = rearSafeTopRatio,
                        safeLeftRatio = rearSafeLeftRatio,
                        safeRightRatio = rearSafeRightRatio,
                        safeBottomRatio = rearSafeBottomRatio,
                        rearDisplay = true,
                        showInterface = false,
                        onGesture = { action, _ -> RearAgentInteractionDispatcher.enqueue(this@RearAvatarActivity, action) }
                    )
                } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.allowContentAccess = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            addJavascriptInterface(RearMamlBridge(this@RearAvatarActivity), "maml")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    applyAvatarState(view, avatar.avatarName, avatar.avatarId, avatar.mood, avatar.reply, avatar.backgroundColor)
                                }
                            }
                            rearWebView = this
                            loadUrl("file:///android_asset/web/index.html")
                        }
                    },
                    update = { view ->
                        applyAvatarState(view, avatar.avatarName, avatar.avatarId, avatar.mood, avatar.reply, avatar.backgroundColor)
                    }
                )
                }
                AvatarStatusBadges(Modifier.align(Alignment.TopCenter).padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().zIndex(2f))
                if (live2dModel == null) Button(
                    onClick = { startVoiceInput() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2878F0))
                ) {
                    Text(if (listening) "正在聆听…" else "🎙 说话", color = Color.White, fontSize = 13.sp)
                }
            }
            LaunchedEffect(avatar.updatedAt, avatar.reply) {
                val reply = avatar.reply.trim()
                if (reply.isNotBlank() && reply != lastSpokenReply &&
                    reply != "让我想一想……" && reply != "我准备休息啦。" &&
                    reply != "我听到啦，正在回答你……" && !listening
                ) {
                    lastSpokenReply = reply
                    speak(reply)
                }
            }
        }
    }

    private fun applyAvatarState(view: WebView, name: String, avatar: String, mood: String, reply: String, backgroundColor: String) {
        val script = "window.__setAgentState && window.__setAgentState(" +
            "${org.json.JSONObject.quote(name)}," +
            "${org.json.JSONObject.quote(avatar)}," +
            "${org.json.JSONObject.quote(mood)}," +
            "${org.json.JSONObject.quote(reply)}," +
            "${org.json.JSONObject.quote(backgroundColor)});"
        view.evaluateJavascript(script, null)
    }

    class RearMamlBridge(private val activity: RearAvatarActivity) {
        @JavascriptInterface
        fun getStringByName(name: String): String {
            val state = AvatarStateStore.state.value
            return when (name) {
                "avatarName", "petName" -> state.avatarName
                "avatarId", "petEmoji" -> state.avatarId
                "mood" -> state.mood
                "reply" -> state.reply
                "backgroundColor" -> state.backgroundColor
                "enabled" -> state.enabled.toString()
                "updatedAt" -> state.updatedAt.toString()
                "safeTopRatio" -> activity.rearSafeTopRatio.toString()
                else -> ""
            }
        }

        @JavascriptInterface
        fun doAction(action: String) {
            activity.runOnUiThread {
                if (action in setOf(
                    "avatar_touch", "avatar_happy", "avatar_long_press", "avatar_drag", "avatar_thinking", "avatar_rest",
                    "pet_touch", "pet_happy", "pet_long_press", "pet_drag", "pet_thinking", "pet_rest"
                )) {
                    RearAgentInteractionDispatcher.enqueue(activity, action)
                }
            }
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        launchRecognizer()
    }

    private fun launchRecognizer() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            val current = AvatarStateStore.read(this)
            AvatarStateStore.update(this, current.avatarName, current.avatarId, "sad", "当前系统没有可用的语音识别服务。")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请和虚拟形象说话")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        runCatching { startActivityForResult(intent, REQUEST_SPEECH) }.onFailure {
            listening = false
            val current = AvatarStateStore.read(this)
            AvatarStateStore.update(this, current.avatarName, current.avatarId, "sad", "无法启动语音识别，请检查系统语音服务。")
        }
    }

    @Deprecated("Legacy activity result is used for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        listening = false
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (resultCode == RESULT_OK && spoken.isNotBlank()) {
            RearAgentInteractionDispatcher.enqueue(this, "avatar_voice", spoken)
        } else {
            val current = AvatarStateStore.read(this)
            AvatarStateStore.update(this, current.avatarName, current.avatarId, "normal", "没有听清，再点麦克风试一次吧。")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && pendingPermission) {
            pendingPermission = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) launchRecognizer()
            else {
                val current = AvatarStateStore.read(this)
                AvatarStateStore.update(this, current.avatarName, current.avatarId, "sad", "需要麦克风权限才能和我说话。")
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.SIMPLIFIED_CHINESE
            tts?.setSpeechRate(1.0f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { live2dSpeaking = true; Live2DControllerHub.setSpeaking(true) }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { live2dSpeaking = false; Live2DControllerHub.setSpeaking(false) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { live2dSpeaking = false; Live2DControllerHub.setSpeaking(false) }
                }
            })
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, null, "rear-agent-reply")
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        rearWebView?.apply {
            stopLoading()
            removeJavascriptInterface("maml")
            loadUrl("about:blank")
            destroy()
        }
        rearWebView = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        AvatarDeviceStateStore.pause()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 8101
        private const val REQUEST_SPEECH = 8102
        @Volatile private var activeActivity: RearAvatarActivity? = null
        fun hideIfVisible() { activeActivity?.runOnUiThread { activeActivity?.finish() } }
    }
}
