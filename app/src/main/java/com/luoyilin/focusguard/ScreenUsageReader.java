package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;

final class ScreenUsageReader {
    static final class Snapshot {
        final ScreenUsageLedger.Result usage;
        final String unavailableReason;

        Snapshot(ScreenUsageLedger.Result usage, String unavailableReason) {
            this.usage = usage;
            this.unavailableReason = unavailableReason;
        }
    }

    static Snapshot read(Context context, long start, long end) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return new Snapshot(null, "当前设备需要 Android 9 或更新版本才能读取亮屏事件");
        }
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        UsageStatsManager stats = context.getSystemService(UsageStatsManager.class);
        if (appOps == null || stats == null) return new Snapshot(null, "系统使用记录暂不可用");
        int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), context.getPackageName())
                : appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), context.getPackageName());
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return new Snapshot(null, "请先开启使用情况访问权限");
        }
        try {
            UsageEvents history = stats.queryEvents(Math.max(0, start - 48 * 3600000L), end);
            if (history == null) return new Snapshot(null, "系统使用记录暂不可用");
            List<ScreenUsageLedger.Event> events = new ArrayList<>();
            UsageEvents.Event event = new UsageEvents.Event();
            while (history.hasNextEvent()) {
                history.getNextEvent(event);
                int type = event.getEventType();
                int mapped;
                if (type == UsageEvents.Event.SCREEN_INTERACTIVE) mapped = ScreenUsageLedger.SCREEN_ON;
                else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) mapped = ScreenUsageLedger.SCREEN_OFF;
                else if (type == UsageEvents.Event.KEYGUARD_HIDDEN) mapped = ScreenUsageLedger.KEYGUARD_HIDDEN;
                else if (type == UsageEvents.Event.KEYGUARD_SHOWN) mapped = ScreenUsageLedger.KEYGUARD_SHOWN;
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && type == UsageEvents.Event.DEVICE_SHUTDOWN) mapped = ScreenUsageLedger.SHUTDOWN;
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && type == UsageEvents.Event.DEVICE_STARTUP) mapped = ScreenUsageLedger.STARTUP;
                else continue;
                events.add(new ScreenUsageLedger.Event(event.getTimeStamp(), mapped));
            }
            return new Snapshot(ScreenUsageLedger.calculate(start, end, events), null);
        } catch (RuntimeException error) {
            android.util.Log.w("ScreenUsage", "Unable to read system screen events", error);
            return new Snapshot(null, "读取系统使用记录失败");
        }
    }
}
