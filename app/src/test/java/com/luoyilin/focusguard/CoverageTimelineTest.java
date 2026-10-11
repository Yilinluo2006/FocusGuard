package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.Properties;

public class CoverageTimelineTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final long start = LocalDateTime.parse("2026-09-22T08:00:00").atZone(zone).toInstant().toEpochMilli();
    private void sample(Properties p, long offset, boolean unlocked) {
        CoverageLedger.sample(p, start + offset, offset + 1000, 1, "a", unlocked, true, zone);
    }
    @Test public void mergesContinuousSamples() {
        Properties p = new Properties();
        sample(p, 0, true); sample(p, 10000, true); sample(p, 20000, true);
        assertEquals(1, StreakLedger.n(p, "timeline.last"));
        assertEquals(start, StreakLedger.n(p, "segment.1.start"));
        assertEquals(start + 20000, StreakLedger.n(p, "segment.1.end"));
    }
    @Test public void gapDoesNotPretendToBeSleep() {
        Properties p = new Properties();
        sample(p, 0, false); sample(p, 3600000, false);
        assertEquals("unknown", p.getProperty("segment.1.state"));
        assertTrue(p.getProperty("segment.1.reason").contains("不能判定"));
    }
    @Test public void migrationDoesNotInventOldSegments() {
        Properties p = new Properties();
        p.setProperty("wall", "" + (start - 3600000));
        sample(p, 0, true);
        assertFalse(p.containsKey("segment.1.start"));
        sample(p, 10000, true);
        assertEquals(start, StreakLedger.n(p, "segment.1.start"));
    }
    @Test public void retentionIsBounded() {
        Properties p = new Properties();
        for (int i = 0; i < 650; i++)
            CoverageTimeline.append(p, start + i, start + i + 1, i % 2 == 0 ? "locked" : "protected", "");
        assertEquals(51, StreakLedger.n(p, "timeline.first"));
        assertFalse(p.containsKey("segment.1.start"));
        assertTrue(p.containsKey("segment.650.end"));
    }
    @Test public void displayClipsAtFourAndDoesNotModifyStore() {
        Properties p = new Properties();
        long four = StreakLedger.boundary(StreakLedger.day(start, zone), zone);
        p.setProperty("timeline.started", "" + (four - 10000));
        p.setProperty("wall", "" + (four + 10000));
        CoverageTimeline.append(p, four - 10000, four + 10000, "protected", "");
        Properties before = (Properties) p.clone();
        String detail = CoverageTimeline.details(p, four + 10000, zone);
        assertTrue(detail.contains("04:00:00–04:00:10"));
        assertEquals(before, p);
    }
}
