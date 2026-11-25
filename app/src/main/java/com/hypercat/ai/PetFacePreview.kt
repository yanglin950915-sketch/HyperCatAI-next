package com.hypercat.ai

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
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
