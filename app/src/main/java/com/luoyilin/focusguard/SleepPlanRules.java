package com.luoyilin.focusguard;

import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** A stronger weekly plan must retain every previously protected minute and app. */
final class SleepPlanRules {
    static final String[] KEYS = {"daily", "minute", "duration", "sleepWeekdays", "allowed"};
    private static final int WEEK_MINUTES = 7 * 1440;

    static boolean valid(Map<String, ?> plan) {
        Object daily = plan.get("daily"), allowed = plan.get("allowed");
        if (daily != null && !(daily instanceof Boolean)) return false;
        for (String key : new String[]{"minute", "duration", "sleepWeekdays"})
            if (plan.get(key) != null && !(plan.get(key) instanceof Integer)) return false;
        int minute = number(plan, "minute", 1350), duration = number(plan, "duration", 180);
        int days = number(plan, "sleepWeekdays", 127);
        if (minute < 0 || minute >= 1440 || duration < 1 || duration > 720 || days < 0 || days > 127
                || (Boolean.TRUE.equals(daily) && days == 0)) return false;
        if (allowed != null && !(allowed instanceof Set)) return false;
        if (allowed instanceof Set) for (Object item : (Set<?>) allowed)
            if (!(item instanceof String)) return false;
        return true;
    }

    static boolean changed(Map<String, ?> before, Map<String, ?> after) {
        return Boolean.TRUE.equals(before.get("daily")) != Boolean.TRUE.equals(after.get("daily"))
                || number(before, "minute", 1350) != number(after, "minute", 1350)
                || number(before, "duration", 180) != number(after, "duration", 180)
                || number(before, "sleepWeekdays", 127) != number(after, "sleepWeekdays", 127)
                || !allowed(before).equals(allowed(after));
    }

    static boolean isRelaxation(Map<String, ?> before, Map<String, ?> after) {
        if (!Boolean.TRUE.equals(before.get("daily"))) return false;
        BitSet lost = coverage(before);
        if (lost.isEmpty()) return false;
        if (!allowed(before).containsAll(allowed(after))) return true;
        lost.andNot(coverage(after));
        return !lost.isEmpty();
    }

    static boolean isRelaxation(Map<String, ?> before, Map<String, ?> after, ZoneId zone, long now) {
        if (isRelaxation(before, after)) return true;
        // Durations are elapsed minutes. Moving a start across an offset transition can
        // lose real coverage even when the nominal weekly-minute ranges contain each other.
        return Boolean.TRUE.equals(before.get("daily"))
                && number(before, "minute", 1350) != number(after, "minute", 1350)
                && zone.getRules().nextTransition(Instant.ofEpochMilli(now).minus(1, ChronoUnit.DAYS)) != null;
    }

    private static BitSet coverage(Map<String, ?> plan) {
        BitSet covered = new BitSet(WEEK_MINUTES);
        if (!Boolean.TRUE.equals(plan.get("daily"))) return covered;
        int minute = number(plan, "minute", 1350), duration = number(plan, "duration", 180);
        int days = number(plan, "sleepWeekdays", 127);
        for (int day = 0; day < 7; day++) {
            if ((days & (1 << day)) == 0) continue;
            int start = day * 1440 + minute, end = start + duration;
            covered.set(start, Math.min(end, WEEK_MINUTES));
            if (end > WEEK_MINUTES) covered.set(0, end - WEEK_MINUTES);
        }
        return covered;
    }

    private static int number(Map<String, ?> plan, String key, int fallback) {
        Object value = plan.get(key);
        return value == null ? fallback : (Integer) value;
    }

    private static Set<?> allowed(Map<String, ?> plan) {
        Object value = plan.get("allowed");
        return value == null ? Collections.emptySet() : (Set<?>) value;
    }
}
