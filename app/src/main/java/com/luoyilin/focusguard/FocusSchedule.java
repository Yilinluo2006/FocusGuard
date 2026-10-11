package com.luoyilin.focusguard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class FocusSchedule {
    static final int ALL_DAYS = 127;
    static final String WEEKDAYS_KEY = "sleepWeekdays";

    static long nextStart(long now, int minuteOfDay, ZoneId zone) {
        return nextStart(now, minuteOfDay, ALL_DAYS, zone);
    }

    static long nextStart(long now, int minuteOfDay, int weekdays, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int days = 0; days <= 7; days++) {
            LocalDate date = today.plusDays(days);
            if (!selected(date, weekdays)) continue;
            long start = date.atStartOfDay().plusMinutes(minuteOfDay).atZone(zone).toInstant().toEpochMilli();
            if (start > now) return start;
        }
        return 0;
    }

    private static boolean selected(LocalDate date, int weekdays) {
        return (weekdays & (1 << (date.getDayOfWeek().getValue() - 1))) != 0;
    }

    static int reminderStage(long remaining) {
        return remaining <= 0 || remaining > 300_000 ? 0 : remaining <= 60_000 ? 2 : 1;
    }
    static long activeStart(long now, int minuteOfDay, int durationMinutes, ZoneId zone) {
        return activeStart(now, minuteOfDay, durationMinutes, ALL_DAYS, zone);
    }

    static long activeStart(long now, int minuteOfDay, int durationMinutes, int weekdays, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int days = 0; days <= 1; days++) {
            LocalDate date = today.minusDays(days);
            if (!selected(date, weekdays)) continue;
            long start = date.atStartOfDay().plusMinutes(minuteOfDay)
                    .atZone(zone).toInstant().toEpochMilli();
            if (now >= start && now < start + durationMinutes * 60_000L) return start;
        }
        return 0;
    }
}
