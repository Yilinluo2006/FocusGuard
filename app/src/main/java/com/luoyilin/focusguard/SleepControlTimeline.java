package com.luoyilin.focusguard;

import java.util.ArrayList;
import java.util.List;

/** Observed app-control intervals; gaps and clock changes are never credited. */
final class SleepControlTimeline {
    static final String HEALTHY = "healthy";
    static final long MAX_GAP = 35_000L;
    static final class Segment {
        final long from, to;
        final String status;
        Segment(long from, long to, String status) { this.from = from; this.to = to; this.status = status; }
    }
    static final class Session {
        final long start, plannedEnd;
        long lastAt, lastElapsed, endedAt;
        int boot = -1, disabledCount, unavailableCount;
        String status = "", reason = "";
        final List<Segment> segments = new ArrayList<>();
        Session(long start, long plannedEnd) { this.start = start; this.plannedEnd = plannedEnd; }
        void observe(long now, long elapsed, int currentBoot, String currentStatus) {
            if (!reason.isEmpty() || now < start) return;
            long wallDelta = now - lastAt, clockDelta = elapsed - lastElapsed;
            long until = Math.min(now, plannedEnd);
            if (lastAt > 0 && currentBoot >= 0 && boot == currentBoot
                    && clockDelta > 0 && clockDelta <= MAX_GAP && wallDelta > 0 && wallDelta <= MAX_GAP
                    && Math.abs(wallDelta - clockDelta) <= 2000 && status.equals(currentStatus)) {
                add(lastAt, until, status);
            }
            if (now < plannedEnd && !currentStatus.equals(status)) {
                if ("accessibility_disabled".equals(currentStatus)) disabledCount++;
                if ("service_unavailable".equals(currentStatus)) unavailableCount++;
            }
            lastAt = until; lastElapsed = elapsed; boot = currentBoot; status = currentStatus;
        }
        private void add(long from, long to, String value) {
            if (to <= from) return;
            if (!segments.isEmpty()) {
                Segment previous = segments.get(segments.size() - 1);
                if (previous.to == from && previous.status.equals(value)) {
                    segments.set(segments.size() - 1, new Segment(previous.from, to, value)); return;
                }
            }
            segments.add(new Segment(from, to, value));
        }
        void finish(long now, String value) {
            if (!reason.isEmpty()) return;
            endedAt = Math.max(start, Math.min(now, plannedEnd)); reason = value;
        }
        long between(long from, long to, boolean healthy) {
            long result = 0;
            for (Segment segment : segments) {
                if (HEALTHY.equals(segment.status) == healthy)
                    result += Math.max(0, Math.min(segment.to, to) - Math.max(segment.from, from));
            }
            return result;
        }
        long span() { return Math.max(0, (reason.isEmpty() ? lastAt : endedAt) - start); }
        long unknown() { return Math.max(0, span() - between(start, plannedEnd, true) - between(start, plannedEnd, false)); }
    }
    static String settleReason(Session session, long now, long activeStart, long activeEnd, long dismissed) {
        if (now >= session.plannedEnd) return "planned_end";
        if (now < session.lastAt) return "clock_changed";
        if (dismissed == session.start) return "user_interrupted";
        return activeStart != session.start || activeEnd != session.plannedEnd ? "plan_changed" : "";
    }
    static boolean shouldStart(long activeStart, long recordedThrough, long dismissed) {
        return activeStart > 0 && activeStart > recordedThrough && activeStart != dismissed;
    }
    static String reason(String value) {
        switch (value) {
            case "planned_end": return "计划到时";
            case "user_interrupted": return "主动提前解除";
            case "plan_changed": return "计划变更";
            case "clock_changed": return "系统时间变化";
            default: return "其他结束";
        }
    }
}
