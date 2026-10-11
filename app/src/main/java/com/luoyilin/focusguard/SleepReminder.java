package com.luoyilin.focusguard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import java.time.ZoneId;

final class SleepReminder {
    private static final String CHANNEL = "sleep_preparation";
    private static final int ID = 52;
    private final Context context;
    private final FocusModeStore store;
    private final NotificationManager manager;
    private long visibleStart;
    private final SleepReminderOverlay overlay;

    SleepReminder(Context context, FocusModeStore store) {
        this.context = context;
        this.store = store;
        overlay = new SleepReminderOverlay(context, store);
        manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "睡眠计划准备提醒", NotificationManager.IMPORTANCE_HIGH));
        // Remove a stale reminder left by a previous process.
        manager.cancel(ID);
    }

    void refresh(long now) {
        long start = FocusSchedule.nextStart(now, store.prefs.getInt("minute", 1350),
                store.prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ZoneId.systemDefault());
        int stage = store.prefs.getBoolean("daily", false) && store.end(now) == 0
                ? FocusSchedule.reminderStage(start - now) : 0;
        overlay.refresh(now, start, stage);
        if (visibleStart != 0 && (stage == 0 || visibleStart != start)) {
            manager.cancel(ID);
            visibleStart = 0;
        }
        if (stage == 0 || !manager.areNotificationsEnabled()) return;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return;
        if (store.prefs.getLong("reminderStart", 0) == start
                && store.prefs.getInt("reminderStage", 0) >= stage) return;
        long minutes = (start - now + 59_999) / 60_000;
        String text = "请结束手头操作，准备休息。开始后仅可使用白名单应用。";
        PendingIntent open = PendingIntent.getActivity(context, ID,
                new Intent(context, FocusModeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.notify(ID, new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("睡眠模式将在 " + minutes + " 分钟后开始")
                    .setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(start - now)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).build());
            visibleStart = start;
            store.prefs.edit().putLong("reminderStart", start).putInt("reminderStage", stage).apply();
        } catch (SecurityException error) {
            android.util.Log.w("SleepReminder", "Notification permission unavailable", error);
        }
    }

    void close() { overlay.hide(); }
}
