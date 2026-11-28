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
    enum class Owner { REALTIME_CALL, VOICE_MESSAGE, LEGACY_RECOGNIZER }

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock = Any()
    private var activeOwner: Owner? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var focusRequest: AudioFocusRequest? = null

    fun acquire(owner: Owner, speakerphone: Boolean = false): Lease {
        synchronized(lock) {
            check(activeOwner == null) { "音频设备正在被 ${activeOwner} 使用，请先结束当前音频会话" }
            previousMode = audioManager.mode
            previousSpeaker = audioManager.isSpeakerphoneOn
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onFocusLost?.invoke(change)
            }
            val result = if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            check(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "无法获取语音通话音频焦点" }
            activeOwner = owner
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerphone
            return Lease(owner, listener)
        }
    }

    @Volatile var onFocusLost: ((Int) -> Unit)? = null

    inner class Lease internal constructor(private val owner: Owner, private val listener: AudioManager.OnAudioFocusChangeListener) : Closeable {
        private val released = AtomicBoolean(false)
        override fun close() {
            if (!released.compareAndSet(false, true)) return
            synchronized(lock) {
                if (activeOwner != owner) return
                if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(listener)
                }
                focusRequest = null
                audioManager.isSpeakerphoneOn = previousSpeaker
                audioManager.mode = previousMode
                activeOwner = null
            }
        }
    }
}
