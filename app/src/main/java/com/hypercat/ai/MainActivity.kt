package com.hypercat.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypercat.ai.pet.PetCatalog
import com.hypercat.ai.pet.PetMood

class MainActivity : ComponentActivity() {
    fun launchPetOnRearDisplay(onStatus: (String) -> Unit) {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                onStatus("当前 Android 版本不支持多显示屏启动。")
                return
            }
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val options = android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(1)
            startActivity(intent, options.toBundle())
            onStatus("已请求在副显示屏启动。若背屏没有显示，说明系统未向第三方应用开放该显示屏。")
        } catch (e: Exception) {
            onStatus("副屏启动被系统拒绝或不可用：${e.message ?: "未知错误"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HyperCatApp() }
    }
}

@Composable
private fun HyperCatApp() {
    var selectedPet by remember { mutableStateOf(PetCatalog.all.first()) }
    var mood by remember { mutableStateOf(PetMood.IDLE) }
    var prompt by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("你好！我是你的 AI 萌宠伙伴。选择一个伙伴，或者告诉我你想做什么。") }
    var tab by remember { mutableIntStateOf(0) }
    var rearStatus by remember { mutableStateOf("背屏尚未连接") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val panel = Color(0xFF171F35)
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B9CFF), secondary = Color(0xFF75E0C1), background = Color(0xFF0B1020), surface = panel)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF182344), Color(0xFF0B1020)))).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HYPERCAT AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("你的随身 AI 萌宠", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Surface(color = Color(0xFF243451), shape = RoundedCornerShape(50)) {
                    Text("开发预览 0.1", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFFB9C8FF), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(Modifier.fillMaxWidth(), color = panel.copy(alpha = .94f), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("背屏伙伴 · ${selectedPet.name}", color = Color(0xFFB7C5EA), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(selectedPet.emoji, fontSize = 88.sp)
                    Text(selectedPet.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(selectedPet.description, color = Color(0xFFB5BED3), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color(0xFF273655), shape = RoundedCornerShape(20.dp)) {
                        Text(reply, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoodButton("开心", mood == PetMood.HAPPY) { mood = PetMood.HAPPY; reply = "耶！今天也要元气满满！" }
                        MoodButton("思考", mood == PetMood.THINKING) { mood = PetMood.THINKING; reply = "让我认真想一想……" }
                        MoodButton("睡觉", mood == PetMood.SLEEPING) { mood = PetMood.SLEEPING; reply = "Zzz…休息一下再出发。" }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PetCatalog.all.forEach { pet ->
                    Surface(color = if (selectedPet.id == pet.id) Color(0xFF384A78) else panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { selectedPet = pet; mood = PetMood.HAPPY; reply = "${pet.name} 已加入！" }) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(pet.emoji, fontSize = 27.sp)
                            Text(pet.name, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF202D49), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("小米背屏连接（实验功能）", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("原生背屏应用列表目前没有可供普通第三方应用直接注册的公开接口。可选用 Shizuku 尝试将萌宠界面启动到副屏。", color = Color(0xFFB5BED3), fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        (context as? MainActivity)?.launchPetOnRearDisplay { rearStatus = it }
                    }, modifier = Modifier.fillMaxWidth()) { Text("尝试启动到背屏") }
                    Text(rearStatus, color = Color(0xFFB5BED3), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("萌宠", "Agent", "模型").forEachIndexed { index, label ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(10.dp))
            when (tab) {
                0 -> Text("选择你的专属伙伴。下一阶段加入逐帧动画、动作状态机和背屏尺寸适配。", color = Color(0xFFB5BED3), fontSize = 13.sp)
                1 -> Text("Agent 工具按权限分级：普通操作、用户二次确认、系统授权。当前仅展示规划，尚未执行真实设备操作。", color = Color(0xFFB5BED3), fontSize = 13.sp)
                else -> Text("双引擎路线：本地 GGUF 模型（llama.cpp）+ 在线兼容 API。当前为接口骨架，尚未连接模型服务。", color = Color(0xFFB5BED3), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White, fontSize = 14.sp), decorationBox = { inner ->
                        if (prompt.isEmpty()) Text("和萌宠说句话…", color = Color(0xFF7F8AA5), fontSize = 14.sp)
                        inner()
                    })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (prompt.isNotBlank()) { reply = "已收到：$prompt（模型接入开发中）"; prompt = ""; mood = PetMood.THINKING } }) { Text("发送") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("HyperCat AI · 本地优先 · 权限透明", Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF77839E), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MoodButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFF4C5F91) else Color(0xFF26334D))) {
        Text(label, fontSize = 12.sp)
    }
}
