package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class PomodoroTimelineTest {
    @Test public void finalRoundHasNoForcedRest() {
        assertEquals(25 * 60000L, PomodoroTimeline.duration(1));
        assertEquals(85 * 60000L, PomodoroTimeline.duration(3));
        assertEquals(0, PomodoroTimeline.phaseRemaining(85 * 60000L, 3));
        assertEquals(75 * 60000L, PomodoroTimeline.workTime(90 * 60000L, 3));
    }
    @Test public void exactWorkAndRestBoundaries() {
        assertFalse(PomodoroTimeline.resting(25 * 60000L - 1));
        assertTrue(PomodoroTimeline.resting(25 * 60000L));
        assertEquals(5 * 60000L, PomodoroTimeline.phaseRemaining(25 * 60000L, 3));
        assertFalse(PomodoroTimeline.resting(30 * 60000L));
        assertEquals(2, PomodoroTimeline.round(30 * 60000L, 3));
        assertEquals(25 * 60000L, PomodoroTimeline.phaseRemaining(30 * 60000L, 3));
    }
    @Test public void restNeverCountsAsWork() {
        assertEquals(25 * 60000L, PomodoroTimeline.workTime(29 * 60000L, 3));
        assertEquals(1, PomodoroTimeline.completedRounds(49 * 60000L, 3));
        assertEquals(2, PomodoroTimeline.completedRounds(55 * 60000L, 3));
    }
    @Test public void clockEditsDoNotSkipRoundsWithinOneBoot() {
        assertEquals(1000, PomodoroTimeline.elapsed(10000, 100, 4, 999999, 1100, 4));
        assertEquals(2000, PomodoroTimeline.elapsed(10000, 100, 4, 12000, 20, 5));
        assertEquals(0, PomodoroTimeline.elapsed(10000, 100, 4, 9000, 20, 5));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectZeroRounds() {
        PomodoroTimeline.duration(0);
    }
}
