package com.luoyilin.focusguard;
import java.time.*;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionTimeLedgerTest {
    private long at(String time){return LocalDateTime.parse(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    @Test public void changingSeventeenToSevenMustNotOpenTheModificationWindow() {
        Properties p=new Properties();
        ProtectionTimeLedger.sample(p,at("2026-10-08T17:13:00"),10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.verify(p,at("2026-10-08T17:13:00"),10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.Sample changed=ProtectionTimeLedger.sample(p,at("2026-10-08T07:14:00"),70000,1,"Asia/Shanghai");
        assertFalse("Manual 17:13 -> 07:14 must not allow increasing Instagram's limit",ProtectionTimeLedger.canRelax(changed,420,480));
        assertEquals(at("2026-10-08T17:14:00"),changed.millis);
    }
    @Test public void changingToTomorrowMustNotResetTheQuotaDay() {
        Properties p=new Properties();
        ProtectionTimeLedger.Sample before=ProtectionTimeLedger.sample(p,at("2026-10-08T17:13:00"),10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.verify(p,before.millis,10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.Sample changed=ProtectionTimeLedger.sample(p,at("2026-10-09T07:14:00"),70000,1,"Asia/Shanghai");
        assertEquals("Moving the date must not replenish quota or temporary uses",before.day,changed.day);
    }
    @Test public void legitimateVerifiedMorningWindowWorksWithoutAnotherNetworkRequest() {
        Properties p=new Properties();long time=at("2026-10-08T07:13:00");
        ProtectionTimeLedger.verify(p,time,10000,1,"Asia/Shanghai");
        assertTrue(ProtectionTimeLedger.canRelax(ProtectionTimeLedger.sample(p,time+60000,70000,1,"Asia/Shanghai"),420,480));
    }
    @Test public void timezoneChangesCannotOpenAMorningWindow() {
        Properties p=new Properties();long time=at("2026-10-08T17:13:00");ProtectionTimeLedger.verify(p,time,10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.Sample changed=ProtectionTimeLedger.sample(p,time,10000,1,"Pacific/Honolulu");
        assertFalse(changed.aligned);assertEquals("Asia/Shanghai",changed.zone.getId());assertFalse(ProtectionTimeLedger.canRelax(changed,420,480));
    }
    @Test public void offlineRebootKeepsTheQuotaDayUntilTimeIsVerified() {
        Properties p=new Properties();long time=at("2026-10-08T17:13:00");ProtectionTimeLedger.verify(p,time,10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.sample(p,time+60000,70000,1,"Asia/Shanghai");
        ProtectionTimeLedger.Sample reboot=ProtectionTimeLedger.sample(p,at("2026-10-09T07:14:00"),1000,2,"Asia/Shanghai");
        assertFalse(reboot.verified);assertEquals(StreakLedger.day(time,ZoneId.of("Asia/Shanghai")),reboot.day);
        assertFalse(ProtectionTimeLedger.canRelax(reboot,420,480));
        ProtectionTimeLedger.verify(p,at("2026-10-09T07:14:00"),1000,2,"Asia/Shanghai");
        ProtectionTimeLedger.Sample confirmed=ProtectionTimeLedger.sample(p,at("2026-10-09T07:14:00"),1000,2,"Asia/Shanghai");
        assertTrue(confirmed.verified);assertTrue(ProtectionTimeLedger.canRelax(confirmed,420,480));assertEquals(reboot.day+1,confirmed.day);
    }
    @Test public void actualFourOClockResetsButFakeFourDoesNot() {
        Properties p=new Properties();long time=at("2026-10-08T03:59:00");ProtectionTimeLedger.verify(p,time,10000,1,"Asia/Shanghai");
        long day=ProtectionTimeLedger.sample(p,time,10000,1,"Asia/Shanghai").day;
        assertEquals(day,ProtectionTimeLedger.sample(p,time+3600000,10000,1,"Asia/Shanghai").day);
        assertEquals(day+1,ProtectionTimeLedger.sample(p,time+60000,70000,1,"Asia/Shanghai").day);
    }
    @Test public void corruptInitialWallClockCanBeCorrectedByExternalTime() {
        Properties p=new Properties();ProtectionTimeLedger.sample(p,at("2027-10-08T17:13:00"),10000,1,"Asia/Shanghai");
        long correct=at("2026-10-08T17:13:00");ProtectionTimeLedger.verify(p,correct,10000,1,"Asia/Shanghai");
        ProtectionTimeLedger.Sample value=ProtectionTimeLedger.sample(p,correct,10000,1,"Asia/Shanghai");
        assertEquals(correct,value.millis);assertTrue(value.verified&&value.aligned);
    }
}
