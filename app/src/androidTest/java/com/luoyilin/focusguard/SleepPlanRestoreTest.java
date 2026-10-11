package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.LoginResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Restore calls and synthetic account/clock data belong only to the isolated probe package. */
public class SleepPlanRestoreTest {
    private static final String[] STORES = {"focus_mode", "focus_tasks", "focus_guard_prefs",
            "device_sync", "cloud_backup", "focusguard_session", "app_wallpaper"};
    private static final String LOCAL_TASKS = "[{\"id\":\"local-task\",\"name\":\"Local task\",\"mode\":\"countup\",\"value\":0}]";
    private static final String LOCAL_HISTORY = "[{\"id\":\"local-history\",\"startedAt\":100,\"endedAt\":200,\"elapsedMillis\":100}]";
    private Context context;
    private AtomicBoolean busy;
    private boolean verifierFrozen;
    private SessionManager session;

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        Field field = ProtectionClock.class.getDeclaredField("BUSY");
        field.setAccessible(true); busy = (AtomicBoolean) field.get(null);
        for (int i = 0; i < 30 && busy.get(); i++) Thread.sleep(200);
        assertTrue("The probe's asynchronous verifier must be idle before freezing", busy.compareAndSet(false, true));
        verifierFrozen = true;
        clearProbePreferences();
        writeProperties("streak", new Properties());
        clock(System.currentTimeMillis());
        LocalTime now = Instant.ofEpochMilli(ProtectionClock.now(context)).atZone(ProtectionClock.zone(context)).toLocalTime();
        int minute = now.getHour() * 60 + now.getMinute();
        Properties restriction = new Properties();
        restriction.setProperty("start", Integer.toString((minute + 360) % 1440));
        restriction.setProperty("end", Integer.toString((minute + 420) % 1440));
        writeProperties("restriction", restriction);
        assertFalse("These restore tests must run outside the trusted edit window", RestrictionStore.canRelax(context));
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit()
                .putBoolean("daily", true).putInt("minute", (minute + 120) % 1440)
                .putString("focusHistory", LOCAL_HISTORY).putString("pomodoroHistory", "[]")
                .putString("sleepControlHistory", "[]").commit());
        assertTrue(context.getSharedPreferences("focus_tasks", 0).edit().putString("tasks", LOCAL_TASKS).commit());
        assertTrue(context.getSharedPreferences("focus_guard_prefs", 0).edit()
                .putStringSet("selected_packages", Collections.singleton("com.example.local"))
                .putInt("limit_minutes_com.example.local", 30).commit());
        assertTrue("No sleep or focus session may obscure the restore-window decision",
                new FocusModeStore(context).end(ProtectionClock.now(context)) <= 0);
        session = new SessionManager(context);
        session.saveLoginSession(new Gson().fromJson("{\"userId\":7,\"token\":\"restore-probe-token\","
                + "\"tokenType\":\"Bearer\",\"displayName\":\"Restore probe\",\"email\":\"probe@example.invalid\"}", LoginResponse.class));
        assertTrue(session.isLoggedIn());
    }

    @After public void cleanup() throws Exception {
        if (context == null || !context.getPackageName().endsWith(".setupprobe")) return;
        try {
            if (verifierFrozen) {
                clearProbePreferences();
                writeProperties("restriction", new Properties());
                clock(System.currentTimeMillis());
            }
        } finally {
            if (verifierFrozen) busy.set(false);
        }
    }

    @Test public void backupCannotShortenAMissingLegacyDurationOutsideWindow() throws Exception {
        assertFalse(context.getSharedPreferences("focus_mode", 0).contains("duration"));
        JSONObject payload = payload(); payload.getJSONObject("focus").put("duration", 120);
        assertDeniedWithoutConfigWrites(payload);
    }

    @Test public void backupCanStrengthenAMissingLegacyDurationOutsideWindow() throws Exception {
        JSONObject payload = payload(); payload.getJSONObject("focus").put("duration", 240);
        Bundle result = restore(payload);
        assertTrue(result.getString("error"), result.getBoolean("saved"));
        SharedPreferences focus = context.getSharedPreferences("focus_mode", 0);
        assertEquals(240, focus.getInt("duration", 0));
        assertTrue(focus.getBoolean("daily", false));
        assertFalse("A legacy daily plan must retain its implicit all-days selection", focus.contains(FocusSchedule.WEEKDAYS_KEY));
        assertEquals(2, new JSONArray(context.getSharedPreferences("focus_tasks", 0).getString("tasks", "[]")).length());
        assertEquals(2, context.getSharedPreferences("focus_guard_prefs", 0).getStringSet("selected_packages", Collections.emptySet()).size());
        JSONArray history = new JSONArray(focus.getString("focusHistory", "[]"));
        assertEquals(2, history.length()); assertEquals("local-history", history.getJSONObject(0).getString("id"));
    }

    @Test public void backupCannotAddAMissingAllowlistOutsideWindow() throws Exception {
        assertFalse(context.getSharedPreferences("focus_mode", 0).contains("allowed"));
        JSONObject payload = payload();
        payload.getJSONObject("focus").put("allowed", new JSONArray().put("com.example.allowed"));
        assertDeniedWithoutConfigWrites(payload);
    }

    private void assertDeniedWithoutConfigWrites(JSONObject payload) throws Exception {
        Map<String, ?> focus = new HashMap<>(context.getSharedPreferences("focus_mode", 0).getAll());
        Map<String, ?> tasks = new HashMap<>(context.getSharedPreferences("focus_tasks", 0).getAll());
        Map<String, ?> limits = new HashMap<>(context.getSharedPreferences("focus_guard_prefs", 0).getAll());
        Bundle result = restore(payload);
        assertFalse("The restore must enforce the trusted edit window", result.getBoolean("saved"));
        String error = result.getString("error", "");
        assertTrue("Failure must come from the sleep-plan window gate: " + error, error.contains("以下时间"));
        assertEquals("Rejected restore must preserve all sleep configuration and histories", focus,
                context.getSharedPreferences("focus_mode", 0).getAll());
        assertEquals("Rejected restore must preserve local tasks", tasks, context.getSharedPreferences("focus_tasks", 0).getAll());
        assertEquals("Rejected restore must preserve local limits", limits, context.getSharedPreferences("focus_guard_prefs", 0).getAll());
    }

    private JSONObject payload() throws Exception {
        JSONObject root = new JSONObject("{\"schemaVersion\":1,\"tasks\":[],\"limits\":[],"
                + "\"focus\":{\"focusHistory\":[],\"pomodoroHistory\":[],\"sleepControlHistory\":[]},"
                + "\"wallpaper\":0,\"appearance\":0,\"restriction\":{},\"streak\":{}}");
        root.put("appearance", Appearance.read(context));
        root.put("tasks", new JSONArray("[{\"id\":\"remote-task\",\"name\":\"Remote task\",\"mode\":\"countup\",\"value\":0}]"));
        root.put("limits", new JSONArray("[{\"packageName\":\"com.example.remote\",\"minutes\":45}]"));
        root.getJSONObject("focus").put("focusHistory", new JSONArray("[{\"id\":\"remote-history\",\"startedAt\":300,\"endedAt\":400,\"elapsedMillis\":100}]"));
        return root;
    }

    private Bundle restore(JSONObject payload) throws Exception {
        CloudBackupData.validate(payload.toString());
        Bundle args = new Bundle(); args.putString("payload", payload.toString());
        args.putLong("userId", session.getUserId()); args.putString("authorization", session.getAuthorizationHeader());
        Bundle result = context.getContentResolver().call(LimitConfigProvider.URI, "restoreBackup", "focus_mode", args);
        assertNotNull("The real restore provider must respond", result); return result;
    }

    private void clearProbePreferences() {
        for (String store : STORES) assertTrue(context.getSharedPreferences(store, 0).edit().clear().commit());
    }

    private void clock(long trusted) throws Exception {
        Properties properties = new Properties();
        ProtectionTimeLedger.verify(properties, trusted, SystemClock.elapsedRealtime(), ProtectionClock.boot(context), ZoneId.systemDefault().getId());
        writeProperties("protection-clock", properties);
        Field field = ProtectionClock.class.getDeclaredField("cached"); field.setAccessible(true); field.set(null, null);
    }

    private void writeProperties(String name, Properties properties) throws Exception {
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".properties"));
        FileOutputStream output = null;
        try { output = file.startWrite(); properties.store(output, null); file.finishWrite(output); }
        catch (Exception error) { if (output != null) file.failWrite(output); throw error; }
    }
}
