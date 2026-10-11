package com.luoyilin.focusguard;
import org.junit.Test;
import static org.junit.Assert.*;
public class SharedQuotaPolicyTest {
    @Test public void eachDeviceKeepsItsOwnRemainingTime(){
        assertEquals(20*60_000L,SharedQuotaPolicy.perDeviceRemaining(30*60_000L,10*60_000L));
        assertEquals(25*60_000L,SharedQuotaPolicy.perDeviceRemaining(30*60_000L,5*60_000L));
        assertEquals(0,SharedQuotaPolicy.perDeviceRemaining(30*60_000L,35*60_000L));
        assertEquals(30*60_000L,SharedQuotaPolicy.perDeviceRemaining(30*60_000L,-1));
    }
    @Test public void absentLegacyModeDefaultsToPerDevice(){
        assertFalse(SharedQuotaPolicy.strictMode(null));assertFalse(SharedQuotaPolicy.strictMode(SharedQuotaPolicy.PER_DEVICE));
        assertTrue(SharedQuotaPolicy.strictMode(SharedQuotaPolicy.STRICT_SHARED));
    }
    @Test public void onlineTimerShowsSharedDailyRemainingInsteadOfTwoMinuteGrant(){
        assertEquals(22*60_000L,SharedQuotaPolicy.displayRemaining(30*60_000L,4*60_000L,5*60_000L,7*60_000L,2*60_000L,true));
    }
    @Test public void offlineTimerShowsOnlySpendableGrantAndNeverInventsTime(){
        assertEquals(2*60_000L,SharedQuotaPolicy.displayRemaining(30*60_000L,4*60_000L,5*60_000L,7*60_000L,2*60_000L,false));
        assertEquals(0,SharedQuotaPolicy.displayRemaining(30*60_000L,0,0,0,0,true));
    }
    @Test public void offlineCanSpendOnlyReservedTime(){assertEquals(60000,SharedQuotaPolicy.remaining(180000,100000,120000,100,1000,5,5));assertEquals(0,SharedQuotaPolicy.remaining(180000,100000,180001,100,1000,5,5));}
    @Test public void dayExpiryRebootAndCounterLossRefuseOldGrants(){assertEquals(0,SharedQuotaPolicy.remaining(180000,120000,120000,1000,1000,5,5));assertEquals(0,SharedQuotaPolicy.remaining(180000,120000,120000,100,1000,5,6));assertEquals(0,SharedQuotaPolicy.remaining(180000,120000,0,100,1000,5,5));}
}
