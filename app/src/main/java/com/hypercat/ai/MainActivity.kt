package com.hypercat.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypercat.ai.pet.PetCatalog
import com.hypercat.ai.pet.PetProfile
import com.hypercat.ai.rear.PetStateStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HyperCatApp() }
    }
}

@Composable
private fun HyperCatApp() {
    val context = LocalContext.current
    var selectedPet by remember { mutableStateOf(PetCatalog.all.first()) }
    var confirmedPet by remember { mutableStateOf(PetCatalog.all.first()) }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("选择萌宠后，点击确认更换，背屏才会更新。") }

    LaunchedEffect(Unit) {
        val saved = PetStateStore.read(context)
        val restored = PetCatalog.all.firstOrNull { it.name == saved.petName } ?: PetCatalog.all.first()
        selectedPet = restored
        confirmedPet = restored
        loaded = true
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF91A7FF), secondary = Color(0xFF75E0C1), background = Color(0xFF0B1020), surface = Color(0xFF19243B))) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A294A), Color(0xFF0B1020)))).padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text("HYPERCAT AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(5.dp))
            Text("背屏萌宠", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("选择一个伙伴，让它出现在手机背屏。", color = Color(0xFFB5BED3), fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF19243B), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前选择", color = Color(0xFFB7C5EA), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(selectedPet.emoji, fontSize = 76.sp)
                    Text(selectedPet.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(selectedPet.description, color = Color(0xFFB5BED3), fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            PetStateStore.update(context, selectedPet.name, selectedPet.emoji, "IDLE", "${selectedPet.name} 已准备好，在背屏等你！")
                            confirmedPet = selectedPet
                            status = "已确认更换为${selectedPet.name}。背屏模板会自动刷新，请稍等约 1–2 秒。"
                        },
                        enabled = loaded,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (selectedPet.id == confirmedPet.id) "确认并同步到背屏" else "确认更换萌宠", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color(0xFF9FB1D6), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("选择萌宠", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(PetCatalog.all) { pet ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPet = pet
                            status = if (pet.id == confirmedPet.id) "${pet.name} 已是背屏当前萌宠。" else "已选择${pet.name}，请点击上方确认按钮完成更换。"
                        },
                        color = if (selectedPet.id == pet.id) Color(0xFF344B7A) else Color(0xFF19243B),
                        shape = RoundedCornerShape(18.dp),
                        border = if (selectedPet.id == pet.id) BorderStroke(1.dp, Color(0xFF91A7FF)) else null
                    ) {
                        Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(pet.emoji, fontSize = 38.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(pet.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (selectedPet.id == pet.id) "已选择" else if (confirmedPet.id == pet.id) "背屏当前" else pet.description,
                                color = Color(0xFF9AA8C2), fontSize = 10.sp, maxLines = 1
                            )
                        }
                    }
                }
            }
            Text("背屏通过 MAML 模板读取 HyperCat AI 数据。", color = Color(0xFF77839E), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
