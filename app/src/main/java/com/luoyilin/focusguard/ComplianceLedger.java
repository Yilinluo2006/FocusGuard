package com.luoyilin.focusguard;

import java.time.ZoneId;
import java.util.*;

/** Evidence based day results. An unknown observation is never a failed day. */
final class ComplianceLedger {
    static final String OK = "ok", PENDING = "pending", FAILED = "failed";
    static final long OVERUSE_GRACE = 15_000;

    static void activate(Properties p, long now, ZoneId zone) {
        if (p.containsKey("complianceFrom")) return;
        // Finish the old accounting before starting the new policy. Preserve past rewards.
        StreakLedger.settle(p, now, zone);
        StreakLedger.put(p, "complianceFrom", StreakLedger.day(now, zone));
        StreakLedger.put(p, "complianceAt", now);
    }

    static void rule(Properties p, long day, String id, String state, boolean executed, String reason) {
        String key = "c.rule." + day + "." + id;
        // A later successful sample cannot erase a confirmed violation.
        if (!FAILED.equals(p.getProperty(key))) p.setProperty(key, state);
        if (executed) p.setProperty("c.active." + day, "true");
        if (FAILED.equals(state)) p.setProperty("c.failure." + day, reason);
    }

    static boolean overrun(long used, long quota, long baseline, long granted) {
        return used > Math.max(quota, baseline) + granted + OVERUSE_GRACE;
    }
    static long unexcused(long from, long to, List<long[]> grants) {
        long cursor = from, result = 0; List<long[]> sorted = new ArrayList<>(grants);
        sorted.sort(Comparator.comparingLong(v -> v[0]));
        for (long[] v : sorted) { if (v[1] <= cursor || v[0] >= to) continue;
            result += Math.max(0, Math.min(to, v[0]) - cursor); cursor = Math.max(cursor, Math.min(to, v[1])); }
        return result + Math.max(0, to - cursor);
    }

    /** Grant time belongs only to its package and the actual part inside that quota day. */
    static void grant(Properties p, String pkg, long from, long until, ZoneId zone) {
        for (long cursor = from; cursor < until;) {
            long d = StreakLedger.day(cursor, zone), end = Math.min(until, StreakLedger.boundary(d + 1, zone));
            String key = "bonus." + d + "." + pkg;
            StreakLedger.put(p, key, StreakLedger.n(p, key) + end - cursor);
            cursor = end;
        }
    }

    static String local(Properties p, long d) {
        if (p.containsKey("broken." + d) || p.containsKey("c.failure." + d)) return "未完成";
        boolean pending = !p.containsKey("c.checked." + d);
        String prefix = "c.rule." + d + ".";
        for (String key : p.stringPropertyNames()) if (key.startsWith(prefix)) {
            if (FAILED.equals(p.getProperty(key))) return "未完成";
            pending |= PENDING.equals(p.getProperty(key));
        }
        if (pending) return "待确认";
        return Boolean.parseBoolean(p.getProperty("c.active." + d)) ? "完成" : "无记录";
    }

    static void settle(Properties p, long now, ZoneId zone) {
        long today = StreakLedger.day(now, zone), first = StreakLedger.n(p, "complianceFrom");
        for (long d = first; d < today; d++) {
            String local = local(p, d);
            if (!"未完成".equals(local) && StreakLedger.n(p, "c.through." + d) < StreakLedger.boundary(d + 1, zone)) local = "待确认";
            p.setProperty("c.local." + d, local);
            String result = local;
            if ("true".equals(p.getProperty("c.sync"))) {
                String remote = p.getProperty("c.remote." + d);
                if ("未完成".equals(remote)) result = remote;
                else if (!"未完成".equals(local)) {
                    // Closed-day confirmation must be fetched after the 04:00 boundary.
                    boolean confirmed = StreakLedger.n(p, "c.remoteAt." + d) >= StreakLedger.boundary(d + 1, zone);
                    result = confirmed && "完成".equals(remote) && !"待确认".equals(local)
                            ? "完成" : confirmed && "无记录".equals(remote) && "无记录".equals(local)
                            ? "无记录" : "待确认";
                }
            }
            p.setProperty("result." + d, result);
            p.setProperty("ruleVersion." + d, "2");
        }
        StreakLedger.put(p, "next", today);
        rebuild(p, today);
    }

    static void rebuild(Properties p, long today) {
        TreeSet<Long> days = new TreeSet<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith("result.")) {
            try { long d = Long.parseLong(k.substring(7)); if (d < today) days.add(d); }
            catch (NumberFormatException ignored) { }
        }
        long run = 0, best = StreakLedger.n(p, "best"), previous = Long.MIN_VALUE;
        long latestConfirmed = 0;
        for (long d : days) {
            if (previous != d - 1) run = 0;
            String result = p.getProperty("result." + d);
            if ("完成".equals(result) && !p.containsKey("broken." + d)) run++;
            else if ("待确认".equals(result)) {
                // Retain the last confirmed run while pending; do not bridge an unverified day.
                if (latestConfirmed == 0) latestConfirmed = run;
                run = 0;
            } else if (!"准备".equals(result)) { run = 0; latestConfirmed = 0; }
            best = Math.max(best, run); previous = d;
        }
        if (previous != today - 1) run = 0;
        if (previous == today - 1 && "待确认".equals(p.getProperty("result." + previous))) run = latestConfirmed;
        StreakLedger.put(p, "streak", run); StreakLedger.put(p, "best", best);
    }
}
