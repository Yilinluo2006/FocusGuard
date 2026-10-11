package com.luoyilin.focusguard;

/** Pure phase arithmetic; the last work interval completes the session. */
final class PomodoroTimeline {
    static final long WORK = 25 * 60_000L;
    static final long REST = 5 * 60_000L;
    static long duration(int rounds) {
        if (rounds < 1 || rounds > 24) throw new IllegalArgumentException("rounds");
        return rounds * WORK + (rounds - 1L) * REST;
    }
    static long elapsed(long startWall, long startElapsed, int startBoot,
                        long nowWall, long nowElapsed, int boot) {
        return Math.max(0, startBoot >= 0 && startBoot == boot
                ? nowElapsed - startElapsed : nowWall - startWall);
    }
    static boolean resting(long elapsed) { return elapsed % (WORK + REST) >= WORK; }
    static int round(long elapsed, int rounds) {
        return Math.min(rounds, (int) (elapsed / (WORK + REST)) + 1);
    }
    static long phaseRemaining(long elapsed, int rounds) {
        if (elapsed >= duration(rounds)) return 0;
        long position = elapsed % (WORK + REST);
        return (position < WORK ? WORK : WORK + REST) - position;
    }
    static long workTime(long elapsed, int rounds) {
        long bounded = Math.max(0, Math.min(elapsed, duration(rounds)));
        return bounded / (WORK + REST) * WORK + Math.min(WORK, bounded % (WORK + REST));
    }
    static int completedRounds(long elapsed, int rounds) {
        return (int) (workTime(elapsed, rounds) / WORK);
    }
}
