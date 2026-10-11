package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

public class FocusHeatmapDataTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private long at(String time) { return LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(); }

    @Test public void sumsDifferentTasksAndSplitsAtFour() {
        long start = at("2026-10-02T03:50:00"), cutoff = at("2026-10-02T12:00:00");
        Map<LocalDate, Long> days = FocusHeatmapData.totals(Arrays.asList(
                new FocusStatistics.Entry("阅读", "countup", "completed", start, start + 30 * 60000, 30 * 60000),
                new FocusStatistics.Entry("写作", "countdown", "user_interrupted", start + 40 * 60000,
                        start + 45 * 60000, 5 * 60000)), zone, cutoff);
        assertEquals(Long.valueOf(10 * 60000), days.get(LocalDate.of(2026, 10, 1)));
        assertEquals(Long.valueOf(25 * 60000), days.get(LocalDate.of(2026, 10, 2)));
    }
    @Test public void restAndFutureWorkDoNotLightUpCells() {
        long start = at("2026-10-02T03:33:00");
        Map<LocalDate, Long> days = FocusHeatmapData.totals(Arrays.asList(
                new FocusStatistics.Entry("番茄钟", "strict_pomodoro", "completed", start,
                        start + 55 * 60000, 50 * 60000),
                new FocusStatistics.Entry("未来", "countup", "completed", start + 60 * 60000,
                        start + 70 * 60000, 10 * 60000)), zone, at("2026-10-02T04:15:00"));
        assertEquals(Long.valueOf(25 * 60000), days.get(LocalDate.of(2026, 10, 1)));
        assertEquals(Long.valueOf(12 * 60000), days.get(LocalDate.of(2026, 10, 2)));
        assertEquals(2, days.size());
    }
    @Test public void partialMinutesStillLightUpAndThresholdsStayFixed() {
        assertEquals(0, FocusHeatmapData.level(0));
        assertEquals(1, FocusHeatmapData.level(30_000));
        assertEquals(1, FocusHeatmapData.level(25 * 60_000L - 1));
        assertEquals(2, FocusHeatmapData.level(25 * 60_000L));
        assertEquals(3, FocusHeatmapData.level(60 * 60_000L));
        assertEquals(4, FocusHeatmapData.level(120 * 60_000L));
    }
}
