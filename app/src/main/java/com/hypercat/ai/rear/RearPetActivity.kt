package com.hypercat.ai.rear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

/** Rear display UI: character, reply bubble, touch actions and voice conversation. */
class RearPetActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening by mutableStateOf(false)
    private var lastSpokenReply = ""
    private var pendingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetStateStore.read(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeActivity = this
        tts = TextToSpeech(this, this)
        setContent {
            val pet by PetStateStore.state.collectAsState()
            Box(Modifier.fillMaxSize().background(Color(0xFF090D16)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("HYPERCAT AI", color = Color(0xFF9BAAC0), fontSize = 10.sp, letterSpacing = 2.sp)
                    Surface(Modifier.padding(top = 8.dp).size(172.dp), shape = CircleShape, color = Color(0xFF17243A)) {
                        Box(contentAlignment = Alignment.Center) { Text(pet.petEmoji.ifBlank { "🌸" }, fontSize = 100.sp) }
                    }
                    Text(pet.petName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Surface(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF17243A)
                    ) {
                        Text(
                            pet.reply.ifBlank { "你好！和我说说话吧。" },
                            color = Color(0xFFEAF1FB),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    Text(if (listening) "正在聆听…" else "轻触角色互动 · 长按开始对话", color = Color(0xFF8996AA), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { startVoiceInput() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2878F0))
                        ) { Text(if (listening) "聆听中…" else "🎙 说话", color = Color.White) }
                        Button(
                            onClick = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_thinking") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263449))
                        ) { Text("思考", color = Color.White) }
                        Button(
                            onClick = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_rest") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263449))
                        ) { Text("休息", color = Color.White) }
                    }
                    // Tap and long-press are kept separate from the action buttons.
                    Box(Modifier.fillMaxWidth().weight(1f).pointerInput(pet.mood, pet.reply) {
                        detectTapGestures(
                            onTap = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_touch") },
                            onLongPress = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_long_press") }
                        )
                    })
                }
            }
            androidx.compose.runtime.LaunchedEffect(pet.updatedAt, pet.reply) {
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
            PetStateStore.update(this, PetStateStore.read(this).petName, PetStateStore.read(this).petEmoji, "sad", "当前系统没有可用的语音识别服务。")
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
            PetStateStore.update(this, PetStateStore.read(this).petName, PetStateStore.read(this).petEmoji, "sad", "无法启动语音识别，请检查系统语音服务。")
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
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, null, "rear-agent-reply")
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
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
