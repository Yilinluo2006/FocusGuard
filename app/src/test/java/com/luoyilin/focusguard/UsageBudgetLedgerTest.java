package com.luoyilin.focusguard;
import java.util.*;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

public class UsageBudgetLedgerTest {
    @Test public void changedWallRecordsCannotEraseUsageAndElapsedTimeStillCounts() {
        Properties p=new Properties();long now=StreakLedger.boundary(20000,ZoneId.of("Asia/Shanghai"))+100000;
        UsageBudgetLedger.sample(p,20000,now,1000,1,"instagram",Collections.singletonMap("instagram",15*60000L));
        UsageBudgetLedger.sample(p,20000,now+2000,3000,1,"instagram",Collections.emptyMap());
        assertEquals(15*60000L+2000,UsageBudgetLedger.used(p,"instagram"));
        UsageBudgetLedger.sample(p,20000,now+4000,5000,1,null,Collections.singletonMap("instagram",0L));
        assertEquals(15*60000L+4000,UsageBudgetLedger.used(p,"instagram"));
        UsageBudgetLedger.sample(p,20000,now+6000,7000,1,null,Collections.emptyMap());
        assertEquals(15*60000L+4000,UsageBudgetLedger.used(p,"instagram"));
    }
    @Test public void rebootDoesNotRefillOrCountUnobservedTime() {
        Properties p=new Properties();UsageBudgetLedger.sample(p,20000,1000,10000,1,"app",Collections.singletonMap("app",30*60000L));
        UsageBudgetLedger.sample(p,20000,2000,1000,2,"app",Collections.emptyMap());
        assertEquals(30*60000L,UsageBudgetLedger.used(p,"app"));
    }
    @Test public void actualNewDayClearsOldUsageAndSplitsTheActiveIntervalAtFour() {
        Properties p=new Properties();long boundary=StreakLedger.boundary(20001,ZoneId.of("Asia/Shanghai"));
        UsageBudgetLedger.sample(p,20000,boundary-1000,1000,1,"app",Collections.singletonMap("app",30*60000L));
        UsageBudgetLedger.sample(p,20001,boundary+1000,3000,1,"app",Collections.emptyMap());
        assertEquals(1000,UsageBudgetLedger.used(p,"app"));
    }
}
