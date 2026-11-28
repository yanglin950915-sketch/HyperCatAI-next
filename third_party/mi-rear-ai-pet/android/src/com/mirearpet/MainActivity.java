package com.mirearpet;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Clean, beginner-friendly control launcher for Xiaomi Rear Screen AI Pet.
 * - Auto-discovers PC server on the same Wi-Fi (UDP 8766)
 * - Guided Shizuku install -> activate -> authorize -> 1-tap rear projection flow
 * - Borderless fullscreen mode for any Android phone/tablet (no Shizuku required)
 * - In-app 1-tap OTA updates + collapsed compact diagnostics
 */
public class MainActivity extends Activity implements ShizukuBridge.Listener, DiagLog.Listener {
    private static final String SHIZUKU_DOWNLOAD_URL = "https://github.com/RikkaApps/Shizuku/releases";

    private SharedPreferences prefs;
    private EditText urlInput;
    private LinearLayout discoveredBox;
    private TextView nextStepBanner;
    private TextView shizukuStatusText;
    private TextView shizukuGuideToggle;
    private TextView shizukuGuideBody;
    private TextView diagToggle;
    private LinearLayout diagContentBox;
    private TextView diagLogView;
    private Button updateBannerBtn;
    private Button shizukuActionBtn;
    private Button openShizukuBtn;
    private Switch guardSwitch;
    private List<LanDiscovery.DiscoveredHost> lastHosts;
    private AppUpdater.UpdateInfo pendingUpdate;

    // Non-stacking, rate-limited Toast state
    private Toast activeToast;
    private String lastToastMsg = "";
    private long lastToastAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mi_rear_pet", MODE_PRIVATE);
        ShizukuBridge.initContext(getApplicationContext());
        ShizukuBridge.addListener(this);
        DiagLog.addListener(this);

        getWindow().setStatusBarColor(Color.parseColor("#080B12"));
        getWindow().setNavigationBarColor(Color.parseColor("#080B12"));

        setContentView(buildUi());

        DiagLog.setServerUrl(currentUrl());
        DiagLog.log("App", "v" + AppUpdater.getLocalVersionName(this) + " | "
                + Build.MANUFACTURER + " " + Build.MODEL + " | Android " + Build.VERSION.RELEASE);

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
            } catch (Throwable ignored) {}
        }

        ShizukuBridge.requestBinderFromManager(this);
        refreshStatusUi();
        startDiscovery();
        checkForUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShizukuBridge.requestBinderFromManager(this);
        refreshStatusUi();
        checkForUpdate(false);
    }

    @Override
    protected void onDestroy() {
        ShizukuBridge.removeListener(this);
        DiagLog.removeListener(this);
        if (activeToast != null) {
            try { activeToast.cancel(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public void onShizukuStateChanged() {
        refreshStatusUi();
    }

    @Override
    public void onLogUpdated() {
        if (diagLogView != null) {
            diagLogView.setText(DiagLog.getTailText(40));
        }
    }

    /**
     * Shows a single non-stacking Toast and ignores repeated identical prompts within 2 seconds.
     */
    private void showToast(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        if (msg.equals(lastToastMsg) && (now - lastToastAt) < 2000L) {
            return;
        }
        if (activeToast != null) {
            try { activeToast.cancel(); } catch (Throwable ignored) {}
        }
        lastToastMsg = msg;
        lastToastAt = now;
        activeToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable roundedBg(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            gd.setStroke(dp(1), strokeColor);
        }
        return gd;
    }

    private LinearLayout makeCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(Color.parseColor("#111722"), Color.parseColor("#243044"), 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        if (title != null && !title.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(title);
            tv.setTextColor(Color.parseColor("#EAF6FF"));
            tv.setTextSize(15f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = dp(10);
            tv.setLayoutParams(tlp);
            card.addView(tv);
        }
        return card;
    }

    private Button makeButton(String text, int bgColor, int textColor, int strokeColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(14f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(roundedBg(bgColor, strokeColor, 12));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        return b;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#080B12"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int padH = dp(18);
        final int padTopBase = dp(16);
        final int padBottomBase = dp(24);
        root.setPadding(padH, padTopBase, padH, padBottomBase);

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = 0;
            int bottomInset = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(padH, topInset + padTopBase, padH, bottomInset + padBottomBase);
            return insets;
        });

        // Header row: Title + version + Check Update button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrLp.bottomMargin = dp(12);
        headerRow.setLayoutParams(hrLp);

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("背屏 AI 桌宠");
        title.setTextColor(Color.parseColor("#34E8FF"));
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleWrap.addView(title);

        TextView sub = new TextView(this);
        sub.setText("小米妙享背屏客户端 · v" + AppUpdater.getLocalVersionName(this));
        sub.setTextColor(Color.parseColor("#8FA3B8"));
        sub.setTextSize(12f);
        titleWrap.addView(sub);
        headerRow.addView(titleWrap);

        Button checkUpdateBtn = makeButton("检查更新", Color.parseColor("#162336"), Color.parseColor("#92EDFF"), Color.parseColor("#2A4C6E"));
        checkUpdateBtn.setTextSize(12f);
        checkUpdateBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        checkUpdateBtn.setOnClickListener(v -> checkForUpdate(true));
        headerRow.addView(checkUpdateBtn);
        root.addView(headerRow);

        // OTA update banner (hidden unless newer version exists on PC)
        updateBannerBtn = makeButton("发现新版本 · 点击立即更新", Color.parseColor("#1C1540"), Color.parseColor("#D6B8FF"), Color.parseColor("#8E63FF"));
        updateBannerBtn.setTextSize(13.5f);
        updateBannerBtn.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams ubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ubLp.bottomMargin = dp(12);
        updateBannerBtn.setLayoutParams(ubLp);
        updateBannerBtn.setVisibility(View.GONE);
        updateBannerBtn.setOnClickListener(v -> startPendingUpdate());
        root.addView(updateBannerBtn);

        // Concise single-line next step banner
        nextStepBanner = new TextView(this);
        nextStepBanner.setTextColor(Color.parseColor("#FFE299"));
        nextStepBanner.setTextSize(12.5f);
        nextStepBanner.setLineSpacing(dp(2), 1f);
        nextStepBanner.setBackground(roundedBg(Color.parseColor("#1A1A10"), Color.parseColor("#66501E"), 12));
        nextStepBanner.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout.LayoutParams nbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nbLp.bottomMargin = dp(14);
        nextStepBanner.setLayoutParams(nbLp);
        root.addView(nextStepBanner);

        // Card 1: PC Connection (Wi-Fi Auto-Discovery)
        LinearLayout card1 = makeCard("1. 连接电脑");

        discoveredBox = new LinearLayout(this);
        discoveredBox.setOrientation(LinearLayout.VERTICAL);
        card1.addView(discoveredBox);

        urlInput = new EditText(this);
        urlInput.setHint("http://192.168.x.x:8765/");
        urlInput.setHintTextColor(Color.parseColor("#5B6E82"));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setTextSize(13.5f);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setBackground(roundedBg(Color.parseColor("#0A0E16"), Color.parseColor("#2B3B52"), 10));
        urlInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        urlInput.setText(prefs.getString("url", ""));
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = dp(8);
        urlLp.bottomMargin = dp(10);
        urlInput.setLayoutParams(urlLp);
        card1.addView(urlInput);

        Button scanBtn = makeButton("重新搜索局域网电脑", Color.parseColor("#182436"), Color.parseColor("#92EDFF"), Color.parseColor("#2B4866"));
        scanBtn.setTextSize(13f);
        scanBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scanBtn.setOnClickListener(v -> startDiscovery());
        card1.addView(scanBtn);
        root.addView(card1);

        // Card 2: Xiaomi Rear Screen Projection (Clean & Minimal)
        LinearLayout card2 = makeCard("2. 小米妙享背屏投射");

        shizukuStatusText = new TextView(this);
        shizukuStatusText.setTextColor(Color.parseColor("#C7D8EA"));
        shizukuStatusText.setTextSize(13.5f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.bottomMargin = dp(10);
        shizukuStatusText.setLayoutParams(stLp);
        card2.addView(shizukuStatusText);

        shizukuActionBtn = makeButton("下载安装 Shizuku", Color.parseColor("#262010"), Color.parseColor("#FFD166"), Color.parseColor("#7A5C1B"));
        LinearLayout.LayoutParams saLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saLp.bottomMargin = dp(8);
        shizukuActionBtn.setLayoutParams(saLp);
        shizukuActionBtn.setOnClickListener(v -> handleShizukuAction());
        card2.addView(shizukuActionBtn);

        openShizukuBtn = makeButton("若弹窗未出现：打开 Shizuku 手动授权", Color.parseColor("#131A26"), Color.parseColor("#9FB4C9"), Color.parseColor("#243044"));
        openShizukuBtn.setTextSize(12f);
        openShizukuBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        osLp.bottomMargin = dp(8);
        openShizukuBtn.setLayoutParams(osLp);
        openShizukuBtn.setVisibility(View.GONE);
        openShizukuBtn.setOnClickListener(v -> openShizukuManager());
        card2.addView(openShizukuBtn);

        // Collapsible Shizuku 3-step beginner guide (auto-hides once Shizuku is granted!)
        shizukuGuideToggle = new TextView(this);
        shizukuGuideToggle.setText("▸ 新手如何安装激活 Shizuku？（点此查看 3 步）");
        shizukuGuideToggle.setTextColor(Color.parseColor("#7ADDF5"));
        shizukuGuideToggle.setTextSize(12f);
        shizukuGuideToggle.setPadding(dp(2), dp(4), dp(2), dp(8));
        card2.addView(shizukuGuideToggle);

        shizukuGuideBody = new TextView(this);
        shizukuGuideBody.setText(
                "1. 点上方「下载安装 Shizuku」安装好 Shizuku；\n"
                + "2. 手机「设置 → 我的设备 → 全部参数」连点 OS 版本开启开发者选项，再进「更多设置 → 开发者选项」打开：USB 调试、无线调试、USB 调试（安全设置）；\n"
                + "3. 打开 Shizuku 点「配对」→ 进无线调试用配对码完成配对 → 返回 Shizuku 点「启动」，显示「正在运行」后回到本 App 授权即可。"
        );
        shizukuGuideBody.setTextColor(Color.parseColor("#99B0C7"));
        shizukuGuideBody.setTextSize(11.5f);
        shizukuGuideBody.setLineSpacing(dp(3), 1f);
        shizukuGuideBody.setBackground(roundedBg(Color.parseColor("#0A0F18"), Color.parseColor("#1D2A3D"), 10));
        shizukuGuideBody.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams gbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gbLp.bottomMargin = dp(10);
        shizukuGuideBody.setLayoutParams(gbLp);
        shizukuGuideBody.setVisibility(View.GONE);
        shizukuGuideToggle.setOnClickListener(v -> {
            boolean show = shizukuGuideBody.getVisibility() != View.VISIBLE;
            shizukuGuideBody.setVisibility(show ? View.VISIBLE : View.GONE);
            shizukuGuideToggle.setText((show ? "▾ " : "▸ ") + "新手如何安装激活 Shizuku？（点此查看 3 步）");
        });
        card2.addView(shizukuGuideBody);

        // Primary CTA
        Button rearBtn = makeButton("一键投到妙享背屏", Color.parseColor("#0D3B4C"), Color.parseColor("#7AF4FF"), Color.parseColor("#34E8FF"));
        rearBtn.setTextSize(16f);
        rearBtn.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = dp(10);
        rearBtn.setLayoutParams(rLp);
        rearBtn.setOnClickListener(v -> handleProjectClick());
        card2.addView(rearBtn);

        Button stopRearBtn = makeButton("退出 / 撤回背屏桌宠", Color.parseColor("#181E2B"), Color.parseColor("#A7BBD0"), Color.parseColor("#2B3B52"));
        stopRearBtn.setTextSize(12.5f);
        stopRearBtn.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.bottomMargin = dp(12);
        stopRearBtn.setLayoutParams(srLp);
        stopRearBtn.setOnClickListener(v -> {
            if (RearGuardService.isRunning) {
                toggleGuard(false);
                if (guardSwitch != null) guardSwitch.setChecked(false);
            }
            ShizukuBridge.withdrawFromRearDisplay(this, (ok, detail) -> showToast(detail));
        });
        card2.addView(stopRearBtn);

        // Auto-Guard toggle row (keep exact wording per user preference)
        LinearLayout guardRow = new LinearLayout(this);
        guardRow.setOrientation(LinearLayout.HORIZONTAL);
        guardRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView guardLabel = new TextView(this);
        guardLabel.setText("息屏 / 切走后自动拉回背屏（守护模式）");
        guardLabel.setTextColor(Color.parseColor("#D5E3F0"));
        guardLabel.setTextSize(13f);
        guardLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        guardRow.addView(guardLabel);

        guardSwitch = new Switch(this);
        guardSwitch.setChecked(RearGuardService.isRunning);
        guardSwitch.setOnCheckedChangeListener((btn, checked) -> toggleGuard(checked));
        guardRow.addView(guardSwitch);
        card2.addView(guardRow);
        root.addView(card2);

        // Card 3: Regular Phone / Tablet Fullscreen Mode
        LinearLayout card3 = makeCard("3. 普通手机 / 平板全屏模式");
        TextView c3Hint = new TextView(this);
        c3Hint.setText("无需 Shizuku，任意闲置安卓手机或平板可纯黑全屏运行。");
        c3Hint.setTextColor(Color.parseColor("#8FA3B8"));
        c3Hint.setTextSize(12f);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.bottomMargin = dp(10);
        c3Hint.setLayoutParams(hLp);
        card3.addView(c3Hint);

        Button fullBtn = makeButton("当前屏幕全屏运行 AI 桌宠", Color.parseColor("#0F2D22"), Color.parseColor("#7CFFBE"), Color.parseColor("#38F59A"));
        fullBtn.setOnClickListener(v -> {
            if (urlInput.getText().toString().trim().isEmpty()) {
                showToast("请先确保已连接到同一 Wi-Fi 下的电脑");
                return;
            }
            savePrefs();
            Intent intent = new Intent(this, FullscreenPetActivity.class);
            intent.putExtra("url", currentUrl());
            intent.putExtra("rear_mode", false);
            startActivity(intent);
        });
        card3.addView(fullBtn);
        root.addView(card3);

        // Collapsed Diagnostic Logs Card (hidden by default so beginners never get overwhelmed)
        LinearLayout diagCard = makeCard(null);
        diagToggle = new TextView(this);
        diagToggle.setText("▸ 诊断日志（出问题反馈给作者时点此展开）");
        diagToggle.setTextColor(Color.parseColor("#7E95AD"));
        diagToggle.setTextSize(12.5f);
        diagToggle.setTypeface(Typeface.DEFAULT_BOLD);
        diagCard.addView(diagToggle);

        diagContentBox = new LinearLayout(this);
        diagContentBox.setOrientation(LinearLayout.VERTICAL);
        diagContentBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams dcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dcLp.topMargin = dp(10);
        diagContentBox.setLayoutParams(dcLp);

        LinearLayout diagBtnRow = new LinearLayout(this);
        diagBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dbrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dbrLp.bottomMargin = dp(8);
        diagBtnRow.setLayoutParams(dbrLp);

        Button copyLogBtn = makeButton("复制日志", Color.parseColor("#0D3B4C"), Color.parseColor("#8CF6FF"), Color.parseColor("#34E8FF"));
        copyLogBtn.setTextSize(12f);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        clLp.rightMargin = dp(6);
        copyLogBtn.setLayoutParams(clLp);
        copyLogBtn.setOnClickListener(v -> copyLogsToClipboard());
        diagBtnRow.addView(copyLogBtn);

        Button snapBtn = makeButton("采集状态", Color.parseColor("#182436"), Color.parseColor("#D5E3F0"), Color.parseColor("#2B4866"));
        snapBtn.setTextSize(12f);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spLp.rightMargin = dp(6);
        snapBtn.setLayoutParams(spLp);
        snapBtn.setOnClickListener(v -> ShizukuBridge.collectDiagnostics(this, (ok, detail) -> showToast(detail)));
        diagBtnRow.addView(snapBtn);

        Button clearLogBtn = makeButton("清空", Color.parseColor("#1B1F2A"), Color.parseColor("#8FA3B8"), Color.parseColor("#2B3B52"));
        clearLogBtn.setTextSize(12f);
        clearLogBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f));
        clearLogBtn.setOnClickListener(v -> {
            DiagLog.clear();
            showToast("日志已清空");
        });
        diagBtnRow.addView(clearLogBtn);
        diagContentBox.addView(diagBtnRow);

        diagLogView = new TextView(this);
        diagLogView.setText(DiagLog.getTailText(40));
        diagLogView.setTextColor(Color.parseColor("#9BC0D9"));
        diagLogView.setTextSize(10f);
        diagLogView.setTypeface(Typeface.MONOSPACE);
        diagLogView.setTextIsSelectable(true);
        diagLogView.setBackground(roundedBg(Color.parseColor("#070A10"), Color.parseColor("#1E2C3E"), 10));
        diagLogView.setPadding(dp(10), dp(8), dp(10), dp(8));
        diagContentBox.addView(diagLogView);
        diagCard.addView(diagContentBox);

        diagToggle.setOnClickListener(v -> {
            boolean show = diagContentBox.getVisibility() != View.VISIBLE;
            diagContentBox.setVisibility(show ? View.VISIBLE : View.GONE);
            diagToggle.setText((show ? "▾ " : "▸ ") + "诊断日志（出问题反馈给作者时点此展开）");
        });
        root.addView(diagCard);

        scroll.addView(root);
        return scroll;
    }

    private void handleProjectClick() {
        if (urlInput.getText().toString().trim().isEmpty()) {
            showToast("请先确保手机与电脑连同一 Wi-Fi 并搜到电脑");
            return;
        }
        if (!ShizukuBridge.isShizukuInstalled(this)) {
            showToast("请先点上方「下载安装 Shizuku」并激活");
            return;
        }
        if (!ShizukuBridge.isBinderAlive()) {
            ShizukuBridge.requestBinderFromManager(this);
        }
        if (!ShizukuBridge.isBinderAlive()) {
            showToast("请先打开 Shizuku 点「启动」，显示正在运行后再投屏");
            return;
        }
        if (!ShizukuBridge.isPermissionGranted()) {
            showToast("请先点上方「一键授权 Shizuku」并允许权限");
            return;
        }
        savePrefs();
        int did = ShizukuBridge.detectRearDisplayId(this);
        ShizukuBridge.launchOnRearDisplay(this, currentUrl(), did, (ok, detail) -> showToast(detail));
    }

    private void copyLogsToClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("MiRearPet_DiagLog", DiagLog.getAllText()));
                showToast("已复制日志，直接粘贴发送即可");
            }
        } catch (Throwable t) {
            showToast("复制失败: " + t.getMessage());
        }
    }

    private void checkForUpdate(boolean manual) {
        String u = urlInput != null ? urlInput.getText().toString().trim() : "";
        String baseUrl = u.isEmpty() ? "" : currentUrl();
        AppUpdater.checkUpdateAsync(this, baseUrl, info -> {
            if (!info.error.isEmpty()) {
                if (manual) showToast("检查更新失败：" + info.error);
                return;
            }
            if (info.hasUpdate) {
                pendingUpdate = info;
                updateBannerBtn.setText("✨ 发现新版本 v" + info.remoteVersionName + " · 点击立即更新");
                updateBannerBtn.setVisibility(View.VISIBLE);
                if (manual) showToast("发现新版本 v" + info.remoteVersionName + "，点顶部紫色条立即更新");
            } else {
                pendingUpdate = null;
                updateBannerBtn.setVisibility(View.GONE);
                if (manual) showToast("当前已是最新版本 v" + info.localVersionName);
            }
        });
    }

    private void startPendingUpdate() {
        AppUpdater.UpdateInfo info = pendingUpdate;
        if (info == null || !info.hasUpdate) {
            checkForUpdate(true);
            return;
        }
        updateBannerBtn.setEnabled(false);
        AppUpdater.downloadAndInstallAsync(this, info.apkUrl, info.remoteVersionName, new AppUpdater.InstallCallback() {
            @Override
            public void onProgress(String statusText) {
                updateBannerBtn.setText(statusText);
            }

            @Override
            public void onDone(boolean ok, String detail) {
                updateBannerBtn.setEnabled(true);
                updateBannerBtn.setText("✨ 发现新版本 v" + info.remoteVersionName + " · 点击立即更新");
                showToast(detail);
            }
        });
    }

    private String currentUrl() {
        String s = urlInput.getText().toString().trim();
        if (s.isEmpty()) return "http://127.0.0.1:8765/";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }

    private void savePrefs() {
        String u = currentUrl();
        DiagLog.setServerUrl(u);
        prefs.edit()
                .putString("url", u)
                .putInt("display_id", ShizukuBridge.detectRearDisplayId(this))
                .apply();
    }

    private void installOrDownloadShizuku() {
        String u = urlInput != null ? urlInput.getText().toString().trim() : "";
        if (!u.isEmpty()) {
            String base = currentUrl();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String localShizukuUrl = base + "/download/Shizuku.apk";
            shizukuActionBtn.setEnabled(false);
            AppUpdater.installExternalApkAsync(this, localShizukuUrl, "moe.shizuku.privileged.api", "Shizuku", new AppUpdater.InstallCallback() {
                @Override
                public void onProgress(String statusText) {
                    shizukuActionBtn.setText(statusText);
                }

                @Override
                public void onDone(boolean ok, String detail) {
                    shizukuActionBtn.setEnabled(true);
                    refreshStatusUi();
                    if (ok) {
                        showToast(detail);
                    } else if (detail != null && detail.contains("HTTP 404")) {
                        openShizukuDownloadPage();
                    } else {
                        showToast(detail);
                    }
                }
            });
            return;
        }
        openShizukuDownloadPage();
    }

    private void openShizukuDownloadPage() {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL));
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browser);
            showToast("正在打开 Shizuku GitHub 发布页");
        } catch (Throwable t) {
            showToast("无法打开浏览器，请手动搜索下载 Shizuku");
        }
    }

    private void openShizukuManager() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) {
            startActivity(launch);
            showToast("请在 Shizuku 确认正在运行并允许本应用授权");
        } else {
            installOrDownloadShizuku();
        }
    }

    private void handleShizukuAction() {
        if (!ShizukuBridge.isShizukuInstalled(this)) {
            installOrDownloadShizuku();
            return;
        }
        if (!ShizukuBridge.isBinderAlive()) {
            boolean pulled = ShizukuBridge.requestBinderFromManager(this);
            if (!pulled) {
                openShizukuManager();
                return;
            }
        }
        if (!ShizukuBridge.isPermissionGranted()) {
            String res = ShizukuBridge.requestPermission(this);
            if ("granted".equals(res)) {
                showToast("Shizuku 已授权就绪！");
            } else if ("requested".equals(res)) {
                showToast("请在弹出的窗口点「始终允许」");
            } else {
                showToast(res);
            }
        }
        refreshStatusUi();
    }

    private void toggleGuard(boolean enable) {
        savePrefs();
        Intent svc = new Intent(this, RearGuardService.class);
        if (enable) {
            if (!ShizukuBridge.isPermissionGranted()) {
                guardSwitch.setChecked(false);
                showToast("请先完成 Shizuku 授权再开启守护模式");
                return;
            }
            svc.setAction(RearGuardService.ACTION_START);
            svc.putExtra("url", currentUrl());
            svc.putExtra("display_id", ShizukuBridge.detectRearDisplayId(this));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            showToast("已开启守护模式");
        } else {
            svc.setAction(RearGuardService.ACTION_STOP);
            startService(svc);
            showToast("已关闭守护模式");
        }
    }

    private void refreshStatusUi() {
        boolean installed = ShizukuBridge.isShizukuInstalled(this);
        boolean alive = ShizukuBridge.isBinderAlive();
        boolean granted = ShizukuBridge.isPermissionGranted();

        if (!installed) {
            shizukuStatusText.setText("● Shizuku：未安装");
            shizukuStatusText.setTextColor(Color.parseColor("#FF8C9A"));
            shizukuActionBtn.setText("下载安装 Shizuku");
            shizukuActionBtn.setVisibility(View.VISIBLE);
            openShizukuBtn.setVisibility(View.GONE);
            shizukuGuideToggle.setVisibility(View.VISIBLE);
            nextStepBanner.setText("下一步：投小米背屏请点「下载安装 Shizuku」；普通手机/平板直接滑到底部全屏运行。");
            nextStepBanner.setTextColor(Color.parseColor("#FFE299"));
            nextStepBanner.setBackground(roundedBg(Color.parseColor("#1A1A10"), Color.parseColor("#66501E"), 12));
        } else if (!alive) {
            shizukuStatusText.setText("● Shizuku：未运行（需在 Shizuku 点启动）");
            shizukuStatusText.setTextColor(Color.parseColor("#FFB830"));
            shizukuActionBtn.setText("打开 Shizuku 去启动");
            shizukuActionBtn.setVisibility(View.VISIBLE);
            openShizukuBtn.setVisibility(View.GONE);
            shizukuGuideToggle.setVisibility(View.VISIBLE);
            nextStepBanner.setText("下一步：点「打开 Shizuku 去启动」，确认顶部显示「正在运行」后再返回本页。");
            nextStepBanner.setTextColor(Color.parseColor("#FFE299"));
            nextStepBanner.setBackground(roundedBg(Color.parseColor("#1A1A10"), Color.parseColor("#66501E"), 12));
        } else if (!granted) {
            shizukuStatusText.setText("● Shizuku：已运行，等待授权");
            shizukuStatusText.setTextColor(Color.parseColor("#FFB830"));
            shizukuActionBtn.setText("一键授权 Shizuku");
            shizukuActionBtn.setVisibility(View.VISIBLE);
            openShizukuBtn.setVisibility(View.VISIBLE);
            shizukuGuideToggle.setVisibility(View.GONE);
            shizukuGuideBody.setVisibility(View.GONE);
            nextStepBanner.setText("下一步：点「一键授权 Shizuku」并点「始终允许」→ 再点「一键投到妙享背屏」。");
            nextStepBanner.setTextColor(Color.parseColor("#FFE299"));
            nextStepBanner.setBackground(roundedBg(Color.parseColor("#1A1A10"), Color.parseColor("#66501E"), 12));
        } else {
            shizukuStatusText.setText("● Shizuku：已授权就绪");
            shizukuStatusText.setTextColor(Color.parseColor("#38F59A"));
            shizukuActionBtn.setVisibility(View.GONE);
            openShizukuBtn.setVisibility(View.GONE);
            shizukuGuideToggle.setVisibility(View.GONE);
            shizukuGuideBody.setVisibility(View.GONE);
            nextStepBanner.setText("已就绪！点「一键投到妙享背屏」，翻到背面即可看到 AI 桌宠（长按背屏 2 秒可校准）。");
            nextStepBanner.setTextColor(Color.parseColor("#7CFFBE"));
            nextStepBanner.setBackground(roundedBg(Color.parseColor("#0D261D"), Color.parseColor("#2A7A58"), 12));
        }

        if (guardSwitch != null) {
            guardSwitch.setChecked(RearGuardService.isRunning);
        }
    }

    private void startDiscovery() {
        discoveredBox.removeAllViews();
        TextView scanning = new TextView(this);
        scanning.setText("正在搜索同一 Wi-Fi 下的电脑...");
        scanning.setTextColor(Color.parseColor("#7ADDF5"));
        scanning.setTextSize(12f);
        discoveredBox.addView(scanning);

        LanDiscovery.discoverAsync(hosts -> {
            lastHosts = hosts;
            if (hosts.size() == 1) {
                urlInput.setText(hosts.get(0).url);
                savePrefs();
                checkForUpdate(false);
            }
            renderDiscoveredHosts();
        });
    }

    private void renderDiscoveredHosts() {
        discoveredBox.removeAllViews();
        if (lastHosts == null || lastHosts.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("未自动搜到电脑（请确认手机与电脑同 Wi-Fi，或手动输入电脑端地址）");
            none.setTextColor(Color.parseColor("#7A92AA"));
            none.setTextSize(11.5f);
            discoveredBox.addView(none);
            return;
        }
        String activeUrl = urlInput.getText().toString().trim();
        for (LanDiscovery.DiscoveredHost h : lastHosts) {
            boolean selected = h.url.equals(activeUrl);
            String label = selected
                    ? ("✓ 已连接：" + h.host + " (" + h.ip + ":" + h.port + ")")
                    : ("发现电脑：" + h.host + " (" + h.ip + ":" + h.port + ")  · 点击选用");
            Button chip = makeButton(
                    label,
                    selected ? Color.parseColor("#0D2B20") : Color.parseColor("#0D2C38"),
                    selected ? Color.parseColor("#7CFFBE") : Color.parseColor("#8CF6FF"),
                    selected ? Color.parseColor("#2E8A63") : Color.parseColor("#2A7C94")
            );
            chip.setTextSize(12.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                urlInput.setText(h.url);
                savePrefs();
                renderDiscoveredHosts();
                checkForUpdate(false);
                showToast("已连接：" + h.host);
            });
            discoveredBox.addView(chip);
        }
    }
}
