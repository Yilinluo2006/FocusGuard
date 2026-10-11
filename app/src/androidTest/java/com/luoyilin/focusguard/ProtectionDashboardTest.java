package com.luoyilin.focusguard;

import android.content.*;
import android.os.SystemClock;
import android.util.AtomicFile;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

public class ProtectionDashboardTest {
    private Context c;
    @Before public void setup(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));}
    @After public void cleanup(){if(c!=null&&c.getPackageName().endsWith(".setupprobe"))new File(c.getNoBackupFilesDir(),"wallpaper-monitor-state").delete();}
    private void write(int boot,long time,String status)throws Exception {
        AtomicFile f=new AtomicFile(new File(c.getNoBackupFilesDir(),"wallpaper-monitor-state"));FileOutputStream out=f.startWrite();DataOutputStream d=new DataOutputStream(out);
        d.writeInt(boot);d.writeLong(time);d.writeUTF(status);d.flush();f.finishWrite(out);
    }
    @Test public void oldBootAndStaleHeartbeatsCannotReportLiveProtection()throws Exception {
        write(ProtectionClock.boot(c)-1,SystemClock.elapsedRealtime(),WallpaperMonitorState.RUNNING);assertEquals(WallpaperMonitorState.OFF,WallpaperMonitorState.read(c));
        write(ProtectionClock.boot(c),SystemClock.elapsedRealtime()-16000,WallpaperMonitorState.RUNNING);assertEquals(WallpaperMonitorState.OFF,WallpaperMonitorState.read(c));
        write(ProtectionClock.boot(c),SystemClock.elapsedRealtime(),WallpaperMonitorState.ERROR);assertFalse(WallpaperMonitorState.ready(WallpaperMonitorState.read(c)));
        write(ProtectionClock.boot(c),SystemClock.elapsedRealtime(),WallpaperMonitorState.RUNNING);assertTrue(WallpaperMonitorState.ready(WallpaperMonitorState.read(c)));
    }
    @Test public void snapshotDoesNotInventAccessibilityFromWallpaperHeartbeat()throws Exception {
        write(ProtectionClock.boot(c),SystemClock.elapsedRealtime(),WallpaperMonitorState.RUNNING);
        ProtectionDashboardSnapshot p=ProtectionDashboardSnapshot.read(c);SetupStatus actual=new SetupStatus(c);
        assertEquals(actual.accessibility,p.capabilities.sleep);assertEquals(actual.accessibility,p.capabilities.focus);
        if(!actual.accessibility){assertFalse(p.sleep.good);assertFalse(p.focus.good);}
    }
    @Test public void dashboardShowsDistinctProtectionRows(){
        try(ActivityScenario<android.app.Activity> scenario=ActivityScenario.launch(new Intent().setClassName(c.getPackageName(),"com.luoyilin.focusguard.ProtectionDashboardActivity"))){
            onView(withText("应用额度保护")).check(matches(isDisplayed()));
            onView(withText("睡眠保护")).check(matches(isDisplayed()));
            onView(withText("专注保护")).check(matches(isDisplayed()));
        }
    }
}
