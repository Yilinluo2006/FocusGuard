package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

/** Provider race regressions confined to .setupprobe; device time and real app data are untouched. */
public class SleepPlanSyncAtomicTest {
    private Context context;
    private AtomicBoolean clockBusy;
    private boolean clockFrozen;
    private int startMinute;

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Synchronization regressions require the isolated .setupprobe package",
                context.getPackageName().endsWith(".setupprobe"));
        assumeTrue("A real boot counter is required", ProtectionClock.boot(context) >= 0);
        Field busy = ProtectionClock.class.getDeclaredField("BUSY");
        busy.setAccessible(true);
        clockBusy = (AtomicBoolean) busy.get(null);
        for (int i = 0; i < 30 && clockBusy.get(); i++) Thread.sleep(200);
        assertTrue("Clock verifier must be idle before injection", clockBusy.compareAndSet(false, true));
        clockFrozen = true;
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit().clear().commit());
        assertTrue(context.getSharedPreferences("device_sync", 0).edit().clear().commit());
        clock(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        startMinute = (minute(now) + 120) % 1440;
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit()
                .putBoolean("daily", true).putInt("minute", startMinute).putInt("duration", 240)
                .putInt(FocusSchedule.WEEKDAYS_KEY, 127).putStringSet("allowed", allowedApps()).commit());
        assertEquals(0L, FocusSchedule.activeStart(now, startMinute, 240, 127, ZoneId.systemDefault()));
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

    private static int minute(long now) {
        LocalTime local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime();
        return local.getHour() * 60 + local.getMinute();
    }

    private void window(boolean open) throws Exception {
        int center = (minute(System.currentTimeMillis()) + (open ? 0 : 20)) % 1440;
        Properties rules = new Properties();
        rules.setProperty("start", Integer.toString((center + 1435) % 1440));
        rules.setProperty("end", Integer.toString((center + 5) % 1440));
        write("restriction", rules);
        assertEquals(open, RestrictionStore.canRelax(context));
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

    private SharedPreferences sync() { return context.getSharedPreferences("device_sync", 0); }
    private FocusPreferences preferences() { return new FocusPreferences(context); }

    private String sleep(boolean enabled, int duration) throws Exception {
        return SharedSettingsData.canonical(new JSONObject().put("daily", enabled)
                .put("minute", startMinute).put("duration", duration)
                .put(FocusSchedule.WEEKDAYS_KEY, 127)
                .put("allowed", new JSONArray(new TreeSet<>(allowedApps()))));
    }

    private Bundle call(String method, Bundle extras) {
        Bundle reply = context.getContentResolver().call(LimitConfigProvider.URI, method, "focus_mode", extras);
        assertNotNull("Configuration provider must return a result", reply);
        return reply;
    }

    @SuppressWarnings("unchecked")
    private Bundle apply(Bundle snapshot, String sleep, boolean queueIfDenied) {
        Bundle request = new Bundle();
        request.putSerializable("expectedPlan",
                new HashMap<>((Map<String, Object>) snapshot.getSerializable("plan")));
        request.putString("expectedPending", snapshot.getString("pending"));
        request.putString("sleep", sleep);
        request.putBoolean("queueIfDenied", queueIfDenied);
        return call("applySyncedSleep", request);
    }

    @Test public void staleRemoteSnapshotCannotRecreatePendingAfterLocalStrengthening() throws Exception {
        String remote = sleep(false, 240);
        assertTrue(sync().edit().putString("pendingSleep", remote).commit());
        Bundle snapshot = call("sleepSyncSnapshot", null);
        assertEquals(remote, snapshot.getString("pending"));
        assertTrue(preferences().edit().putInt("duration", 300).commit());
        assertFalse(sync().contains("pendingSleep"));
        Map<String, ?> strengthened = new HashMap<>(preferences().getAll());

        Bundle result = apply(snapshot, remote, true);
        assertTrue("An obsolete plan/pending snapshot must be rejected as stale", result.getBoolean("stale"));
        assertFalse(result.getBoolean("saved"));
        assertFalse(result.getBoolean("applied"));
        assertFalse("A stale sync must not recreate the canceled pending value", sync().contains("pendingSleep"));
        assertEquals(strengthened, preferences().getAll());
        assertEquals(300, preferences().getInt("duration", 0));
        assertTrue(preferences().getBoolean("daily", false));
    }

    @Test public void replayAfterLocalCancellationDoesNotChangeTheStrengthenedPlan() throws Exception {
        assertTrue(sync().edit().putString("pendingSleep", sleep(false, 240)).commit());
        assertTrue(preferences().edit().putInt("duration", 300).commit());
        Map<String, ?> strengthened = new HashMap<>(preferences().getAll());

        Bundle result = call("replayPendingSleep", null);
        assertTrue(result.getBoolean("saved"));
        assertFalse(result.getBoolean("applied"));
        assertFalse(sync().contains("pendingSleep"));
        assertEquals(strengthened, preferences().getAll());
    }

    @Test public void deniedRemoteStaysPendingUntilProviderCanApplyAndClearIt() throws Exception {
        String remote = sleep(false, 240);
        Map<String, ?> original = new HashMap<>(preferences().getAll());
        Bundle queued = apply(call("sleepSyncSnapshot", null), remote, true);
        assertTrue(queued.getBoolean("saved"));
        assertTrue(queued.getBoolean("denied"));
        assertFalse(queued.getBoolean("applied"));
        assertEquals(remote, sync().getString("pendingSleep", null));
        assertEquals(original, preferences().getAll());

        Bundle waiting = call("replayPendingSleep", null);
        assertTrue(waiting.getBoolean("saved"));
        assertTrue(waiting.getBoolean("denied"));
        assertFalse(waiting.getBoolean("applied"));
        assertEquals(remote, sync().getString("pendingSleep", null));
        assertEquals(original, preferences().getAll());

        window(true);
        Bundle applied = call("replayPendingSleep", null);
        assertTrue(applied.getBoolean("saved"));
        assertTrue(applied.getBoolean("applied"));
        assertFalse(sync().contains("pendingSleep"));
        assertFalse(preferences().getBoolean("daily", true));
        assertEquals(240, preferences().getInt("duration", 0));
    }

    @Test public void directAndLegacySyncedWritesCannotBypassGateOrReplacePending() throws Exception {
        String pending = sleep(false, 240);
        assertTrue(sync().edit().putString("pendingSleep", pending).commit());
        Map<String, ?> original = new HashMap<>(preferences().getAll());
        Bundle denied = apply(call("sleepSyncSnapshot", null), sleep(true, 180), false);
        assertFalse(denied.getBoolean("saved"));
        assertTrue(denied.getBoolean("denied"));
        assertFalse(denied.getBoolean("applied"));
        assertEquals(pending, sync().getString("pendingSleep", null));
        assertEquals(original, preferences().getAll());

        Bundle changes = new Bundle();
        changes.putBoolean("daily", false);
        Bundle write = new Bundle();
        write.putBundle("changes", changes);
        assertFalse(call("writeSyncedSleep", write).getBoolean("saved"));
        assertEquals("Legacy synchronized writes must also preserve denied pending state",
                pending, sync().getString("pendingSleep", null));
        assertEquals(original, preferences().getAll());
    }
}
