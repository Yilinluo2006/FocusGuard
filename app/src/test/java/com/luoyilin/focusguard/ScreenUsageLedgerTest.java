package com.luoyilin.focusguard;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ScreenUsageLedgerTest {
    private ScreenUsageLedger.Event event(long at, int type) {
        return new ScreenUsageLedger.Event(at, type);
    }

    @Test public void overnightScreenOffDoesNotCountAsUsageOrUnknown() {
        ScreenUsageLedger.Result result = ScreenUsageLedger.calculate(0, 10_000, Arrays.asList(
                event(-1000, ScreenUsageLedger.SCREEN_OFF),
                event(-900, ScreenUsageLedger.KEYGUARD_SHOWN),
                event(5000, ScreenUsageLedger.SCREEN_ON),
                event(5100, ScreenUsageLedger.KEYGUARD_HIDDEN),
                event(7100, ScreenUsageLedger.SCREEN_OFF)));
        assertEquals(2000, result.unlockedMillis);
        assertEquals(0, result.uncertainStateMillis);
        assertEquals(Arrays.asList(5100L), result.sessionStarts);
    }

    @Test public void unknownInitialStateCannotBecomeFalseFullCoverage() {
        ScreenUsageLedger.Result result = ScreenUsageLedger.calculate(0, 10_000, Arrays.asList(
                event(5000, ScreenUsageLedger.SCREEN_ON),
                event(6000, ScreenUsageLedger.KEYGUARD_HIDDEN)));
        assertEquals(4000, result.unlockedMillis);
        assertEquals(6000, result.uncertainStateMillis);
    }

    @Test public void eachNewUnlockedScreenSessionCountsOnce() {
        ScreenUsageLedger.Result result = ScreenUsageLedger.calculate(0, 10_000, Arrays.asList(
                event(-1000, ScreenUsageLedger.SCREEN_OFF),
                event(-900, ScreenUsageLedger.KEYGUARD_SHOWN),
                event(1000, ScreenUsageLedger.SCREEN_ON),
                event(1100, ScreenUsageLedger.KEYGUARD_HIDDEN),
                event(1100, ScreenUsageLedger.KEYGUARD_HIDDEN),
                event(3000, ScreenUsageLedger.SCREEN_OFF),
                event(5000, ScreenUsageLedger.SCREEN_ON),
                event(7000, ScreenUsageLedger.SCREEN_OFF)));
        assertEquals(3900, result.unlockedMillis);
        assertEquals(Arrays.asList(1100L, 5000L), result.sessionStarts);
    }

    @Test public void shutdownDoesNotChargePoweredOffTime() {
        ScreenUsageLedger.Result result = ScreenUsageLedger.calculate(0, 10_000, Arrays.asList(
                event(-1000, ScreenUsageLedger.SCREEN_ON),
                event(-900, ScreenUsageLedger.KEYGUARD_HIDDEN),
                event(1000, ScreenUsageLedger.SHUTDOWN),
                event(8000, ScreenUsageLedger.STARTUP),
                event(9000, ScreenUsageLedger.SCREEN_OFF)));
        assertEquals(1000, result.unlockedMillis);
        assertEquals(1000, result.uncertainStateMillis);
    }
}
