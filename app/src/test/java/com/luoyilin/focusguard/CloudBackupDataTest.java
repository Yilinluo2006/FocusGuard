package com.luoyilin.focusguard;

import org.json.*;
import org.junit.Test;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class CloudBackupDataTest {
    private JSONObject empty() throws Exception {
        return new JSONObject("{\"schemaVersion\":1,\"limits\":[],\"tasks\":[],\"focus\":{\"focusHistory\":[],\"pomodoroHistory\":[],\"sleepControlHistory\":[]},\"wallpaper\":0,\"appearance\":2,\"restriction\":{},\"streak\":{}}");
    }
    private void rejected(JSONObject value) throws Exception {
        try { CloudBackupData.validate(value.toString()); fail("Invalid backup accepted"); }
        catch (IllegalArgumentException | JSONException | java.io.IOException expected) { }
    }
    @Test public void acceptsAllThreeTaskModesAndConfiguredQuota() throws Exception {
        JSONObject value = empty();
        value.put("tasks", new JSONArray("[{\"id\":\"a\",\"name\":\"学习\",\"mode\":\"countup\",\"value\":0},{\"id\":\"b\",\"name\":\"阅读\",\"mode\":\"countdown\",\"value\":25},{\"id\":\"c\",\"name\":\"工作\",\"mode\":\"pomodoro\",\"value\":4}]"));
        value.put("limits", new JSONArray("[{\"packageName\":\"com.example.app\",\"minutes\":30}]"));
        assertEquals(3, CloudBackupData.validate(value.toString()).getJSONArray("tasks").length());
    }
    @Test public void acceptsLegacyDailyBackupAndRetainsNewWeekdays() throws Exception {
        JSONObject value = empty();
        value.getJSONObject("focus").put("daily", true);
        assertFalse(CloudBackupData.validate(value.toString()).getJSONObject("focus").has(FocusSchedule.WEEKDAYS_KEY));
        value.getJSONObject("focus").put(FocusSchedule.WEEKDAYS_KEY, 31);
        assertEquals(31, CloudBackupData.validate(value.toString()).getJSONObject("focus").getInt(FocusSchedule.WEEKDAYS_KEY));
    }
    @Test public void rejectsInvalidWeekdaysAndEnabledEmptySelection() throws Exception {
        JSONObject value = empty();
        for (Object mask : new Object[]{-1, 128, 1.5, "31"}) {
            value.getJSONObject("focus").put(FocusSchedule.WEEKDAYS_KEY, mask);
            rejected(value);
        }
        value.getJSONObject("focus").put(FocusSchedule.WEEKDAYS_KEY, 0).put("daily", true);
        rejected(value);
        value.getJSONObject("focus").put("daily", false);
        CloudBackupData.validate(value.toString());
    }
    @Test public void rejectsActiveTimersAndEmergencyAllowance() throws Exception {
        JSONObject value = empty(); value.getJSONObject("focus").put("manualEnd", 12345); rejected(value);
        value = empty(); value.getJSONObject("focus").put("sleepControlActive", "{}"); rejected(value);
        value = empty(); value.getJSONObject("restriction").put("active", "com.example.app"); rejected(value);
    }
    @Test public void rejectsInvalidQuotaDuplicateTaskAndFutureSchema() throws Exception {
        JSONObject value = empty(); value.put("schemaVersion", 2); rejected(value);
        value = empty(); value.put("limits", new JSONArray("[{\"packageName\":\"com.example.app\",\"minutes\":0}]")); rejected(value);
        value = empty(); value.put("tasks", new JSONArray("[{\"id\":\"same\",\"name\":\"a\",\"mode\":\"countup\",\"value\":0},{\"id\":\"same\",\"name\":\"b\",\"mode\":\"countup\",\"value\":0}]")); rejected(value);
    }
    @Test public void rejectsTodayProtectionTimeButAcceptsPastDay() throws Exception {
        long today = StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault());
        JSONObject value = empty(); value.getJSONObject("streak").put("ms." + (today - 1), "7200000");
        CloudBackupData.validate(value.toString());
        value.getJSONObject("streak").put("ms." + today, "7200000"); rejected(value);
    }
    @Test public void restoreMergePreservesLocalChangesAndIsIdempotent() throws Exception {
        JSONArray local = new JSONArray("[{\"id\":\"same\",\"name\":\"new local name\"}]");
        JSONArray remote = new JSONArray("[{\"id\":\"same\",\"name\":\"old cloud name\"},{\"id\":\"missing\",\"name\":\"import\"}]");
        JSONArray once = CloudBackupData.merge(local, remote, "id");
        assertEquals(2, once.length()); assertEquals("new local name", once.getJSONObject(0).getString("name"));
        assertEquals(once.toString(), CloudBackupData.merge(once, remote, "id").toString());
        assertEquals(1, local.length());
    }
    @Test public void mergesSleepHistoryByNumericStartWithoutDuplication() throws Exception {
        JSONArray local = new JSONArray("[{\"startedAt\":100,\"reason\":\"local\"}]");
        JSONArray remote = new JSONArray("[{\"startedAt\":100,\"reason\":\"remote\"},{\"startedAt\":200}]");
        JSONArray result = CloudBackupData.merge(local, remote, "startedAt");
        assertEquals(2, result.length()); assertEquals("local", result.getJSONObject(0).getString("reason"));
    }
    @Test public void rejectsIncompleteFocusRecord() throws Exception {
        JSONObject value = empty();
        value.getJSONObject("focus").put("focusHistory", new JSONArray("[{\"id\":\"a\",\"startedAt\":20,\"endedAt\":10,\"elapsedMillis\":0}]")); rejected(value);
    }
    @Test public void fingerprintMatchesKnownSha256AndHandlesUnicode() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", BackupFingerprint.sha256("abc"));
        assertEquals(64, BackupFingerprint.sha256("专注").length());
        assertNotEquals(BackupFingerprint.sha256("专注"), BackupFingerprint.sha256("睡眠"));
    }
}
