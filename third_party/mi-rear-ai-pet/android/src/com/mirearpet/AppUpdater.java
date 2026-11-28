package com.mirearpet;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dual-source OTA updater (LAN PC + GitHub Releases):
 * 1) Checks local PC server GET <pc_url>/api/info (instant LAN updates)
 * 2) Also checks GitHub Releases API for nzz0991999-ai/xiaomi-rear-ai-pet (global viewer updates!)
 * 3) Picks whichever newer version is available and installs via native PackageInstaller session.
 */
public final class AppUpdater {
    private static final String GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/nzz0991999-ai/xiaomi-rear-ai-pet/releases/latest";

    public static final class UpdateInfo {
        public final boolean hasUpdate;
        public final long localVersionCode;
        public final String localVersionName;
        public final long remoteVersionCode;
        public final String remoteVersionName;
        public final String apkUrl;
        public final String source; // "lan" or "github"
        public final String error;

        public UpdateInfo(boolean hasUpdate, long localVersionCode, String localVersionName,
                          long remoteVersionCode, String remoteVersionName, String apkUrl,
                          String source, String error) {
            this.hasUpdate = hasUpdate;
            this.localVersionCode = localVersionCode;
            this.localVersionName = localVersionName;
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.apkUrl = apkUrl;
            this.source = source;
            this.error = error;
        }
    }

    public interface CheckCallback {
        void onResult(UpdateInfo info);
    }

    public interface InstallCallback {
        void onProgress(String statusText);
        void onDone(boolean ok, String detail);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private AppUpdater() {}

    public static long getLocalVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pi.getLongVersionCode();
            }
            //noinspection deprecation
            return pi.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String getLocalVersionName(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "0.0.0";
        } catch (Throwable t) {
            return "0.0.0";
        }
    }

    private static String normalizeBase(String baseUrl) {
        if (baseUrl == null) return "";
        String u = baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static int compareSemver(String a, String b) {
        if (a == null) a = "0";
        if (b == null) b = "0";
        String ca = a.trim().replaceFirst("^[vV]", "");
        String cb = b.trim().replaceFirst("^[vV]", "");
        String[] pa = ca.split("\\.");
        String[] pb = cb.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            String digits = s.replaceAll("[^0-9].*$", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void checkUpdateAsync(Context ctx, String baseUrl, CheckCallback cb) {
        final Context app = ctx.getApplicationContext();
        final String base = normalizeBase(baseUrl);
        final long localVc = getLocalVersionCode(app);
        final String localVn = getLocalVersionName(app);

        EXEC.execute(() -> {
            UpdateInfo best = null;
            String lastErr = "";

            // 1) Check local LAN PC server first (fastest, works offline)
            if (base.startsWith("http://") || base.startsWith("https://")) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(base + "/api/info");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2200);
                    conn.setReadTimeout(2200);
                    if (conn.getResponseCode() == 200) {
                        JSONObject json = new JSONObject(new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8));
                        boolean apkAvailable = json.optBoolean("apk_available", false);
                        long remoteVc = json.optLong("apk_version_code", 0);
                        String remoteVn = json.optString("apk_version_name", "");
                        boolean hasUpdate = apkAvailable && (remoteVc > localVc || compareSemver(remoteVn, localVn) > 0);
                        best = new UpdateInfo(hasUpdate, localVc, localVn, remoteVc, remoteVn,
                                base + "/download/MiRearPet.apk", "lan", "");
                    }
                } catch (Throwable t) {
                    lastErr = String.valueOf(t.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            // 2) Also check GitHub Releases so viewers always get author's latest release automatically!
            if (best == null || !best.hasUpdate) {
                HttpURLConnection gh = null;
                try {
                    URL url = new URL(GITHUB_LATEST_RELEASE_API);
                    gh = (HttpURLConnection) url.openConnection();
                    gh.setRequestMethod("GET");
                    gh.setConnectTimeout(3000);
                    gh.setReadTimeout(3000);
                    gh.setRequestProperty("Accept", "application/vnd.github+json");
                    gh.setRequestProperty("User-Agent", "MiRearPet/" + localVn);
                    if (gh.getResponseCode() == 200) {
                        JSONObject rel = new JSONObject(new String(readAll(gh.getInputStream()), StandardCharsets.UTF_8));
                        String tag = rel.optString("tag_name", "").replaceFirst("^[vV]", "");
                        String apkDownloadUrl = "";
                        JSONArray assets = rel.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject a = assets.optJSONObject(i);
                                if (a == null) continue;
                                String name = a.optString("name", "");
                                if (name.endsWith(".apk") && name.toLowerCase().contains("mirearpet")) {
                                    apkDownloadUrl = a.optString("browser_download_url", "");
                                    break;
                                }
                            }
                        }
                        if (!tag.isEmpty() && !apkDownloadUrl.isEmpty() && compareSemver(tag, localVn) > 0) {
                            best = new UpdateInfo(true, localVc, localVn, localVc + 1, tag,
                                    apkDownloadUrl, "github", "");
                        } else if (best == null && !tag.isEmpty()) {
                            best = new UpdateInfo(false, localVc, localVn, localVc, tag, apkDownloadUrl, "github", "");
                        }
                    }
                } catch (Throwable t) {
                    if (lastErr.isEmpty()) lastErr = String.valueOf(t.getMessage());
                } finally {
                    if (gh != null) gh.disconnect();
                }
            }

            final UpdateInfo finalInfo = best != null
                    ? best
                    : new UpdateInfo(false, localVc, localVn, 0, "", "", "", lastErr);
            MAIN.post(() -> cb.onResult(finalInfo));
        });
    }

    public static void downloadAndInstallAsync(Activity activity, String apkUrl, String versionName, InstallCallback cb) {
        installApkFromUrlAsync(activity, apkUrl, activity.getPackageName(), "v" + versionName, cb);
    }

    public static void installExternalApkAsync(Activity activity, String apkUrl, String targetPackage, String label, InstallCallback cb) {
        installApkFromUrlAsync(activity, apkUrl, targetPackage, label, cb);
    }

    private static void installApkFromUrlAsync(Activity activity, String apkUrl, String targetPackage, String label, InstallCallback cb) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                } catch (Throwable ignored) {}
                cb.onDone(false, "请先开启「允许来自此来源的应用」，返回后再点一次即可安装");
                return;
            }
        }

        final Context app = activity.getApplicationContext();
        cb.onProgress("正在下载 " + label + "...");
        DiagLog.log("Install", "下载安装包: " + apkUrl);

        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            PackageInstaller.Session session = null;
            try {
                // Follow HTTP->HTTPS redirects cleanly (GitHub Releases redirects to objects.githubusercontent.com)
                URL url = new URL(apkUrl);
                int redirects = 0;
                while (redirects < 5) {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "MiRearPet");
                    int code = conn.getResponseCode();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (loc == null || loc.isEmpty()) throw new RuntimeException("重定向地址为空");
                        url = new URL(url, loc);
                        redirects++;
                        continue;
                    }
                    if (code != 200) {
                        throw new RuntimeException("HTTP " + code);
                    }
                    break;
                }
                byte[] apkBytes = readAll(conn.getInputStream());
                if (apkBytes.length < 4096) {
                    throw new RuntimeException("安装包异常（大小=" + apkBytes.length + "）");
                }

                MAIN.post(() -> cb.onProgress("下载完成 (" + (apkBytes.length / 1024) + " KB)，正在拉起安装..."));
                DiagLog.log("Install", "下载完成 size=" + apkBytes.length + " bytes");

                PackageInstaller installer = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                if (targetPackage != null && !targetPackage.isEmpty()) {
                    params.setAppPackageName(targetPackage);
                }
                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);
                try (OutputStream out = session.openWrite("package.apk", 0, apkBytes.length)) {
                    out.write(apkBytes);
                    session.fsync(out);
                }

                Intent callbackIntent = new Intent(app, UpdateInstallReceiver.class);
                callbackIntent.setAction(UpdateInstallReceiver.ACTION_INSTALL_RESULT);
                int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    piFlags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(app, sessionId, callbackIntent, piFlags);
                session.commit(pi.getIntentSender());
                session.close();
                session = null;

                MAIN.post(() -> cb.onDone(true, "已拉起系统安装弹窗，点「安装/更新」即可！"));
            } catch (Throwable t) {
                if (session != null) {
                    try { session.abandon(); } catch (Throwable ignored) {}
                }
                String err = "安装失败: " + t.getMessage();
                DiagLog.log("Install", err);
                MAIN.post(() -> cb.onDone(false, err));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}
