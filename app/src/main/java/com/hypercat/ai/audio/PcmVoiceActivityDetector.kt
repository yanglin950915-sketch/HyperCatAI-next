package com.hypercat.ai.audio

import kotlin.math.sqrt

/**
 * Small frame-based voice activity detector used for local barge-in hints.
 * The server-side VAD remains authoritative for turn boundaries.
 */
class PcmVoiceActivityDetector(
    private val threshold: Double = 0.035,
    private val onsetFrames: Int = 2,
    private val releaseFrames: Int = 18,
) {
    enum class Event { SPEECH_STARTED, SPEECH_CONTINUES, SPEECH_ENDED, SILENCE }

    init {
        require(threshold.isFinite() && threshold >= 0.0) { "VAD threshold must be finite and non-negative" }
        require(onsetFrames > 0) { "VAD onsetFrames must be positive" }
        require(releaseFrames > 0) { "VAD releaseFrames must be positive" }
    }

    private var activeFrames = 0
    private var quietFrames = 0
    var isSpeechActive: Boolean = false
        private set

    fun process(pcm16: ByteArray, length: Int = pcm16.size): Event {
        require(length in 0..pcm16.size) { "PCM length must be within the supplied buffer" }
        require(length % 2 == 0) { "PCM16 frame length must be even" }
        if (length == 0) return Event.SILENCE
        val rms = rms(pcm16, length)
        if (rms >= threshold) {
            activeFrames++
            quietFrames = 0
            if (!isSpeechActive && activeFrames >= onsetFrames) {
                isSpeechActive = true
                return Event.SPEECH_STARTED
            }
            return if (isSpeechActive) Event.SPEECH_CONTINUES else Event.SILENCE
        }
        activeFrames = 0
        if (isSpeechActive) {
            quietFrames++
            if (quietFrames >= releaseFrames) {
                isSpeechActive = false
                quietFrames = 0
                return Event.SPEECH_ENDED
            }
            return Event.SPEECH_CONTINUES
        }
        return Event.SILENCE
    }

    fun reset() {
        activeFrames = 0
        quietFrames = 0
        isSpeechActive = false
    }

    private fun rms(bytes: ByteArray, length: Int): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
            val value = sample / 32768.0
            sum += value * value
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }
}
