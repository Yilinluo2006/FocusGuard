package com.luoyilin.focusguard;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class SleepAppearancePolicyTest {
    private long time(String value){return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    @Test public void sleepOverridesEveryPreferenceAndRestoresItAfterward(){
        for(int mode=0;mode<=2;mode++){assertEquals(2,SleepAppearancePolicy.effectiveMode(mode,true));assertEquals(mode,SleepAppearancePolicy.effectiveMode(mode,false));}
    }
    @Test public void fridaySleepContinuesAfterMidnightUntilItsExactEnd(){
        ZoneId zone=ZoneId.of("Asia/Shanghai");int friday=1<<4;
        assertTrue(SleepAppearancePolicy.sleeping(time("2026-10-09T23:30:00"),true,1410,240,friday,zone));
        assertTrue(SleepAppearancePolicy.sleeping(time("2026-10-10T02:00:00"),true,1410,240,friday,zone));
        assertFalse(SleepAppearancePolicy.sleeping(time("2026-10-10T03:30:00"),true,1410,240,friday,zone));
    }
    @Test public void disabledPlanAndUnselectedDaysDoNotForceDark(){
        ZoneId zone=ZoneId.of("Asia/Shanghai");long now=time("2026-10-09T23:45:00");
        assertFalse(SleepAppearancePolicy.sleeping(now,false,1410,240,127,zone));
        assertFalse(SleepAppearancePolicy.sleeping(now,true,1410,240,1,zone));
    }
}
