package com.luoyilin.focusguard;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.junit.Assert.*;

public class AppUsageTimelineTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String APP = "example.app";

    private long at(String local) {
        return LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli();
    }

    @Test public void midnightDoesNotResetUsage() {
        long start = at("2026-09-26T04:00:00");
        AppUsageTimeline timeline = new AppUsageTimeline(start, Collections.singleton(APP));
        timeline.accept(APP, "Main", at("2026-09-26T23:50:00"), AppUsageTimeline.RESUME);
        timeline.accept(APP, "Main", at("2026-09-27T00:10:00"), AppUsageTimeline.PAUSE);
        assertEquals(20 * 60_000L, timeline.session(APP).totalAt(at("2026-09-27T00:10:00")));
    }

    @Test public void appOpenAcrossFourCountsOnlyNewPeriod() {
        long start = at("2026-09-27T04:00:00");
        AppUsageTimeline timeline = new AppUsageTimeline(start, Collections.singleton(APP));
        timeline.accept(APP, "Main", at("2026-09-27T03:50:00"), AppUsageTimeline.RESUME);
        assertEquals(10 * 60_000L, timeline.session(APP).totalAt(at("2026-09-27T04:10:00")));
        timeline.accept(APP, "Main", at("2026-09-27T04:10:00"), AppUsageTimeline.PAUSE);
        assertEquals(10 * 60_000L, timeline.totalsAt(at("2026-09-27T04:20:00")).get(APP).longValue());
    }

    @Test public void screenOffEndsPreviousSession() {
        long start = at("2026-09-27T04:00:00");
        AppUsageTimeline timeline = new AppUsageTimeline(start, null);
        timeline.accept(APP, "Main", at("2026-09-27T03:50:00"), AppUsageTimeline.RESUME);
        timeline.accept(null, null, at("2026-09-27T03:55:00"), AppUsageTimeline.END_ALL);
        timeline.accept(APP, "Main", at("2026-09-27T04:05:00"), AppUsageTimeline.RESUME);
        assertEquals(5 * 60_000L, timeline.session(APP).totalAt(at("2026-09-27T04:10:00")));
    }
}
