package com.luoyilin.focusguard;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

final class ProtectionStateStore {

    private static final String TAG = "ProtectionState";
    private static final String FILE_NAME = "protection_state.bin";
    private static final int FILE_VERSION = 1;
    private static final long HEALTH_TIMEOUT_MILLIS = 35_000L;

    private ProtectionStateStore() {
    }

    static synchronized void markAccessibilityConnected(Context context, String event) {
        State state = read(context);
        state.accessibilityConnected = true;
        state.accessibilityHeartbeatAt = SystemClock.uptimeMillis();
        updateDiagnostics(context, state, event);
        write(context, state);
    }

    static synchronized void touchAccessibility(Context context) {
        State state = read(context);
        state.accessibilityConnected = true;
        state.accessibilityHeartbeatAt = SystemClock.uptimeMillis();
        updateDiagnostics(context, state, null);
        write(context, state);
    }

    static synchronized void markAccessibilityDisconnected(Context context, String event) {
        State state = read(context);
        state.accessibilityConnected = false;
        state.accessibilityHeartbeatAt = SystemClock.uptimeMillis();
        updateDiagnostics(context, state, event);
        write(context, state);
    }

    static synchronized void markProtectionRunning(Context context, boolean running, String event) {
        State state = read(context);
        state.protectionRunning = running;
        state.protectionHeartbeatAt = SystemClock.uptimeMillis();
        updateDiagnostics(context, state, event);
        write(context, state);
    }

    static synchronized void touchProtection(Context context) {
        State state = read(context);
        state.protectionRunning = true;
        state.protectionHeartbeatAt = SystemClock.uptimeMillis();
        updateDiagnostics(context, state, null);
        write(context, state);
    }

    static boolean isAccessibilityHealthy(Context context) {
        State state = read(context);
        return state.accessibilityConnected && isFresh(state.accessibilityHeartbeatAt);
    }

    static boolean isProtectionHealthy(Context context) {
        State state = read(context);
        return state.protectionRunning && isFresh(state.protectionHeartbeatAt);
    }

    private static boolean isFresh(long timestamp) {
        long age = SystemClock.uptimeMillis() - timestamp;
        return timestamp > 0 && age >= 0 && age <= HEALTH_TIMEOUT_MILLIS;
    }

    private static void updateDiagnostics(Context context, State state, String event) {
        state.pid = Process.myPid();
        state.processName = getProcessName(context);
        if (event != null) {
            state.lastEvent = event;
        }
    }

    private static String getProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }

        int pid = Process.myPid();
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager != null) {
            List<ActivityManager.RunningAppProcessInfo> processes =
                    manager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == pid) {
                        return process.processName;
                    }
                }
            }
        }

        return "unknown";
    }

    private static AtomicFile getFile(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        return new AtomicFile(file);
    }

    private static State read(Context context) {
        AtomicFile atomicFile = getFile(context);
        if (!atomicFile.getBaseFile().exists()) {
            return new State();
        }

        try (FileInputStream input = atomicFile.openRead();
             DataInputStream data = new DataInputStream(new BufferedInputStream(input))) {
            int version = data.readInt();
            if (version != FILE_VERSION) {
                return new State();
            }

            State state = new State();
            state.accessibilityConnected = data.readBoolean();
            state.accessibilityHeartbeatAt = data.readLong();
            state.protectionRunning = data.readBoolean();
            state.protectionHeartbeatAt = data.readLong();
            state.pid = data.readInt();
            state.processName = data.readUTF();
            state.lastEvent = data.readUTF();
            return state;
        } catch (IOException exception) {
            Log.w(TAG, "Unable to read protection state", exception);
            return new State();
        }
    }

    private static void write(Context context, State state) {
        AtomicFile atomicFile = getFile(context);
        FileOutputStream output = null;

        try {
            output = atomicFile.startWrite();
            DataOutputStream data = new DataOutputStream(new BufferedOutputStream(output));
            data.writeInt(FILE_VERSION);
            data.writeBoolean(state.accessibilityConnected);
            data.writeLong(state.accessibilityHeartbeatAt);
            data.writeBoolean(state.protectionRunning);
            data.writeLong(state.protectionHeartbeatAt);
            data.writeInt(state.pid);
            data.writeUTF(state.processName == null ? "" : state.processName);
            data.writeUTF(state.lastEvent == null ? "" : state.lastEvent);
            data.flush();
            atomicFile.finishWrite(output);
        } catch (IOException exception) {
            if (output != null) {
                atomicFile.failWrite(output);
            }
            Log.e(TAG, "Unable to persist protection state", exception);
        }
    }

    private static final class State {
        boolean accessibilityConnected;
        long accessibilityHeartbeatAt;
        boolean protectionRunning;
        long protectionHeartbeatAt;
        int pid;
        String processName = "";
        String lastEvent = "";
    }
}
