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
    private val supportedActions = setOf("pet_touch", "pet_happy", "pet_long_press", "pet_thinking", "pet_rest", "pet_voice")

    fun enqueue(context: Context, action: String): Boolean = enqueue(context, action, "")

    fun enqueue(context: Context, action: String, userText: String): Boolean {
        if (action !in supportedActions) return false
        if (action == "pet_voice" && userText.isBlank()) return false
        val current = PetStateStore.read(context)
        val immediateMood = when (action) {
            "pet_thinking" -> "thinking"
            "pet_rest" -> "sleepy"
            "pet_voice" -> "thinking"
            else -> "happy"
        }
        val immediateReply = when (action) {
            "pet_thinking" -> "让我想一想……"
            "pet_rest" -> "我准备休息啦。"
            "pet_long_press" -> "我在听，你想和我聊什么？"
            "pet_voice" -> "我听到啦，正在回答你……"
            else -> "嘿嘿，你摸到我啦！"
        }
        PetStateStore.update(context, current.petName, current.petEmoji, immediateMood, immediateReply, userText = userText)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<RearAgentInteractionWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                RearAgentInteractionWorker.KEY_ACTION to action,
                RearAgentInteractionWorker.KEY_USER_TEXT to userText.take(1000)
            )).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return true
    }
}
