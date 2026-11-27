package com.hypercat.ai.rear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives explicit rear-widget interactions and feeds them into the same Agent pet state. */
class RearPetInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PET_INTERACTION) return
        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        val current = PetStateStore.read(context)
        val next = when (action) {
            "pet_happy" -> "happy" to "收到背屏摸摸啦！我也很开心。"
            "pet_thinking" -> "thinking" to "我在认真思考，马上回来。"
            "pet_rest" -> "sleepy" to "我先休息一会儿，随时可以叫醒我。"
            else -> return
        }
        PetStateStore.update(context, current.petName, current.petEmoji, next.first, next.second)
    }

    companion object {
        const val ACTION_PET_INTERACTION = "com.hypercat.ai.rear.PET_INTERACTION"
        const val EXTRA_ACTION = "action"
    }
}
