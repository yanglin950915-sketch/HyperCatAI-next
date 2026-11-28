package com.mirearpet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app ring-buffer diagnostic logger with:
 * - Millisecond timestamps
 * - Live UI listener for MainActivity log viewer & 1-tap clipboard copy
 * - Best-effort async upload to PC server's /api/client-log endpoint
 */
public final class DiagLog {
    public interface Listener {
        void onLogUpdated();
    }

    private static final int MAX_LINES = 60;
    private static final List<String> LINES = new ArrayList<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NET_EXEC = Executors.newSingleThreadExecutor();
    private static volatile String serverBaseUrl = "";

    private DiagLog() {}

    public static void setServerUrl(String url) {
        if (url == null) return;
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        serverBaseUrl = u;
    }

    public static void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    public static void log(String tag, String msg) {
        if (msg == null) msg = "";
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String[] split = msg.replace("\r", "").split("\n");
        StringBuilder batch = new StringBuilder();
        synchronized (LINES) {
            for (String part : split) {
                if (part.trim().isEmpty()) continue;
                String line = "[" + ts + "] [" + tag + "] " + part;
                LINES.add(line);
                batch.append(line).append('\n');
                while (LINES.size() > MAX_LINES) {
                    LINES.remove(0);
                }
            }
        }
        notifyListeners();
        final String payload = batch.toString();
        if (!payload.isEmpty()) {
            uploadBestEffort(payload);
        }
    }

    public static String getAllText() {
        synchronized (LINES) {
            if (LINES.isEmpty()) return "（暂无日志）";
            StringBuilder sb = new StringBuilder();
            for (String l : LINES) {
                sb.append(l).append('\n');
            }
            return sb.toString().trim();
        }
    }

    public static String getTailText(int maxLines) {
        synchronized (LINES) {
            if (LINES.isEmpty()) return "（暂无日志，点上方按钮操作后会自动记录详细轨迹）";
            int start = Math.max(0, LINES.size() - maxLines);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < LINES.size(); i++) {
                sb.append(LINES.get(i)).append('\n');
            }
            return sb.toString().trim();
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
        notifyListeners();
    }

    private static void notifyListeners() {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                try {
                    l.onLogUpdated();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void uploadBestEffort(String text) {
        final String base = serverBaseUrl;
        if (base == null || base.isEmpty() || base.contains("127.0.0.1") == false && !base.startsWith("http")) {
            return;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) return;
        NET_EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(base + "/api/client-log");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
                conn.getResponseCode();
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
