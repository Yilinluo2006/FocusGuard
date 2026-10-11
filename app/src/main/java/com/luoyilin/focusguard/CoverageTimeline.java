package com.luoyilin.focusguard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/** Bounded diagnostic intervals; never used to award streak credit. */
final class CoverageTimeline {
    static final int LIMIT = 600;
    static void append(Properties p, long start, long end, String state, String reason) {
        if (end <= start) return;
        long last = StreakLedger.n(p, "timeline.last");
        String key = "segment." + last + ".";
        if (last > 0 && StreakLedger.n(p, key + "end") == start
                && state.equals(p.getProperty(key + "state"))
                && reason.equals(p.getProperty(key + "reason"))) {
            StreakLedger.put(p, key + "end", end);
        } else {
            last++;
            key = "segment." + last + ".";
            StreakLedger.put(p, "timeline.last", last);
            StreakLedger.put(p, key + "start", start);
            StreakLedger.put(p, key + "end", end);
            p.setProperty(key + "state", state);
            p.setProperty(key + "reason", reason);
            long first = Math.max(1, StreakLedger.n(p, "timeline.first"));
            if (last - first >= LIMIT) {
                String old = "segment." + first + ".";
                for (String field : new String[]{"start", "end", "state", "reason"}) p.remove(old + field);
                first++;
            }
            StreakLedger.put(p, "timeline.first", first);
        }
    }

    static String details(Properties p, long now, ZoneId zone) {
        long dayStart = StreakLedger.boundary(StreakLedger.day(now, zone), zone);
        long started = StreakLedger.n(p, "timeline.started");
        if (started == 0) return "\n\n时段明细：等待首次采样。";
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm:ss");
        StringBuilder out = new StringBuilder("\n\n今日时段明细（最近记录在前）\n");
        out.append("分段记录启用于 ").append(Instant.ofEpochMilli(started).atZone(zone)
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")));
        out.append("\n启用前只有汇总，无法还原时段。\n");
        long latest = StreakLedger.n(p, "wall");
        if (now > latest + 30000 && latest >= started) {
            out.append("\n").append(time(Math.max(dayStart, latest), zone, format)).append("–")
                    .append(time(now, zone, format)).append("  待确认：尚未收到新采样\n");
        }
        int count = 0;
        long first = Math.max(1, StreakLedger.n(p, "timeline.first"));
        for (long i = StreakLedger.n(p, "timeline.last"); i >= first; i--) {
            String key = "segment." + i + ".";
            long start = Math.max(dayStart, StreakLedger.n(p, key + "start"));
            long end = Math.min(now, StreakLedger.n(p, key + "end"));
            if (end <= start) continue;
            if (++count > 60) { out.append("\n仅展示最近 60 段。"); break; }
            String state = p.getProperty(key + "state", "unknown");
            String label = "protected".equals(state) ? "解锁 · 已保护"
                    : "unprotected".equals(state) ? "解锁 · 未满足保护条件"
                    : "locked".equals(state) ? "锁屏／熄屏（采样确认）" : "待确认";
            out.append("\n").append(time(start, zone, format)).append("–").append(time(end, zone, format))
                    .append("  ").append(label);
            String reason = p.getProperty(key + "reason", "");
            if (!reason.isEmpty()) out.append("\n").append(reason);
            out.append("\n");
        }
        if (count == 0) out.append("\n尚无已结束的采样间隔。");
        if (first > 1) out.append("\n较早分段可能已清理，最多保留 600 段。");
        return out.toString();
    }
    private static String time(long ms, ZoneId zone, DateTimeFormatter format) {
        return Instant.ofEpochMilli(ms).atZone(zone).format(format);
    }
}
