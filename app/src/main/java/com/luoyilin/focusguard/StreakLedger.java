package com.luoyilin.focusguard;

import java.time.*;
import java.util.Properties;

/** Pure accounting: only adjacent, same-session heartbeat intervals are credited. */
final class StreakLedger {
    static final long REQUIRED = 12 * 60 * 60_000L;
    static long day(long now, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(zone);
        LocalDate date = t.toLocalDate();
        if (t.toLocalTime().isBefore(LocalTime.of(4, 0))) date = date.minusDays(1);
        return date.toEpochDay();
    }
    static long boundary(long day, ZoneId zone) {
        return LocalDate.ofEpochDay(day).atTime(4, 0).atZone(zone).toInstant().toEpochMilli();
    }
    static long n(Properties p, String key) { return Long.parseLong(p.getProperty(key, "0")); }
    static void put(Properties p, String key, long value) { p.setProperty(key, "" + value); }
    static void init(Properties p, long now, ZoneId zone) {
        if (!p.containsKey("first")) {
            long d = day(now, zone);
            put(p, "first", d); put(p, "next", d);
            p.setProperty("preparation", "" + (boundary(d + 1, zone) - now < REQUIRED));
        }
    }
    static void settle(Properties p, long now, ZoneId zone) {
        if (p.containsKey("complianceFrom")) { ComplianceLedger.settle(p, now, zone); return; }
        init(p, now, zone);
        long today = day(now, zone), next = n(p, "next");
        for (long d = next; d < today; d++) {
            boolean broken = p.containsKey("broken." + d);
            boolean success = !broken && n(p, "ms." + d) >= REQUIRED;
            boolean preparation = d == n(p, "first") && !broken
                    && Boolean.parseBoolean(p.getProperty("preparation"));
            p.setProperty("result." + d, success ? "完成" : preparation ? "准备" : "未完成");
            if (success) {
                put(p, "streak", n(p, "streak") + 1);
                put(p, "best", Math.max(n(p, "best"), n(p, "streak")));
            } else if (!preparation) put(p, "streak", 0);
            put(p, "next", d + 1);
        }
    }
    static void pulse(Properties p, long now, long elapsed, String session,
                      boolean eligible, ZoneId zone) {
        init(p, now, zone);
        long previous = n(p, "wall"), delta = elapsed - n(p, "elapsed");
        long wallDelta = now - previous;
        if (eligible && "true".equals(p.getProperty("eligible"))
                && session.equals(p.getProperty("session"))
                && delta > 0 && delta <= 30_000 && wallDelta > 0
                && Math.abs(delta - wallDelta) <= 2000) {
            long credited = Math.min(delta, wallDelta);
            long cursor = now - credited;
            while (cursor < now) {
                long d = day(cursor, zone);
                long end = Math.min(now, boundary(d + 1, zone));
                if (d >= n(p, "next")) put(p, "ms." + d, n(p, "ms." + d) + end - cursor);
                cursor = end;
            }
        }
        put(p, "wall", now); put(p, "elapsed", elapsed);
        p.setProperty("session", session); p.setProperty("eligible", "" + eligible);
        settle(p, now, zone);
    }
    static void breakToday(Properties p, long now, ZoneId zone, String reason) {
        settle(p, now, zone);
        p.setProperty("broken." + day(now, zone), reason);
    }
    /** Derive counters from imported past days; never import heartbeat or today's credited time. */
    static void rebuildHistory(Properties p, long now, ZoneId zone) {
        if (p.containsKey("complianceFrom")) { ComplianceLedger.settle(p, now, zone); return; }
        settle(p, now, zone);
        long today = day(now, zone), run = 0, best = n(p, "best"), previous = Long.MIN_VALUE;
        java.util.TreeSet<Long> days = new java.util.TreeSet<>();
        for (String key : p.stringPropertyNames()) if (key.startsWith("result.")) {
            try { long d = Long.parseLong(key.substring(7)); if (d < today) days.add(d); }
            catch (NumberFormatException ignored) { }
        }
        for (long d : days) {
            if (previous != d - 1) run = 0;
            if ("完成".equals(p.getProperty("result." + d)) && !p.containsKey("broken." + d)
                    && ("2".equals(p.getProperty("ruleVersion." + d)) || n(p, "ms." + d) >= REQUIRED)) run++;
            else if (!"准备".equals(p.getProperty("result." + d))) run = 0;
            best = Math.max(best, run); previous = d;
        }
        put(p, "best", best); put(p, "streak", previous == today - 1 ? run : 0);
    }
}
