package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.ZoneId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Timed sessions are mutated only in the synchronized configuration provider. */
final class TimedFocusSession {
    private static final long MAX_COUNTUP = 24 * 60 * 60_000L;

    static Bundle call(Context context, SharedPreferences prefs, String operation, Bundle args) {
        long now = ProtectionClock.now(context);
        long start = prefs.getLong("manualStart", 0);
        int minutes = prefs.getInt("manualMinutes", 0);
        String mode = prefs.getString("manualMode", "");
        long duration = FocusTaskStore.COUNTUP.equals(mode) ? MAX_COUNTUP : minutes * 60_000L;
        long elapsed = 0;
        if (start > 0) {
            elapsed = PomodoroTimeline.elapsed(start, prefs.getLong("manualElapsed", 0),
                    prefs.getInt("manualBoot", -1), now, SystemClock.elapsedRealtime(),
                    Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1));
            long sleep = prefs.getLong("manualSleep", 0);
            TimedFocusTimeline.Outcome outcome = TimedFocusTimeline.settle(start, sleep, now,
                    elapsed, duration, FocusTaskStore.COUNTUP.equals(mode), "timedFinish".equals(operation));
            if (outcome != null) {
                try {
                    JSONArray history = new JSONArray(prefs.getString("focusHistory", "[]"));
                    JSONObject record = new JSONObject();
                    record.put("id", prefs.getString("manualId", ""));
                    record.put("taskId", prefs.getString("manualTaskId", ""));
                    record.put("name", prefs.getString("manualName", ""));
                    record.put("mode", mode); record.put("startedAt", start);
                    record.put("endedAt", outcome.endedAt);
                    record.put("elapsedMillis", outcome.elapsedMillis);
                    record.put("reason", outcome.reason);
                    history.put(record);
                    SharedPreferences.Editor editor = prefs.edit().putString("focusHistory", history.toString())
                            .putLong("manualStart", 0).putLong("manualEnd", 0)
                            .putString("manualMode", "");
                    if ("completed".equals(outcome.reason)) editor.putLong("lastCompletedAt", outcome.endedAt);
                    if (!editor.commit()) return failure("记录保存失败，请重试");
                    ComplianceAudit.focusEnded(context, prefs.getString("manualId", ""), start, outcome.endedAt, outcome.reason);
                    if ("user_interrupted".equals(outcome.reason))
                        StreakStore.breakToday(context, "提前结束倒计时专注");
                    start = 0;
                } catch (JSONException error) { return failure("记录保存失败，请重试"); }
            }
        }
        if ("timedStart".equals(operation)) {
            String requestedMode = args.getString("mode", "");
            int requestedMinutes = args.getInt("minutes", 0);
            if (!FocusTaskStore.COUNTUP.equals(requestedMode)
                    && !FocusTaskStore.COUNTDOWN.equals(requestedMode)) return failure("未知计时方式");
            if (FocusTaskStore.COUNTDOWN.equals(requestedMode)
                    && (requestedMinutes < 1 || requestedMinutes > 720)) return failure("请选择 1 到 720 分钟");
            if (start > 0 || prefs.getLong("manualEnd", 0) > now
                    || PomodoroSession.call(context, prefs, "pomodoroState", null).getBoolean("active"))
                return failure("请先完成当前专注");
            long nextSleep = 0;
            if (prefs.getBoolean("daily", false)) {
                long active = FocusSchedule.activeStart(now, prefs.getInt("minute", 1350),
                        prefs.getInt("duration", 180), prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context));
                if (active > 0) return failure("当前正在睡眠计划中");
                nextSleep = FocusSchedule.nextStart(now, prefs.getInt("minute", 1350),
                        prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context));
                if (FocusTaskStore.COUNTDOWN.equals(requestedMode)
                        && nextSleep > 0 && nextSleep < now + requestedMinutes * 60_000L)
                    return failure("本次专注将跨过睡眠开始时间，请缩短时长");
            }
            duration = FocusTaskStore.COUNTUP.equals(requestedMode) ? MAX_COUNTUP : requestedMinutes * 60_000L;
            boolean saved = prefs.edit().putLong("manualStart", now)
                    .putLong("manualElapsed", SystemClock.elapsedRealtime())
                    .putInt("manualBoot", Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1))
                    .putString("manualMode", requestedMode).putInt("manualMinutes", requestedMinutes)
                    .putLong("manualSleep", nextSleep).putLong("manualEnd", now + duration)
                    .putString("manualName", args.getString("name", ""))
                    .putString("manualTaskId", args.getString("taskId", ""))
                    .putString("manualId", java.util.UUID.randomUUID().toString()).commit();
            if (!saved) return failure("保存失败，请重试");
            ComplianceAudit.focusStarted(context, prefs.getString("manualId", ""), now, now + duration,
                    prefs.getStringSet("allowed", java.util.Collections.emptySet()));
            start = now; elapsed = 0; mode = requestedMode;
        }
        Bundle result = new Bundle();
        result.putBoolean("saved", true); result.putBoolean("active", start > 0);
        if (start > 0) {
            result.putBoolean("countUp", FocusTaskStore.COUNTUP.equals(mode));
            result.putLong("elapsed", elapsed);
            result.putLong("remaining", Math.max(0, duration - elapsed));
            result.putLong("total", duration);
            result.putLong("end", now + Math.max(0, duration - elapsed));
            result.putString("name", prefs.getString("manualName", ""));
        }
        return result;
    }

    private static Bundle failure(String message) {
        Bundle result = new Bundle(); result.putBoolean("saved", false);
        result.putString("error", message); return result;
    }
}
