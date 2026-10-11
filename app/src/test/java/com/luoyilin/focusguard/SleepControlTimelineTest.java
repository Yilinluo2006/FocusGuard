package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class SleepControlTimelineTest {
    private final long start = 1_000_000L;
    private SleepControlTimeline.Session session() { return new SleepControlTimeline.Session(start, start + 60_000); }
    private void observe(SleepControlTimeline.Session s, long offset, String status) {
        s.observe(start + offset, 500_000 + offset, 7, status);
    }
    @Test public void confirmedIntervalsMergeAndStopAtPlannedEnd() {
        SleepControlTimeline.Session s = session();
        observe(s, 0, "healthy"); observe(s, 30_000, "healthy"); observe(s, 65_000, "healthy");
        s.finish(start + 65_000, "planned_end");
        assertEquals(60_000, s.between(start, s.plannedEnd, true));
        assertEquals(1, s.segments.size()); assertEquals(0, s.unknown());
        assertEquals(s.plannedEnd, s.endedAt);
    }
    @Test public void lateFirstObservationDoesNotBackfill() {
        SleepControlTimeline.Session s = session();
        observe(s, 20_000, "healthy"); observe(s, 30_000, "healthy");
        s.finish(start + 30_000, "user_interrupted");
        assertEquals(10_000, s.between(start, s.plannedEnd, true)); assertEquals(20_000, s.unknown());
    }
    @Test public void longSilenceIsUnknownRatherThanProtectedOrFailed() {
        SleepControlTimeline.Session s = session();
        observe(s, 0, "healthy"); observe(s, 50_000, "healthy"); observe(s, 60_000, "healthy");
        s.finish(s.plannedEnd, "planned_end");
        assertEquals(10_000, s.between(start, s.plannedEnd, true)); assertEquals(50_000, s.unknown());
        assertEquals(0, s.unavailableCount);
    }
    @Test public void disabledAndUnavailableAreDistinctAndTransitionTimeIsUnknown() {
        SleepControlTimeline.Session s = session();
        observe(s, 0, "healthy"); observe(s, 10_000, "accessibility_disabled");
        observe(s, 20_000, "accessibility_disabled"); observe(s, 30_000, "service_unavailable");
        observe(s, 40_000, "service_unavailable"); observe(s, 50_000, "healthy"); observe(s, 60_000, "healthy");
        s.finish(s.plannedEnd, "planned_end");
        assertEquals(1, s.disabledCount); assertEquals(1, s.unavailableCount);
        assertEquals(20_000, s.between(start, s.plannedEnd, false));
        assertEquals(10_000, s.between(start, s.plannedEnd, true)); assertEquals(30_000, s.unknown());
    }
    @Test public void rebootAndClockJumpCannotCreateConfirmedTime() {
        SleepControlTimeline.Session s = session(); observe(s, 0, "healthy");
        s.observe(start + 10_000, 510_000, 8, "healthy");
        s.observe(start + 30_000, 520_000, 8, "healthy");
        assertEquals(0, s.between(start, s.plannedEnd, true)); assertEquals(30_000, s.unknown());
    }
    @Test public void unknownBootNeverCreditsAnInterval() {
        SleepControlTimeline.Session s = session();
        s.observe(start, 500_000, -1, "healthy"); s.observe(start + 10_000, 510_000, -1, "healthy");
        assertEquals(0, s.between(start, s.plannedEnd, true));
    }
    @Test public void repeatedFinishOrObservationCannotAlterSettledRecord() {
        SleepControlTimeline.Session s = session(); observe(s, 0, "healthy"); observe(s, 10_000, "healthy");
        s.finish(start + 10_000, "user_interrupted");
        observe(s, 30_000, "healthy"); s.finish(s.plannedEnd, "planned_end");
        assertEquals("user_interrupted", s.reason); assertEquals(start + 10_000, s.endedAt);
        assertEquals(10_000, s.between(start, s.plannedEnd, true));
    }
    @Test public void scheduleSettlementKeepsEndReasonsDistinct() {
        SleepControlTimeline.Session s = session(); observe(s, 0, "healthy");
        assertEquals("", SleepControlTimeline.settleReason(s, start + 10_000, start, s.plannedEnd, 0));
        assertEquals("user_interrupted", SleepControlTimeline.settleReason(s, start + 10_000, start, s.plannedEnd, start));
        assertEquals("plan_changed", SleepControlTimeline.settleReason(s, start + 10_000, 0, 0, 0));
        assertEquals("planned_end", SleepControlTimeline.settleReason(s, s.plannedEnd, 0, 0, 0));
        assertEquals("clock_changed", SleepControlTimeline.settleReason(s, start - 1, 0, 0, 0));
    }
    @Test public void settledOrDismissedScheduleNeverRestarts() {
        assertFalse(SleepControlTimeline.shouldStart(start, start, 0));
        assertFalse(SleepControlTimeline.shouldStart(start, 0, start));
        assertFalse(SleepControlTimeline.shouldStart(0, 0, 0));
        assertTrue(SleepControlTimeline.shouldStart(start + 86400000, start, 0));
    }
    @Test public void intervalsSplitExactlyAcrossFourOClock() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long boundary = FocusStatistics.boundary(LocalDate.of(2026, 9, 30), zone);
        SleepControlTimeline.Session s = new SleepControlTimeline.Session(boundary - 10_000, boundary + 10_000);
        s.observe(s.start, 500_000, 7, "healthy"); s.observe(s.plannedEnd, 520_000, 7, "healthy");
        s.finish(s.plannedEnd, "planned_end");
        assertEquals(10_000, s.between(s.start, boundary, true));
        assertEquals(10_000, s.between(boundary, s.plannedEnd, true));
    }
}
