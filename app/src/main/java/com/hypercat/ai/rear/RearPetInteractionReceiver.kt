package com.hypercat.ai.rear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Converts a verified rear-screen gesture into a durable Agent interaction. */
class RearPetInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PET_INTERACTION) return

        // getSentFromUid is available from Android 14 (API 34). Fail closed on
        // older releases rather than accepting spoofable exported broadcasts.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val senderUid = sentFromUid
        if (!RearCallerVerifier.isTrustedUid(context, senderUid)) return

        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        if (action !in SUPPORTED_ACTIONS) return

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

        // WorkManager survives the UI being backgrounded or recreated. APPEND
        // preserves rapid successive touches instead of silently replacing them.
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RearAgentInteractionWorker>()
            .setConstraints(networkConstraints)
            .setInputData(workDataOf(RearAgentInteractionWorker.KEY_ACTION to action))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        const val ACTION_PET_INTERACTION = "com.hypercat.ai.rear.PET_INTERACTION"
        const val EXTRA_ACTION = "action"
        private const val UNIQUE_WORK_NAME = "hypercat-rear-agent-interactions"
        private val SUPPORTED_ACTIONS = setOf("pet_touch", "pet_happy", "pet_long_press", "pet_thinking", "pet_rest")
    }
}
