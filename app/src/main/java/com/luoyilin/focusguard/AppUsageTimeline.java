package com.luoyilin.focusguard;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Foreground sessions reconstructed before and after a quota boundary. */
final class AppUsageTimeline {
    static final int RESUME = 1;
    static final int PAUSE = 2;
    static final int END_ALL = 3;

    private final long start;
    private final Set<String> packages;
    private final Map<String, UsageSession> sessions = new HashMap<>();

    AppUsageTimeline(long start, Set<String> packages) {
        this.start = start;
        this.packages = packages;
    }

    void accept(String pkg, String activity, long at, int type) {
        if (type == END_ALL) {
            for (UsageSession session : sessions.values()) session.endAll(at);
            return;
        }
        if (pkg == null || (packages != null && !packages.contains(pkg))) return;
        if (type == RESUME) {
            sessions.computeIfAbsent(pkg, unused -> new UsageSession(start)).resume(activity, at);
        } else if (type == PAUSE) {
            UsageSession session = sessions.get(pkg);
            if (session != null) session.end(activity, at);
        }
    }

    UsageSession session(String pkg) { return sessions.get(pkg); }

    Map<String, java.util.List<long[]>> intervalsAt(long now) {
        Map<String, java.util.List<long[]>> result = new HashMap<>();
        for (Map.Entry<String, UsageSession> entry : sessions.entrySet()) result.put(entry.getKey(), entry.getValue().intervalsAt(now));
        return result;
    }

    Map<String, Long> totalsAt(long now) {
        Map<String, Long> totals = new HashMap<>();
        for (Map.Entry<String, UsageSession> entry : sessions.entrySet()) {
            totals.put(entry.getKey(), Math.min(Math.max(0, now - start),
                    entry.getValue().totalAt(now)));
        }
        return totals;
    }
}
