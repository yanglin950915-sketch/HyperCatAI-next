package com.hypercat.ai.rear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AvatarState(
    val avatarName: String,
    val avatarId: String,
    val mood: String,
    val reply: String,
    val updatedAt: Long,
    val enabled: Boolean = true,
    val userText: String = "",
    val backgroundColor: String = "#080B12"
)

/** Shared virtual-avatar state for the Agent UI and the Xiaomi rear-screen provider. */
object AvatarStateStore {
    private const val PREFS = "hypercat_avatar"
    private const val LEGACY_PREFS = "hypercat_rear_pet"
    private const val KEY_NAME = "avatar_name"
    private const val KEY_ID = "avatar_id"
    private const val KEY_MOOD = "mood"
    private const val KEY_REPLY = "reply"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_ENABLED = "rear_avatar_enabled"
    private const val KEY_USER_TEXT = "user_text"
    private const val KEY_BACKGROUND = "background_color"

    // The old URI remains readable by already-deployed MAML cards.
    val AVATAR_URI: Uri = Uri.parse("content://com.hypercat.ai.rear.provider/avatar-state")
    val LEGACY_URI: Uri = Uri.parse("content://com.hypercat.ai.rear.provider/pet-state")

    private val mutableState = MutableStateFlow(
        AvatarState("小橘", "sakura", "normal", "你好！我是你的 AI 虚拟形象。", 0L, false)
    )
    val state: StateFlow<AvatarState> = mutableState.asStateFlow()

    fun update(
        context: Context,
        name: String,
        avatarId: String,
        mood: String,
        reply: String,
        enabled: Boolean? = null,
        userText: String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_TEXT, "").orEmpty(),
        backgroundColor: String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BACKGROUND, "#080B12").orEmpty()
    ) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = AvatarState(
            avatarName = name.ifBlank { "小橘" }.take(16),
            avatarId = avatarId.ifBlank { "sakura" }.take(32),
            mood = mood.ifBlank { "normal" }.take(24),
            reply = reply.take(100),
            updatedAt = System.currentTimeMillis(),
            enabled = enabled ?: prefs.getBoolean(KEY_ENABLED, legacy.getBoolean("rear_pet_enabled", false)),
            userText = userText.take(1000),
            backgroundColor = if (backgroundColor.matches(Regex("^#[0-9A-Fa-f]{6}$"))) backgroundColor else "#080B12"
        )
        val saved = prefs.edit()
            .putString(KEY_NAME, next.avatarName)
            .putString(KEY_ID, next.avatarId)
            .putString(KEY_MOOD, next.mood)
            .putString(KEY_REPLY, next.reply)
            .putLong(KEY_UPDATED, next.updatedAt)
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_USER_TEXT, next.userText)
            .putString(KEY_BACKGROUND, next.backgroundColor)
            .commit()
        check(saved) { "无法保存背屏虚拟形象状态" }
        mutableState.value = next
        context.contentResolver.notifyChange(AVATAR_URI, null)
        context.contentResolver.notifyChange(LEGACY_URI, null)
    }

    fun setBackgroundColor(context: Context, color: String) {
        val current = read(context)
        update(context, current.avatarName, current.avatarId, current.mood, current.reply, current.enabled, current.userText, color)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val current = read(context)
        update(context, current.avatarName, current.avatarId, current.mood, current.reply, enabled)
    }

    fun read(context: Context): AvatarState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val result = AvatarState(
            avatarName = prefs.getString(KEY_NAME, null) ?: legacy.getString("pet_name", "小橘") ?: "小橘",
            avatarId = prefs.getString(KEY_ID, null) ?: legacy.getString("pet_emoji", "sakura") ?: "sakura",
            mood = prefs.getString(KEY_MOOD, null) ?: legacy.getString(KEY_MOOD, "normal") ?: "normal",
            reply = prefs.getString(KEY_REPLY, null) ?: legacy.getString(KEY_REPLY, "你好！我是你的 AI 虚拟形象。") ?: "",
            updatedAt = prefs.getLong(KEY_UPDATED, legacy.getLong(KEY_UPDATED, 0L)),
            enabled = prefs.getBoolean(KEY_ENABLED, legacy.getBoolean("rear_pet_enabled", false)),
            userText = prefs.getString(KEY_USER_TEXT, legacy.getString(KEY_USER_TEXT, "")) ?: "",
            backgroundColor = prefs.getString(KEY_BACKGROUND, legacy.getString(KEY_BACKGROUND, "#080B12")) ?: "#080B12"
        )
        mutableState.value = result
        return result
    }
}
