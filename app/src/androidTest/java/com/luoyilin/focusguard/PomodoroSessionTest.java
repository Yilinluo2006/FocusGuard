package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PomodoroSessionTest {
    private Context context;
    private SharedPreferences prefs;
    private String name;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "pomodoro-test-" + java.util.UUID.randomUUID();
        prefs = context.getSharedPreferences(name, 0);
    }
    @After public void cleanup() { context.deleteSharedPreferences(name); }
    private Bundle start(int rounds) {
        Bundle args = new Bundle(); args.putInt("rounds", rounds);
        return PomodoroSession.call(context, prefs, "pomodoroStart", args);
    }
    private void advance(long elapsed) {
        long now = System.currentTimeMillis();
        prefs.edit().putLong("pomodoroStart", now - elapsed)
                .putLong("pomodoroElapsed", SystemClock.elapsedRealtime() - elapsed)
                .putInt("pomodoroBoot", Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1))
                .commit();
    }
    private Bundle state() { return PomodoroSession.call(context, prefs, "pomodoroState", null); }
    @Test public void restRemainsActiveAndSurvivesReload() {
        assertTrue(start(3).getBoolean("saved"));
        advance(26 * 60000L);
        prefs = context.getSharedPreferences(name, 0);
        Bundle result = state();
        assertTrue(result.getBoolean("active"));
        assertTrue(result.getBoolean("resting"));
        assertEquals(1, result.getInt("round"));
        assertTrue(result.getLong("remaining") <= 4 * 60000L);
        assertFalse(start(1).getBoolean("saved"));
    }
    @Test public void completesOnceAndExcludesRest() throws Exception {
        assertTrue(start(3).getBoolean("saved"));
        advance(85 * 60000L);
        assertFalse(state().getBoolean("active"));
        assertFalse(state().getBoolean("active"));
        JSONArray records = new JSONArray(prefs.getString("pomodoroHistory", "[]"));
        assertEquals(1, records.length());
        assertEquals("completed", records.getJSONObject(0).getString("reason"));
        assertEquals(75 * 60000L, records.getJSONObject(0).getLong("workMillis"));
        assertEquals(10 * 60000L, records.getJSONObject(0).getLong("restMillis"));
    }
    @Test public void sleepTakeoverSettlesPartialWork() throws Exception {
        start(3); advance(20 * 60000L);
        prefs.edit().putLong("pomodoroSleep", System.currentTimeMillis() - 10 * 60000L).commit();
        assertFalse(state().getBoolean("active"));
        JSONArray records = new JSONArray(prefs.getString("pomodoroHistory", "[]"));
        assertEquals("sleep_takeover", records.getJSONObject(0).getString("reason"));
        assertTrue(records.getJSONObject(0).getLong("workMillis") < 11 * 60000L);
    }
    @Test public void rejectsExistingManualSession() {
        prefs.edit().putLong("manualEnd", System.currentTimeMillis() + 60000).commit();
        assertFalse(start(1).getBoolean("saved"));
        assertEquals(0, prefs.getLong("pomodoroStart", 0));
    }
    @Test public void rejectsSleepConflict() {
        java.time.ZonedDateTime next = java.time.ZonedDateTime.now().plusMinutes(10);
        prefs.edit().putBoolean("daily", true).putInt("minute", next.getHour() * 60 + next.getMinute())
                .putInt("duration", 1).commit();
        assertFalse(start(1).getBoolean("saved"));
    }
}
