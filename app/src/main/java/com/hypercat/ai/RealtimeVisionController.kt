package com.hypercat.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Asynchronous visual observer for Realtime calls.
 *
 * Camera frames are offered at the camera cadence (up to 2 FPS), but only
 * meaningful scene changes are sent to the vision endpoint. The result is
 * added to the Realtime conversation as context without creating a response,
 * so it never blocks microphone streaming or causes one spoken reply per frame.
 */
internal class RealtimeVisionController(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val onContext: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HyperCat-RealtimeVision").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val closed = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    @Volatile private var lastHash: Long? = null
    @Volatile private var lastAnalysisAt = 0L
    @Volatile private var lastSuccessAt = 0L
    @Volatile private var lastErrorAt = 0L

    fun offerFrame(jpeg: ByteArray) {
        if (closed.get() || jpeg.isEmpty() || inFlight.get()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisAt < MIN_ANALYSIS_INTERVAL_MS) return

        val hash = runCatching { perceptualHash(jpeg) }.getOrNull() ?: return
        val previous = lastHash
        val changed = previous == null || java.lang.Long.bitCount(previous xor hash) >= CHANGE_THRESHOLD_BITS
        val refreshDue = now - lastSuccessAt >= FORCE_REFRESH_INTERVAL_MS
        if (!changed && !refreshDue) return
        if (!inFlight.compareAndSet(false, true)) return

        lastAnalysisAt = now
        val snapshot = jpeg.copyOf()
        executor.execute {
            try {
                if (closed.get()) return@execute
                val observation = VisionModelClient.describeFrame(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = model,
                    jpeg = snapshot,
                    question = "这是持续视频通话中的一帧。请只提供简短、客观的场景观察摘要，包含可确认的人物/物品/环境以及相较常见静态场景明显的动作或变化；不要猜测身份、意图或不可见细节，不要向用户提问，不要生成对话式回答。控制在80字以内。"
                ).trim()
                if (observation.isNotBlank() && !closed.get()) {
                    lastHash = hash
                    lastSuccessAt = SystemClock.elapsedRealtime()
                    onContext("摄像头画面观察（自动更新，仅作为当前通话上下文，不需要单独回复）：$observation")
                    onStatus("视觉感知已更新 · 正在持续观察画面")
                }
            } catch (error: Throwable) {
                val failedAt = SystemClock.elapsedRealtime()
                if (!closed.get() && failedAt - lastErrorAt >= ERROR_REPORT_INTERVAL_MS) {
                    lastErrorAt = failedAt
                    Log.w(TAG, "Realtime visual observation failed", error)
                    onStatus("视觉感知暂不可用：${(error.message ?: "请检查视觉模型配置").take(72)}")
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun perceptualHash(jpeg: ByteArray): Long {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: throw IllegalArgumentException("无法解码摄像头 JPEG")
        val tiny = Bitmap.createScaledBitmap(decoded, HASH_SIZE, HASH_SIZE, true)
        if (tiny !== decoded) decoded.recycle()
        try {
            val pixels = IntArray(HASH_SIZE * HASH_SIZE)
            tiny.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
            val luminance = IntArray(pixels.size) { index ->
                val color = pixels[index]
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                (red * 299 + green * 587 + blue * 114) / 1000
            }
            val average = luminance.average().toInt()
            var hash = 0L
            for (index in luminance.indices) {
                if (luminance[index] >= average) hash = hash or (1L shl index)
            }
            return hash
        } finally {
            tiny.recycle()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "HyperCatVision"
        private const val HASH_SIZE = 8
        private const val CHANGE_THRESHOLD_BITS = 7
        private const val MIN_ANALYSIS_INTERVAL_MS = 3_500L
        private const val FORCE_REFRESH_INTERVAL_MS = 12_000L
        private const val ERROR_REPORT_INTERVAL_MS = 30_000L
    }
}
