package com.hypercat.ai.rear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

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

        RearAgentInteractionDispatcher.enqueue(context, action)
    }

    companion object {
        const val ACTION_PET_INTERACTION = "com.hypercat.ai.rear.PET_INTERACTION"
        const val EXTRA_ACTION = "action"
        private val SUPPORTED_ACTIONS = setOf("pet_touch", "pet_happy", "pet_long_press", "pet_thinking", "pet_rest")
    }
}
