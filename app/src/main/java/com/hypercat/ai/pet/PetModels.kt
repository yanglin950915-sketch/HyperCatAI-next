package com.hypercat.ai.pet

enum class PetMood { IDLE, HAPPY, THINKING, SLEEPING, ANGRY, WALKING }

data class PetProfile(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String
)

object PetCatalog {
    val all = listOf(
        PetProfile("cat", "小橘", "🐈", "好奇、黏人，随时准备帮忙"),
        PetProfile("fox", "赤狐", "🦊", "机灵敏捷，擅长寻找答案"),
        PetProfile("panda", "团团", "🐼", "沉稳温和，认真处理任务"),
        PetProfile("rabbit", "雪球", "🐰", "活泼可爱，行动轻快"),
        PetProfile("dragon", "团子龙", "🐲", "勇敢可靠，守护你的设备")
    )
}
