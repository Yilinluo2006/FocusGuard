package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.Properties;

public class StreakLedgerTest {
    @Test public void disconnectedTimeIsNotCreditedAndRecoveryResumes() {
        Properties p = new Properties();
        long now = at("2026-09-21T05:00:00");
        String key = "ms." + StreakLedger.day(now, zone);
        StreakLedger.pulse(p, now, 0, "a", true, zone);
        StreakLedger.pulse(p, now + 10000, 10000, "a", true, zone);
        StreakLedger.pulse(p, now + 15000, 15000, "a", false, zone);
        StreakLedger.pulse(p, now + 20000, 20000, "a", true, zone);
        assertEquals(10000, StreakLedger.n(p, key));
        StreakLedger.pulse(p, now + 30000, 30000, "a", true, zone);
        assertEquals(20000, StreakLedger.n(p, key));
        assertFalse(p.containsKey("broken." + StreakLedger.day(now, zone)));
    }
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private long at(String s) { return LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli(); }
    @Test public void dayRollsAtFour() {
        assertEquals(StreakLedger.day(at("2026-09-20T03:59:59"), zone) + 1,
                StreakLedger.day(at("2026-09-20T04:00:00"), zone));
    }
    @Test public void noCreditForLongGapsOrNewProcess() {
        Properties p = new Properties(); long now = at("2026-09-20T05:00:00");
        long day = StreakLedger.day(now, zone);
        StreakLedger.pulse(p, now, 1000, "a", true, zone);
        StreakLedger.pulse(p, now + 10000, 11000, "a", true, zone);
        assertEquals(10000, StreakLedger.n(p, "ms." + day));
        StreakLedger.pulse(p, now + 100000, 101000, "a", true, zone);
        StreakLedger.pulse(p, now + 110000, 111000, "b", true, zone);
        assertEquals(10000, StreakLedger.n(p, "ms." + day));
    }
    @Test public void noCreditWithoutRestrictionOrAfterClockChange() {
        Properties p = new Properties(); long now = at("2026-09-20T05:00:00");
        StreakLedger.pulse(p, now, 0, "a", false, zone);
        StreakLedger.pulse(p, now + 10000, 10000, "a", true, zone);
        StreakLedger.pulse(p, now + 40000, 20000, "a", true, zone);
        assertEquals(0, StreakLedger.n(p, "ms." + StreakLedger.day(now, zone)));
    }
    @Test public void fullDayAndRepeatedSettlement() {
        Properties p = new Properties(); long now = at("2026-09-20T04:00:00");
        for (long ms = 0; ms <= StreakLedger.REQUIRED; ms += 10000)
            StreakLedger.pulse(p, now + ms, ms, "a", true, zone);
        StreakLedger.settle(p, now + 86400000, zone);
        StreakLedger.settle(p, now + 86400000, zone);
        assertEquals(1, StreakLedger.n(p, "streak"));
        assertEquals(1, StreakLedger.n(p, "best"));
    }
    @Test public void brokenDayCannotEarnSuccessAndBestSurvives() {
        Properties p = new Properties(); long now = at("2026-09-20T04:00:00");
        StreakLedger.init(p, now, zone);
        StreakLedger.put(p, "ms." + StreakLedger.day(now, zone), StreakLedger.REQUIRED);
        StreakLedger.put(p, "streak", 7); StreakLedger.put(p, "best", 7);
        StreakLedger.breakToday(p, now + 10000, zone, "test");
        StreakLedger.settle(p, now + 86400000, zone);
        assertEquals(0, StreakLedger.n(p, "streak"));
        assertEquals(7, StreakLedger.n(p, "best"));
    }
    @Test public void lateFirstDayIsPreparationThenMissingDayBreaks() {
        Properties p = new Properties(); long now = at("2026-09-20T21:00:00");
        long day = StreakLedger.day(now, zone);
        StreakLedger.init(p, now, zone);
        StreakLedger.settle(p, at("2026-09-23T04:00:00"), zone);
        assertEquals("准备", p.getProperty("result." + day));
        assertEquals("未完成", p.getProperty("result." + (day + 1)));
        assertEquals(0, StreakLedger.n(p, "streak"));
    }
    @Test public void pulseSplitsAcrossBoundary() {
        Properties p = new Properties(); long now = at("2026-09-20T03:59:55");
        StreakLedger.pulse(p, now, 0, "a", true, zone);
        StreakLedger.pulse(p, now + 10000, 10000, "a", true, zone);
        assertEquals(5000, StreakLedger.n(p, "ms." + StreakLedger.day(now, zone)));
        assertEquals(5000, StreakLedger.n(p, "ms." + StreakLedger.day(now + 10000, zone)));
    }
    @Test public void dstStillUsesLocalFour() {
        ZoneId ny = ZoneId.of("America/New_York");
        long day = LocalDate.of(2026, 3, 7).toEpochDay();
        assertEquals(23 * 3600000L, StreakLedger.boundary(day + 1, ny) - StreakLedger.boundary(day, ny));
    }
}
