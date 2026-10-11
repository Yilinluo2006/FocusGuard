package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.Properties;

public class CoverageLedgerTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final long start = LocalDateTime.parse("2026-09-22T03:59:50").atZone(zone).toInstant().toEpochMilli();
    private void sample(Properties p, long offset, boolean unlocked, boolean active) {
        CoverageLedger.sample(p, start + offset, offset + 1000, 1, "a", unlocked, active, zone);
    }
    @Test public void splitsAtFourAndDoesNotChangeStreak() {
        Properties p = new Properties(); sample(p, 0, true, true); sample(p, 20000, true, true);
        long d = StreakLedger.day(start, zone);
        assertEquals(10000, StreakLedger.n(p, "protected." + d));
        assertEquals(10000, StreakLedger.n(p, "protected." + (d + 1)));
        assertFalse(p.containsKey("streak"));
    }
    @Test public void distinguishesUnprotectedLockedAndTransitions() {
        Properties p = new Properties(); sample(p, 0, true, false); sample(p, 5000, true, false);
        sample(p, 6000, false, false); sample(p, 9000, false, false);
        long d = StreakLedger.day(start, zone);
        assertEquals(5000, StreakLedger.n(p, "unprotected." + d));
        assertEquals(1000, StreakLedger.n(p, "unknown." + d));
        assertEquals(3000, StreakLedger.n(p, "locked." + d));
    }
    @Test public void longSleepGapIsExplicitlyUnknown() {
        Properties p = new Properties(); sample(p, 0, false, true); sample(p, 3600000, false, true);
        long sum = p.stringPropertyNames().stream().filter(k -> k.startsWith("unknown."))
                .mapToLong(k -> StreakLedger.n(p, k)).sum();
        assertEquals(3600000, sum);
    }
    @Test public void restartAndDuplicateDoNotCreditProtection() {
        Properties p = new Properties(); sample(p, 0, true, true);
        CoverageLedger.sample(p, start + 5000, 6000, 1, "b", true, true, zone);
        CoverageLedger.sample(p, start + 5000, 6000, 1, "b", true, true, zone);
        assertEquals(5000, StreakLedger.n(p, "unknown." + StreakLedger.day(start, zone)));
        assertEquals(0, StreakLedger.n(p, "protected." + StreakLedger.day(start, zone)));
    }
}
