package com.hypercat.ai

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Local WebView host for the shared HyperCat Canvas face engine. */
@Composable
internal fun PetFacePreview(
    modifier: Modifier = Modifier,
    onMoodChange: (String) -> Unit
) {
    val latestOnMoodChange = rememberUpdatedState(onMoodChange)
    val hostedWebView = remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            hostedWebView.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("HyperCatBridge")
                destroy()
            }
            hostedWebView.value = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                hostedWebView.value = this
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        return !url.startsWith("file:///android_asset/")
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onMoodChanged(mood: String) {
                        val safeMood = mood.lowercase().take(24)
                        Handler(Looper.getMainLooper()).post {
                            latestOnMoodChange.value(safeMood)
                        }
                    }
                }, "HyperCatBridge")
                loadUrl("file:///android_asset/face-preview.html")
            }
        },
        update = { }
    )
}
