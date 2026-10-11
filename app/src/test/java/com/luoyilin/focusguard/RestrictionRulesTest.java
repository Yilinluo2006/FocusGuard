package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class RestrictionRulesTest {
    @Test public void sharedQuotaAllowsExactlyFiveGrants() {
        int used = 0;
        for (int i = 0; i < 5; i++) used = RestrictionRules.consume(used, 0);
        assertEquals(5, used);
        try { RestrictionRules.consume(used, 0); fail(); }
        catch (IllegalStateException expected) { }
        try { RestrictionRules.consume(0, 1); fail(); }
        catch (IllegalStateException expected) { }
    }
    @Test public void onlyNextDayResetsQuota() {
        assertEquals(5, RestrictionRules.quotaUsed(20, 20, 5));
        assertEquals(5, RestrictionRules.quotaUsed(19, 20, 5));
        assertEquals(0, RestrictionRules.quotaUsed(21, 20, 5));
    }
    @Test public void quotaDayChangesAtFourNotMidnight() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long beforeMidnight = LocalDateTime.parse("2026-09-26T23:59:59")
                .atZone(zone).toInstant().toEpochMilli();
        long afterMidnight = LocalDateTime.parse("2026-09-27T00:00:01")
                .atZone(zone).toInstant().toEpochMilli();
        long beforeFour = LocalDateTime.parse("2026-09-27T03:59:59")
                .atZone(zone).toInstant().toEpochMilli();
        long atFour = LocalDateTime.parse("2026-09-27T04:00:00")
                .atZone(zone).toInstant().toEpochMilli();
        long quotaDay = StreakLedger.day(beforeMidnight, zone);
        assertEquals(5, RestrictionRules.quotaUsed(StreakLedger.day(afterMidnight, zone), quotaDay, 5));
        assertEquals(5, RestrictionRules.quotaUsed(StreakLedger.day(beforeFour, zone), quotaDay, 5));
        assertEquals(0, RestrictionRules.quotaUsed(StreakLedger.day(atFour, zone), quotaDay, 5));
    }
    @Test public void windowIncludesStartButNotEnd() {
        assertFalse(RestrictionRules.inWindow(359, 360, 420));
        assertTrue(RestrictionRules.inWindow(360, 360, 420));
        assertTrue(RestrictionRules.inWindow(419, 360, 420));
        assertFalse(RestrictionRules.inWindow(420, 360, 420));
    }
    @Test public void overnightWindow() {
        assertTrue(RestrictionRules.inWindow(1439, 1380, 60));
        assertTrue(RestrictionRules.inWindow(0, 1380, 60));
        assertFalse(RestrictionRules.inWindow(60, 1380, 60));
        assertFalse(RestrictionRules.inWindow(360, 360, 360));
    }
    @Test public void onlyRelaxationsAreGated() {
        assertTrue(RestrictionRules.isRelaxation(true, 30, false, 30));
        assertTrue(RestrictionRules.isRelaxation(true, 30, true, 31));
        assertFalse(RestrictionRules.isRelaxation(true, 30, true, 29));
        assertFalse(RestrictionRules.isRelaxation(true, 30, true, 30));
        assertFalse(RestrictionRules.isRelaxation(false, 0, true, 30));
    }
    @Test public void grantExpiresAndClockRollbackCannotExtendIt() {
        assertEquals(120000, RestrictionRules.remaining(1000, 100, 1, 121000, 120100, 1));
        assertEquals(100, RestrictionRules.remaining(0, 120000, 1, 121000, 120100, 1));
        assertEquals(0, RestrictionRules.remaining(121000, 120100, 1, 121000, 120100, 1));
        assertEquals(0, RestrictionRules.remaining(2000, 100, 2, 121000, 120100, 1));
    }
}
