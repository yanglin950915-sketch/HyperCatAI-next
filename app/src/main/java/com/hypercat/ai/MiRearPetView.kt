package com.hypercat.ai

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
@SuppressLint("SetJavaScriptEnabled")
internal fun MiRearPetView(
    modifier: Modifier = Modifier,
    mood: String = "normal",
    speaking: Boolean = false,
    onInteraction: (String) -> Unit = {}
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember(onInteraction) {
        object {
            @JavascriptInterface
            fun onPetInteraction(action: String) {
                mainHandler.post { onInteraction(action) }
            }
        }
    }
    val stateScript = "window.hyperCatSetState && window.hyperCatSetState(" +
        org.json.JSONObject.quote(mood) + ", " + speaking + ");"
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(stateScript, null)
                    }
                }
                addJavascriptInterface(bridge, "HyperCatBridge")
                loadUrl("file:///android_asset/mi_rear_pet/index.html")
            }
        },
        update = { view -> view.evaluateJavascript(stateScript, null) }
    )
}
