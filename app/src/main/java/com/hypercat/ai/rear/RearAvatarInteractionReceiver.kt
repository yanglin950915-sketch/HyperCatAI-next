package com.hypercat.ai.rear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Converts a verified native MAML rear-screen gesture into a durable Agent interaction. */
class RearAvatarInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_AVATAR_INTERACTION, ACTION_PET_INTERACTION, ACTION_MEDIA_CONTROL)) return

        // getSentFromUid is available from Android 14 (API 34). Fail closed on
        // older releases rather than accepting spoofable exported broadcasts.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val senderUid = sentFromUid
        if (!RearCallerVerifier.isTrustedUid(context, senderUid)) return

        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        if (action in MEDIA_ACTIONS) {
            com.hypercat.ai.avatar.AvatarDeviceStateStore.handleMediaAction(context, action)
            return
        }
        if (action !in SUPPORTED_ACTIONS) return
        RearAgentInteractionDispatcher.enqueue(context, action)
    }

    companion object {
        const val ACTION_AVATAR_INTERACTION = "com.hypercat.ai.rear.AVATAR_INTERACTION"
        const val ACTION_MEDIA_CONTROL = "com.hypercat.ai.rear.MEDIA_CONTROL"
        private val MEDIA_ACTIONS = setOf("media_toggle", "media_next", "media_previous", "music_settings")
        @Deprecated("Legacy MAML action retained for already-deployed cards")
        const val ACTION_PET_INTERACTION = "com.hypercat.ai.rear.PET_INTERACTION"
        const val EXTRA_ACTION = "action"
        private val SUPPORTED_ACTIONS = setOf(
            "avatar_touch", "avatar_happy", "avatar_long_press", "avatar_drag", "avatar_thinking", "avatar_rest",
            "pet_touch", "pet_happy", "pet_long_press", "pet_drag", "pet_thinking", "pet_rest"
        )
    }
}
