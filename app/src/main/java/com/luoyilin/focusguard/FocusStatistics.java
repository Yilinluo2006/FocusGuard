package com.luoyilin.focusguard;

import java.time.*;
import java.util.*;

/** Read-only statistics of settled sessions. Days follow the existing 04:00 boundary. */
final class FocusStatistics {
    static final class Entry {
        final String name, mode, reason;
        final long start, end, work;
        Entry(String name, String mode, String reason, long start, long end, long work) {
            this.name = name == null || name.trim().isEmpty() ? "未命名专注" : name;
            this.mode = mode; this.reason = reason; this.start = start;
            this.end = Math.max(start, end); this.work = Math.max(0, work);
        }
        long between(long from, long to) {
            if (to <= from || work == 0) return 0;
            if (!"strict_pomodoro".equals(mode)) {
                long span = end - start;
                if (span <= 0) return 0;
                long a = Math.max(start, from), b = Math.min(end, to);
                // Difference of cumulative values preserves totals across adjacent buckets.
                return b <= a ? 0 : (long) (work * ((b - start) / (double) span))
                        - (long) (work * ((a - start) / (double) span));
            }
            long total = 0, left = Math.min(work, 24 * PomodoroTimeline.WORK);
            for (long at = start; left > 0 && at < end; at += PomodoroTimeline.WORK + PomodoroTimeline.REST) {
                long amount = Math.min(left, PomodoroTimeline.WORK);
                total += Math.max(0, Math.min(Math.min(at + amount, end), to) - Math.max(at, from));
                left -= amount;
            }
            return total;
        }
    }
    static final class Summary {
        long millis;
        int sessions, completed;
        final Map<String, Long> tasks = new LinkedHashMap<>();
        final Map<String, Long> reasons = new LinkedHashMap<>();
    }
    static LocalDate day(long at, ZoneId zone) {
        ZonedDateTime local = Instant.ofEpochMilli(at).atZone(zone);
        return local.toLocalTime().isBefore(LocalTime.of(4, 0))
                ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }
    static long boundary(LocalDate date, ZoneId zone) {
        return date.atTime(4, 0).atZone(zone).toInstant().toEpochMilli();
    }
    static Summary summarize(List<Entry> entries, long from, long to) {
        Summary result = new Summary();
        for (Entry e : entries) {
            long amount = e.between(from, to);
            result.millis += amount;
            if (amount > 0) result.tasks.merge(e.name, amount, Long::sum);
            if (e.start >= from && e.start < to) {
                result.sessions++;
                if ("completed".equals(e.reason)) result.completed++;
                else result.reasons.merge(reason(e.reason), 1L, Long::sum);
            }
        }
        return result;
    }
    static String reason(String value) {
        if ("completed".equals(value)) return "正常完成";
        if ("user_interrupted".equals(value)) return "主动结束";
        if ("sleep_takeover".equals(value)) return "睡眠接管";
        return "其他结束";
    }
    static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + " 分钟" : minutes / 60 + " 小时 " + minutes % 60 + " 分";
    }
}
