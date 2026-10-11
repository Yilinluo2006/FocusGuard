package com.luoyilin.focusguard;

import android.content.Context;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.time.*;
import java.util.Collections;
import org.json.*;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Calls the actual configuration provider with synchronization enabled and no server session. */
public class OfflineSleepTemporaryTest {
    private Context c;
    @Before public void setup() throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("focusguard_session",0).edit().clear().commit();
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        c.getSharedPreferences("device_sync",0).edit().clear().putBoolean("enabled",true)
                .putString("server",BuildConfig.API_BASE_URL).commit();
        SharedQuota.publish(c,null,Collections.emptyMap());
        LocalTime time=LocalTime.now().minusMinutes(1);
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",true)
                .putInt("minute",time.getHour()*60+time.getMinute()).putInt("duration",30)
                .putInt(FocusSchedule.WEEKDAYS_KEY,127).commit();
    }
    @After public void clean() {
        if(c!=null&&c.getPackageName().endsWith(".setupprobe")) {
            c.getSharedPreferences("focus_mode",0).edit().clear().commit();
            c.getSharedPreferences("device_sync",0).edit().clear().commit();
        }
    }
    @Test public void enabledSyncMustNotPreventAnImmediateOfflineTwoMinuteGrant() {
        Bundle grant=SleepTemporaryUse.request(c);
        assertTrue(grant.getBoolean("saved"));
        assertFalse("Offline request must grant time, not queue a backend request",grant.getBoolean("pending"));
        assertTrue("No two-minute interval was granted",grant.getLong("remaining")>115_000);
        assertEquals(4,grant.getInt("left"));
    }
    @Test public void offlineFiveUsesExpireAndThePlanPageDisablesTheSixth() {
        long end=new FocusModeStore(c).end(System.currentTimeMillis());
        for(int i=0;i<5;i++) {
            Bundle grant=SleepTemporaryUse.request(c);
            assertTrue(grant.getBoolean("saved"));assertFalse(grant.getBoolean("pending"));
            assertEquals(4-i,grant.getInt("left"));assertTrue(grant.getLong("remaining")>115_000);
            assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));
            assertTrue(new FocusPreferences(c).edit().putLong("sleepTemporaryElapsedUntil",0).commit());
        }
        Bundle state=SleepTemporaryUse.state(c);
        assertEquals(0,state.getInt("left"));assertEquals(0,state.getLong("remaining"));
        assertTrue(SleepTemporaryPolicy.controlling(end,state.getLong("remaining")));
        assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));
        assertFalse(new FocusModeStore(c).finishEarly(System.currentTimeMillis()));
        assertEquals(end,new FocusModeStore(c).end(System.currentTimeMillis()));
        try(ActivityScenario<android.app.Activity> scenario=ActivityScenario.launch(new Intent()
                .setClassName(c.getPackageName(),"com.luoyilin.focusguard.SleepTemporaryTestActivity"))) {
            onView(withText("睡眠全局临时次数已用完")).check(matches(isNotEnabled()));
        }
    }
    @Test public void migrationCountsOnlyThisDevicesActualGrantsAndClearsPendingRequests() throws Exception {
        long start=new FocusModeStore(c).scheduledStart(System.currentTimeMillis());
        JSONArray history=new JSONArray().put(new JSONObject().put("start",start).put("from",start).put("until",start+1000))
                .put(new JSONObject().put("start",start).put("from",start+1000).put("until",start+2000));
        c.getSharedPreferences("focus_mode",0).edit().putLong("sleepTemporarySharedStart",start)
                .putLong("sleepTemporaryRemoteStart",start).putInt("sleepTemporaryLeft",0)
                .putString("sleepTemporaryPendingId","stale").putLong("sleepTemporaryPendingStart",start)
                .putString("sleepTemporaryMessage","无法连接到服务器").putString("sleepTemporaryHistory",history.toString()).commit();
        Bundle state=SleepTemporaryUse.state(c);
        assertEquals(3,state.getInt("left"));assertFalse(state.getBoolean("pending"));assertFalse(state.getBoolean("shared"));
        assertEquals("",state.getString("message"));
        assertFalse(c.getSharedPreferences("focus_mode",0).contains("sleepTemporaryPendingId"));
        assertEquals(2,SleepTemporaryUse.request(c).getInt("left"));
    }
    @Test public void migrationPreservesALiveGrantAndCannotResetConsumedUses() {
        long start=new FocusModeStore(c).scheduledStart(System.currentTimeMillis()),now=System.currentTimeMillis();
        int boot=android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);
        c.getSharedPreferences("focus_mode",0).edit().putLong("sleepTemporaryDay",StreakLedger.day(start,ZoneId.systemDefault()))
                .putInt("sleepTemporaryUsed",3).putLong("sleepTemporaryGrantStart",start).putLong("sleepTemporaryUntil",now+60000)
                .putLong("sleepTemporaryElapsedUntil",android.os.SystemClock.elapsedRealtime()+60000).putInt("sleepTemporaryBoot",boot).commit();
        Bundle state=SleepTemporaryUse.state(c);assertEquals(2,state.getInt("left"));assertTrue(state.getLong("remaining")>55000);
        assertFalse(SleepTemporaryUse.request(c).getBoolean("saved"));
        c.getSharedPreferences("device_sync",0).edit().putBoolean("enabled",false).commit();
        assertEquals(2,SleepTemporaryUse.state(c).getInt("left"));
        assertTrue(new FocusPreferences(c).edit().putLong("sleepTemporaryElapsedUntil",0).commit());
        assertEquals(1,SleepTemporaryUse.request(c).getInt("left"));
    }
}
