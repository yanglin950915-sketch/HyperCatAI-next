package com.hypercat.ai.rear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RearPetState(
    val petName: String,
    val petEmoji: String,
    val mood: String,
    val reply: String,
    val updatedAt: Long,
    val enabled: Boolean = true,
    val userText: String = ""
)

/** Shared state bridge for the exported rear-screen provider and the foreground Agent UI. */
object PetStateStore {
    private const val PREFS = "hypercat_rear_pet"
    private const val KEY_NAME = "pet_name"
    private const val KEY_EMOJI = "pet_emoji"
    private const val KEY_MOOD = "mood"
    private const val KEY_REPLY = "reply"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_ENABLED = "rear_pet_enabled"
    private const val KEY_USER_TEXT = "user_text"
    val PET_URI: Uri = Uri.parse("content://com.hypercat.ai.rear.provider/pet-state")

    private val mutableState = MutableStateFlow(
        RearPetState("小橘", "🤖", "normal", "你好！我是你的 AI 虚拟形象。", 0L, false)
    )
    val state: StateFlow<RearPetState> = mutableState.asStateFlow()

    fun update(context: Context, name: String, emoji: String, mood: String, reply: String, enabled: Boolean? = null, userText: String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_TEXT, "").orEmpty()) {
        val next = RearPetState(
            petName = name.ifBlank { "小橘" }.take(16),
            petEmoji = emoji.ifBlank { "🤖" }.take(8),
            mood = mood.ifBlank { "normal" }.take(24),
            reply = reply.take(100),
            updatedAt = System.currentTimeMillis(),
            enabled = enabled ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false),
            userText = userText.take(1000)
        )
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, next.petName)
            .putString(KEY_EMOJI, next.petEmoji)
            .putString(KEY_MOOD, next.mood)
            .putString(KEY_REPLY, next.reply)
            .putLong(KEY_UPDATED, next.updatedAt)
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_USER_TEXT, next.userText)
            .commit()
        check(saved) { "无法保存背屏虚拟形象状态" }
        mutableState.value = next
        context.contentResolver.notifyChange(PET_URI, null)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val current = read(context)
        update(context, current.petName, current.petEmoji, current.mood, current.reply, enabled)
    }

    fun read(context: Context): RearPetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = RearPetState(
            petName = prefs.getString(KEY_NAME, "小橘") ?: "小橘",
            petEmoji = prefs.getString(KEY_EMOJI, "🤖") ?: "🤖",
            mood = prefs.getString(KEY_MOOD, "normal") ?: "normal",
            reply = prefs.getString(KEY_REPLY, "你好！我是你的 AI 虚拟形象。") ?: "",
            updatedAt = prefs.getLong(KEY_UPDATED, 0L),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            userText = prefs.getString(KEY_USER_TEXT, "") ?: ""
        )
        mutableState.value = result
        return result
    }
}
