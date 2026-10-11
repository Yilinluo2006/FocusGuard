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

/** Called only by the synchronized configuration provider. */
final class PomodoroSession {
    static Bundle call(Context context, SharedPreferences prefs, String operation, Bundle args) {
        long now = ProtectionClock.now(context);
        long elapsedNow = SystemClock.elapsedRealtime();
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        Bundle result = new Bundle();
        long start = prefs.getLong("pomodoroStart", 0);
        int rounds = prefs.getInt("pomodoroRounds", 1);
        long elapsed = start == 0 ? 0 : PomodoroTimeline.elapsed(start,
                prefs.getLong("pomodoroElapsed", 0), prefs.getInt("pomodoroBoot", -1), now, elapsedNow, boot);
        long duration = PomodoroTimeline.duration(rounds);
        long sleep = prefs.getLong("pomodoroSleep", 0);
        String reason = null;
        long accounted = elapsed;
        if (start > 0) {
            if (sleep > start && now >= sleep && (sleep < start + duration || elapsed < duration)) {
                reason = "sleep_takeover";
                accounted = Math.min(elapsed, sleep - start);
            } else if (elapsed >= duration) {
                reason = "completed";
                accounted = duration;
            } else if ("pomodoroFinish".equals(operation)) reason = "user_interrupted";
        }
        if (reason != null) {
            try {
                JSONArray history = new JSONArray(prefs.getString("pomodoroHistory", "[]"));
                JSONObject record = new JSONObject();
                record.put("id", prefs.getString("pomodoroId", ""));
                record.put("taskId", prefs.getString("pomodoroTaskId", ""));
                record.put("name", prefs.getString("pomodoroName", ""));
                record.put("mode", "strict_pomodoro");
                record.put("startedAt", start);
                record.put("endedAt", start + Math.min(accounted, duration));
                record.put("rounds", rounds);
                record.put("completedRounds", PomodoroTimeline.completedRounds(accounted, rounds));
                record.put("workMillis", PomodoroTimeline.workTime(accounted, rounds));
                record.put("restMillis", Math.min(accounted, duration) - PomodoroTimeline.workTime(accounted, rounds));
                record.put("reason", reason);
                history.put(record);
                SharedPreferences.Editor edit = prefs.edit().putString("pomodoroHistory", history.toString())
                        .putLong("pomodoroStart", 0).putString("pomodoroLastReason", reason);
                if ("completed".equals(reason)) edit.putLong("lastCompletedAt", now);
                if (!edit.commit()) return unsettled(now, elapsed, rounds);
                ComplianceAudit.focusEnded(context, prefs.getString("pomodoroId", ""), start,
                        record.getLong("endedAt"), reason);
                if ("user_interrupted".equals(reason)) StreakStore.breakToday(context, "紧急结束严格番茄钟");
                start = 0;
            } catch (JSONException error) {
                android.util.Log.e("Pomodoro", "Cannot settle session", error);
                return unsettled(now, elapsed, rounds);
            }
        }
        if ("pomodoroStart".equals(operation)) {
            rounds = args.getInt("rounds", 0);
            if (rounds < 1 || rounds > 24) return failure("请选择 1 到 24 轮");
            if (start > 0 || prefs.getLong("manualEnd", 0) > now) return failure("请先完成当前专注");
            duration = PomodoroTimeline.duration(rounds);
            sleep = 0;
            if (prefs.getBoolean("daily", false)) {
                long active = FocusSchedule.activeStart(now, prefs.getInt("minute", 1350),
                        prefs.getInt("duration", 180), prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context));
                if (active > 0) return failure("当前正在睡眠计划中");
                sleep = FocusSchedule.nextStart(now, prefs.getInt("minute", 1350),
                        prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context));
                if (sleep > 0 && sleep < now + duration) return failure("本组将跨过睡眠开始时间，请减少轮数");
            }
            boolean saved = prefs.edit().putLong("pomodoroStart", now)
                    .putLong("pomodoroElapsed", elapsedNow).putInt("pomodoroBoot", boot)
                    .putInt("pomodoroRounds", rounds).putLong("pomodoroSleep", sleep)
                    .putString("pomodoroName", args.getString("name", ""))
                    .putString("pomodoroTaskId", args.getString("taskId", ""))
                    .putString("pomodoroId", java.util.UUID.randomUUID().toString()).commit();
            if (!saved) return failure("保存失败，请重试");
            ComplianceAudit.focusStarted(context, prefs.getString("pomodoroId", ""), now, now + duration,
                    prefs.getStringSet("allowed", java.util.Collections.emptySet()));
            start = now;
            elapsed = 0;
        }
        result.putBoolean("saved", true);
        result.putBoolean("active", start > 0);
        if (start > 0) {
            result.putLong("end", now + Math.max(0, duration - elapsed));
            result.putLong("remaining", PomodoroTimeline.phaseRemaining(elapsed, rounds));
            result.putInt("round", PomodoroTimeline.round(elapsed, rounds));
            result.putInt("rounds", rounds);
            result.putBoolean("resting", PomodoroTimeline.resting(elapsed));
            result.putString("name", prefs.getString("pomodoroName", ""));
        }
        return result;
    }
    private static Bundle failure(String message) {
        Bundle result = new Bundle(); result.putBoolean("saved", false); result.putString("error", message); return result;
    }
    private static Bundle unsettled(long now, long elapsed, int rounds) {
        Bundle result = new Bundle();
        result.putBoolean("saved", false); result.putBoolean("active", true);
        result.putLong("end", now + Math.max(1000, PomodoroTimeline.duration(rounds) - elapsed));
        result.putLong("remaining", PomodoroTimeline.phaseRemaining(elapsed, rounds));
        result.putInt("round", PomodoroTimeline.round(elapsed, rounds)); result.putInt("rounds", rounds);
        result.putBoolean("resting", PomodoroTimeline.resting(elapsed));
        return result;
    }
}
