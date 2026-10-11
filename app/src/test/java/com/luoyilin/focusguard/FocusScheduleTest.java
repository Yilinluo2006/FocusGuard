package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class FocusScheduleTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private long at(String value) { return LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli(); }
    @Test public void spansMidnight() {
        assertEquals(at("2026-09-19T22:30:00"), FocusSchedule.activeStart(at("2026-09-20T01:00:00"), 1350, 180, zone));
    }
    @Test public void boundaries() {
        long start = at("2026-09-19T22:30:00");
        assertEquals(0, FocusSchedule.activeStart(start - 1, 1350, 180, zone));
        assertEquals(start, FocusSchedule.activeStart(start, 1350, 180, zone));
        assertEquals(0, FocusSchedule.activeStart(start + 180 * 60000L, 1350, 180, zone));
    }
    @Test public void nextDayRepeats() {
        long start = at("2026-09-20T22:30:00");
        assertEquals(start, FocusSchedule.activeStart(start + 1000, 1350, 180, zone));
    }
    @Test public void selectedFridayContinuesIntoSaturdayButSaturdayNightIsFree() {
        int friday = 1 << 4;
        long start = at("2026-10-02T23:30:00");
        assertEquals(start, FocusSchedule.activeStart(at("2026-10-03T02:00:00"), 1410, 240, friday, zone));
        assertEquals(0, FocusSchedule.activeStart(at("2026-10-03T03:30:00"), 1410, 240, friday, zone));
        assertEquals(0, FocusSchedule.activeStart(at("2026-10-03T23:30:00"), 1410, 240, friday, zone));
        assertEquals(0, FocusSchedule.activeStart(at("2026-10-02T02:00:00"), 1410, 240, friday, zone));
    }
    @Test public void nextStartSkipsUnselectedDaysAndRepeatsNextWeekAtExactStart() {
        int friday = 1 << 4;
        assertEquals(at("2026-10-02T23:30:00"), FocusSchedule.nextStart(at("2026-10-01T23:30:00"), 1410, friday, zone));
        assertEquals(at("2026-10-09T23:30:00"), FocusSchedule.nextStart(at("2026-10-02T23:30:00"), 1410, friday, zone));
    }
    @Test public void sundayToMondayReminderUsesTheSelectedMonday() {
        long now = at("2026-10-04T23:58:00");
        long next = FocusSchedule.nextStart(now, 0, 1, zone);
        assertEquals(at("2026-10-05T00:00:00"), next);
        assertEquals(1, FocusSchedule.reminderStage(next - now));
        assertEquals(0, FocusSchedule.reminderStage(FocusSchedule.nextStart(now, 0, 1 << 1, zone) - now));
    }
    @Test public void emptySelectionDoesNotStartOrRemind() {
        long now = at("2026-10-02T23:30:00");
        assertEquals(0, FocusSchedule.nextStart(now, 1410, 0, zone));
        assertEquals(0, FocusSchedule.activeStart(now, 1410, 240, 0, zone));
        assertEquals(0, FocusSchedule.reminderStage(FocusSchedule.nextStart(now, 1410, 0, zone) - now));
    }
    @Test public void allWeekdaysMatchesTheExistingDailyPlan() {
        long now = at("2026-10-03T01:00:00");
        assertEquals(FocusSchedule.activeStart(now, 1410, 240, zone),
                FocusSchedule.activeStart(now, 1410, 240, FocusSchedule.ALL_DAYS, zone));
        assertEquals(FocusSchedule.nextStart(now, 1410, zone),
                FocusSchedule.nextStart(now, 1410, FocusSchedule.ALL_DAYS, zone));
    }
}
