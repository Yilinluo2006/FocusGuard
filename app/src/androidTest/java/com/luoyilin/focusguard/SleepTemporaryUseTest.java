package com.luoyilin.focusguard;

import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import org.junit.*;
import java.time.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.startsWith;

/** Sleep plans and grants are only created in the isolated app; user settings stay untouched. */
public class SleepTemporaryUseTest {
    private Context c;private FocusPreferences p;
    @Before public void setup()throws Exception{
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("device_sync",0).edit().clear().commit();SharedQuota.publish(c,null,java.util.Collections.emptyMap());
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        p=new FocusPreferences(c);
        ZonedDateTime t=Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).minusMinutes(1);
        assertTrue(p.edit().putBoolean("daily",true).putInt("minute",t.getHour()*60+t.getMinute()).putInt("duration",30)
                .putInt(FocusSchedule.WEEKDAYS_KEY,127).commit());
    }
    @After public void clean(){if(c!=null&&c.getPackageName().endsWith(".setupprobe"))c.getSharedPreferences("focus_mode",0).edit().clear().commit();}
    @Test public void activeSleepRejectsPlanEditsAndLegacyDismissal() {
        long end=new FocusModeStore(c).end(System.currentTimeMillis());
        assertFalse(p.edit().putBoolean("daily",false).commit());
        assertFalse(p.edit().putInt("duration",1).commit());
        assertFalse(p.edit().putLong("dismissed",SleepTemporaryUse.state(c).getLong("start")).commit());
        assertFalse(p.edit().clear().commit());
        assertEquals(end,new FocusModeStore(c).end(System.currentTimeMillis()));
        // Simulate an old persisted dismissal left behind by a previous version.
        c.getSharedPreferences("focus_mode",0).edit().putLong("dismissed",SleepTemporaryUse.state(c).getLong("start")).commit();
        assertTrue(SleepTemporaryUse.state(c).getBoolean("sleeping"));
        assertFalse(new FocusModeStore(c).finishEarly(System.currentTimeMillis()));
        assertEquals(end,new FocusModeStore(c).end(System.currentTimeMillis()));
    }
    @Test public void grantKeepsSleepActiveAndOldFinishCannotDismissIt() {
        FocusModeStore store=new FocusModeStore(c);long end=store.end(System.currentTimeMillis());assertTrue(end>System.currentTimeMillis());
        Bundle grant=SleepTemporaryUse.request(c);assertTrue(grant.getBoolean("saved"));assertEquals(4,grant.getInt("left"));
        assertTrue(grant.getLong("remaining")>115_000);assertEquals(end,store.end(System.currentTimeMillis()));
        assertFalse(store.finishEarly(System.currentTimeMillis()));
        assertFalse(SleepTemporaryUse.callProvider(c,"sleepFinish",null).getBoolean("saved"));
        assertEquals(0,p.getLong("dismissed",0));assertEquals("[]",p.getString("sleepControlHistory","[]"));
        assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));assertEquals(4,SleepTemporaryUse.state(c).getInt("left"));
    }
    @Test public void fiveIntervalsExhaustAndExpiryRestoresControlWithNoPermanentRelease() {
        for(int i=0;i<5;i++){
            Bundle grant=SleepTemporaryUse.request(c);assertTrue(grant.getBoolean("saved"));assertEquals(4-i,grant.getInt("left"));
            // Simulate the expiry seam; the actual wall/elapsed boundary is covered by the JVM policy test.
            p.edit().putLong("sleepTemporaryElapsedUntil",0).commit();assertEquals(0,SleepTemporaryUse.state(c).getLong("remaining"));
        }
        assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));assertTrue(SleepTemporaryUse.state(c).getBoolean("sleeping"));
        assertTrue(new FocusModeStore(c).end(System.currentTimeMillis())>0);assertEquals(0,p.getLong("dismissed",0));
    }
    @Test public void planPageReplacesPermanentExitWithTemporaryUse() {
        // This host exists only in build/setup-probe-project and uses the real FocusModePage.
        try(ActivityScenario<android.app.Activity> scenario=ActivityScenario.launch(new Intent()
                .setClassName(c.getPackageName(),"com.luoyilin.focusguard.SleepTemporaryTestActivity"))){
            onView(withText(startsWith("睡眠全局临时使用 2 分钟"))).check(matches(isEnabled()));
            assertFalse(new FocusModeStore(c).finishEarly(System.currentTimeMillis()));
        }
    }
    @Test public void legacySharedBalanceDoesNotConsumeLocalBudget() {
        assertTrue(p.edit().putLong("sleepTemporarySharedStart",SleepTemporaryUse.state(c).getLong("start"))
                .putLong("sleepTemporaryRemoteStart",SleepTemporaryUse.state(c).getLong("start")).putInt("sleepTemporaryLeft",0).commit());
        assertFalse(SleepTemporaryUse.state(c).getBoolean("shared"));
        Bundle grant=SleepTemporaryUse.request(c);assertTrue(grant.getBoolean("saved"));assertEquals(4,grant.getInt("left"));
    }
    @Test public void configurationSyncDoesNotRequireOnlineSleepGrants() throws Exception {
        c.getSharedPreferences("device_sync",0).edit().putBoolean("enabled",true).putString("server",BuildConfig.API_BASE_URL).commit();
        SharedQuota.publish(c,null,java.util.Collections.emptyMap());
        assertFalse(SharedQuota.enabled(c));assertTrue(SharedQuota.synchronizationEnabled(c));
        Bundle state=SleepTemporaryUse.state(c);assertFalse(state.getBoolean("shared"));
        assertTrue(p.edit().putLong("sleepTemporaryRemoteStart",state.getLong("start")).putInt("sleepTemporaryLeft",0).commit());
        Bundle grant=SleepTemporaryUse.request(c);assertTrue(grant.getBoolean("saved"));assertFalse(grant.getBoolean("pending"));
        assertEquals(4,grant.getInt("left"));assertTrue(SleepTemporaryUse.state(c).getBoolean("sleeping"));
        assertTrue(SharedQuota.synchronizationEnabled(c));
    }
    @Test public void repeatedServerConfirmationCannotReactivateAnExpiredInterval() {
        long start=SleepTemporaryUse.state(c).getLong("start");
        int boot=android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);
        assertTrue(p.edit().putLong("sleepTemporarySharedStart",start).putString("sleepTemporaryPendingId","first")
                .putLong("sleepTemporaryPendingStart",start).putInt("sleepTemporaryPendingBoot",boot).commit());
        Bundle ack=new Bundle();ack.putLong("start",start);ack.putInt("left",4);ack.putString("pendingId","first");
        ack.putString("grantId","server-grant");ack.putLong("duration",120_000);
        assertFalse(SleepTemporaryUse.callProvider(c,"sleepTemporaryPublish",ack).getBoolean("saved"));
        Bundle local=SleepTemporaryUse.request(c);assertTrue(local.getBoolean("saved"));
        assertTrue(p.edit().putLong("sleepTemporaryElapsedUntil",0).putString("sleepTemporaryPendingId","retry").commit());
        ack.putString("pendingId","retry");
        assertFalse(SleepTemporaryUse.callProvider(c,"sleepTemporaryPublish",ack).getBoolean("saved"));
        assertEquals(0,SleepTemporaryUse.state(c).getLong("remaining"));
        assertEquals(4,SleepTemporaryUse.state(c).getInt("left"));
        assertEquals(0,p.getLong("dismissed",0));
    }
}
