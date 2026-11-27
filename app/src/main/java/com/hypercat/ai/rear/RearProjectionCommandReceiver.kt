package com.hypercat.ai.rear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the Shizuku shell hide command for the secondary-display Activity. */
class RearProjectionCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hypercat.ai.rear.ACTION_HIDE_PROJECTION") {
            RearAvatarActivity.hideIfVisible()
        }
    }
}
