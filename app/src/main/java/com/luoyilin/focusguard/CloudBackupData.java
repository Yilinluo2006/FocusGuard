package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.*;
import java.time.ZoneId;
import java.util.*;

/** Explicit portable fields only: never tokens, running timers, heartbeats or emergency grants. */
final class CloudBackupData {
    private static final String[] FOCUS_CONFIG = {"daily", "minute", "duration", "allowed", FocusSchedule.WEEKDAYS_KEY};
    private static final String[] HISTORIES = {"focusHistory", "pomodoroHistory", "sleepControlHistory"};
    static String collect(Context context) throws Exception {
        Map<String, ?> focus = FocusPreferences.read(context, "focus_mode");
        JSONObject root = new JSONObject(); root.put("schemaVersion", 1);
        JSONArray limits = new JSONArray();
        SharedPreferences p = context.getSharedPreferences("focus_guard_prefs", 0);
        for (String pkg : new TreeSet<>(p.getStringSet("selected_packages", Collections.emptySet()))) {
            JSONObject value = new JSONObject(); value.put("packageName", pkg); value.put("minutes", p.getInt("limit_minutes_" + pkg, 30)); limits.put(value);
        }
        root.put("limits", limits);
        root.put("tasks", new JSONArray(context.getSharedPreferences("focus_tasks", 0).getString("tasks", "[]")));
        JSONObject config = new JSONObject();
        for (String key : FOCUS_CONFIG) if (focus.containsKey(key)) {
            Object value = focus.get(key);
            config.put(key, value instanceof Set ? new JSONArray(new TreeSet<>((Set<String>) value)) : value);
        }
        for (String key : HISTORIES) config.put(key, new JSONArray(focus.get(key) == null ? "[]" : focus.get(key).toString()));
        root.put("focus", config);
        root.put("wallpaper", AppWallpaper.selectedIndex(context)); root.put("appearance", Appearance.read(context));
        root.put("restriction", new JSONObject(RestrictionStore.backupConfig(context)));
        JSONObject streak = new JSONObject();
        Properties values = StreakStore.snapshot(context);
        long today = StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault());
        for (String key : values.stringPropertyNames()) {
            if (historicalKey(key, today)) streak.put(key, values.getProperty(key));
        }
        root.put("streak", streak);
        String payload = canonical(root);
        validate(payload); return payload;
    }
    static JSONObject validate(String payload) throws Exception {
        if (payload == null || payload.length() > 2_000_000 || payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4_000_000)
            throw new IllegalArgumentException("备份文件过大");
        JSONObject root = new JSONObject(payload);
        if (root.getInt("schemaVersion") != 1) throw new IllegalArgumentException("备份版本不支持");
        JSONArray limits = root.getJSONArray("limits"), tasks = root.getJSONArray("tasks");
        if (limits.length() > 2000 || tasks.length() > 2000) throw new IllegalArgumentException("备份记录过多");
        Set<String> packages = new HashSet<>(), ids = new HashSet<>();
        for (int i = 0; i < limits.length(); i++) {
            JSONObject item = limits.getJSONObject(i); String pkg = item.getString("packageName"); int minutes = item.getInt("minutes");
            if (!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") || minutes < 1 || minutes > 1440 || !packages.add(pkg))
                throw new IllegalArgumentException("应用额度数据无效");
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject item = tasks.getJSONObject(i); String mode = item.getString("mode"); int value = item.getInt("value");
            if (item.getString("name").trim().isEmpty() || item.getString("id").isEmpty() || !ids.add(item.getString("id"))
                    || !(FocusTaskStore.COUNTUP.equals(mode) && value == 0
                    || FocusTaskStore.COUNTDOWN.equals(mode) && value >= 1 && value <= 720
                    || FocusTaskStore.POMODORO.equals(mode) && value >= 1 && value <= 24))
                throw new IllegalArgumentException("专注任务数据无效");
        }
        JSONObject focus = root.getJSONObject("focus");
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(FOCUS_CONFIG)); allowedKeys.addAll(Arrays.asList(HISTORIES));
        for (Iterator<String> keys = focus.keys(); keys.hasNext();) if (!allowedKeys.contains(keys.next()))
            throw new IllegalArgumentException("备份含不支持的运行状态");
        if (focus.has("daily")) focus.getBoolean("daily");
        if (focus.has(FocusSchedule.WEEKDAYS_KEY)) {
            Object value = focus.get(FocusSchedule.WEEKDAYS_KEY);
            if (!(value instanceof Integer) || (int) value < 0 || (int) value > FocusSchedule.ALL_DAYS
                    || (focus.optBoolean("daily", false) && (int) value == 0))
                throw new IllegalArgumentException("计划星期无效");
        }
        if (focus.has("minute") && (focus.getInt("minute") < 0 || focus.getInt("minute") >= 1440)) throw new IllegalArgumentException("计划时间无效");
        if (focus.has("duration") && (focus.getInt("duration") < 1 || focus.getInt("duration") > 720)) throw new IllegalArgumentException("计划时长无效");
        if (focus.has("allowed")) {
            JSONArray allowed = focus.getJSONArray("allowed");
            if (allowed.length() > 2000) throw new IllegalArgumentException("允许应用过多");
            for (int i = 0; i < allowed.length(); i++) if (!allowed.getString(i).matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
                throw new IllegalArgumentException("允许应用无效");
        }
        for (String key : HISTORIES) {
            JSONArray history = focus.getJSONArray(key);
            if (history.length() > 50_000) throw new IllegalArgumentException("历史记录过多");
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.getJSONObject(i);
                if ("sleepControlHistory".equals(key)) {
                    if (SleepControlSession.decode(item).reason.isEmpty()) throw new IllegalArgumentException("备份包含未结束管控");
                } else if (item.getLong("startedAt") <= 0 || item.getLong("endedAt") < item.getLong("startedAt")
                        || item.getLong("focusHistory".equals(key) ? "elapsedMillis" : "workMillis") < 0 || item.getString("id").isEmpty())
                    throw new IllegalArgumentException("专注记录无效");
            }
        }
        int wallpaper = root.getInt("wallpaper"), appearance = root.getInt("appearance");
        if (wallpaper < 0 || wallpaper >= AppWallpaper.count() || appearance < 0 || appearance > 2) throw new IllegalArgumentException("外观设置无效");
        RestrictionStore.validateBackupConfig(root.getJSONObject("restriction"));
        JSONObject streak = root.getJSONObject("streak"); long today = StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault());
        for (Iterator<String> keys = streak.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!historicalKey(key, today) || streak.getString(key).length() > 300) throw new IllegalArgumentException("坚持历史记录无效");
            if (key.startsWith("ms.") && (Long.parseLong(streak.getString(key)) < 0 || Long.parseLong(streak.getString(key)) > 26 * 3600000L))
                throw new IllegalArgumentException("保护时长无效");
            if (key.startsWith("result.") && !Arrays.asList("完成", "准备", "未完成", "待确认", "无记录").contains(streak.getString(key)))
                throw new IllegalArgumentException("坚持历史结果无效");
            if (key.startsWith("ruleVersion.") && !"2".equals(streak.getString(key))) throw new IllegalArgumentException("坚持规则版本无效");
        }
        return root;
    }
    /** Called inside the provider lock. Existing local configuration and records win. */
    static Bundle restore(Context context, SharedPreferences focusPrefs, Bundle args) {
        Bundle result = new Bundle();
        try {
            com.luoyilin.focusguard.auth.SessionManager session = new com.luoyilin.focusguard.auth.SessionManager(context);
            if (!session.isLoggedIn() || session.getUserId() != args.getLong("userId")
                    || !Objects.equals(session.getAuthorizationHeader(), args.getString("authorization")))
                throw new IllegalStateException("登录账号已变化，请重新操作");
            JSONObject root = validate(args.getString("payload"));
            if (new FocusModeStore(context).end(ProtectionClock.now(context)) > 0)
                throw new IllegalStateException("请先完成当前专注或睡前管控，再恢复");
            JSONArray mergedTasks = merge(new JSONArray(context.getSharedPreferences("focus_tasks", 0).getString("tasks", "[]")), root.getJSONArray("tasks"), "id");
            JSONObject focus = root.getJSONObject("focus"); SharedPreferences.Editor editor = focusPrefs.edit();
            JSONObject merged = new JSONObject(collect(context));
            merged.put("tasks", mergedTasks);
            // Parse every local history before making any changes; malformed local data is preserved.
            for (String key : HISTORIES) {
                JSONArray items = merge(new JSONArray(focusPrefs.getString(key, "[]")),
                        focus.getJSONArray(key), "sleepControlHistory".equals(key) ? "startedAt" : "id");
                editor.putString(key, items.toString()); merged.getJSONObject("focus").put(key, items);
            }
            Bundle planChanges = new Bundle();
            // An existing local daily plan predates weekday selection and must remain daily.
            for (String key : FOCUS_CONFIG) if (!focusPrefs.contains(key) && focus.has(key)
                    && !(FocusSchedule.WEEKDAYS_KEY.equals(key) && focusPrefs.contains("daily"))) {
                if ("daily".equals(key)) {
                    editor.putBoolean(key, focus.getBoolean(key)); planChanges.putBoolean(key, focus.getBoolean(key));
                }
                else if ("allowed".equals(key)) {
                    Set<String> apps = new HashSet<>(); JSONArray array = focus.getJSONArray(key);
                    for (int i = 0; i < array.length(); i++) apps.add(array.getString(i)); editor.putStringSet(key, apps);
                    planChanges.putStringArrayList(key, new ArrayList<>(apps));
                } else {
                    editor.putInt(key, focus.getInt(key)); planChanges.putInt(key, focus.getInt(key));
                }
                merged.getJSONObject("focus").put(key, focus.get(key));
            }
            SharedPreferences limits = context.getSharedPreferences("focus_guard_prefs", 0);
            Set<String> selected = new HashSet<>(limits.getStringSet("selected_packages", Collections.emptySet()));
            SharedPreferences.Editor limitEditor = limits.edit(); JSONArray remoteLimits = root.getJSONArray("limits");
            for (int i = 0; i < remoteLimits.length(); i++) {
                JSONObject item = remoteLimits.getJSONObject(i); String pkg = item.getString("packageName");
                if (selected.add(pkg)) {
                    limitEditor.putInt("limit_minutes_" + pkg, item.getInt("minutes")); merged.getJSONArray("limits").put(item);
                }
            }
            // Refuse a merge exceeding the upload format before changing any local settings.
            validate(merged.toString());
            Map<String, ?> currentPlan = focusPrefs.getAll();
            Map<String, Object> proposedPlan = SleepPlanGuard.candidate(currentPlan, planChanges, false);
            String denial = SleepPlanGuard.denial(context, currentPlan, proposedPlan);
            if (denial != null) throw new IllegalStateException(denial);
            if (SleepPlanRules.changed(currentPlan, proposedPlan)
                    && !DeviceSyncManager.prefs(context).edit().remove("pendingSleep").commit())
                throw new IllegalStateException("未能取消旧的待同步计划，请稍后重试恢复");
            if (!limitEditor.putStringSet("selected_packages", selected).commit() || !LimitSnapshot.publish(context))
                throw new IllegalStateException("应用规则保存失败，可以重试恢复");
            if (!context.getSharedPreferences("focus_tasks", 0).edit().putString("tasks", mergedTasks.toString()).commit() || !editor.commit())
                throw new IllegalStateException("记录保存失败，可以重试恢复");
            RestrictionStore.restoreMissingConfig(context, root.getJSONObject("restriction"));
            JSONObject history = root.getJSONObject("streak");
            StreakStore.access(context, local -> {
                for (Iterator<String> keys = history.keys(); keys.hasNext();) {
                    String key = keys.next(); if (!local.containsKey(key)) local.setProperty(key, history.optString(key));
                }
                StreakLedger.rebuildHistory(local, System.currentTimeMillis(), ZoneId.systemDefault());
            });
            if (FocusPreferences.read(context, "app_wallpaper").get("selected") == null) AppWallpaper.select(context, root.getInt("wallpaper"));
            Appearance.restoreMissing(context, root.getInt("appearance"));
            result.putBoolean("saved", true);
        } catch (Exception error) {
            result.putBoolean("saved", false); result.putString("error", error.getMessage() == null ? "恢复失败，请重试" : error.getMessage());
        }
        return result;
    }
    static JSONArray merge(JSONArray local, JSONArray remote, String key) throws JSONException {
        JSONArray result = new JSONArray(); Set<String> ids = new HashSet<>();
        for (int i = 0; i < local.length(); i++) { JSONObject value = local.getJSONObject(i); ids.add(String.valueOf(value.get(key))); result.put(value); }
        for (int i = 0; i < remote.length(); i++) { JSONObject value = remote.getJSONObject(i); if (ids.add(String.valueOf(value.get(key)))) result.put(value); }
        return result;
    }
    private static boolean historicalKey(String key, long today) {
        if (!(key.startsWith("ms.") || key.startsWith("result.") || key.startsWith("broken.") || key.startsWith("ruleVersion."))) return false;
        try { long day = Long.parseLong(key.substring(key.indexOf('.') + 1)); return day >= 0 && day < today; }
        catch (NumberFormatException invalid) { return false; }
    }
    private static String canonical(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value; TreeSet<String> keys = new TreeSet<>(); object.keys().forEachRemaining(keys::add);
            List<String> fields = new ArrayList<>(); for (String key : keys) fields.add(JSONObject.quote(key) + ":" + canonical(object.get(key)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value; List<String> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) items.add(canonical(array.get(i))); return "[" + String.join(",", items) + "]";
        }
        return value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
    }
}
