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
        PetProfile("cat", "小橘", "🤖", "好奇型机器人，喜欢陪伴"),
        PetProfile("fox", "赤狐", "🤖", "机灵型机器人，擅长寻找答案"),
        PetProfile("panda", "团团", "🤖", "稳重型机器人，认真处理任务"),
        PetProfile("rabbit", "雪球", "🤖", "活泼型机器人，行动轻快"),
        PetProfile("dragon", "团子龙", "🤖", "勇敢型机器人，守护你的设备")
    )
}
