package com.luoyilin.focusguard;

/** Determines when a timed focus session ended and how much focus time it earned. */
final class TimedFocusTimeline {
    static final class Outcome {
        final String reason;
        final long elapsedMillis;
        final long endedAt;

        Outcome(String reason, long elapsedMillis, long endedAt) {
            this.reason = reason;
            this.elapsedMillis = elapsedMillis;
            this.endedAt = endedAt;
        }
    }

    static Outcome settle(long start, long sleep, long now, long elapsed,
                          long duration, boolean countUp, boolean finishRequested) {
        if (start <= 0) return null;
        long focused = Math.max(0, elapsed);
        if (sleep > start && now >= sleep && (sleep < start + duration || focused < duration)) {
            return new Outcome("sleep_takeover", Math.min(focused, sleep - start), sleep);
        }
        if (focused >= duration) {
            return new Outcome("completed", duration, now - (focused - duration));
        }
        if (finishRequested) {
            return new Outcome(countUp ? "completed" : "user_interrupted", focused, now);
        }
        return null;
    }
}
