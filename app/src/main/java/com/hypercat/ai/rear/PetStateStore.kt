package com.hypercat.ai.rear

import android.content.Context
import android.net.Uri

data class RearPetState(
    val petName: String,
    val petEmoji: String,
    val mood: String,
    val reply: String,
    val updatedAt: Long
)

/** Local state bridge shared by the main UI and the MAML ContentProvider. */
object PetStateStore {
    private const val PREFS = "hypercat_rear_pet"
    private const val KEY_NAME = "pet_name"
    private const val KEY_EMOJI = "pet_emoji"
    private const val KEY_MOOD = "mood"
    private const val KEY_REPLY = "reply"
    private const val KEY_UPDATED = "updated_at"
    private val PET_URI = Uri.parse("content://uriroute/data?group=hypercat&name=pet-state")

    fun update(context: Context, name: String, emoji: String, mood: String, reply: String) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name)
            .putString(KEY_EMOJI, emoji)
            .putString(KEY_MOOD, mood)
            .putString(KEY_REPLY, reply.take(100))
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .commit()
        // Notify MAML immediately; the one-second MAML refresh remains as a fallback.
        if (saved) context.contentResolver.notifyChange(PET_URI, null)
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
