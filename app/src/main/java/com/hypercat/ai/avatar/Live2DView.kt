package com.hypercat.ai.avatar

import android.annotation.SuppressLint
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.CopyOnWriteArraySet

data class Live2DRenderState(val ready: Boolean = false, val error: String? = null, val fps: Int = 0)

/** WebGL Live2D host. The default Cubism model is bundled into the APK assets. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun Live2DView(
    model: Live2DModel,
    modifier: Modifier = Modifier,
    mood: String = "normal",
    speaking: Boolean = false,
    musicPlaying: Boolean = false,
    halfBody: Boolean = true,
    safeTopRatio: Float = 0f,
    safeLeftRatio: Float = 0f,
    safeRightRatio: Float = 0f,
    safeBottomRatio: Float = 0f,
    rearDisplay: Boolean = false,
    showInterface: Boolean = false,
    onGesture: (String, String) -> Unit = { _, _ -> },
    onState: (Live2DRenderState) -> Unit = {},
    onPortrait: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf(Live2DRenderState()) }
    val latestGesture by androidx.compose.runtime.rememberUpdatedState(onGesture)
    val latestState by androidx.compose.runtime.rememberUpdatedState(onState)
    val latestPortrait by androidx.compose.runtime.rememberUpdatedState(onPortrait)
    val controller = remember { Live2DController() }
    val bridge = remember(controller) {
        object {
            @JavascriptInterface fun onReady() {
                Handler(Looper.getMainLooper()).post { state = state.copy(ready = true, error = null); latestState(state) }
            }
            @JavascriptInterface fun onError(message: String) {
                Log.e(TAG, "Live2D: $message")
                Handler(Looper.getMainLooper()).post { state = state.copy(error = message); latestState(state) }
            }
            @JavascriptInterface fun onGesture(action: String, part: String) {
                val normalized = if (action.startsWith("pet_")) "avatar_" + action.removePrefix("pet_") else action
                if (normalized in setOf("avatar_touch", "avatar_long_press", "avatar_drag")) {
                    Handler(Looper.getMainLooper()).post { latestGesture(normalized, part) }
                }
            }
            @JavascriptInterface fun onFps(fps: Int) {
                Handler(Looper.getMainLooper()).post { state = state.copy(fps = fps); latestState(state) }
            }
            @JavascriptInterface fun onPortrait(dataUrl: String) {
                Handler(Looper.getMainLooper()).post { latestPortrait(dataUrl) }
            }
            @JavascriptInterface fun onPoke(part: String) { latestGesture("avatar_touch", part) }
        }
    }
    val webView = remember(controller) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            // Let the Compose scene behind the Live2D WebView show through.
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            addJavascriptInterface(bridge, "NativeBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "js: ${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
        }
    }
    DisposableEffect(webView) {
        controller.attach(webView)
        Live2DControllerHub.register(controller)
        onDispose {
            Live2DControllerHub.unregister(controller)
            controller.detach()
            webView.stopLoading()
            webView.removeJavascriptInterface("NativeBridge")
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }
    LaunchedEffect(model.directory, model.entry, halfBody, safeTopRatio, safeLeftRatio, safeRightRatio, safeBottomRatio, rearDisplay) {
        state = Live2DRenderState()
        val dir = android.net.Uri.encode(model.directory, "/:")
        val entry = android.net.Uri.encode(model.entry, "/")
        // Clear the previous WebView surface before loading the next model. Keeping the old
        // composited frame visible during navigation caused motion trails on fast swaps.
        // Main-screen rendering remains centred in the full viewport. Only the
        // rear-display host receives the measured camera/cutout safe-area inset.
        val rearSafeTop = if (rearDisplay) safeTopRatio.coerceIn(0f, 0.95f) else 0f
        val rearSafeLeft = if (rearDisplay) safeLeftRatio.coerceIn(0f, 0.95f) else 0f
        val rearSafeRight = if (rearDisplay) safeRightRatio.coerceIn(0f, 0.95f) else 0f
        val rearSafeBottom = if (rearDisplay) safeBottomRatio.coerceIn(0f, 0.95f) else 0f
        val targetUrl = "file:///android_asset/web/live2d/render.html?dir=$dir&entry=$entry&half=" + (if (halfBody) 1 else 0) + "&ratio=0.55&safeTop=$rearSafeTop&safeLeft=$rearSafeLeft&safeRight=$rearSafeRight&safeBottom=$rearSafeBottom&fps=60&portrait=1&ui=${if (showInterface) 1 else 0}&offsetX=0&rear=${if (rearDisplay) 1 else 0}"
        webView.stopLoading()
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.loadUrl("about:blank")
        kotlinx.coroutines.delay(45)
        webView.loadUrl(targetUrl)
    }
    LaunchedEffect(mood, state.ready) {
        if (state.ready) controller.setEmotion(
            when (mood.lowercase()) {
                "happy" -> "happy"
                "sad" -> "sad"
                "angry" -> "angry"
                "shy" -> "shy"
                else -> "neutral"
            }
        )
    }
    LaunchedEffect(speaking, state.ready) { if (state.ready) controller.setSpeaking(speaking) }
    LaunchedEffect(musicPlaying, state.ready) {
        if (state.ready) {
            if (!musicPlaying) controller.setMusicBeat(0f)
            else {
                var phase = false
                while (true) {
                    phase = !phase
                    controller.setMusicBeat(if (phase) 1f else 0.18f)
                    kotlinx.coroutines.delay(270L)
                }
            }
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

class Live2DController internal constructor() {
    @Volatile private var webView: WebView? = null
    internal fun attach(view: WebView) { webView = view }
    internal fun detach() { webView = null }
    private fun call(script: String) { webView?.post { webView?.evaluateJavascript(script, null) } }
    fun setSpeaking(value: Boolean) = call("window.__l2d&&window.__l2d.setSpeaking($value)")
    fun setMusicBeat(value: Float) = call("window.__l2d&&window.__l2d.setMusicBeat(${value.coerceIn(0f,1f)})")
    fun setMouth(value: Float) = call("window.__l2d&&window.__l2d.setMouth(${value.coerceIn(0f, 1f)})")
    fun setEmotion(value: String) = call("window.__l2d&&window.__l2d.setEmotion('${value.replace("'", "")}')")
    fun setGaze(x: Float, y: Float) = call("window.__l2d&&window.__l2d.setGaze(${x.coerceIn(-1f,1f)},${y.coerceIn(-1f,1f)})")
    fun setView(halfBody: Boolean, ratio: Float = 0.55f) = call("window.__l2d&&window.__l2d.setView($halfBody,${ratio.coerceIn(0.3f,0.8f)})")
}

object Live2DControllerHub {
    private val controllers = CopyOnWriteArraySet<Live2DController>()
    internal fun register(controller: Live2DController) { controllers.add(controller) }
    internal fun unregister(controller: Live2DController) { controllers.remove(controller) }
    fun setSpeaking(value: Boolean) { controllers.forEach { it.setSpeaking(value) } }
    fun setMouth(value: Float) { controllers.forEach { it.setMouth(value) } }
    fun setEmotion(value: String) { controllers.forEach { it.setEmotion(value) } }
}

private const val TAG = "HyperCatLive2D"
