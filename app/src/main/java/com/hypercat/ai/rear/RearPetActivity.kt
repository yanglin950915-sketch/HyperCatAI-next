package com.hypercat.ai.rear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import com.hypercat.ai.pet.Live2DModelStore
import com.hypercat.ai.pet.Live2DView
import com.hypercat.ai.pet.Live2DControllerHub

/** Direct rear-display host. Reuses the same offline anime renderer as the REAREye MAML card. */
class RearPetActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening by mutableStateOf(false)
    private var live2dSpeaking by mutableStateOf(false)
    private var lastSpokenReply = ""
    private var pendingPermission = false
    private var rearWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetStateStore.read(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeActivity = this
        tts = TextToSpeech(this, this)
        setContent {
            val pet by PetStateStore.state.collectAsState()
            val live2dModel = androidx.compose.runtime.remember { Live2DModelStore.current(this@RearPetActivity) }
            Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
                if (live2dModel != null) {
                    Live2DView(
                        model = live2dModel,
                        modifier = Modifier.fillMaxSize(),
                        mood = pet.mood,
                        speaking = live2dSpeaking,
                        halfBody = true,
                        onGesture = { action, _ -> RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, action) }
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
                            settings.allowContentAccess = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            addJavascriptInterface(RearMamlBridge(this@RearPetActivity), "maml")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    applyPetState(view, pet.petName, pet.petEmoji, pet.mood, pet.reply, pet.backgroundColor)
                                }
                            }
                            rearWebView = this
                            loadUrl("file:///android_asset/web/index.html")
                        }
                    },
                    update = { view ->
                        applyPetState(view, pet.petName, pet.petEmoji, pet.mood, pet.reply, pet.backgroundColor)
                    }
                )
                }
                if (live2dModel == null) Button(
                    onClick = { startVoiceInput() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2878F0))
                ) {
                    Text(if (listening) "正在聆听…" else "🎙 说话", color = Color.White, fontSize = 13.sp)
                }
            }
            LaunchedEffect(pet.updatedAt, pet.reply) {
                val reply = pet.reply.trim()
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

    private fun applyPetState(view: WebView, name: String, avatar: String, mood: String, reply: String, backgroundColor: String) {
        val script = "window.__setAgentState && window.__setAgentState(" +
            "${org.json.JSONObject.quote(name)}," +
            "${org.json.JSONObject.quote(avatar)}," +
            "${org.json.JSONObject.quote(mood)}," +
            "${org.json.JSONObject.quote(reply)}," +
            "${org.json.JSONObject.quote(backgroundColor)});"
        view.evaluateJavascript(script, null)
    }

    class RearMamlBridge(private val activity: RearPetActivity) {
        @JavascriptInterface
        fun getStringByName(name: String): String {
            val state = PetStateStore.state.value
            return when (name) {
                "petName" -> state.petName
                "petEmoji" -> state.petEmoji
                "mood" -> state.mood
                "reply" -> state.reply
                "backgroundColor" -> state.backgroundColor
                "enabled" -> state.enabled.toString()
                "updatedAt" -> state.updatedAt.toString()
                else -> ""
            }
        }

        @JavascriptInterface
        fun doAction(action: String) {
            activity.runOnUiThread {
                if (action in setOf("pet_touch", "pet_happy", "pet_long_press", "pet_drag", "pet_thinking", "pet_rest")) {
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
            val current = PetStateStore.read(this)
            PetStateStore.update(this, current.petName, current.petEmoji, "sad", "当前系统没有可用的语音识别服务。")
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
            val current = PetStateStore.read(this)
            PetStateStore.update(this, current.petName, current.petEmoji, "sad", "无法启动语音识别，请检查系统语音服务。")
        }
    }

    @Deprecated("Legacy activity result is used for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        listening = false
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (resultCode == RESULT_OK && spoken.isNotBlank()) {
            RearAgentInteractionDispatcher.enqueue(this, "pet_voice", spoken)
        } else {
            val current = PetStateStore.read(this)
            PetStateStore.update(this, current.petName, current.petEmoji, "normal", "没有听清，再点麦克风试一次吧。")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && pendingPermission) {
            pendingPermission = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) launchRecognizer()
            else {
                val current = PetStateStore.read(this)
                PetStateStore.update(this, current.petName, current.petEmoji, "sad", "需要麦克风权限才能和我说话。")
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
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 8101
        private const val REQUEST_SPEECH = 8102
        @Volatile private var activeActivity: RearPetActivity? = null
        fun hideIfVisible() { activeActivity?.runOnUiThread { activeActivity?.finish() } }
    }
}
