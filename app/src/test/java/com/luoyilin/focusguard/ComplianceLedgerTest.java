package com.luoyilin.focusguard;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class ComplianceLedgerTest {
    private final ZoneId zone=ZoneId.of("Asia/Shanghai");
    private long at(String s){return LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli();}
    private Properties day(long now){Properties p=new Properties();ComplianceLedger.activate(p,now,zone);return p;}
    private void checked(Properties p,long d){p.setProperty("c.checked."+d,"true");StreakLedger.put(p,"c.through."+d,StreakLedger.boundary(d+1,zone));}
    @Test public void fifteenMinutesUnderQuotaCompletesWithoutTwelveHoursOrFocus(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,true,"");checked(p,d);
        ComplianceLedger.settle(p,StreakLedger.boundary(d+1,zone),zone);
        assertEquals("完成",p.getProperty("result."+d));assertEquals(1,StreakLedger.n(p,"streak"));assertEquals(0,StreakLedger.n(p,"ms."+d));
    }
    @Test public void configuredButUnusedDoesNotEarnFreeSuccess(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);checked(p,d);
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,false,"");
        ComplianceLedger.settle(p,StreakLedger.boundary(d+1,zone),zone);assertEquals("无记录",p.getProperty("result."+d));
    }
    @Test public void aSinglePendingRulePreventsOtherRulesFromAwardingTheDay(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);checked(p,d);
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,true,"");
        ComplianceLedger.rule(p,d,"sleep:night",ComplianceLedger.PENDING,true,"");
        long next=at("2026-10-06T04:00:00");ComplianceLedger.settle(p,next,zone);
        assertEquals("待确认",p.getProperty("result."+d));assertEquals(0,StreakLedger.n(p,"streak"));
        ComplianceLedger.rule(p,d,"sleep:night",ComplianceLedger.OK,true,"");ComplianceLedger.settle(p,next+3600000,zone);
        assertEquals("完成",p.getProperty("result."+d));assertEquals(1,StreakLedger.n(p,"streak"));
    }
    @Test public void staleMiddaySampleCannotFinalizeAWholeDay(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);checked(p,d);
        StreakLedger.put(p,"c.through."+d,now);ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,true,"");
        ComplianceLedger.settle(p,StreakLedger.boundary(d+1,zone),zone);assertEquals("待确认",p.getProperty("result."+d));
    }
    @Test public void confirmedOverrunCannotBeErasedByReconnect(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);checked(p,d);
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.FAILED,true,"超额");
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,true,"");
        ComplianceLedger.settle(p,StreakLedger.boundary(d+1,zone),zone);assertEquals("未完成",p.getProperty("result."+d));
    }
    @Test public void grantsArePerAppAndSplitAtFourAm(){
        Properties p=new Properties();long from=at("2026-10-06T03:59:00"),d=StreakLedger.day(from,zone);
        ComplianceLedger.grant(p,"video",from,from+120000,zone);
        assertEquals(60000,StreakLedger.n(p,"bonus."+d+".video"));assertEquals(60000,StreakLedger.n(p,"bonus."+(d+1)+".video"));
        assertEquals(0,StreakLedger.n(p,"bonus."+d+".other"));
        assertTrue(ComplianceLedger.overrun(35*60000,30*60000,0,2*60000));
        assertFalse(ComplianceLedger.overrun(32*60000,30*60000,0,2*60000));
        assertFalse(ComplianceLedger.overrun(40*60000,30*60000,0,10*60000));
    }
    @Test public void anAlreadyOverQuotaAppIsNotRetroactivelyPenalizedWhenRuleIsAdded(){
        assertFalse(ComplianceLedger.overrun(45*60000,30*60000,45*60000,0));
        assertTrue(ComplianceLedger.overrun(46*60000,30*60000,45*60000,0));
    }
    @Test public void sleepTemporaryWindowsAndBlockedScreenAreExcludedWithoutDoubleCounting(){
        assertEquals(0,ComplianceLedger.unexcused(100,200,Arrays.asList(new long[]{100,180},new long[]{150,210})));
        assertEquals(50,ComplianceLedger.unexcused(100,250,Arrays.asList(new long[]{100,200})));
        assertEquals(150,ComplianceLedger.unexcused(100,250,Arrays.asList(new long[]{300,400})));
        assertEquals(0,ComplianceLedger.unexcused(250,100,Collections.emptyList()));
    }
    @Test public void offlineWaitsForAccountConfirmationInsteadOfFailing(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=day(now);checked(p,d);p.setProperty("c.sync","true");
        ComplianceLedger.rule(p,d,"app:video",ComplianceLedger.OK,true,"");long next=StreakLedger.boundary(d+1,zone);
        ComplianceLedger.settle(p,next,zone);assertEquals("待确认",p.getProperty("result."+d));
        p.setProperty("c.remote."+d,"完成");StreakLedger.put(p,"c.remoteAt."+d,next);
        ComplianceLedger.settle(p,next,zone);assertEquals(1,StreakLedger.n(p,"streak"));
        p.setProperty("c.remote."+d,"未完成");ComplianceLedger.settle(p,next,zone);assertEquals(0,StreakLedger.n(p,"streak"));
    }
    @Test public void migrationPreservesPastRewardsAndBest(){
        long now=at("2026-10-05T12:00:00"),d=StreakLedger.day(now,zone);Properties p=new Properties();
        StreakLedger.init(p,now,zone);p.setProperty("result."+(d-1),"完成");StreakLedger.put(p,"ms."+(d-1),StreakLedger.REQUIRED);
        StreakLedger.put(p,"streak",1);StreakLedger.put(p,"best",7);ComplianceLedger.activate(p,now,zone);
        ComplianceLedger.settle(p,now,zone);assertEquals("完成",p.getProperty("result."+(d-1)));assertEquals(1,StreakLedger.n(p,"streak"));assertEquals(7,StreakLedger.n(p,"best"));
    }
}
