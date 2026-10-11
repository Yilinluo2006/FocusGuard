package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.AtomicFile;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Runs only in the isolated probe. No device date, timezone or real app data is changed. */
public class SleepPlanEditWindowTest {
    private static final int ALL_DAYS = 127;
    private Context context;
    private AtomicBoolean clockBusy;
    private boolean clockFrozen;
    private int startMinute;

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Sleep edit regression requires the isolated .setupprobe package",
                context.getPackageName().endsWith(".setupprobe"));
        assumeTrue("A real boot counter is required for trusted-clock tests",
                ProtectionClock.boot(context) >= 0);
        Field busyField = ProtectionClock.class.getDeclaredField("BUSY");
        busyField.setAccessible(true);
        clockBusy = (AtomicBoolean) busyField.get(null);
        for (int i = 0; i < 30 && clockBusy.get(); i++) Thread.sleep(200);
        assertTrue("Clock verifier must be idle before it is frozen",
                clockBusy.compareAndSet(false, true));
        clockFrozen = true;
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit().clear().commit());
        assertTrue(context.getSharedPreferences("device_sync", 0).edit().clear().commit());
        clock(System.currentTimeMillis());
        seedPlan(System.currentTimeMillis(), ZoneId.systemDefault(), ALL_DAYS);
        window(false);
    }

    @After public void cleanup() throws Exception {
        if (!clockFrozen || context == null
                || !context.getPackageName().endsWith(".setupprobe")) return;
        try {
            context.getSharedPreferences("focus_mode", 0).edit().clear().commit();
            context.getSharedPreferences("device_sync", 0).edit().clear().commit();
            write("restriction", new Properties());
            clock(System.currentTimeMillis());
        } finally {
            clockBusy.set(false);
            clockFrozen = false;
        }
    }

    private static Set<String> allowedApps() {
        return new HashSet<>(Arrays.asList("probe.phone", "probe.messages", "probe.calendar",
                "probe.camera", "probe.clock", "probe.contacts"));
    }

    private void seedPlan(long now, ZoneId zone, int weekdays) {
        // Seed the existing configuration directly; setup must not use the save gate being tested.
        startMinute = (minute(now, zone) + 120) % 1440;
        if (startMinute == 1350) startMinute++; // Removing minute must produce a genuine change.
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit().clear()
                .putBoolean("daily", true).putInt("minute", startMinute).putInt("duration", 240)
                .putInt(FocusSchedule.WEEKDAYS_KEY, weekdays).putStringSet("allowed", allowedApps())
                .commit());
        assertEquals("The existing plan must not be running during edit-window tests", 0L,
                FocusSchedule.activeStart(now, startMinute, 240, weekdays, zone));
    }

    private static int minute(long millis, ZoneId zone) {
        LocalTime time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime();
        return time.getHour() * 60 + time.getMinute();
    }

    private void window(boolean open) throws Exception {
        int wallMinute = minute(System.currentTimeMillis(), ZoneId.systemDefault());
        int center = (wallMinute + (open ? 0 : 20)) % 1440;
        Properties values = new Properties();
        values.setProperty("start", Integer.toString((center + 1435) % 1440));
        values.setProperty("end", Integer.toString((center + 5) % 1440));
        write("restriction", values);
        assertEquals("Test setup must select the intended modification window",
                open, RestrictionStore.canRelax(context));
    }

    private void write(String name, Properties values) throws Exception {
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".properties"));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            values.store(output, null);
            file.finishWrite(output);
        } catch (Exception failure) {
            if (output != null) file.failWrite(output);
            throw failure;
        }
    }

    private void clock(long trusted) throws Exception {
        Properties values = new Properties();
        ProtectionTimeLedger.verify(values, trusted, SystemClock.elapsedRealtime(),
                ProtectionClock.boot(context), ZoneId.systemDefault().getId());
        write("protection-clock", values);
        Field cached = ProtectionClock.class.getDeclaredField("cached");
        cached.setAccessible(true);
        cached.set(null, null);
    }

    private FocusPreferences preferences() { return new FocusPreferences(context); }

    private void rejectedUnchanged(SharedPreferences.Editor edit) {
        Map<String, ?> original = new HashMap<>(preferences().getAll());
        assertFalse("A relaxation outside the trusted window must fail at commit", edit.commit());
        assertEquals("Rejected save must preserve every original setting", original, preferences().getAll());
    }

    @Test public void outsideWindowRejectsDisablingPlan() {
        rejectedUnchanged(preferences().edit().putBoolean("daily", false));
    }

    @Test public void outsideWindowRejectsShorterDuration() {
        rejectedUnchanged(preferences().edit().putInt("duration", 239));
    }

    @Test public void outsideWindowRejectsRemovingWeekday() {
        rejectedUnchanged(preferences().edit().putInt(FocusSchedule.WEEKDAYS_KEY, 126));
    }

    @Test public void outsideWindowRejectsLaterStartEvenWithLongerDuration() {
        rejectedUnchanged(preferences().edit().putInt("minute", (startMinute + 15) % 1440)
                .putInt("duration", 300));
    }

    @Test public void outsideWindowRejectsAddingWhitelistApp() {
        Set<String> apps = allowedApps();
        apps.add("probe.browser");
        rejectedUnchanged(preferences().edit().putStringSet("allowed", apps));
    }

    @Test public void outsideWindowAllowsLongerDuration() {
        assertTrue(preferences().edit().putInt("duration", 270).commit());
        assertEquals(270, preferences().getInt("duration", 0));
        assertEquals(startMinute, preferences().getInt("minute", -1));
    }

    @Test public void outsideWindowAllowsAddingWeekday() {
        seedPlan(System.currentTimeMillis(), ZoneId.systemDefault(), 126);
        assertTrue(preferences().edit().putInt(FocusSchedule.WEEKDAYS_KEY, ALL_DAYS).commit());
        assertEquals(ALL_DAYS, preferences().getInt(FocusSchedule.WEEKDAYS_KEY, 0));
    }

    @Test public void outsideWindowAllowsRemovingWhitelistApp() {
        Set<String> apps = allowedApps();
        apps.remove("probe.camera");
        assertTrue(preferences().edit().putStringSet("allowed", apps).commit());
        assertEquals(apps, preferences().getStringSet("allowed", null));
    }

    @Test public void outsideWindowAllowsEarlierStartThatPreservesOldEnd() {
        assertTrue(preferences().edit().putInt("minute", (startMinute + 1410) % 1440)
                .putInt("duration", 270).commit());
        assertEquals((startMinute + 1410) % 1440, preferences().getInt("minute", -1));
        assertEquals(270, preferences().getInt("duration", 0));
    }

    @Test public void outsideWindowRejectsEarlierStartThatLosesOldEnd() {
        rejectedUnchanged(preferences().edit().putInt("minute", (startMinute + 1410) % 1440));
    }

    @Test public void removingStartTimeCannotBypassWindow() {
        rejectedUnchanged(preferences().edit().remove("minute"));
    }

    @Test public void removingEnabledFlagCannotBypassWindow() {
        rejectedUnchanged(preferences().edit().remove("daily"));
    }

    @Test public void clearCannotBypassWindow() {
        rejectedUnchanged(preferences().edit().clear());
    }

    @Test public void insideWindowAllowsRelaxingDurationAndWhitelist() throws Exception {
        window(true);
        Set<String> apps = allowedApps();
        apps.add("probe.browser");
        assertTrue(preferences().edit().putInt("duration", 180).putStringSet("allowed", apps).commit());
        assertEquals(180, preferences().getInt("duration", 0));
        assertEquals(apps, preferences().getStringSet("allowed", null));
    }

    @Test public void insideWindowAllowsDisablingPlan() throws Exception {
        window(true);
        assertTrue(preferences().edit().putBoolean("daily", false).commit());
        assertFalse(preferences().getBoolean("daily", true));
    }

    @Test public void editorOpenedInsideWindowMustRecheckWhenWindowCloses() throws Exception {
        window(true);
        SharedPreferences.Editor draft = preferences().edit().putBoolean("daily", false);
        // Reproduce permission expiry without waiting or altering the device's clock.
        window(false);
        rejectedUnchanged(draft);
    }

    @Test public void wallTimeInsideWindowCannotOverrideMismatchedTrustedClock() throws Exception {
        window(true);
        // Both local times remain within the test window; only clock alignment denies the edit.
        clock(System.currentTimeMillis() - 2 * 60_000L);
        ProtectionTimeLedger.Sample sample = ProtectionClock.read(context);
        assertTrue(sample.verified);
        assertFalse(sample.aligned);
        assertTrustedMinuteInsideWindow(sample);
        assertEquals(0L, FocusSchedule.activeStart(sample.millis, startMinute, 240,
                ALL_DAYS, sample.zone));
        rejectedUnchanged(preferences().edit().putBoolean("daily", false));
    }

    @Test public void unverifiedRebootCannotOpenModificationWindow() throws Exception {
        window(true);
        Properties values = new Properties();
        ProtectionTimeLedger.verify(values, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                ProtectionClock.boot(context) - 1, ZoneId.systemDefault().getId());
        write("protection-clock", values);
        Field cached = ProtectionClock.class.getDeclaredField("cached");
        cached.setAccessible(true);
        cached.set(null, null);
        ProtectionTimeLedger.Sample sample = ProtectionClock.read(context);
        assertFalse(sample.verified);
        assertTrustedMinuteInsideWindow(sample);
        assertEquals(0L, FocusSchedule.activeStart(sample.millis, startMinute, 240,
                ALL_DAYS, sample.zone));
        rejectedUnchanged(preferences().edit().putBoolean("daily", false));
    }

    @Test public void timezoneMisalignmentCannotOpenModificationWindow() throws Exception {
        window(true);
        long now = System.currentTimeMillis();
        ZoneId otherZone = ZoneId.systemDefault().getId().equals("UTC")
                ? ZoneId.of("Asia/Shanghai") : ZoneId.of("UTC");
        seedPlan(now, otherZone, ALL_DAYS);
        int trustedMinute = minute(now, otherZone);
        Properties rules = new Properties();
        rules.setProperty("start", Integer.toString((trustedMinute + 1435) % 1440));
        rules.setProperty("end", Integer.toString((trustedMinute + 5) % 1440));
        write("restriction", rules);
        // Persist the same injected zone so cache expiration retains the misalignment.
        Properties values = new Properties();
        ProtectionTimeLedger.verify(values, now, SystemClock.elapsedRealtime(),
                ProtectionClock.boot(context), otherZone.getId());
        write("protection-clock", values);
        ProtectionTimeLedger.Sample injected = new ProtectionTimeLedger.Sample(now, otherZone,
                true, false, StreakLedger.day(now, otherZone));
        Field cached = ProtectionClock.class.getDeclaredField("cached");
        cached.setAccessible(true);
        cached.set(null, injected);
        Field cachedAt = ProtectionClock.class.getDeclaredField("cachedAt");
        cachedAt.setAccessible(true);
        cachedAt.setLong(null, SystemClock.elapsedRealtime());
        assertTrue(ProtectionClock.read(context).verified);
        assertFalse(ProtectionClock.read(context).aligned);
        assertTrustedMinuteInsideWindow(ProtectionClock.read(context));
        rejectedUnchanged(preferences().edit().putBoolean("daily", false));
    }

    private void assertTrustedMinuteInsideWindow(ProtectionTimeLedger.Sample sample) throws Exception {
        Map<String, Object> rules = RestrictionStore.backupConfig(context);
        assertTrue("The clock failure must be tested while its local minute is inside the window",
                RestrictionRules.inWindow(minute(sample.millis, sample.zone),
                        ((Number) rules.get("start")).intValue(), ((Number) rules.get("end")).intValue()));
    }

    private JSONObject remoteSleep(boolean enabled, int duration) throws Exception {
        return new JSONObject().put("daily", enabled).put("minute", startMinute)
                .put("duration", duration).put(FocusSchedule.WEEKDAYS_KEY, ALL_DAYS)
                .put("allowed", new JSONArray(new TreeSet<>(allowedApps())));
    }

    @Test public void directRemoteSleepApplicationCannotBypassWindow() throws Exception {
        Map<String, ?> original = new HashMap<>(preferences().getAll());
        assertFalse(SharedSettingsData.applySleep(context, remoteSleep(false, 240)));
        assertEquals("Rejected remote plan must preserve the local plan",
                original, preferences().getAll());
    }

    @Test public void remoteRelaxationStaysPendingAndLocalStrengtheningClearsIt() throws Exception {
        String expected = SharedSettingsData.collect(context);
        JSONObject remote = new JSONObject(expected).put("sleep", remoteSleep(false, 240));
        Map<String, ?> original = new HashMap<>(preferences().getAll());
        assertTrue(SharedSettingsData.apply(context, expected, SharedSettingsData.canonical(remote)));
        assertEquals("Remote relaxation must not replace the protected local plan",
                original, preferences().getAll());
        SharedPreferences sync = context.getSharedPreferences("device_sync", 0);
        assertTrue("Rejected remote relaxation must be retained as pending", sync.contains("pendingSleep"));
        assertFalse(new JSONObject(sync.getString("pendingSleep", "{}")).getBoolean("daily"));
        assertTrue(preferences().edit().putInt("duration", 300).commit());
        assertFalse("A fresh local edit must clear the obsolete pending remote plan",
                sync.contains("pendingSleep"));
        JSONObject finalSleep = new JSONObject(SharedSettingsData.collect(context)).getJSONObject("sleep");
        assertTrue(finalSleep.getBoolean("daily"));
        assertEquals(300, finalSleep.getInt("duration"));
    }

    @Test public void localStrengtheningClearsPendingEvenWhileGlobalSyncFlagIsSet() throws Exception {
        SharedPreferences sync = context.getSharedPreferences("device_sync", 0);
        assertTrue(sync.edit().putString("pendingSleep",
                SharedSettingsData.canonical(remoteSleep(false, 240))).commit());
        Field applying = DeviceSyncManager.class.getDeclaredField("applying");
        applying.setAccessible(true);
        boolean original = applying.getBoolean(null);
        try {
            // The provider's process may be syncing while a guard process submits a local edit.
            applying.setBoolean(null, true);
            assertTrue(preferences().edit().putInt("duration", 300).commit());
            assertFalse("A global sync flag must not misclassify a local edit as a synced write",
                    sync.contains("pendingSleep"));
            assertTrue(preferences().getBoolean("daily", false));
            assertEquals(300, preferences().getInt("duration", 0));
        } finally {
            applying.setBoolean(null, original);
        }
    }
}
