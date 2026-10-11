package com.luoyilin.focusguard;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Only the isolated probe's clock file is changed; the device date and real app data are untouched. */
public class ClockHardeningTest {
    private Context c;private AtomicBoolean busy;
    @Before public void setup()throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        Field f=ProtectionClock.class.getDeclaredField("BUSY");f.setAccessible(true);busy=(AtomicBoolean)f.get(null);
        for(int i=0;i<30&&busy.get();i++)Thread.sleep(200);
        busy.set(true); // Freeze only the probe's asynchronous verifier while injecting clock observations.
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        c.getSharedPreferences("device_sync",0).edit().clear().commit();
        write("restriction",new Properties());write("usage-budget",new Properties());clock(System.currentTimeMillis());
    }
    @After public void cleanup()throws Exception {
        if(c!=null&&c.getPackageName().endsWith(".setupprobe")) {
            c.getSharedPreferences("focus_mode",0).edit().clear().commit();
            clock(System.currentTimeMillis());busy.set(false);
        }
    }
    private void write(String name,Properties p)throws Exception {
        android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),name+".properties"));
        FileOutputStream out=file.startWrite();p.store(out,null);file.finishWrite(out);
    }
    private void clock(long trusted)throws Exception {
        Properties p=new Properties();ProtectionTimeLedger.verify(p,trusted,SystemClock.elapsedRealtime(),ProtectionClock.boot(c),ZoneId.systemDefault().getId());
        write("protection-clock",p);invalidate();
    }
    private void invalidate()throws Exception {Field field=ProtectionClock.class.getDeclaredField("cached");field.setAccessible(true);field.set(null,null);}
    @Test public void realWindowGateRejectsAWallClockMismatch()throws Exception {
        LocalTime wall=LocalTime.now();int minute=wall.getHour()*60+wall.getMinute();
        Properties rules=new Properties();rules.setProperty("start",Integer.toString((minute+1435)%1440));rules.setProperty("end",Integer.toString((minute+5)%1440));write("restriction",rules);
        assertTrue(RestrictionStore.canRelax(c));
        clock(System.currentTimeMillis()-10*3600000L);
        assertFalse("Raw wall time falls in the window but must not override the verified clock",RestrictionStore.canRelax(c));
    }
    @Test public void unknownRebootCannotRefillTemporaryUsesOrReleaseALockedApp()throws Exception {
        long priorDay=StreakLedger.day(System.currentTimeMillis(),ZoneId.systemDefault())-1;
        Properties clock=new Properties();ProtectionTimeLedger.verify(clock,System.currentTimeMillis()-86400000L,SystemClock.elapsedRealtime(),ProtectionClock.boot(c)-1,ZoneId.systemDefault().getId());
        write("protection-clock",clock);invalidate();
        Properties rules=new Properties();rules.setProperty("quotaDay",Long.toString(priorDay));rules.setProperty("used","5");
        rules.setProperty("lockedDay.test.app",Long.toString(priorDay));rules.setProperty("lockedMinutes.test.app","30");write("restriction",rules);
        assertFalse(ProtectionClock.read(c).verified);assertEquals(0,RestrictionStore.left(c));assertEquals("30",RestrictionStore.lockedLimits(c).getProperty("test.app"));
    }
    @Test public void sleepUsesVerifiedTimeEvenWhenWallTimeIsTenHoursAhead()throws Exception {
        long trusted=System.currentTimeMillis()-10*3600000L;clock(trusted);
        LocalTime start=Instant.ofEpochMilli(trusted-60000).atZone(ZoneId.systemDefault()).toLocalTime();
        assertTrue(new FocusPreferences(c).edit().putBoolean("daily",true).putInt("minute",start.getHour()*60+start.getMinute()).putInt("duration",30).putInt(FocusSchedule.WEEKDAYS_KEY,127).commit());
        FocusModeStore store=new FocusModeStore(c);long end=store.end(System.currentTimeMillis());assertTrue(end>trusted);
        assertFalse(store.finishEarly(System.currentTimeMillis()));
        for(int i=0;i<5;i++){
            assertTrue(SleepTemporaryUse.request(c).getBoolean("saved"));
            assertTrue(new FocusPreferences(c).edit().putLong("sleepTemporaryElapsedUntil",0).commit());
        }
        assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));assertEquals(end,store.end(System.currentTimeMillis()));
        assertFalse(new FocusPreferences(c).edit().putBoolean("daily",false).commit());
    }
    @Test public void lowerNativeUsageAfterAClockChangeDoesNotRestoreRemainingQuota()throws Exception {
        Properties counter=new Properties();counter.setProperty("day",Long.toString(ProtectionClock.day(c)));counter.setProperty("used.test.app","900000");write("usage-budget",counter);
        clock(System.currentTimeMillis()-10*3600000L);
        assertTrue(UsageBudgetStore.tick(c,"test.app",0)>=900000);
    }
}
