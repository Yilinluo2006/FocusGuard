package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class FocusStatisticsTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private long at(String value) { return LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli(); }
    @Test public void statisticsDayChangesAtFour() {
        assertEquals(LocalDate.of(2026, 9, 28), FocusStatistics.day(at("2026-09-29T03:59:59"), zone));
        assertEquals(LocalDate.of(2026, 9, 29), FocusStatistics.day(at("2026-09-29T04:00:00"), zone));
    }
    @Test public void crossBoundarySplitsDurationButCountsSessionOnce() {
        long start = at("2026-09-29T03:50:00"), middle = at("2026-09-29T04:00:00");
        FocusStatistics.Entry entry = new FocusStatistics.Entry("阅读", "countup", "completed", start, start + 30 * 60000, 30 * 60000);
        FocusStatistics.Summary before = FocusStatistics.summarize(Collections.singletonList(entry), start, middle);
        FocusStatistics.Summary after = FocusStatistics.summarize(Collections.singletonList(entry), middle, entry.end);
        assertEquals(10 * 60000, before.millis); assertEquals(20 * 60000, after.millis);
        assertEquals(1, before.sessions); assertEquals(0, after.sessions);
        assertEquals(1, before.completed);
    }
    @Test public void pomodoroRestIsExcludedEvenWhenBoundaryCutsThroughRest() {
        long start = at("2026-09-29T03:33:00"), boundary = at("2026-09-29T04:00:00");
        FocusStatistics.Entry entry = new FocusStatistics.Entry("写作", "strict_pomodoro", "completed", start,
                start + 55 * 60000, 50 * 60000);
        assertEquals(25 * 60000, entry.between(start, boundary));
        assertEquals(25 * 60000, entry.between(boundary, entry.end));
        assertEquals(0, entry.between(start + 25 * 60000, start + 30 * 60000));
    }
    @Test public void interruptedWorkCountsAndReasonsRemainDistinct() {
        long start = at("2026-09-29T10:00:00");
        List<FocusStatistics.Entry> entries = Arrays.asList(
                new FocusStatistics.Entry("阅读", "countdown", "user_interrupted", start, start + 60000, 60000),
                new FocusStatistics.Entry("阅读", "countup", "sleep_takeover", start + 60000, start + 180000, 120000));
        FocusStatistics.Summary summary = FocusStatistics.summarize(entries, start, start + 180000);
        assertEquals(180000, summary.millis); assertEquals(2, summary.sessions); assertEquals(0, summary.completed);
        assertEquals(Long.valueOf(1), summary.reasons.get("主动结束"));
        assertEquals(Long.valueOf(1), summary.reasons.get("睡眠接管"));
        assertEquals(Long.valueOf(180000), summary.tasks.get("阅读"));
    }
    @Test public void emptyOrFutureRangeHasNoDuration() {
        FocusStatistics.Entry e = new FocusStatistics.Entry("", "countup", "completed", 1000, 2000, 1000);
        assertEquals(0, e.between(2000, 3000)); assertEquals(0, e.between(2000, 1000));
        assertEquals(0, FocusStatistics.summarize(Collections.emptyList(), 0, 1000).sessions);
    }
    @Test public void monotonicRecordedDurationIsPreservedAcrossBuckets() {
        FocusStatistics.Entry e = new FocusStatistics.Entry("钟表调整", "countup", "completed", 1000, 2000, 700);
        assertEquals(700, e.between(1000, 1333) + e.between(1333, 1666) + e.between(1666, 2000));
    }
    @Test public void dstBoundaryUsesLocalFourOClock() {
        ZoneId ny = ZoneId.of("America/New_York");
        long from = FocusStatistics.boundary(LocalDate.of(2026, 3, 7), ny);
        long to = FocusStatistics.boundary(LocalDate.of(2026, 3, 8), ny);
        assertEquals(23 * 3600000L, to - from);
    }
}
