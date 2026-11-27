package com.hypercat.ai.rear

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Shared in-app entry point for main-screen and rear-screen pet gestures. */
object RearAgentInteractionDispatcher {
    private const val UNIQUE_WORK_NAME = "hypercat-rear-agent-interactions"
    private val supportedActions = setOf("pet_touch", "pet_happy", "pet_long_press", "pet_thinking", "pet_rest")

    fun enqueue(context: Context, action: String): Boolean {
        if (action !in supportedActions) return false
        val current = PetStateStore.read(context)
        val immediateMood = when (action) {
            "pet_thinking" -> "thinking"
            "pet_rest" -> "sleepy"
            else -> "happy"
        }
        val immediateReply = when (action) {
            "pet_thinking" -> "让我想一想……"
            "pet_rest" -> "我准备休息啦。"
            "pet_long_press" -> "我在听，你想和我聊什么？"
            else -> "嘿嘿，你摸到我啦！"
        }
        PetStateStore.update(context, current.petName, current.petEmoji, immediateMood, immediateReply)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RearAgentInteractionWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(RearAgentInteractionWorker.KEY_ACTION to action))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return true
    }
}
