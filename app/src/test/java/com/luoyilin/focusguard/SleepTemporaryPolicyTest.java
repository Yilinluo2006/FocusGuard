package com.luoyilin.focusguard;
import org.junit.Test;
import static org.junit.Assert.*;
public class SleepTemporaryPolicyTest {
    @Test public void sleepNeverExemptsTheLauncherEvenIfItWasWhitelisted() {
        assertFalse(SleepTemporaryPolicy.permitsApp(true,true,true,false));
        assertFalse(SleepTemporaryPolicy.permitsApp(true,true,false,false));
        assertTrue(SleepTemporaryPolicy.permitsApp(true,false,true,false));
        assertTrue(SleepTemporaryPolicy.permitsApp(true,false,false,true));
        assertTrue(SleepTemporaryPolicy.permitsApp(false,true,true,false));
    }
    @Test public void resumesExactlyAfterTwoMinutesAndLeavingAnAppDoesNotPause() {
        assertEquals(120_000,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,5000,10000,2,125000,130000,2));
        assertEquals(1000,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,124000,129000,2,125000,130000,2));
        assertEquals(0,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,125000,130000,2,125000,130000,2));
        assertFalse(SleepTemporaryPolicy.controlling(1_000_000,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,124000,129000,2,125000,130000,2)));
        assertTrue(SleepTemporaryPolicy.controlling(1_000_000,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,125000,130000,2,125000,130000,2)));
        assertFalse(SleepTemporaryPolicy.controlling(0,0));
    }
    @Test public void rebootClockChangeAndAnotherSleepCannotExtendAnInterval() {
        assertEquals(0,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,5000,10000,3,125000,130000,2));
        assertEquals(0,SleepTemporaryPolicy.remaining(1000,1_000_000,1000,1000,130000,2,125000,130000,2));
        assertEquals(0,SleepTemporaryPolicy.remaining(2000,1_000_000,1000,5000,10000,2,125000,130000,2));
        assertEquals(1000,SleepTemporaryPolicy.remaining(1000,6000,1000,5000,10000,2,125000,130000,2));
    }
    @Test public void fiveUsesCannotReplenishWithinTheSameQuotaDayOrClockRollback() {
        assertEquals(0,SleepTemporaryPolicy.left(20000,20000,5));
        assertEquals(0,SleepTemporaryPolicy.left(19999,20000,5));
        assertEquals(5,SleepTemporaryPolicy.left(20001,20000,5));
    }
}
