package com.hypercat.ai.rear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen pet surface launched directly on the Xiaomi rear display. */
class RearPetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetStateStore.read(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeActivity = this
        setContent {
            val pet by PetStateStore.state.collectAsState()
            Box(
                Modifier.fillMaxSize().background(Color(0xFF080A10)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                        .pointerInput(pet.mood, pet.reply) {
                            detectTapGestures(
                                onTap = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_touch") },
                                onLongPress = { RearAgentInteractionDispatcher.enqueue(this@RearPetActivity, "pet_long_press") }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(196.dp),
                        shape = RoundedCornerShape(56.dp),
                        color = Color(0xFF151B29)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(pet.petEmoji.ifBlank { "🐱" }, fontSize = 112.sp)
                        }
                    }
                    Text(
                        pet.petName,
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    Text(
                        pet.reply,
                        color = Color(0xFFD6DEEA),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "轻触互动 · 长按对话",
                        color = Color(0xFF8996AA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 22.dp)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeActivity: RearPetActivity? = null

        fun hideIfVisible() {
            activeActivity?.runOnUiThread { activeActivity?.finish() }
        }
    }
}
