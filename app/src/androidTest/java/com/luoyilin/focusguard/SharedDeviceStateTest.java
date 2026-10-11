package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.DeviceSyncData;
import com.luoyilin.focusguard.network.LoginResponse;
import java.io.FileOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Only the isolated package may alter test settings; the user's app stays running. */
public class SharedDeviceStateTest {
    private Context context;
    private AtomicBoolean syncBusy;
    private boolean networkFrozen;
    @Before public void reset() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        Field field=DeviceSyncManager.class.getDeclaredField("BUSY");field.setAccessible(true);
        syncBusy=(AtomicBoolean)field.get(null);
        for(int i=0;i<30&&syncBusy.get();i++)Thread.sleep(200);
        assertTrue("Probe network worker must be idle before injecting the test account",syncBusy.compareAndSet(false,true));
        networkFrozen=true; // Exercise quota state without allowing a fake account to send network requests.
        assertTrue(new FocusPreferences(context).edit().clear().commit());
        for(String name:new String[]{"focus_tasks","focus_guard_prefs","device_sync","cloud_backup","focusguard_session"})assertTrue(context.getSharedPreferences(name,0).edit().clear().commit());
        SharedQuota.publish(context,null,Collections.emptyMap());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->new SessionManager(context).saveLoginSession(
                new Gson().fromJson("{\"userId\":7,\"token\":\"quota-probe-token\",\"tokenType\":\"Bearer\",\"displayName\":\"Quota probe\",\"email\":\"probe@example.invalid\"}",LoginResponse.class)));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SessionManager session=new SessionManager(context);
        assertTrue(session.isLoggedIn());assertEquals(7L,session.getUserId());
        assertTrue(DeviceSyncManager.prefs(context).edit().putLong("owner",session.getUserId()).putString("server",BuildConfig.API_BASE_URL).commit());
        assertFalse("Individual test cases enable synchronization themselves",DeviceSyncManager.enabled(context));
    }
    @After public void clean() throws Exception {
        if(context!=null&&context.getPackageName().endsWith(".setupprobe")&&networkFrozen){
            try{
                assertTrue(context.getSharedPreferences("device_sync",0).edit().clear().commit());
                assertTrue(context.getSharedPreferences("cloud_backup",0).edit().clear().commit());
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->new SessionManager(context).clearSession());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                SharedQuota.publish(context,null,Collections.emptyMap());
                new FocusPreferences(context).edit().clear().commit();
            }finally{syncBusy.set(false);}
        }
    }
    private String remote(String local) throws Exception {
        JSONObject value=new JSONObject(local);
        value.getJSONObject("sleep").put("daily",false).put("minute",1410).put("duration",240).put("sleepWeekdays",31);
        return SharedSettingsData.canonical(value);
    }
    @Test public void newInstallationReceivesAccountSleepPlanWithoutConflict() throws Exception {
        String local=SharedSettingsData.collect(context),remote=remote(local);
        String merged=SharedSettingsData.merge(SharedSettingsData.initialBase(context),local,remote);
        assertEquals(remote,merged);
        assertTrue(SharedSettingsData.apply(context,local,merged));
        FocusPreferences saved=new FocusPreferences(context);
        assertEquals(1410,saved.getInt("minute",0));assertEquals(240,saved.getInt("duration",0));assertEquals(31,saved.getInt("sleepWeekdays",0));
    }
    @Test public void remoteSleepChangeWaitsForActiveControlAndPreservesHistory() throws Exception {
        FocusPreferences saved=new FocusPreferences(context);
        String history="[]";
        assertTrue(saved.edit().putInt("minute",1350).putLong("manualEnd",System.currentTimeMillis()+60_000).putString("focusHistory",history).commit());
        String local=SharedSettingsData.collect(context),remote=remote(local);
        assertTrue(SharedSettingsData.apply(context,local,remote));
        assertEquals(1350,saved.getInt("minute",0));
        assertEquals(history,saved.getString("focusHistory",null));
        assertNotNull(DeviceSyncManager.prefs(context).getString("pendingSleep",null));
        assertEquals(remote,SharedSettingsData.collect(context));
    }
    @Test public void missingOrCorruptQuotaCannotRestoreIndependentAllowance() throws Exception {
        DeviceSyncManager.prefs(context).edit().putBoolean("enabled",true).putString("quotaMode",SharedQuotaPolicy.STRICT_SHARED).putString("server",BuildConfig.API_BASE_URL).commit();
        DeviceSyncData.Usage grant=new DeviceSyncData.Usage();
        grant.serverTime=System.currentTimeMillis();grant.validUntil=grant.serverTime+60_000;grant.day="2026-10-04";grant.requestStarted=SystemClock.elapsedRealtime();
        grant.ceilings=Collections.singletonMap("com.example.app",180_000L);
        SharedQuota.publish(context,grant,Collections.singletonMap("com.example.app",100_000L));
        assertEquals(60_000,SharedQuota.remaining(context,"com.example.app",120_000));
        assertEquals(0,SharedQuota.remaining(context,"com.example.app",180_000));
        try(FileOutputStream output=new FileOutputStream(new File(context.getNoBackupFilesDir(),"shared-quota.properties"))){output.write(new byte[]{0});}
        assertTrue(SharedQuota.enabled(context));
        assertEquals(0,SharedQuota.remaining(context,"com.example.app",120_000));
    }
    @Test public void onlineTimerUsesDailyTotalAndOfflineTimerUsesOnlyAllocatedTime() throws Exception {
        DeviceSyncManager.prefs(context).edit().putBoolean("enabled",true).putString("quotaMode",SharedQuotaPolicy.STRICT_SHARED).putString("server",BuildConfig.API_BASE_URL).commit();
        DeviceSyncData.Usage grant=new DeviceSyncData.Usage();
        grant.serverTime=System.currentTimeMillis();grant.validUntil=grant.serverTime+60_000;grant.day="2026-10-04";grant.requestStarted=SystemClock.elapsedRealtime();
        grant.ceilings=Collections.singletonMap("com.example.app",7*60_000L);
        grant.totalMillis=Collections.singletonMap("com.example.app",7*60_000L);
        SharedQuota.publish(context,grant,Collections.singletonMap("com.example.app",4*60_000L));
        SharedQuota.Timer online=SharedQuota.timer(context,"com.example.app",5*60_000L,30*60_000L,2*60_000L);
        assertFalse(online.offline);assertEquals(22*60_000L,online.millis);
        SharedQuota.markOffline(context);
        SharedQuota.Timer offline=SharedQuota.timer(context,"com.example.app",5*60_000L,30*60_000L,2*60_000L);
        assertTrue(offline.offline);assertEquals(2*60_000L,offline.millis);
        grant.totalMillis=Collections.emptyMap();
        SharedQuota.publish(context,grant,Collections.emptyMap());
        SharedQuota.Timer unused=SharedQuota.timer(context,"com.example.unused",0,30*60_000L,2*60_000L);
        assertFalse(unused.offline);assertEquals(30*60_000L,unused.millis);
    }
    @Test public void legacySharedSnapshotDoesNotLimitIndependentOfflineUsage() throws Exception {
        DeviceSyncManager.prefs(context).edit().putBoolean("enabled",true).putString("server",BuildConfig.API_BASE_URL).commit();
        assertFalse(DeviceSyncManager.strictQuota(context));
        // Exact upgrade shape: old sync marker and a two-minute grant, with no quotaMode field.
        new File(context.getNoBackupFilesDir(),"shared-quota-enabled").createNewFile();
        java.util.Properties legacy=new java.util.Properties();legacy.setProperty("enabled","true");
        legacy.setProperty("online","false");legacy.setProperty("ceiling.com.example.app","720000");
        legacy.setProperty("reported.com.example.app","600000");legacy.setProperty("total.com.example.app","1500000");
        try(FileOutputStream output=new FileOutputStream(new File(context.getNoBackupFilesDir(),"shared-quota.properties"))){legacy.store(output,null);}
        assertTrue(SharedQuota.synchronizationEnabled(context));assertFalse(SharedQuota.enabled(context));
        assertEquals(Long.MAX_VALUE,SharedQuota.remaining(context,"com.example.app",10*60_000L));
        SharedQuota.Timer timer=SharedQuota.timer(context,"com.example.app",10*60_000L,30*60_000L,2*60_000L);
        assertFalse(timer.offline);assertEquals(20*60_000L,timer.millis);
        assertEquals(0,SharedQuota.timer(context,"com.example.app",30*60_000L,30*60_000L,2*60_000L).millis);
        assertTrue(SharedQuota.blockedMessage(context,"测试应用").contains("本机每日额度"));
    }
    @Test public void switchingToPerDevicePreservesLocalUsageAndSynchronization() throws Exception {
        DeviceSyncManager.prefs(context).edit().putBoolean("enabled",true).putString("quotaMode",SharedQuotaPolicy.STRICT_SHARED).putString("server",BuildConfig.API_BASE_URL).commit();
        DeviceSyncData.Usage grant=new DeviceSyncData.Usage();grant.day="2026-10-04";
        grant.serverTime=System.currentTimeMillis();grant.validUntil=grant.serverTime+60_000;grant.requestStarted=SystemClock.elapsedRealtime();
        grant.ceilings=Collections.singletonMap("com.example.app",12*60_000L);
        grant.totalMillis=Collections.singletonMap("com.example.app",25*60_000L);
        SharedQuota.publish(context,grant,Collections.singletonMap("com.example.app",10*60_000L));
        assertTrue(SharedQuota.enabled(context));
        DeviceSyncManager.prefs(context).edit().remove("quotaMode").commit();
        SharedQuota.publish(context,grant,Collections.singletonMap("com.example.app",10*60_000L));
        SharedQuota.markOffline(context);
        assertTrue(DeviceSyncManager.enabled(context));assertTrue(SharedQuota.synchronizationEnabled(context));
        assertFalse(SharedQuota.enabled(context));
        assertEquals(20*60_000L,SharedQuota.timer(context,"com.example.app",10*60_000L,30*60_000L,2*60_000L).millis);
        try(FileOutputStream output=new FileOutputStream(new File(context.getNoBackupFilesDir(),"shared-quota.properties"))){output.write(new byte[]{0});}
        assertEquals(Long.MAX_VALUE,SharedQuota.remaining(context,"com.example.app",10*60_000L));
        assertEquals(20*60_000L,SharedQuota.timer(context,"com.example.app",10*60_000L,30*60_000L,0).millis);
    }
}
