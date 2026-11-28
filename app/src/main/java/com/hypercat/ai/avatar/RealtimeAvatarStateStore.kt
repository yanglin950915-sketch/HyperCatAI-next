package com.hypercat.ai.avatar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RealtimeAvatarPhase {
    IDLE, CONNECTING, LISTENING, RESPONDING, INTERRUPTED, FAILED
}

/** Shared realtime voice state consumed by the main and rear pet surfaces. */
object RealtimeAvatarStateStore {
    private val mutable = MutableStateFlow(RealtimeAvatarPhase.IDLE)
    val state = mutable.asStateFlow()
    fun set(phase: RealtimeAvatarPhase) {
        mutable.value = phase
    }
}
