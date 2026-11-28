package com.mirearpet;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Pure-black, borderless, sticky-immersive AI Pet container for Xiaomi Rear Screen
 * (and any secondary/spare Android screen via FullscreenPetActivity).
 *
 * Key stability design for HyperOS / Android 17:
 * - For RearPetActivity, DO NOT create Chromium WebView while temporarily staged on Display 0!
 *   Create WebView ONLY after arriving on Display 1 (displayId != 0), so Chromium's GPU Surface
 *   is born natively on Display 1's 976x596 450dpi context (never corrupted by Display 0 -> 1 move).
 * - NEVER call webView.onPause() on rear display, so the pet stays live even when Display 0 has focus.
 */
public class RearPetActivity extends Activity {
    public static volatile boolean isVisible = false;
    public static volatile int activeDisplayId = -1;
    private static volatile WeakReference<RearPetActivity> rearInstanceRef = new WeakReference<>(null);

    private FrameLayout rootLayout;
    private WebView webView;
    private TextView statusOverlay;
    private String currentUrl = "http://127.0.0.1:8765/";
    private int webViewCreatedDisplayId = -1;
    private boolean hasEverBeenOnRearDisplay = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = () -> {
        if (webView != null && !isFinishing()) {
            DiagLog.log("Web", "自动重试加载: " + currentUrl);
            webView.loadUrl(currentUrl);
        }
    };

    protected boolean isFrontFullscreenMode() {
        return false;
    }

    public static boolean hasRearInstance() {
        RearPetActivity act = rearInstanceRef.get();
        return act != null && !act.isFinishing() && !act.isDestroyed();
    }

    public static void finishRearInstanceIfRunning() {
        RearPetActivity act = rearInstanceRef.get();
        if (act != null && !act.isFinishing()) {
            act.runOnUiThread(() -> {
                try {
                    act.finishAndRemoveTask();
                } catch (Throwable ignored) {
                    act.finish();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isFrontFullscreenMode()) {
            rearInstanceRef = new WeakReference<>(this);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        statusOverlay = new TextView(this);
        statusOverlay.setBackgroundColor(Color.BLACK);
        statusOverlay.setTextColor(Color.parseColor("#7FEAFF"));
        statusOverlay.setTextSize(14f);
        statusOverlay.setGravity(Gravity.CENTER);
        statusOverlay.setPadding(32, 32, 32, 32);
        statusOverlay.setText("正在连接 AI 桌宠...");
        statusOverlay.setOnClickListener(v -> {
            statusOverlay.setText("正在重连电脑端...\n" + currentUrl);
            if (webView != null) webView.loadUrl(currentUrl);
        });

        rootLayout.addView(statusOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootLayout);

        extractUrlFromIntent(getIntent());
        applyImmersive();
        logLifecycle("onCreate");
        ensureWebViewReadyIfOnTargetDisplay();
    }

    private int currentDisplayId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getDisplay() != null) {
            return getDisplay().getDisplayId();
        }
        return 0;
    }

    private void applyRearLockscreenPolicy(int dId) {
        if (isFrontFullscreenMode()) return;
        boolean onRear = (dId != 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(onRear);
        }
        if (onRear) {
            // Keep pet visible on Display 1 even when the front screen locks!
            //noinspection deprecation
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            // NEVER allow showWhenLocked on Display 0 so front lockscreen is never touched
            //noinspection deprecation
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void ensureWebViewReadyIfOnTargetDisplay() {
        int dId = currentDisplayId();
        applyRearLockscreenPolicy(dId);
        if (!isFrontFullscreenMode() && dId == 0) {
            // Destroy any old WebView if bounced back to Display 0 so it never corrupts across DPI changes
            if (webView != null) {
                try {
                    rootLayout.removeView(webView);
                    webView.destroy();
                } catch (Throwable ignored) {}
                webView = null;
                webViewCreatedDisplayId = -1;
            }
            if (hasEverBeenOnRearDisplay) {
                // HyperOS (e.g. when Camera takes over Display 1) or withdraw bounced this rear task
                // back onto Display 0: finish immediately so it NEVER blocks Camera or any front app!
                DiagLog.log("PetAct", "背屏实例被退回主屏(d=0)，自动退出以避免遮挡前景应用");
                try {
                    moveTaskToBack(true);
                } catch (Throwable ignored) {}
                try {
                    finishAndRemoveTask();
                } catch (Throwable ignored) {
                    finish();
                }
                return;
            }
            statusOverlay.setText("正在投射到背屏...");
            statusOverlay.setVisibility(View.VISIBLE);
            return;
        }
        if (!isFrontFullscreenMode() && dId != 0) {
            hasEverBeenOnRearDisplay = true;
        }
        if (webView != null && webViewCreatedDisplayId == dId) {
            return;
        }
        if (webView != null) {
            try {
                rootLayout.removeView(webView);
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }

        DiagLog.log("Web", "在 displayId=" + dId + " 原生创建 WebView -> " + currentUrl);
        webViewCreatedDisplayId = dId;
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                DiagLog.log("Web", "onPageFinished d=" + currentDisplayId()
                        + " view=" + view.getWidth() + "x" + view.getHeight() + " url=" + url);
                if (url != null && !url.startsWith("about:")) {
                    statusOverlay.setVisibility(View.GONE);
                    view.evaluateJavascript(
                            "(function(){var c=document.getElementById('petCanvas'); return document.readyState+' win='+window.innerWidth+'x'+window.innerHeight+' canvas='+(c?(c.width+'x'+c.height):'none');})()",
                            val -> DiagLog.log("Web", "jsCheck=" + val)
                    );
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) return;
                String desc = error != null ? (error.getErrorCode() + ":" + error.getDescription()) : "unknown";
                DiagLog.log("Web", "onReceivedError " + desc + " url=" + currentUrl);
                showReconnectOverlay();
            }
        });

        rootLayout.addView(webView, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        statusOverlay.setVisibility(View.VISIBLE);
        statusOverlay.setText("正在加载 AI 桌宠...");
        webView.loadUrl(currentUrl);
    }

    private void showReconnectOverlay() {
        handler.removeCallbacks(retryRunnable);
        statusOverlay.setText("等待连接电脑端 AI 桌宠服务...\n" + currentUrl + "\n（3 秒后自动重连，点击立即重试）");
        statusOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(retryRunnable, 3000);
    }

    private void extractUrlFromIntent(Intent intent) {
        String url = intent != null ? intent.getStringExtra("url") : null;
        if (url == null || url.trim().isEmpty()) {
            url = getSharedPreferences("mi_rear_pet", MODE_PRIVATE)
                    .getString("url", "http://127.0.0.1:8765/");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        currentUrl = url;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        extractUrlFromIntent(intent);
        if (webView != null && webViewCreatedDisplayId == currentDisplayId()) {
            webView.loadUrl(currentUrl);
        } else {
            ensureWebViewReadyIfOnTargetDisplay();
        }
    }

    private void logLifecycle(String event) {
        int dId = currentDisplayId();
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        int dpi = getResources().getDisplayMetrics().densityDpi;
        DiagLog.log("PetAct", getClass().getSimpleName() + "." + event
                + " displayId=" + dId + " size=" + w + "x" + h + " dpi=" + dpi
                + " taskId=" + getTaskId());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTrackedDisplayId();
        applyImmersive();
        logLifecycle("onConfigChanged");
        ensureWebViewReadyIfOnTargetDisplay();
    }

    private void updateTrackedDisplayId() {
        if (isFrontFullscreenMode()) return;
        activeDisplayId = currentDisplayId();
    }

    @Override
    protected void onStart() {
        super.onStart();
        logLifecycle("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFrontFullscreenMode()) {
            isVisible = true;
            updateTrackedDisplayId();
        }
        applyImmersive();
        ensureWebViewReadyIfOnTargetDisplay();
        if (webView != null) webView.onResume();
        logLifecycle("onResume");
    }

    @Override
    protected void onPause() {
        logLifecycle("onPause");
        if (!isFrontFullscreenMode()) {
            isVisible = false;
        }
        // Only pause WebView in front fullscreen mode; keep rear-display pet WebView animating live!
        if (isFrontFullscreenMode() && webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        logLifecycle("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logLifecycle("onDestroy");
        if (!isFrontFullscreenMode()) {
            isVisible = false;
            activeDisplayId = -1;
            if (rearInstanceRef.get() == this) {
                rearInstanceRef = new WeakReference<>(null);
            }
        }
        handler.removeCallbacks(retryRunnable);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                ctrl.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                ctrl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }
}
