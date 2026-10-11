package com.luoyilin.focusguard;

import java.util.HashSet;
import java.util.Set;

/** Union of resumed activity intervals for one package, without double counting. */
final class UsageSession {
    private final Set<String> resumed = new HashSet<>();
    private final long countingStart;
    private long started;
    private long total;
    private final java.util.List<long[]> intervals = new java.util.ArrayList<>();

    UsageSession() { this(Long.MIN_VALUE); }

    UsageSession(long countingStart) { this.countingStart = countingStart; }

    void resume(String activity, long time) {
        if (resumed.isEmpty()) started = time;
        resumed.add(activity);
    }

    void end(String activity, long time) {
        if (resumed.remove(activity) && resumed.isEmpty()) {
            total += Math.max(0, time - Math.max(started, countingStart));
            if (time > Math.max(started, countingStart)) intervals.add(new long[]{Math.max(started, countingStart), time});
        }
    }

    void endAll(long time) {
        if (isActive()) {
            total += Math.max(0, time - Math.max(started, countingStart));
            if (time > Math.max(started, countingStart)) intervals.add(new long[]{Math.max(started, countingStart), time});
        }
        resumed.clear();
    }

    boolean isActive() { return !resumed.isEmpty(); }

    java.util.List<long[]> intervalsAt(long now) {
        java.util.List<long[]> result = new java.util.ArrayList<>(intervals);
        if (isActive() && now > Math.max(started, countingStart)) result.add(new long[]{Math.max(started, countingStart), now});
        return result;
    }

    long totalAt(long time) {
        return total + (isActive() ? Math.max(0, time - Math.max(started, countingStart)) : 0);
    }
}
