package com.luoyilin.focusguard;

import android.content.Context;
import android.os.Bundle;
import android.util.AtomicFile;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Actual eligibility + quota providers in an isolated package, with one daily use left. */
public class SleepWhitelistEmergencyTest {
    private Context c;
    private static final String WHITE = "test.sleep.allowed", OTHER = "test.sleep.denied";
    @Before public void setup() throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext(); assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        LocalTime now=Instant.ofEpochMilli(ProtectionClock.now(c)).atZone(ProtectionClock.zone(c)).toLocalTime().minusMinutes(1);
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",true).putInt("minute",now.getHour()*60+now.getMinute())
                .putInt("duration",30).putInt(FocusSchedule.WEEKDAYS_KEY,127).putStringSet("allowed",Collections.singleton(WHITE)).commit();
        Properties p=new Properties();p.setProperty("quotaDay",Long.toString(ProtectionClock.day(c)));p.setProperty("used","4");write(p);
        assertTrue(SleepTemporaryUse.state(c).getBoolean("sleeping")); assertEquals(1,RestrictionStore.left(c));
    }
    @After public void clean()throws Exception { if(c!=null&&c.getPackageName().endsWith(".setupprobe")) {c.getSharedPreferences("focus_mode",0).edit().clear().commit();write(new Properties());} }
    private void write(Properties p)throws Exception { AtomicFile f=new AtomicFile(new File(c.getNoBackupFilesDir(),"restriction.properties"));FileOutputStream out=f.startWrite();p.store(out,null);f.finishWrite(out); }
    private Properties read()throws Exception {Properties p=new Properties();try(InputStream in=new AtomicFile(new File(c.getNoBackupFilesDir(),"restriction.properties")).openRead()){p.load(in);}return p;}
    @Test public void lastDailyUseOnlyReleasesWhitelistAppAndPreservesSleepBudget()throws Exception {
        Bundle before=SleepTemporaryUse.state(c); assertTrue(DailyTemporaryAccess.allowed(c,WHITE)); assertFalse(DailyTemporaryAccess.allowed(c,OTHER));
        RestrictionStore.grant(c,WHITE); assertTrue(RestrictionStore.allowance(c,WHITE)>115000);assertEquals(0,RestrictionStore.allowance(c,OTHER));assertEquals(0,RestrictionStore.left(c));
        Bundle after=SleepTemporaryUse.state(c);assertEquals(before.getInt("left"),after.getInt("left"));assertEquals(0,after.getLong("remaining"));assertTrue(after.getBoolean("sleeping"));
        assertTrue(SleepTemporaryPolicy.controlling(new FocusModeStore(c).end(ProtectionClock.now(c)),after.getLong("remaining")));
        Properties p=read();p.setProperty("elapsedUntil","0");write(p);assertEquals(0,RestrictionStore.allowance(c,WHITE));
        try {RestrictionStore.grant(c,WHITE);fail("No sixth daily use");}catch(IllegalStateException expected){}
    }
    @Test public void removingWhitelistRevokesEligibilityEvenWithExistingGrant()throws Exception {
        RestrictionStore.grant(c,WHITE);assertTrue(DailyTemporaryAccess.allowed(c,WHITE));
        c.getSharedPreferences("focus_mode",0).edit().putStringSet("allowed",Collections.emptySet()).commit();
        assertFalse(DailyTemporaryAccess.allowed(c,WHITE));
    }
    @Test public void globalSleepTemporaryUseStillWorksIndependently() throws Exception {
        assertFalse(DailyTemporaryAccess.allowed(c,OTHER));Bundle grant=SleepTemporaryUse.request(c);assertTrue(grant.getBoolean("saved"));
        assertEquals(4,grant.getInt("left"));assertEquals(1,RestrictionStore.left(c));assertTrue(DailyTemporaryAccess.allowed(c,OTHER));
    }
}
