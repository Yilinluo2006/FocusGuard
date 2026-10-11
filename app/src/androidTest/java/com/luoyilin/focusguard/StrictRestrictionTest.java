package com.luoyilin.focusguard;

import android.content.Context;
import android.util.AtomicFile;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.time.ZoneId;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Synthetic quota records belong only to the isolated probe, never to the real user package. */
public class StrictRestrictionTest {
    private Context c;
    private static final String APP="test.limited.app";
    @Before public void setup() throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        write(new Properties());
        c.getSharedPreferences("focus_guard_prefs",0).edit().clear()
                .putStringSet("selected_packages",Collections.singleton(APP)).putInt("limit_minutes_"+APP,30).commit();
        assertTrue(LimitSnapshot.publish(c));
    }
    @After public void clean() throws Exception {
        if(c!=null&&c.getPackageName().endsWith(".setupprobe"))write(new Properties());
    }
    private long day(){return StreakLedger.day(System.currentTimeMillis(),ZoneId.systemDefault());}
    private Properties read() throws Exception {
        Properties p=new Properties();try(InputStream in=file().openRead()){p.load(in);}return p;
    }
    private AtomicFile file(){return new AtomicFile(new File(c.getNoBackupFilesDir(),"restriction.properties"));}
    private void write(Properties p)throws Exception {
        AtomicFile file=file();FileOutputStream out=file.startWrite();p.store(out,null);file.finishWrite(out);
    }
    @Test public void legacyPermanentReleaseNoLongerGrantsAnyTime()throws Exception {
        Properties p=read();p.setProperty("release."+APP,Long.toString(day()));write(p);
        assertEquals(0,RestrictionStore.allowance(c,APP));
    }
    @Test public void fifthGrantExpiresAndSixthCannotUnlockApp()throws Exception {
        for(int i=0;i<5;i++) {
            RestrictionStore.grant(c,APP);
            assertTrue(RestrictionStore.allowance(c,APP)>0);
            assertEquals(4-i,RestrictionStore.left(c));
            Properties p=read();p.setProperty("elapsedUntil","0");write(p);
            assertEquals(0,RestrictionStore.allowance(c,APP));
        }
        try{RestrictionStore.grant(c,APP);fail("Sixth grant must be refused");}
        catch(IllegalStateException expected){}
        assertEquals(0,RestrictionStore.allowance(c,APP));
        assertEquals("30",RestrictionStore.lockedLimits(c).getProperty(APP));
    }
    @Test public void exhaustedLimitSurvivesRemovalAndIncreaseButExpiresNextQuotaDay()throws Exception {
        Properties p=read();p.setProperty("quotaDay",Long.toString(day()));p.setProperty("used","5");write(p);
        RestrictionStore.lockExhausted(c,APP,30);
        c.getSharedPreferences("focus_guard_prefs",0).edit().clear().commit();LimitSnapshot.publish(c);
        assertEquals("30",LimitSnapshot.read(c).getProperty(APP));
        c.getSharedPreferences("focus_guard_prefs",0).edit().putStringSet("selected_packages",Collections.singleton(APP))
                .putInt("limit_minutes_"+APP,60).commit();LimitSnapshot.publish(c);
        assertEquals("30",LimitSnapshot.read(c).getProperty(APP));
        p=read();p.setProperty("lockedDay."+APP,Long.toString(day()-1));write(p);
        assertEquals("60",LimitSnapshot.read(c).getProperty(APP));
    }
}
