package com.luoyilin.focusguard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Daily settled work, preserving the existing 04:00 boundary and pomodoro rest exclusions. */
final class FocusHeatmapData {
    static Map<LocalDate, Long> totals(List<FocusStatistics.Entry> entries, ZoneId zone, long cutoff) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (FocusStatistics.Entry entry : entries) {
            long end = Math.min(entry.end, cutoff);
            if (entry.work <= 0 || end <= entry.start) continue;
            LocalDate first = FocusStatistics.day(entry.start, zone);
            LocalDate last = FocusStatistics.day(end - 1, zone);
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                long amount = entry.between(FocusStatistics.boundary(day, zone),
                        Math.min(end, FocusStatistics.boundary(day.plusDays(1), zone)));
                if (amount > 0) result.merge(day, amount, Long::sum);
            }
        }
        return result;
    }
    static int level(long millis) {
        if (millis <= 0) return 0;
        if (millis < 25 * 60_000L) return 1;
        if (millis < 60 * 60_000L) return 2;
        if (millis < 120 * 60_000L) return 3;
        return 4;
    }
}
