package com.luoyilin.focusguard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Collections;

/** Reads configuration in its writer process; guard must not cache SharedPreferences. */
public class LimitConfigProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".limits/config");

    @Override public boolean onCreate() { return true; }

    @Override public synchronized android.os.Bundle call(String method, String name, android.os.Bundle extras) {
        if (!"focus_mode".equals(name) && !"app_wallpaper".equals(name))
            throw new IllegalArgumentException("Unknown preference store");
        SharedPreferences prefs = getContext().getSharedPreferences(name, 0);
        if ("focus_mode".equals(name) && method.startsWith("sleepTemporary"))
            return SleepTemporaryUse.call(getContext(), prefs, method, extras);
        if ("focus_mode".equals(name) && "restoreBackup".equals(method))
            return CloudBackupData.restore(getContext(), prefs, extras);
        if ("focus_mode".equals(name) && "sleepSyncSnapshot".equals(method)) {
            android.os.Bundle snapshot = new android.os.Bundle();
            snapshot.putSerializable("plan", sleepPlan(prefs.getAll()));
            snapshot.putString("pending", DeviceSyncManager.prefs(getContext()).getString("pendingSleep", null));
            return snapshot;
        }
        if ("focus_mode".equals(name) && "applySyncedSleep".equals(method)) {
            android.os.Bundle result = new android.os.Bundle();
            if (!sleepPlan(prefs.getAll()).equals(extras.getSerializable("expectedPlan"))
                    || !java.util.Objects.equals(DeviceSyncManager.prefs(getContext()).getString("pendingSleep", null),
                    extras.getString("expectedPending"))) {
                result.putBoolean("stale", true);
                result.putBoolean("saved", false);
                return result;
            }
            return applySyncedSleep(prefs, extras.getString("sleep"), extras.getBoolean("queueIfDenied"));
        }
        if ("focus_mode".equals(name) && "replayPendingSleep".equals(method)) {
            String waiting = DeviceSyncManager.prefs(getContext()).getString("pendingSleep", null);
            if (waiting == null) {
                android.os.Bundle result = new android.os.Bundle();
                result.putBoolean("saved", true);
                return result;
            }
            return applySyncedSleep(prefs, waiting, true);
        }
        if ("focus_mode".equals(name) && ("sleepObserve".equals(method) || "sleepFinish".equals(method)))
            return SleepControlSession.call(getContext(), prefs, method, extras);
        if ("focus_mode".equals(name) && ("pomodoroStart".equals(method)
                || "pomodoroState".equals(method) || "pomodoroFinish".equals(method)))
            return PomodoroSession.call(getContext(), prefs, method, extras);
        if ("focus_mode".equals(name) && ("timedStart".equals(method)
                || "timedState".equals(method) || "timedFinish".equals(method)))
            return TimedFocusSession.call(getContext(), prefs, method, extras);
        android.os.Bundle reply = new android.os.Bundle();
        if ("read".equals(method)) {
            reply.putSerializable("values", new java.util.HashMap<>(prefs.getAll()));
        } else if ("selectWallpaper".equals(method) && "app_wallpaper".equals(name)) {
            String key = extras == null ? null : extras.getString("selected");
            if (!AppWallpaper.validKey(key)) throw new IllegalArgumentException("Unknown wallpaper");
            reply.putBoolean("saved", prefs.edit().putString("selected", key).commit());
        } else if (("write".equals(method) || "writeSyncedSleep".equals(method)) && "focus_mode".equals(name)) {
            android.os.Bundle changes = extras.getBundle("changes");
            java.util.Map<String, ?> current = prefs.getAll();
            java.util.Map<String, Object> proposed = SleepPlanGuard.candidate(current, changes, extras.getBoolean("clear"));
            String denial = SleepPlanGuard.denial(getContext(), current, proposed);
            if (denial != null) {
                reply.putBoolean("saved", false);
                reply.putString("message", denial);
                return reply;
            }
            long sleepStart = prefs.getBoolean("daily", false) ? FocusSchedule.activeStart(ProtectionClock.now(getContext()),
                    prefs.getInt("minute", 1350), prefs.getInt("duration", 180),
                    prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(getContext())) : 0;
            if (sleepStart > 0) {
                boolean altersPlan = extras.getBoolean("clear");
                for (String key : new String[]{"daily", "minute", "duration", "allowed", "dismissed", FocusSchedule.WEEKDAYS_KEY}) {
                    if (!changes.containsKey(key)) continue;
                    Object value = changes.get(key);
                    if (value instanceof java.util.ArrayList) value = new java.util.HashSet<>((java.util.ArrayList<?>) value);
                    altersPlan |= !java.util.Objects.equals(value, prefs.getAll().get(key));
                }
                if (altersPlan) { reply.putBoolean("saved", false); return reply; }
            }
            if (!"writeSyncedSleep".equals(method) && SleepPlanRules.changed(current, proposed)
                    && !DeviceSyncManager.prefs(getContext()).edit().remove("pendingSleep").commit()) {
                reply.putBoolean("saved", false);
                reply.putString("message", "未能取消旧的待同步计划，请稍后重试");
                return reply;
            }
            SharedPreferences.Editor editor = prefs.edit();
            if (extras.getBoolean("clear")) editor.clear();
            for (String key : changes.keySet()) {
                Object value = changes.get(key);
                if (value == null) editor.remove(key);
                else if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof java.util.ArrayList) editor.putStringSet(key,
                        new java.util.HashSet<>((java.util.ArrayList<String>) value));
                else throw new IllegalArgumentException("Unsupported preference value");
            }
            boolean saved = editor.commit();
            reply.putBoolean("saved", saved);
            if (saved && SleepPlanRules.changed(current, proposed)) {
                DeviceSyncManager.request(getContext());
            }
        } else throw new IllegalArgumentException("Unknown operation");
        return reply;
    }

    private static java.util.HashMap<String, Object> sleepPlan(java.util.Map<String, ?> values) {
        java.util.HashMap<String, Object> plan = new java.util.HashMap<>();
        for (String key : SleepPlanRules.KEYS) if (values.containsKey(key)) {
            Object value = values.get(key);
            plan.put(key, value instanceof java.util.Set ? new java.util.HashSet<>((java.util.Set<?>) value) : value);
        }
        return plan;
    }

    /** Called only while the provider writer lock also owns the plan and pending comparison. */
    private android.os.Bundle applySyncedSleep(SharedPreferences prefs, String payload, boolean queueIfDenied) {
        android.os.Bundle result = new android.os.Bundle();
        try {
            org.json.JSONObject sleep = new org.json.JSONObject(payload);
            android.os.Bundle changes = SharedSettingsData.sleepChanges(sleep);
            java.util.Map<String, ?> current = prefs.getAll();
            String denial = SleepPlanGuard.denial(getContext(), current, SleepPlanGuard.candidate(current, changes, false));
            if (denial == null && new FocusModeStore(getContext()).end(ProtectionClock.now(getContext())) > 0)
                denial = "请先完成当前专注或睡前管控，再同步睡前计划";
            if (denial != null) {
                result.putBoolean("denied", true);
                result.putString("message", denial);
                if (queueIfDenied) {
                    String pending = SharedSettingsData.canonical(sleep);
                    SharedPreferences sync = DeviceSyncManager.prefs(getContext());
                    boolean saved = pending.equals(sync.getString("pendingSleep", null))
                            || sync.edit().putString("pendingSleep", pending).commit();
                    result.putBoolean("saved", saved);
                    if (!saved) result.putString("message", "睡前计划同步状态保存失败");
                } else result.putBoolean("saved", false);
                return result;
            }
            // Cancel the old pending value before writing; a failure must not report success.
            if (!DeviceSyncManager.prefs(getContext()).edit().remove("pendingSleep").commit()) {
                result.putBoolean("saved", false);
                result.putString("message", "睡前计划同步状态保存失败");
                return result;
            }
            android.os.Bundle write = new android.os.Bundle();
            write.putBundle("changes", changes);
            result = call("writeSyncedSleep", "focus_mode", write);
            result.putBoolean("applied", result.getBoolean("saved"));
            if (!result.getBoolean("saved")) {
                current = prefs.getAll();
                denial = SleepPlanGuard.denial(getContext(), current, SleepPlanGuard.candidate(current, changes, false));
                if (denial == null && new FocusModeStore(getContext()).end(ProtectionClock.now(getContext())) > 0)
                    denial = "请先完成当前专注或睡前管控，再同步睡前计划";
                if (denial != null) {
                    result.putBoolean("denied", true);
                    result.putString("message", denial);
                    if (queueIfDenied) {
                        boolean saved = DeviceSyncManager.prefs(getContext()).edit()
                                .putString("pendingSleep", SharedSettingsData.canonical(sleep)).commit();
                        result.putBoolean("saved", saved);
                        if (!saved) result.putString("message", "睡前计划同步状态保存失败");
                    }
                }
            }
            if (!result.getBoolean("saved") && result.getString("message") == null)
                result.putString("message", "睡前计划保存失败");
        } catch (Exception error) {
            result.putBoolean("saved", false);
            result.putString("message", error.getMessage() == null ? "睡前计划保存失败" : error.getMessage());
        }
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown configuration URI");
        SharedPreferences prefs = getContext().getSharedPreferences("focus_guard_prefs", 0);
        MatrixCursor cursor = new MatrixCursor(new String[]{"package_name", "limit_minutes"});
        for (String pkg : prefs.getStringSet("selected_packages", Collections.emptySet())) {
            int minutes = prefs.getInt("limit_minutes_" + pkg, 30);
            if (minutes > 0) cursor.addRow(new Object[]{pkg, minutes});
        }
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.focusguard.limit"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
