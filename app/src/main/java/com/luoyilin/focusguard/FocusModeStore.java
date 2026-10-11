package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

// UI and guard share a single preference owner through LimitConfigProvider.
final class FocusModeStore {
    final SharedPreferences prefs;
    final Context context;
    FocusModeStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = new FocusPreferences(context);
    }
    Set<String> allowed() {
        return new HashSet<>(prefs.getStringSet("allowed", new HashSet<>()));
    }
    long scheduledStart(long now) {
        now = ProtectionClock.now(context);
        return prefs.getBoolean("daily", false) ? FocusSchedule.activeStart(now,
                prefs.getInt("minute", 1350), prefs.getInt("duration", 180),
                prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context)) : 0;
    }
    long end(long now) {
        now = ProtectionClock.now(context);
        android.os.Bundle pomodoro = pomodoro();
        android.os.Bundle timed = timed();
        long manual = timed.getBoolean("active") ? timed.getLong("end")
                : prefs.getString("manualMode", "").isEmpty() ? prefs.getLong("manualEnd", 0) : 0;
        long start = scheduledStart(now);
        long daily = start > 0
                ? start + prefs.getInt("duration", 180) * 60_000L : 0;
        return daily > 0 ? daily : Math.max(manual > now ? manual : 0, pomodoro.getLong("end", 0));
    }
    android.os.Bundle pomodoro() { return pomodoroCall("pomodoroState", null); }
    android.os.Bundle timed() { return pomodoroCall("timedState", null); }
    android.os.Bundle startTimed(FocusTaskStore.Task task) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("mode", task.mode); args.putInt("minutes", task.value);
        args.putString("name", task.name); args.putString("taskId", task.id);
        return pomodoroCall("timedStart", args);
    }
    android.os.Bundle startPomodoro(int rounds) {
        return startPomodoro(rounds, "", "");
    }
    android.os.Bundle startPomodoro(int rounds, String name, String taskId) {
        android.os.Bundle args = new android.os.Bundle(); args.putInt("rounds", rounds);
        args.putString("name", name); args.putString("taskId", taskId);
        return pomodoroCall("pomodoroStart", args);
    }
    private android.os.Bundle pomodoroCall(String operation, android.os.Bundle args) {
        android.os.Bundle result = context.getContentResolver().call(LimitConfigProvider.URI, operation, "focus_mode", args);
        if (result == null) throw new IllegalStateException("Configuration provider unavailable");
        return result;
    }
    boolean finishEarly(long now) {
        now = ProtectionClock.now(context);
        long sleep = scheduledStart(now);
        if (sleep > 0) {
            return false; // Sleep can only pause for a bounded temporary interval.
        }
        if (pomodoro().getBoolean("active")) return pomodoroCall("pomodoroFinish", null).getBoolean("saved");
        if (timed().getBoolean("active")) return pomodoroCall("timedFinish", null).getBoolean("saved");
        boolean saved = prefs.edit().putLong("manualEnd", 0).putLong("dismissed", scheduledStart(now))
                .putLong("lastInterruptedAt", now).commit();
        if (saved) StreakStore.breakToday(context, "提前结束专注或睡眠");
        return saved;
    }
}
