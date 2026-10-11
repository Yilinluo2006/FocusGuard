package com.luoyilin.focusguard;

import android.app.usage.UsageEvents;
import android.app.usage.UsageEventsQuery;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/** Reads each app's foreground time in the current 04:00-to-04:00 quota period. */
final class AppUsageReader {
    private AppUsageReader() { }

    static AppUsageTimeline read(Context context, long now, Set<String> packages) {
        ZoneId zone = ProtectionClock.zone(context);
        long day = StreakLedger.day(now, zone);
        return readRange(context, StreakLedger.boundary(day, zone), now, packages);
    }

    static AppUsageTimeline readRange(Context context, long start, long now, Set<String> packages) {
        if (!ProtectionClock.nativeReliable(context)) return null;
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        if (manager == null) return null;
        ZoneId zone = ProtectionClock.zone(context);
        long day = StreakLedger.day(start, zone);
        long historyStart = StreakLedger.boundary(day - 1, zone);
        try {
            UsageEvents history;
            if (Build.VERSION.SDK_INT >= 35) {
                UsageEventsQuery query = new UsageEventsQuery.Builder(historyStart, now)
                        .setEventTypes(UsageEvents.Event.ACTIVITY_RESUMED,
                                UsageEvents.Event.ACTIVITY_PAUSED,
                                UsageEvents.Event.ACTIVITY_STOPPED,
                                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                                UsageEvents.Event.KEYGUARD_SHOWN,
                                UsageEvents.Event.DEVICE_SHUTDOWN)
                        .build();
                history = manager.queryEvents(query);
            } else {
                history = manager.queryEvents(historyStart, now);
            }
            if (history == null) return null;
            AppUsageTimeline timeline = new AppUsageTimeline(start, packages);
            UsageEvents.Event event = new UsageEvents.Event();
            while (history.hasNextEvent()) {
                history.getNextEvent(event);
                int type = event.getEventType();
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        && (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        || type == UsageEvents.Event.KEYGUARD_SHOWN))
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && type == UsageEvents.Event.DEVICE_SHUTDOWN)) {
                    timeline.accept(null, null, event.getTimeStamp(), AppUsageTimeline.END_ALL);
                    continue;
                }
                String pkg = event.getPackageName();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    timeline.accept(pkg, event.getClassName(), event.getTimeStamp(),
                            AppUsageTimeline.RESUME);
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && type == UsageEvents.Event.ACTIVITY_STOPPED)) {
                    timeline.accept(pkg, event.getClassName(), event.getTimeStamp(),
                            AppUsageTimeline.PAUSE);
                }
            }
            return timeline;
        } catch (RuntimeException error) {
            Log.w("AppUsageReader", "Unable to read app usage events", error);
            return null;
        }
    }

    static Map<String, Long> totals(Context context, long now, Set<String> packages) {
        ProtectionTimeLedger.Sample time = ProtectionClock.read(context);
        long wallNow = System.currentTimeMillis();
        long wallStart = StreakLedger.boundary(time.day, time.zone) + wallNow - time.millis;
        AppUsageTimeline timeline = readRange(context, wallStart, wallNow, packages);
        try { return UsageBudgetStore.totals(context, packages, timeline == null ? null : timeline.totalsAt(wallNow)); }
        catch (java.io.IOException e) { Log.e("AppUsageReader", "Usage budget unavailable", e); return null; }
    }
}
