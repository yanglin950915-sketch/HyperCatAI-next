package com.mirearpet;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Display;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.shizuku.api.BinderContainer;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.ShizukuProvider;

/**
 * Zero-dependency Shizuku IPC bridge & Xiaomi Rear Display projection controller.
 */
public final class ShizukuBridge {
    public interface Listener {
        void onShizukuStateChanged();
    }

    public static final int REQ_PERMISSION = 1001;
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static final String KEY_ATTACH_API_VERSION = "shizuku:attach-api-version";
    private static final String KEY_ATTACH_PACKAGE_NAME = "shizuku:attach-package-name";
    private static final String KEY_REPLY_UID = "shizuku:attach-reply-uid";
    private static final String KEY_REPLY_VERSION = "shizuku:attach-reply-version";
    private static final String KEY_REPLY_GRANTED = "shizuku:attach-reply-permission-granted";
    private static final String KEY_PERM_ALLOWED = "shizuku:request-permission-reply-allowed";

    private static volatile Context appContext;
    private static volatile IBinder rawBinder;
    private static volatile IShizukuService service;
    private static volatile boolean permissionGranted = false;
    private static volatile int serverVersion = -1;
    private static volatile int serverUid = -1;
    private static volatile String lastError = "";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final ExecutorService DAEMON_EXEC = Executors.newSingleThreadExecutor();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile IRemoteProcess keepAliveProc;
    private static volatile java.io.OutputStream keepAliveStdin;

    private static final IShizukuApplication.Stub APP_CALLBACK = new IShizukuApplication.Stub() {
        @Override
        public void bindApplication(Bundle data) {
            if (data != null) {
                serverUid = data.getInt(KEY_REPLY_UID, serverUid);
                serverVersion = data.getInt(KEY_REPLY_VERSION, serverVersion);
                permissionGranted = data.getBoolean(KEY_REPLY_GRANTED, permissionGranted);
            }
            notifyListeners();
        }

        @Override
        public void dispatchRequestPermissionResult(int requestCode, Bundle data) {
            if (requestCode == REQ_PERMISSION && data != null) {
                permissionGranted = data.getBoolean(KEY_PERM_ALLOWED, false);
                notifyListeners();
            }
        }

        @Override
        public void showPermissionConfirmation(int requestPid, int requestUid, String requestPackageName, int requestCode) {
            // Only used by Shizuku manager itself
        }
    };

    private ShizukuBridge() {}

    public static void initContext(Context ctx) {
        if (ctx != null) {
            appContext = ctx.getApplicationContext();
        }
    }

    public static void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void notifyListeners() {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                try {
                    l.onShizukuStateChanged();
                } catch (Throwable ignored) {}
            }
        });
    }

    public static boolean isShizukuInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isBinderAlive() {
        IBinder b = rawBinder;
        return b != null && b.pingBinder();
    }

    public static boolean isPermissionGranted() {
        if (!isBinderAlive()) return false;
        if (permissionGranted) return true;
        try {
            IShizukuService s = service;
            if (s != null) {
                boolean g = s.checkSelfPermission();
                permissionGranted = g;
                return g;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static int getServerVersion() {
        return serverVersion;
    }

    public static int getServerUid() {
        return serverUid;
    }

    public static String getLastError() {
        return lastError;
    }

    public static synchronized void onBinderReceived(Context ctx, IBinder binder) {
        initContext(ctx);
        if (binder == null || !binder.pingBinder()) return;
        rawBinder = binder;
        service = IShizukuService.Stub.asInterface(binder);
        lastError = "";

        try {
            binder.linkToDeath(() -> {
                rawBinder = null;
                service = null;
                permissionGranted = false;
                notifyListeners();
            }, 0);
        } catch (Throwable ignored) {}

        // Perform Shizuku v13 handshake (fallback to v11)
        try {
            Bundle args = new Bundle();
            args.putInt(KEY_ATTACH_API_VERSION, 13);
            args.putString(KEY_ATTACH_PACKAGE_NAME, ctx.getPackageName());
            service.attachApplication(APP_CALLBACK, args);
        } catch (Throwable t13) {
            try {
                service.attachApplicationV11(APP_CALLBACK, ctx.getPackageName());
            } catch (Throwable t11) {
                lastError = "attach 失败: " + t11.getMessage();
            }
        }

        try {
            serverVersion = service.getVersion();
            serverUid = service.getUid();
        } catch (Throwable t) {
            if (lastError.isEmpty()) lastError = "读取 Shizuku 版本失败: " + t.getMessage();
        }

        try {
            permissionGranted = service.checkSelfPermission();
        } catch (Throwable t) {
            if (lastError.isEmpty()) lastError = "检查 Shizuku 权限失败: " + t.getMessage();
        }

        notifyListeners();
    }

    /**
     * Actively pull binder from Shizuku Manager ContentProvider if push hasn't arrived yet.
     */
    public static boolean requestBinderFromManager(Context ctx) {
        initContext(ctx);
        if (isBinderAlive()) {
            isPermissionGranted();
            notifyListeners();
            return true;
        }
        try {
            Uri uri = Uri.parse("content://" + ShizukuProvider.MANAGER_AUTHORITY);
            Bundle reply = ctx.getContentResolver().call(uri, ShizukuProvider.METHOD_GET_BINDER, null, null);
            if (reply != null) {
                reply.setClassLoader(BinderContainer.class.getClassLoader());
                BinderContainer container = reply.getParcelable(ShizukuProvider.EXTRA_BINDER);
                if (container != null && container.binder != null && container.binder.pingBinder()) {
                    onBinderReceived(ctx, container.binder);
                    return true;
                }
            }
        } catch (Throwable t) {
            lastError = String.valueOf(t.getMessage());
        }
        notifyListeners();
        return false;
    }

    public static String requestPermission(Context ctx) {
        if (!isBinderAlive()) {
            requestBinderFromManager(ctx);
        }
        IShizukuService s = service;
        if (s == null) {
            lastError = "未连接到 Shizuku 服务，请先打开 Shizuku 确认正在运行";
            return lastError;
        }
        try {
            if (s.checkSelfPermission()) {
                permissionGranted = true;
                lastError = "";
                notifyListeners();
                return "granted";
            }
            s.requestPermission(REQ_PERMISSION);
            lastError = "";
            return "requested";
        } catch (Throwable t) {
            lastError = "授权请求失败: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            notifyListeners();
            return lastError;
        }
    }

    public static final class ShellResult {
        public final boolean ok;
        public final int exitCode;
        public final String output;

        public ShellResult(boolean ok, int exitCode, String output) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static ShellResult runShellSync(String[] cmd) {
        IShizukuService s = service;
        if (s == null || !isBinderAlive()) {
            return new ShellResult(false, -1, "Shizuku 未连接");
        }
        IRemoteProcess proc = null;
        try {
            proc = s.newProcess(cmd, null, null);
            if (proc == null) {
                return new ShellResult(false, -1, "newProcess 返回空");
            }
            StringBuilder sb = new StringBuilder();
            try (ParcelFileDescriptor inFd = proc.getInputStream();
                 ParcelFileDescriptor errFd = proc.getErrorStream()) {
                if (inFd != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(inFd)))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                }
                if (errFd != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(errFd)))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                }
            } catch (Throwable ignored) {}
            int code = proc.waitFor();
            return new ShellResult(code == 0, code, sb.toString().trim());
        } catch (Throwable t) {
            lastError = String.valueOf(t.getMessage());
            return new ShellResult(false, -1, lastError);
        } finally {
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static List<Display> getSecondaryDisplays(Context ctx) {
        List<Display> list = new ArrayList<>();
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return list;
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                list.add(d);
            }
        }
        return list;
    }

    public static int detectRearDisplayId(Context ctx) {
        List<Display> secs = getSecondaryDisplays(ctx);
        if (secs.isEmpty()) {
            return 1; // Xiaomi rear screen default displayId
        }
        // Prefer display matching Xiaomi rear screen (~976x596) or first physical non-0 display
        for (Display d : secs) {
            Display.Mode mode = d.getMode();
            int w = Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            int h = Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            if (w >= 700 && w <= 1200 && h >= 400 && h <= 800) {
                return d.getDisplayId();
            }
        }
        return secs.get(0).getDisplayId();
    }

    public static boolean isDisplayAwake(Context ctx, int displayId) {
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return false;
        Display d = dm.getDisplay(displayId);
        if (d == null) return false;
        int state = d.getState();
        return state == Display.STATE_ON || state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND;
    }

    public interface LaunchCallback {
        void onComplete(boolean ok, String detail);
    }

    /**
     * Launches RearPetActivity directly onto Xiaomi's rear display (displayId) via Shizuku,
     * with automatic fallback to ActivityOptions.setLaunchDisplayId.
     */
    public static void launchOnRearDisplay(Context ctx, String url, int displayId, LaunchCallback cb) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            if (!isBinderAlive()) {
                requestBinderFromManager(app);
            }
            if (isBinderAlive() && isPermissionGranted()) {
                String apkPath = app.getApplicationInfo().sourceDir;
                // If an old RearPetActivity instance already exists (e.g. after rear screen timeout/cover),
                // automatically withdraw + destroy it on Display 0 FIRST so every tap starts clean with a
                // fresh Display-1 WebView — user never has to tap "退出" manually before re-projecting!
                if (RearPetActivity.hasRearInstance()) {
                    DiagLog.log("Bridge", "检测到已有背屏实例，自动先安全撤回再重新投射");
                    String[] wdCmd = new String[]{
                            "sh", "-c",
                            "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper withdraw \"$2\"",
                            "_",
                            apkPath,
                            app.getPackageName()
                    };
                    ShellResult wdRes = runShellSync(wdCmd);
                    DiagLog.log("Helper", "auto-withdraw exit=" + wdRes.exitCode + "\n" + wdRes.output);
                    RearPetActivity.finishRearInstanceIfRunning();
                    try { Thread.sleep(550); } catch (InterruptedException ignored) {}
                }

                // Strictly use privileged app_process + IActivityTaskManager.moveRootTaskToDisplay
                // (never fallback to raw `am start --display 1`, which triggers HyperOS SurfaceFlinger misrouting).
                String[] helperCmd = new String[]{
                        "sh", "-c",
                        "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper project \"$2\" \"$3\" \"$4\"",
                        "_",
                        apkPath,
                        app.getPackageName(),
                        String.valueOf(displayId),
                        url
                };
                DiagLog.log("Bridge", "launchOnRearDisplay start displayId=" + displayId + " url=" + url);
                ShellResult helperRes = runShellSync(helperCmd);
                DiagLog.log("Helper", "exit=" + helperRes.exitCode + "\n" + helperRes.output);
                if (helperRes.ok && helperRes.output.contains("OK:")) {
                    postCallback(cb, true, "已成功投送到妙享背屏 (displayId=" + displayId + ")，翻到背面看看吧！");
                    return;
                }
                String diag = helperRes.output == null ? "" : helperRes.output.trim();
                postCallback(cb, false, "背屏投射未成功（" + (diag.isEmpty() ? "请重试" : diag) + "）");
                return;
            }

            postCallback(cb, false, "请先启动并授权 Shizuku，再点击「一键投到妙享背屏」");
        });
    }

    /**
     * Silent background guard restore:
     * Calls RearShellHelper's `guard_repin` mode which yields immediately to Camera / protected UIs,
     * prefers in-place Display-1 refront without touching Display 0, and never pops MainActivity over user apps.
     */
    public static void guardRepinOnRearDisplay(Context ctx, String url, int displayId, LaunchCallback cb) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            if (!isBinderAlive()) {
                requestBinderFromManager(app);
            }
            if (!isBinderAlive() || !isPermissionGranted()) {
                postCallback(cb, false, "Shizuku 未授权或未连接");
                return;
            }
            String apkPath = app.getApplicationInfo().sourceDir;
            String[] helperCmd = new String[]{
                    "sh", "-c",
                    "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper guard_repin \"$2\" \"$3\" \"$4\"",
                    "_",
                    apkPath,
                    app.getPackageName(),
                    String.valueOf(displayId),
                    url
            };
            ShellResult res = runShellSync(helperCmd);
            String out = res.output == null ? "" : res.output.trim();
            if (!out.contains("OK:already_on_rear")) {
                DiagLog.log("Guard", "guard_repin exit=" + res.exitCode + " " + out);
            }
            postCallback(cb, res.ok && out.contains("OK:"), out);
        });
    }

    /**
     * Collects read-only display & WindowManager diagnostics without launching or moving any activity.
     */
    public static void collectDiagnostics(Context ctx, LaunchCallback cb) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            if (!isBinderAlive()) {
                requestBinderFromManager(app);
            }
            if (!isBinderAlive() || !isPermissionGranted()) {
                DiagLog.log("Diag", "Shizuku 未授权或未连接");
                postCallback(cb, false, "Shizuku 未授权或未连接");
                return;
            }
            String apkPath = app.getApplicationInfo().sourceDir;
            String[] cmd = new String[]{
                    "sh", "-c",
                    "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper diag \"$2\"",
                    "_",
                    apkPath,
                    app.getPackageName()
            };
            ShellResult res = runShellSync(cmd);
            DiagLog.log("Diag", "exit=" + res.exitCode + "\n" + res.output);
            postCallback(cb, res.ok, res.ok ? "已采集系统屏幕与任务诊断日志" : "采集失败: " + res.output);
        });
    }

    /**
     * Starts a persistent privileged shell daemon that holds a Display-1-only SCREEN_BRIGHT_WAKE_LOCK
     * and responds instantly to "refront" / "wake" commands when the front screen locks.
     */
    public static void startRearKeepAlive(Context ctx, int displayId) {
        final Context app = ctx.getApplicationContext();
        DAEMON_EXEC.execute(() -> {
            stopRearKeepAliveInternal();
            if (!isBinderAlive()) requestBinderFromManager(app);
            IShizukuService s = service;
            if (s == null || !isBinderAlive() || !isPermissionGranted()) {
                DiagLog.log("KeepAlive", "Shizuku 未就绪，跳过背屏常亮锁");
                return;
            }
            try {
                String apkPath = app.getApplicationInfo().sourceDir;
                String[] cmd = new String[]{
                        "sh", "-c",
                        "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper keepalive \"$2\" \"$3\"",
                        "_",
                        apkPath,
                        app.getPackageName(),
                        String.valueOf(displayId)
                };
                IRemoteProcess proc = s.newProcess(cmd, null, null);
                if (proc == null) return;
                keepAliveProc = proc;
                ParcelFileDescriptor outFd = proc.getOutputStream();
                if (outFd != null) {
                    keepAliveStdin = new ParcelFileDescriptor.AutoCloseOutputStream(outFd);
                }
                ParcelFileDescriptor inFd = proc.getInputStream();
                if (inFd != null) {
                    new Thread(() -> {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(inFd)))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                DiagLog.log("KeepAlive", line);
                            }
                        } catch (Throwable ignored) {}
                    }, "KeepAliveReader").start();
                }
                DiagLog.log("KeepAlive", "已启动背屏常亮守护进程 (displayId=" + displayId + ")");
            } catch (Throwable t) {
                DiagLog.log("KeepAlive", "启动失败: " + t.getMessage());
            }
        });
    }

    public static void sendKeepAliveCommand(String cmd) {
        DAEMON_EXEC.execute(() -> {
            java.io.OutputStream os = keepAliveStdin;
            if (os == null) return;
            try {
                os.write((cmd + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            } catch (Throwable t) {
                DiagLog.log("KeepAlive", "发送指令失败(" + cmd + "): " + t.getMessage());
            }
        });
    }

    public static void stopRearKeepAlive() {
        DAEMON_EXEC.execute(ShizukuBridge::stopRearKeepAliveInternal);
    }

    private static void stopRearKeepAliveInternal() {
        java.io.OutputStream os = keepAliveStdin;
        keepAliveStdin = null;
        if (os != null) {
            try {
                os.write("quit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
                os.close();
            } catch (Throwable ignored) {}
        }
        IRemoteProcess proc = keepAliveProc;
        keepAliveProc = null;
        if (proc != null) {
            try { proc.destroy(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Wakes ONLY the target rear display (displayId=1) without waking or touching Display 0.
     */
    public static void wakeRearDisplayOnly(Context ctx, int displayId) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            if (!isBinderAlive()) {
                requestBinderFromManager(app);
            }
            if (!isBinderAlive() || !isPermissionGranted()) return;
            String apkPath = app.getApplicationInfo().sourceDir;
            String[] cmd = new String[]{
                    "sh", "-c",
                    "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper wake \"$2\" \"$3\"",
                    "_",
                    apkPath,
                    app.getPackageName(),
                    String.valueOf(displayId)
            };
            ShellResult res = runShellSync(cmd);
            DiagLog.log("Wake", "wakeDisplay(" + displayId + ") exit=" + res.exitCode + " " + res.output);
        });
    }

    /**
     * Safely withdraws RearPetActivity from Display 1 back to Display 0 BEFORE finishing it,
     * so Xiaomi's rear launcher restores cleanly and subsequent re-projections never freeze Display 0.
     */
    public static void withdrawFromRearDisplay(Context ctx, LaunchCallback cb) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            DiagLog.log("Bridge", "withdrawFromRearDisplay start");
            if (!isBinderAlive()) {
                requestBinderFromManager(app);
            }
            if (isBinderAlive() && isPermissionGranted()) {
                String apkPath = app.getApplicationInfo().sourceDir;
                String[] helperCmd = new String[]{
                        "sh", "-c",
                        "CLASSPATH=\"$1\" exec /system/bin/app_process /system/bin com.mirearpet.RearShellHelper withdraw \"$2\"",
                        "_",
                        apkPath,
                        app.getPackageName()
                };
                ShellResult res = runShellSync(helperCmd);
                DiagLog.log("Helper", "withdraw exit=" + res.exitCode + "\n" + res.output);
            }
            MAIN.post(() -> {
                RearPetActivity.finishRearInstanceIfRunning();
                if (cb != null) {
                    cb.onComplete(true, "已安全撤回并关闭背屏桌宠");
                }
            });
        });
    }

    private static boolean tryStandardDisplayLaunch(Context ctx, String url, int displayId) {
        try {
            Intent intent = new Intent(ctx, RearPetActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("rear_mode", true);
            intent.putExtra("target_display", displayId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(displayId);
                ctx.startActivity(intent, opts.toBundle());
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void postCallback(LaunchCallback cb, boolean ok, String detail) {
        if (cb == null) return;
        MAIN.post(() -> cb.onComplete(ok, detail));
    }
}
