package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public class SleepReminderTest {
    @Test public void reminderBoundaries() {
        assertEquals(0, FocusSchedule.reminderStage(300001));
        assertEquals(1, FocusSchedule.reminderStage(300000));
        assertEquals(1, FocusSchedule.reminderStage(60001));
        assertEquals(2, FocusSchedule.reminderStage(60000));
        assertEquals(2, FocusSchedule.reminderStage(1));
        assertEquals(0, FocusSchedule.reminderStage(0));
        assertEquals(0, FocusSchedule.reminderStage(-1));
    }
    @Test public void midnightAndStartAdvanceToNextOccurrence() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long now = ZonedDateTime.of(2026, 9, 20, 23, 58, 0, 0, zone).toInstant().toEpochMilli();
        long next = FocusSchedule.nextStart(now, 0, zone);
        assertEquals(120000, next - now);
        assertEquals(next + 86400000, FocusSchedule.nextStart(next, 0, zone));
    }
}
