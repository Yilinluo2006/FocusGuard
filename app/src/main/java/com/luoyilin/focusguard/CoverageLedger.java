package com.luoyilin.focusguard;

import java.time.ZoneId;
import java.util.Properties;

/** Shadow accounting only. Unknown intervals are never silently credited. */
final class CoverageLedger {
    static void sample(Properties p, long now, long elapsed, int boot, String session, boolean unlocked,
                       boolean protectedNow, ZoneId zone) {
        long previous = StreakLedger.n(p, "wall");
        if (previous > 0 && now <= previous) return;
        boolean timelineReady = p.containsKey("timeline.started");
        if (!timelineReady) StreakLedger.put(p, "timeline.started", now);
        if (previous > 0) {
            long delta = elapsed - StreakLedger.n(p, "elapsed");
            boolean known = boot >= 0 && boot == StreakLedger.n(p, "boot")
                    && session.equals(p.getProperty("session"))
                    && delta > 0 && delta <= 30_000 && Math.abs(now - previous - delta) <= 2000;
            boolean wasUnlocked = Boolean.parseBoolean(p.getProperty("unlocked"));
            boolean wasProtected = Boolean.parseBoolean(p.getProperty("protected"));
            String bucket = !known || wasUnlocked != unlocked ? "unknown"
                    : !unlocked ? "locked" : wasProtected != protectedNow ? "unknown"
                    : protectedNow ? "protected" : "unprotected";
            String reason = "";
            if ("unknown".equals(bucket)) {
                if (boot < 0) reason = "启动标识不可用，无法确认连续性";
                else if (boot != StreakLedger.n(p, "boot")) reason = "设备重启前后，无法确认空档";
                else if (!session.equals(p.getProperty("session"))) reason = "采样进程切换或重启，无法确认空档";
                else if (delta <= 0 || Math.abs(now - previous - delta) > 2000)
                    reason = "时钟变化，无法确认间隔";
                else if (delta > 30000) reason = "采样空档超过 30 秒，不能判定为休眠或服务中断";
                else if (wasUnlocked != unlocked) reason = "锁屏状态切换，具体切换时刻未知";
                else reason = "保护条件切换，具体切换时刻未知";
            }
            // Migration starts here, not at an old aggregate's last heartbeat.
            if (timelineReady) CoverageTimeline.append(p, previous, now, bucket, reason);
            // Split at the same 04:00 boundary as the existing streak without changing it.
            long cursor = previous;
            while (cursor < now) {
                long day = StreakLedger.day(cursor, zone);
                long end = Math.min(now, StreakLedger.boundary(day + 1, zone));
                String key = bucket + "." + day;
                StreakLedger.put(p, key, StreakLedger.n(p, key) + end - cursor);
                cursor = end;
            }
        } else StreakLedger.put(p, "started", now);
        StreakLedger.put(p, "wall", now);
        StreakLedger.put(p, "elapsed", elapsed);
        StreakLedger.put(p, "boot", boot);
        p.setProperty("session", session);
        p.setProperty("unlocked", String.valueOf(unlocked));
        p.setProperty("protected", String.valueOf(protectedNow));
    }
}
