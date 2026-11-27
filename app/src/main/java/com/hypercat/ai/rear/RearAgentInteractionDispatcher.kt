package com.hypercat.ai.rear

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Shared entry point for touch and voice interactions from the rear display. */
object RearAgentInteractionDispatcher {
    private const val UNIQUE_WORK_NAME = "hypercat-rear-agent-interactions"
    private val supportedActions = setOf("avatar_touch", "avatar_happy", "avatar_long_press", "avatar_drag", "avatar_thinking", "avatar_rest", "avatar_voice")

    fun enqueue(context: Context, action: String): Boolean = enqueue(context, action, "")

    fun enqueue(context: Context, action: String, userText: String): Boolean {
        // Normalize historical MAML action names so already-deployed cards keep working.
        val normalizedAction = if (action.startsWith("pet_")) "avatar_" + action.removePrefix("pet_") else action
        if (normalizedAction !in supportedActions) return false
        if (normalizedAction == "avatar_voice" && userText.isBlank()) return false
        val current = AvatarStateStore.read(context)
        val immediateMood = when (normalizedAction) {
            "avatar_thinking" -> "thinking"
            "avatar_rest" -> "sleepy"
            "avatar_voice" -> "thinking"
            "avatar_drag" -> "curious"
            else -> "happy"
        }
        val immediateReply = when (normalizedAction) {
            "avatar_thinking" -> "让我想一想……"
            "avatar_rest" -> "我准备休息啦。"
            "avatar_long_press" -> "我在听，你想和我聊什么？"
            "avatar_voice" -> "我听到啦，正在回答你……"
            "avatar_drag" -> "咦？你在带动虚拟形象吗？"
            else -> "收到你的虚拟形象互动啦！"
        }
        AvatarStateStore.update(context, current.avatarName, current.avatarId, immediateMood, immediateReply, userText = userText)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<RearAgentInteractionWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                RearAgentInteractionWorker.KEY_ACTION to normalizedAction,
                RearAgentInteractionWorker.KEY_USER_TEXT to userText.take(1000)
            )).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return true
    }
}
