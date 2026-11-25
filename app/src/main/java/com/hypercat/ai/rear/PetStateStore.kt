package com.hypercat.ai.rear

import android.content.Context

data class RearPetState(
    val petName: String,
    val petEmoji: String,
    val mood: String,
    val reply: String,
    val updatedAt: Long
)

/** Small local state bridge shared by the main UI and the MAML ContentProvider. */
object PetStateStore {
    private const val PREFS = "hypercat_rear_pet"
    private const val KEY_NAME = "pet_name"
    private const val KEY_EMOJI = "pet_emoji"
    private const val KEY_MOOD = "mood"
    private const val KEY_REPLY = "reply"
    private const val KEY_UPDATED = "updated_at"

    fun update(context: Context, name: String, emoji: String, mood: String, reply: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name)
            .putString(KEY_EMOJI, emoji)
            .putString(KEY_MOOD, mood)
            .putString(KEY_REPLY, reply.take(100))
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun read(context: Context): RearPetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return RearPetState(
            petName = prefs.getString(KEY_NAME, "小橘") ?: "小橘",
            petEmoji = prefs.getString(KEY_EMOJI, "🐈") ?: "🐈",
            mood = prefs.getString(KEY_MOOD, "IDLE") ?: "IDLE",
            reply = prefs.getString(KEY_REPLY, "你好！我是你的 AI 萌宠。") ?: "你好！我是你的 AI 萌宠。",
            updatedAt = prefs.getLong(KEY_UPDATED, 0L)
        )
    }
}
