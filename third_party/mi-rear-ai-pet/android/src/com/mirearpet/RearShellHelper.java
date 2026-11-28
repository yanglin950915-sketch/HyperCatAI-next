package com.mirearpet;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Privileged shell-process helper executed via Shizuku's newProcess + app_process.
 * Emits detailed step-by-step LOG lines to stdout for real-device debugging.
 */
public final class RearShellHelper {

    private static final class TaskInfo {
        final int rootId;
        final int displayId;
        final boolean visible;
        final String topComponent;
        final int childCount;

        TaskInfo(int rootId, int displayId, boolean visible, String topComponent, int childCount) {
            this.rootId = rootId;
            this.displayId = displayId;
            this.visible = visible;
            this.topComponent = topComponent;
            this.childCount = childCount;
        }
    }

    public static void main(String[] args) {
        try {
            try {
                Looper.prepareMainLooper();
            } catch (Throwable ignored) {}

            if (args == null || args.length < 2) {
                System.out.println("FAIL:invalid_args");
                System.exit(2);
            }

            String mode = args[0];
            String pkg = args[1];
            System.out.println("LOG:start mode=" + mode + " pkg=" + pkg + " uid=" + android.os.Process.myUid());

            Class<?> atmClass = Class.forName("android.app.IActivityTaskManager");
            IBinder atmBinder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "activity_task");
            Object atm = Class.forName("android.app.IActivityTaskManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, atmBinder);
            if (atm == null) {
                System.out.println("FAIL:atm_null");
                System.exit(1);
            }

            Method getRoots = atmClass.getMethod("getAllRootTaskInfos");
            Method moveRoot = atmClass.getMethod("moveRootTaskToDisplay", int.class, int.class);

            if ("diag".equals(mode)) {
                dumpEnvironment(atm, getRoots, pkg);
                System.out.println("OK:diag");
                System.exit(0);
            }

            if ("withdraw".equals(mode)) {
                handleWithdraw(atm, getRoots, moveRoot, pkg);
                return;
            }

            if ("wake".equals(mode)) {
                int targetDisp = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
                wakeDisplay(targetDisp);
                System.out.println("OK:woke:display=" + targetDisp);
                System.exit(0);
            }

            if ("keepalive".equals(mode)) {
                int targetDisp = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
                handleKeepAlive(atm, atmClass, getRoots, moveRoot, pkg, targetDisp);
                return;
            }

            if ("guard_repin".equals(mode)) {
                if (args.length < 4) {
                    System.out.println("FAIL:invalid_args");
                    System.exit(2);
                }
                int displayId = Integer.parseInt(args[2]);
                String url = args[3];
                handleGuardRepin(atm, atmClass, getRoots, moveRoot, pkg, displayId, url);
                return;
            }

            if (!"project".equals(mode) || args.length < 4) {
                System.out.println("FAIL:invalid_args");
                System.exit(2);
            }

            int displayId = Integer.parseInt(args[2]);
            String url = args[3];
            handleProject(atm, getRoots, moveRoot, pkg, displayId, url);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.out.println("FAIL:exception:" + cause.getClass().getSimpleName() + ":" + oneLine(String.valueOf(cause.getMessage())));
            System.exit(1);
        }
    }

    private static void dumpEnvironment(Object atm, Method getRoots, String pkg) throws Exception {
        dumpRoots("snapshot", atm, getRoots, pkg);
        String disp = runCmd("sh", "-c",
                "dumpsys display | grep -E 'mState=|DisplayDeviceInfo\\{|DisplayViewport\\{' | head -n 20");
        System.out.println("LOG:displays=" + oneLine(disp, 500));
        String win = runCmd("sh", "-c",
                "dumpsys window displays | grep -E 'Display: mDisplayId=|mCurrentFocus=|mFocusedApp=|Window\\{.*(" + pkg + "|subscreen)' | head -n 25");
        System.out.println("LOG:windows=" + oneLine(win, 600));
    }

    private static void dumpRoots(String label, Object atm, Method getRoots, String pkg) throws Exception {
        List<?> roots = (List<?>) getRoots.invoke(atm);
        if (roots == null) {
            System.out.println("LOG:roots_" + label + "=null");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Object root : roots) {
            Class<?> type = root.getClass();
            int rootId = type.getField("taskId").getInt(root);
            int dId = type.getField("displayId").getInt(root);
            boolean vis = type.getField("visible").getBoolean(root);
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            String topStr = top != null ? top.flattenToShortString() : "null";
            String[] names = (String[]) type.getField("childTaskNames").get(root);
            int nChildren = names != null ? names.length : 0;
            boolean relevant = vis || dId != 0 || topStr.contains(pkg) || topStr.contains("subscreen");
            if (relevant && count < 12) {
                sb.append("{id=").append(rootId)
                        .append(",d=").append(dId)
                        .append(",vis=").append(vis)
                        .append(",ch=").append(nChildren)
                        .append(",top=").append(topStr)
                        .append("} ");
                count++;
            }
        }
        System.out.println("LOG:roots_" + label + "(total=" + roots.size() + ")=" + sb.toString().trim());
    }

    /**
     * Persistent shell daemon held while Guard Mode is enabled:
     * 1) Acquires a rear-display-only SCREEN_BRIGHT_WAKE_LOCK on displayId=1 (identical to MRSS RearPowerControl)
     *    so locking Display 0 does NOT automatically blank Display 1.
     * 2) Listens on stdin for "refront" / "wake" / "pause_lock" / "resume_lock" / "quit" commands with 0% idle CPU.
     */
    private static void handleKeepAlive(Object atm, Class<?> atmClass, Method getRoots, Method moveRoot,
                                        String pkg, int displayId) {
        Object power = null;
        Method acquireMethod = null;
        Method releaseMethod = null;
        android.os.Binder wakeToken = new android.os.Binder();
        boolean lockHeld = false;
        boolean cameraPaused = false;
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            IBinder powerBinder = (IBinder) smClass.getMethod("getService", String.class).invoke(null, "power");
            Class<?> powerApi = Class.forName("android.os.IPowerManager");
            power = Class.forName("android.os.IPowerManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, powerBinder);
            releaseMethod = powerApi.getMethod("releaseWakeLock", IBinder.class, int.class);

            // Find acquireWakeLock matching Android 14/15/16/17 signature with displayId parameter
            for (Method m : powerApi.getMethods()) {
                if ("acquireWakeLock".equals(m.getName())) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 8 && pts[0] == IBinder.class && pts[1] == int.class && pts[6] == int.class) {
                        acquireMethod = m;
                        break;
                    }
                }
            }
            if (acquireMethod != null) {
                acquireMethod.invoke(power, wakeToken,
                        android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                        "MiRearPet::rear-screen-on",
                        "com.android.shell",
                        null, null, displayId, null);
                lockHeld = true;
                System.out.println("LOG:rear_wakelock=ACQUIRED displayId=" + displayId);
            } else {
                StringBuilder sigs = new StringBuilder();
                for (Method m : powerApi.getMethods()) {
                    if ("acquireWakeLock".equals(m.getName())) {
                        sigs.append(m.getParameterCount()).append("args ");
                    }
                }
                System.out.println("LOG:rear_wakelock=NO_MATCH sigs=" + sigs.toString().trim());
            }
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            System.out.println("LOG:rear_wakelock=ERR:" + c.getClass().getSimpleName() + ":" + oneLine(String.valueOf(c.getMessage()), 160));
        }
        System.out.flush();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = br.readLine()) != null) {
                String cmd = line.trim();
                if ("quit".equals(cmd)) break;
                if ("pause_lock".equals(cmd)) {
                    cameraPaused = true;
                    if (lockHeld && power != null && releaseMethod != null) {
                        try {
                            releaseMethod.invoke(power, wakeToken, 0);
                            lockHeld = false;
                            System.out.println("LOG:rear_wakelock=PAUSED_FOR_CAMERA");
                        } catch (Throwable t) {
                            System.out.println("LOG:rear_wakelock_pause_err=" + oneLine(String.valueOf(t.getMessage()), 120));
                        }
                    }
                    System.out.flush();
                } else if ("resume_lock".equals(cmd)) {
                    cameraPaused = false;
                    if (!lockHeld && power != null && acquireMethod != null) {
                        try {
                            acquireMethod.invoke(power, wakeToken,
                                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                                    "MiRearPet::rear-screen-on",
                                    "com.android.shell",
                                    null, null, displayId, null);
                            lockHeld = true;
                            System.out.println("LOG:rear_wakelock=RESUMED displayId=" + displayId);
                        } catch (Throwable t) {
                            System.out.println("LOG:rear_wakelock_resume_err=" + oneLine(String.valueOf(t.getMessage()), 120));
                        }
                    }
                    System.out.flush();
                } else if ("wake".equals(cmd)) {
                    try {
                        if (cameraPaused) {
                            System.out.println("LOG:wake_skip=camera_paused");
                        } else {
                            String blocker = findYieldBlocker(atm, getRoots, pkg, displayId);
                            if (blocker != null) {
                                System.out.println("LOG:wake_skip=" + blocker);
                            } else {
                                wakeDisplay(displayId);
                            }
                        }
                    } catch (Throwable ignored) {}
                    System.out.flush();
                } else if ("refront".equals(cmd)) {
                    try {
                        if (cameraPaused) {
                            System.out.println("LOG:lock_refront_skip=camera_paused");
                            System.out.flush();
                            continue;
                        }
                        String blocker = findYieldBlocker(atm, getRoots, pkg, displayId);
                        if (blocker != null) {
                            System.out.println("LOG:lock_refront_skip=" + blocker);
                            System.out.flush();
                            continue;
                        }
                        dumpRoots("lock_before", atm, getRoots, pkg);
                        String ds = runCmd("sh", "-c", "dumpsys display | grep -E 'mState=' | head -n 4");
                        System.out.println("LOG:lock_disp=" + oneLine(ds, 160));
                        TaskInfo rearTask = findTask(atm, getRoots, pkg, "RearPetActivity");
                        if (rearTask != null && rearTask.displayId == displayId && !rearTask.visible) {
                            System.out.println("LOG:lock_refront bringing root=" + rearTask.rootId + " back on top of d=" + displayId);
                            try {
                                moveRoot.invoke(atm, rearTask.rootId, displayId);
                            } catch (Throwable ignored) {}
                            try {
                                Method setFocused = atmClass.getMethod("setFocusedRootTask", int.class);
                                setFocused.invoke(atm, rearTask.rootId);
                            } catch (Throwable ignored) {}
                            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            dumpRoots("lock_after", atm, getRoots, pkg);
                        }
                        wakeDisplay(displayId);
                    } catch (Throwable t) {
                        System.out.println("LOG:lock_refront_err=" + oneLine(String.valueOf(t.getMessage()), 140));
                    }
                    System.out.flush();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (lockHeld && power != null && releaseMethod != null) {
                try {
                    releaseMethod.invoke(power, wakeToken, 0);
                    System.out.println("LOG:rear_wakelock=RELEASED");
                } catch (Throwable ignored) {}
            }
            System.out.flush();
        }
    }

    /**
     * Silent background guard restore:
     * 1) Yields immediately if Camera or a protected system UI is active on Display 0 or Display 1.
     * 2) Prefers in-place Display-1 refront without touching Display 0 at all.
     * 3) Only performs Display-0 staging when RearPetActivity is dead AND Display 0 is on Launcher or our own app,
     *    and returns Display 0 back to Home Launcher (NEVER hijacking Display 0 with MainActivity).
     */
    private static void handleGuardRepin(Object atm, Class<?> atmClass, Method getRoots, Method moveRoot,
                                         String pkg, int displayId, String url) throws Exception {
        String blocker = findYieldBlocker(atm, getRoots, pkg, displayId);
        if (blocker != null) {
            System.out.println("OK:yield:" + blocker);
            System.exit(0);
        }

        TaskInfo existing = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (existing != null && existing.displayId == displayId) {
            if (existing.visible) {
                System.out.println("OK:already_on_rear:root=" + existing.rootId + ":display=" + displayId);
                System.exit(0);
            }
            // Step A: Try silent in-place refront on Display 1 without ever touching Display 0
            System.out.println("LOG:guard_refront_inplace root=" + existing.rootId + " d=" + displayId);
            try {
                moveRoot.invoke(atm, existing.rootId, displayId);
            } catch (Throwable ignored) {}
            try {
                Method setFocused = atmClass.getMethod("setFocusedRootTask", int.class);
                setFocused.invoke(atm, existing.rootId);
            } catch (Throwable ignored) {}
            try { Thread.sleep(220); } catch (InterruptedException ignored) {}

            TaskInfo afterInPlace = findTask(atm, getRoots, pkg, "RearPetActivity");
            if (afterInPlace != null && afterInPlace.displayId == displayId && afterInPlace.visible) {
                wakeDisplay(displayId);
                System.out.println("OK:refront_silent:root=" + afterInPlace.rootId + ":display=" + displayId);
                System.exit(0);
            }
            existing = afterInPlace;
        }

        // Step B: Cold rebuild needed. Only stage on Display 0 when Display 0 is idle on Launcher or our own app!
        TaskInfo frontTask = findTopVisibleTaskOnDisplay(atm, getRoots, 0);
        if (!isSafeFrontForStaging(frontTask, pkg)) {
            String frontComp = frontTask != null ? frontTask.topComponent : "unknown";
            System.out.println("OK:defer_front_busy:" + frontComp);
            System.exit(0);
        }

        blocker = findYieldBlocker(atm, getRoots, pkg, displayId);
        if (blocker != null) {
            System.out.println("OK:yield:" + blocker);
            System.exit(0);
        }

        boolean frontWasSelf = frontTask != null && frontTask.topComponent != null
                && frontTask.topComponent.startsWith(pkg + "/");
        System.out.println("LOG:guard_rebuild front=" + (frontTask != null ? frontTask.topComponent : "null")
                + " frontWasSelf=" + frontWasSelf);

        if (existing != null && existing.displayId != 0) {
            moveRoot.invoke(atm, existing.rootId, 0);
            try { Thread.sleep(450); } catch (InterruptedException ignored) {}
            existing = findTask(atm, getRoots, pkg, "RearPetActivity");
        }

        if (existing == null) {
            String startOut = runCmd("am", "start", "-W",
                    "--display", "0",
                    "-n", pkg + "/.RearPetActivity",
                    "--es", "url", url,
                    "--ez", "rear_mode", "true",
                    "--ei", "target_display", String.valueOf(displayId));
            existing = waitForTask(atm, getRoots, pkg, "RearPetActivity", 2200);
            if (existing == null) {
                System.out.println("FAIL:guard_task_not_found:" + oneLine(startOut, 160));
                System.exit(1);
            }
            if (existing.displayId != 0) {
                moveRoot.invoke(atm, existing.rootId, 0);
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
            try { Thread.sleep(260); } catch (InterruptedException ignored) {}
        }

        // Return Display 0 to Home Launcher (or MainActivity ONLY if user was already inside our app)
        if (frontWasSelf) {
            runCmd("am", "start", "-W",
                    "--display", "0",
                    "-n", pkg + "/.MainActivity",
                    "--activity-reorder-to-front");
        } else {
            runCmd("am", "start", "-W",
                    "--display", "0",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.HOME");
        }
        try { Thread.sleep(720); } catch (InterruptedException ignored) {}

        // Re-check blocker right before moving to Display 1 in case Camera opened mid-transition
        blocker = findYieldBlocker(atm, getRoots, pkg, displayId);
        if (blocker != null) {
            System.out.println("OK:yield_mid:" + blocker);
            System.exit(0);
        }

        moveRoot.invoke(atm, existing.rootId, displayId);
        try { Thread.sleep(240); } catch (InterruptedException ignored) {}
        TaskInfo afterMove = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (afterMove != null && afterMove.displayId == displayId) {
            wakeDisplay(displayId);
            System.out.println("OK:guard_rebuilt:root=" + afterMove.rootId + ":display=" + displayId + ":vis=" + afterMove.visible);
            System.exit(0);
        }

        System.out.println("FAIL:guard_move_not_confirmed:root=" + existing.rootId);
        System.exit(1);
    }

    private static void handleWithdraw(Object atm, Method getRoots, Method moveRoot, String pkg) throws Exception {
        dumpRoots("before_withdraw", atm, getRoots, pkg);
        TaskInfo existing = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (existing == null) {
            System.out.println("OK:none");
            System.exit(0);
        }
        System.out.println("LOG:withdraw_target root=" + existing.rootId + " disp=" + existing.displayId + " vis=" + existing.visible);
        if (existing.displayId != 0) {
            moveRoot.invoke(atm, existing.rootId, 0);
            try { Thread.sleep(260); } catch (InterruptedException ignored) {}
        }
        dumpRoots("after_withdraw", atm, getRoots, pkg);
        System.out.println("OK:withdrawn:root=" + existing.rootId);
        System.exit(0);
    }

    private static void handleProject(Object atm, Method getRoots, Method moveRoot,
                                      String pkg, int displayId, String url) throws Exception {
        String targetActivity = pkg + "/.RearPetActivity";
        dumpRoots("before", atm, getRoots, pkg);

        TaskInfo existing = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (existing != null && existing.displayId == displayId) {
            if (existing.visible) {
                wakeDisplay(displayId);
                System.out.println("OK:already_on_rear:root=" + existing.rootId + ":display=" + displayId);
                System.exit(0);
            }
            moveRoot.invoke(atm, existing.rootId, 0);
            try { Thread.sleep(450); } catch (InterruptedException ignored) {}
            existing = findTask(atm, getRoots, pkg, "RearPetActivity");
        }

        if (existing == null) {
            String startOut = runCmd("am", "start", "-W",
                    "--display", "0",
                    "-n", targetActivity,
                    "--es", "url", url,
                    "--ez", "rear_mode", "true",
                    "--ei", "target_display", String.valueOf(displayId));
            existing = waitForTask(atm, getRoots, pkg, "RearPetActivity", 2200);
            if (existing == null) {
                System.out.println("FAIL:task_not_found:" + oneLine(startOut, 160));
                System.exit(1);
            }
            if (existing.displayId != 0) {
                moveRoot.invoke(atm, existing.rootId, 0);
                try { Thread.sleep(320); } catch (InterruptedException ignored) {}
            }
            try { Thread.sleep(280); } catch (InterruptedException ignored) {}
        }

        // Bring MainActivity back to front on Display 0 and wait ~720ms so RearPetActivity
        // fully completes onPause() + onStop() on Display 0 before moveRootTaskToDisplay(rootId, 1).
        runCmd("am", "start", "-W",
                "--display", "0",
                "-n", pkg + "/.MainActivity",
                "--activity-reorder-to-front");
        try { Thread.sleep(720); } catch (InterruptedException ignored) {}

        moveRoot.invoke(atm, existing.rootId, displayId);
        try { Thread.sleep(240); } catch (InterruptedException ignored) {}

        dumpRoots("after", atm, getRoots, pkg);

        TaskInfo afterMove = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (afterMove != null && afterMove.displayId == displayId) {
            wakeDisplay(displayId);
            System.out.println("OK:moved:root=" + afterMove.rootId + ":display=" + displayId + ":vis=" + afterMove.visible);
            System.exit(0);
        }

        wakeDisplay(displayId);
        moveRoot.invoke(atm, existing.rootId, displayId);
        try { Thread.sleep(260); } catch (InterruptedException ignored) {}
        TaskInfo afterRetry = findTask(atm, getRoots, pkg, "RearPetActivity");
        if (afterRetry != null && afterRetry.displayId == displayId) {
            wakeDisplay(displayId);
            System.out.println("OK:moved_retry:root=" + afterRetry.rootId + ":display=" + displayId + ":vis=" + afterRetry.visible);
            System.exit(0);
        }

        int actualDisp = afterRetry != null ? afterRetry.displayId : -1;
        System.out.println("FAIL:move_not_confirmed:root=" + existing.rootId + ":actualDisplay=" + actualDisp + ":target=" + displayId);
        System.exit(1);
    }

    private static TaskInfo waitForTask(Object atm, Method getRoots, String pkg, String activityShort, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (true) {
            TaskInfo found = findTask(atm, getRoots, pkg, activityShort);
            if (found != null) return found;
            if (SystemClock.uptimeMillis() >= deadline) return null;
            try { Thread.sleep(100); } catch (InterruptedException ignored) { return null; }
        }
    }

    /**
     * Returns a non-null blocker reason if Camera or a high-priority system UI currently owns
     * Display 0 or the target rear display, so Guard Mode yields cleanly without conflict.
     */
    private static String findYieldBlocker(Object atm, Method getRoots, String pkg, int targetDisplayId) throws Exception {
        List<?> roots = (List<?>) getRoots.invoke(atm);
        if (roots == null) return null;
        for (Object root : roots) {
            Class<?> type = root.getClass();
            int dId = type.getField("displayId").getInt(root);
            boolean vis = type.getField("visible").getBoolean(root);
            if (!vis) continue;
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            String topStr = top != null ? top.flattenToShortString() : "";
            String[] names = (String[]) type.getField("childTaskNames").get(root);

            if (dId == 0) {
                if (isCameraComponent(topStr)) {
                    return "front_camera:" + topStr;
                }
                if (names != null) {
                    for (String n : names) {
                        if (isCameraComponent(n)) {
                            return "front_camera:" + n;
                        }
                    }
                }
            } else if (dId == targetDisplayId) {
                if (topStr.startsWith(pkg + "/")) continue;
                if (isCameraComponent(topStr)) {
                    return "rear_camera:" + topStr;
                }
                if (isProtectedRearComponent(topStr)) {
                    return "rear_system:" + topStr;
                }
                if (names != null) {
                    for (String n : names) {
                        if (n == null || n.contains(pkg)) continue;
                        if (isCameraComponent(n)) return "rear_camera:" + n;
                        if (isProtectedRearComponent(n)) return "rear_system:" + n;
                    }
                }
            }
        }
        return null;
    }

    private static TaskInfo findTopVisibleTaskOnDisplay(Object atm, Method getRoots, int targetDisplayId) throws Exception {
        List<?> roots = (List<?>) getRoots.invoke(atm);
        if (roots == null) return null;
        for (Object root : roots) {
            Class<?> type = root.getClass();
            int dId = type.getField("displayId").getInt(root);
            boolean vis = type.getField("visible").getBoolean(root);
            if (dId != targetDisplayId || !vis) continue;
            int rootId = type.getField("taskId").getInt(root);
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            String[] names = (String[]) type.getField("childTaskNames").get(root);
            int nChildren = names != null ? names.length : 0;
            String topStr = top != null ? top.flattenToShortString()
                    : (names != null && names.length > 0 && names[0] != null ? names[0] : "");
            if (!topStr.isEmpty()) {
                return new TaskInfo(rootId, dId, true, topStr, nChildren);
            }
        }
        return null;
    }

    private static boolean isCameraComponent(String comp) {
        if (comp == null || comp.isEmpty()) return false;
        String s = comp.toLowerCase(Locale.US);
        return s.contains("com.android.camera")
                || s.contains("com.miui.camera")
                || s.contains("com.xiaomi.camera")
                || s.contains("googlecamera")
                || s.contains("camera")
                || s.contains("camcorder")
                || s.contains("qrcode")
                || s.contains("scanner")
                || s.contains("scanui")
                || s.contains("sightcapture");
    }

    private static boolean isProtectedRearComponent(String comp) {
        if (comp == null || comp.isEmpty()) return false;
        String s = comp.toLowerCase(Locale.US);
        return s.contains("incallui")
                || s.contains("telecom")
                || s.contains("dialer")
                || s.contains("voip")
                || s.contains("deskclock")
                || s.contains("alarm")
                || s.contains("selfie")
                || s.contains("subcamera");
    }

    private static boolean isSafeFrontForStaging(TaskInfo frontTask, String pkg) {
        if (frontTask == null || frontTask.topComponent == null || frontTask.topComponent.isEmpty()) {
            return true;
        }
        String comp = frontTask.topComponent;
        if (comp.startsWith(pkg + "/")) return true;
        String s = comp.toLowerCase(Locale.US);
        int slash = s.indexOf('/');
        String frontPkg = slash > 0 ? s.substring(0, slash) : s;
        return frontPkg.equals("com.miui.home")
                || frontPkg.equals("com.mi.android.globallauncher")
                || frontPkg.equals("com.miui.miuilite")
                || frontPkg.equals("com.android.launcher")
                || frontPkg.equals("com.android.launcher3")
                || frontPkg.equals("com.google.android.apps.nexuslauncher")
                || frontPkg.contains("launcher");
    }

    private static TaskInfo findTask(Object atm, Method getRoots, String pkg, String activityShort) throws Exception {
        List<?> roots = (List<?>) getRoots.invoke(atm);
        if (roots == null) return null;
        for (Object root : roots) {
            Class<?> type = root.getClass();
            int rootId = type.getField("taskId").getInt(root);
            int displayId = type.getField("displayId").getInt(root);
            boolean visible = type.getField("visible").getBoolean(root);
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            String[] names = (String[]) type.getField("childTaskNames").get(root);
            int nChildren = names != null ? names.length : 0;
            if (top != null && pkg.equals(top.getPackageName()) && top.getClassName().contains(activityShort)) {
                return new TaskInfo(rootId, displayId, visible, top.flattenToShortString(), nChildren);
            }
            if (names != null) {
                for (String n : names) {
                    if (n != null && n.contains(pkg) && n.contains(activityShort)) {
                        return new TaskInfo(rootId, displayId, visible, n, nChildren);
                    }
                }
            }
        }
        return null;
    }

    private static void sleepDisplay(int displayId) {
        if (displayId <= 0) return; // Never put main display 0 to sleep!
        injectDisplayKey(displayId, KeyEvent.KEYCODE_SLEEP, "sleepDisplay");
    }

    private static void wakeDisplay(int displayId) {
        injectDisplayKey(displayId, KeyEvent.KEYCODE_WAKEUP, "wakeDisplay");
    }

    private static void injectDisplayKey(int displayId, int keyCode, String label) {
        String[] mgrClasses = new String[]{
                "android.hardware.input.InputManagerGlobal",
                "android.hardware.input.InputManager"
        };
        for (String clsName : mgrClasses) {
            try {
                Class<?> managerClass = Class.forName(clsName);
                Object manager = managerClass.getMethod("getInstance").invoke(null);
                if (manager == null) continue;
                Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
                Method setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                setDisplay.invoke(down, displayId);
                setDisplay.invoke(up, displayId);
                boolean r1 = (Boolean) inject.invoke(manager, down, 2);
                boolean r2 = (Boolean) inject.invoke(manager, up, 2);
                System.out.println("LOG:" + label + "(" + displayId + ") via " + clsName + " ok=" + (r1 && r2));
                return;
            } catch (Throwable ignored) {}
        }
        String keyName = (keyCode == KeyEvent.KEYCODE_SLEEP) ? "KEYCODE_SLEEP" : "KEYCODE_WAKEUP";
        String out = runCmd("input", "-d", String.valueOf(displayId), "keyevent", keyName);
        System.out.println("LOG:" + label + "(" + displayId + ") via input-cmd out=" + oneLine(out, 120));
    }

    private static String runCmd(String... cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor();
        } catch (Throwable t) {
            sb.append("ERR:").append(t.getMessage());
        }
        return sb.toString().trim();
    }

    private static String oneLine(String s) {
        return oneLine(s, 240);
    }

    private static String oneLine(String s, int maxLen) {
        if (s == null) return "";
        String cleaned = s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) + "..." : cleaned;
    }
}
