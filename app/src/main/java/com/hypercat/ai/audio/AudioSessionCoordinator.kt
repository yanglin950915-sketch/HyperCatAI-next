package com.hypercat.ai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exclusive owner for microphone / voice playback sessions.
 * Speech-message capture and realtime calls must acquire the same coordinator
 * before opening their recorder, preventing concurrent microphone clients.
 */
class AudioSessionCoordinator private constructor(context: Context) {
    companion object {
        @Volatile private var instance: AudioSessionCoordinator? = null
        fun get(context: Context): AudioSessionCoordinator = instance ?: synchronized(this) {
            instance ?: AudioSessionCoordinator(context.applicationContext).also { instance = it }
        }
    }

    enum class Owner { REALTIME_CALL, CALL_CAPTURE, VOICE_MESSAGE, LEGACY_RECOGNIZER }

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock = Any()
    private var activeOwner: Owner? = null
    private var activeLease: Lease? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var focusRequest: AudioFocusRequest? = null

    fun acquire(
        owner: Owner,
        speakerphone: Boolean = false,
        onAudioFocusChange: ((Int) -> Unit)? = null,
    ): Lease {
        synchronized(lock) {
            check(activeLease == null) { "音频设备正在被 ${activeOwner} 使用，请先结束当前音频会话" }
            previousMode = audioManager.mode
            previousSpeaker = audioManager.isSpeakerphoneOn
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                onAudioFocusChange?.invoke(change)
            }
            var request: AudioFocusRequest? = null
            try {
                val result = if (Build.VERSION.SDK_INT >= 26) {
                    request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setOnAudioFocusChangeListener(listener)
                        .setAcceptsDelayedFocusGain(false)
                        .build()
                    focusRequest = request
                    audioManager.requestAudioFocus(request!!)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
                check(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "无法获取语音通话音频焦点" }
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = speakerphone
                return Lease(owner, listener).also {
                    activeOwner = owner
                    activeLease = it
                }
            } catch (error: Throwable) {
                abandonFocus(listener, request)
                focusRequest = null
                runCatching { audioManager.isSpeakerphoneOn = previousSpeaker }
                runCatching { audioManager.mode = previousMode }
                activeOwner = null
                activeLease = null
                throw error
            }
        }
    }


    private fun abandonFocus(
        listener: AudioManager.OnAudioFocusChangeListener,
        request: AudioFocusRequest?,
    ) {
        if (Build.VERSION.SDK_INT >= 26) {
            (request ?: focusRequest)?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(listener) }
        }
    }

    inner class Lease internal constructor(
        private val owner: Owner,
        private val listener: AudioManager.OnAudioFocusChangeListener,
    ) : Closeable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (!released.compareAndSet(false, true)) return
            synchronized(lock) {
                // Compare the lease identity, not just Owner: an old lease must
                // never release a newer session that happens to use the same owner.
                if (activeLease !== this) return
                abandonFocus(listener, focusRequest)
                focusRequest = null
                runCatching { audioManager.isSpeakerphoneOn = previousSpeaker }
                runCatching { audioManager.mode = previousMode }
                activeLease = null
                activeOwner = null
            }
        }
    }
}
