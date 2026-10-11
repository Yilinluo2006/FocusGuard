package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.*;
import java.util.Properties;
import static org.junit.Assert.*;

public class BackupStreakRecoveryTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final long now = ZonedDateTime.of(2026,9,30,12,0,0,0,zone).toInstant().toEpochMilli();
    private void completed(Properties p, long day) {
        p.setProperty("result."+day,"完成"); StreakLedger.put(p,"ms."+day,StreakLedger.REQUIRED);
    }
    @Test public void recomputesPastStreakWithoutCreditingTodayOrReplacingHeartbeat() {
        Properties p = new Properties(); long today = StreakLedger.day(now,zone);
        StreakLedger.init(p,now,zone); StreakLedger.put(p,"ms."+today,1234); p.setProperty("session","local-session");
        completed(p,today-2); completed(p,today-1);
        StreakLedger.rebuildHistory(p,now,zone);
        assertEquals(2,StreakLedger.n(p,"streak")); assertEquals(2,StreakLedger.n(p,"best"));
        assertEquals(1234,StreakLedger.n(p,"ms."+today)); assertEquals("local-session",p.getProperty("session"));
        StreakLedger.rebuildHistory(p,now,zone); assertEquals(2,StreakLedger.n(p,"streak"));
    }
    @Test public void missingDaysAndLocalBreakPreventAFalseStreak() {
        Properties p = new Properties(); long today = StreakLedger.day(now,zone); StreakLedger.init(p,now,zone);
        completed(p,today-4); completed(p,today-2); completed(p,today-1); p.setProperty("broken."+(today-1),"本机已中断");
        StreakLedger.rebuildHistory(p,now,zone);
        assertEquals(0,StreakLedger.n(p,"streak")); assertEquals(1,StreakLedger.n(p,"best"));
    }
}
