package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimedFocusTimelineTest {
    private static final long MINUTE = 60_000L;

    @Test public void countdownCompletesAtSleepBoundary() {
        TimedFocusTimeline.Outcome outcome = TimedFocusTimeline.settle(
                MINUTE, 26 * MINUTE, 26 * MINUTE, 25 * MINUTE,
                25 * MINUTE, false, false);
        assertEquals("completed", outcome.reason);
        assertEquals(25 * MINUTE, outcome.elapsedMillis);
        assertEquals(26 * MINUTE, outcome.endedAt);
    }

    @Test public void lateSettlementDoesNotTurnCompletedCountdownIntoSleepTakeover() {
        TimedFocusTimeline.Outcome outcome = TimedFocusTimeline.settle(
                MINUTE, 26 * MINUTE, 28 * MINUTE, 27 * MINUTE,
                25 * MINUTE, false, false);
        assertEquals("completed", outcome.reason);
        assertEquals(26 * MINUTE, outcome.endedAt);
    }

    @Test public void sleepTakeoverCapsCountUpTimeAtPlanStart() {
        TimedFocusTimeline.Outcome outcome = TimedFocusTimeline.settle(
                MINUTE, 31 * MINUTE, 36 * MINUTE, 35 * MINUTE,
                24 * 60 * MINUTE, true, false);
        assertEquals("sleep_takeover", outcome.reason);
        assertEquals(30 * MINUTE, outcome.elapsedMillis);
        assertEquals(31 * MINUTE, outcome.endedAt);
    }

    @Test public void manualFinishDistinguishesCountdownAndCountUp() {
        TimedFocusTimeline.Outcome countdown = TimedFocusTimeline.settle(
                MINUTE, 0, 11 * MINUTE, 10 * MINUTE, 25 * MINUTE, false, true);
        TimedFocusTimeline.Outcome countUp = TimedFocusTimeline.settle(
                MINUTE, 0, 11 * MINUTE, 10 * MINUTE, 24 * 60 * MINUTE, true, true);
        assertEquals("user_interrupted", countdown.reason);
        assertEquals("completed", countUp.reason);
        assertNull(TimedFocusTimeline.settle(MINUTE, 0, 11 * MINUTE,
                10 * MINUTE, 25 * MINUTE, false, false));
    }
}
