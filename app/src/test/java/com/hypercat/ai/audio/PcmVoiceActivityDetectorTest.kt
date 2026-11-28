package com.hypercat.ai.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmVoiceActivityDetectorTest {
    private fun frame(sample: Short, samples: Int = 160): ByteArray {
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            bytes[i * 2] = (sample.toInt() and 0xff).toByte()
            bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return bytes
    }

    @Test
    fun speechRequiresConfiguredOnsetFrames() {
        val detector = PcmVoiceActivityDetector(onsetFrames = 2)
        val loud = frame(6000)
        assertEquals(PcmVoiceActivityDetector.Event.SILENCE, detector.process(loud))
        assertFalse(detector.isSpeechActive)
        assertEquals(PcmVoiceActivityDetector.Event.SPEECH_STARTED, detector.process(loud))
        assertTrue(detector.isSpeechActive)
        assertEquals(PcmVoiceActivityDetector.Event.SPEECH_CONTINUES, detector.process(loud))
    }

    @Test
    fun speechEndsOnlyAfterConfiguredQuietFrames() {
        val detector = PcmVoiceActivityDetector(onsetFrames = 1, releaseFrames = 3)
        detector.process(frame(6000))
        val quiet = frame(0)
        assertEquals(PcmVoiceActivityDetector.Event.SPEECH_CONTINUES, detector.process(quiet))
        assertEquals(PcmVoiceActivityDetector.Event.SPEECH_CONTINUES, detector.process(quiet))
        assertEquals(PcmVoiceActivityDetector.Event.SPEECH_ENDED, detector.process(quiet))
        assertFalse(detector.isSpeechActive)
    }

    @Test
    fun resetClearsSpeechState() {
        val detector = PcmVoiceActivityDetector(onsetFrames = 1)
        detector.process(frame(6000))
        assertTrue(detector.isSpeechActive)
        detector.reset()
        assertFalse(detector.isSpeechActive)
        assertEquals(PcmVoiceActivityDetector.Event.SILENCE, detector.process(frame(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOddPcm16Length() {
        PcmVoiceActivityDetector().process(ByteArray(4), 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLengthOutsideBuffer() {
        PcmVoiceActivityDetector().process(ByteArray(4), 6)
    }
}
