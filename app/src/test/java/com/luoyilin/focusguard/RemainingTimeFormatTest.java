package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RemainingTimeFormatTest {
    @Test public void clampsExpiredTime() {
        assertEquals("00:00", RemainingTimeFormat.format(-1));
        assertEquals("00:00", RemainingTimeFormat.format(0));
    }

    @Test public void roundsUpPartialSeconds() {
        assertEquals("00:01", RemainingTimeFormat.format(1));
        assertEquals("00:01", RemainingTimeFormat.format(1000));
        assertEquals("00:02", RemainingTimeFormat.format(1001));
        assertEquals("01:00", RemainingTimeFormat.format(59999));
    }

    @Test public void formatsHoursWithoutWrapping() {
        assertEquals("59:59", RemainingTimeFormat.format(3599000));
        assertEquals("1:00:00", RemainingTimeFormat.format(3600000));
        assertEquals("25:00:00", RemainingTimeFormat.format(90000000));
    }
}
