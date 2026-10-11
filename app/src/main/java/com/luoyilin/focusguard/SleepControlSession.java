package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.ZoneId;
import org.json.*;

/** All session writes run inside the synchronized configuration provider. */
final class SleepControlSession {
    static void observe(Context context) {
        Bundle args = new Bundle();
        args.putString("status", status(context));
        try { context.getContentResolver().call(LimitConfigProvider.URI, "sleepObserve", "focus_mode", args); }
        catch (RuntimeException error) { android.util.Log.w("SleepControl", "Cannot save observation", error); }
    }
    private static String status(Context context) {
        return !FocusAccessibilityService.isEnabled(context) ? "accessibility_disabled"
                : !FocusAccessibilityService.isRunning(context) ? "service_unavailable"
                : SleepTemporaryUse.state(context).getLong("remaining") > 0 ? "temporary_use" : SleepControlTimeline.HEALTHY;
    }
    static Bundle call(Context context, SharedPreferences prefs, String operation, Bundle args) {
        Bundle reply = new Bundle();
        long now = ProtectionClock.now(context), elapsed = SystemClock.elapsedRealtime();
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        String health = args == null ? status(context) : args.getString("status", status(context));
        // Keep the old operation fail-closed even if a stale UI still calls it.
        if ("sleepFinish".equals(operation)) { reply.putBoolean("saved", false); return reply; }
        try {
            JSONArray history = new JSONArray(prefs.getString("sleepControlHistory", "[]"));
            String raw = prefs.getString("sleepControlActive", "");
            SleepControlTimeline.Session session = raw.isEmpty() ? null : decode(new JSONObject(raw));
            long start = prefs.getBoolean("daily", false) ? FocusSchedule.activeStart(now,
                    prefs.getInt("minute", 1350), prefs.getInt("duration", 180),
                    prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS), ProtectionClock.zone(context)) : 0;
            long end = start + prefs.getInt("duration", 180) * 60_000L;
            long recordedThrough = prefs.getLong("sleepControlRecordedThrough", 0);
            SharedPreferences.Editor editor = prefs.edit();
            if (session != null) {
                String reason = SleepControlTimeline.settleReason(session, now, start, end, 0);
                if (!reason.isEmpty()) {
                    if ("planned_end".equals(reason)) session.observe(now, elapsed, boot, health);
                    session.finish("clock_changed".equals(reason) ? session.lastAt : now, reason);
                    history.put(encode(session)); recordedThrough = Math.max(recordedThrough, session.start); session = null;
                }
            }
            if (session == null && SleepControlTimeline.shouldStart(start, recordedThrough, 0))
                session = new SleepControlTimeline.Session(start, end);
            if (session != null) {
                // UI and guard can both report health; avoid redundant disk writes.
                if (boot == session.boot && health.equals(session.status) && now >= session.lastAt
                        && now - session.lastAt < 5000 && history.toString().equals(prefs.getString("sleepControlHistory", "[]"))) {
                    reply.putBoolean("saved", true); return reply;
                }
                session.observe(now, elapsed, boot, health);
            }
            String newActive = session == null ? "" : encode(session).toString();
            if (raw.equals(newActive) && history.toString().equals(prefs.getString("sleepControlHistory", "[]"))) {
                reply.putBoolean("saved", true); return reply;
            }
            boolean saved = editor.putString("sleepControlActive", newActive)
                    .putString("sleepControlHistory", history.toString())
                    .putLong("sleepControlRecordedThrough", recordedThrough).commit();
            reply.putBoolean("saved", saved);
        } catch (JSONException error) {
            android.util.Log.w("SleepControl", "Cannot read control history", error);
            reply.putBoolean("saved", false);
        }
        return reply;
    }
    static JSONObject encode(SleepControlTimeline.Session s) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("startedAt", s.start); item.put("plannedEnd", s.plannedEnd); item.put("endedAt", s.endedAt);
        item.put("lastAt", s.lastAt); item.put("lastElapsed", s.lastElapsed); item.put("boot", s.boot);
        item.put("status", s.status); item.put("reason", s.reason);
        item.put("disabledCount", s.disabledCount); item.put("unavailableCount", s.unavailableCount);
        JSONArray segments = new JSONArray();
        for (SleepControlTimeline.Segment segment : s.segments) {
            JSONObject value = new JSONObject(); value.put("from", segment.from); value.put("to", segment.to);
            value.put("status", segment.status); segments.put(value);
        }
        item.put("segments", segments); return item;
    }
    static SleepControlTimeline.Session decode(JSONObject item) throws JSONException {
        long start = item.getLong("startedAt"), end = item.getLong("plannedEnd");
        if (start <= 0 || end <= start) throw new JSONException("Invalid control interval");
        SleepControlTimeline.Session s = new SleepControlTimeline.Session(start, end);
        s.endedAt = item.getLong("endedAt"); s.lastAt = item.getLong("lastAt");
        s.lastElapsed = item.getLong("lastElapsed"); s.boot = item.getInt("boot");
        s.status = item.getString("status"); s.reason = item.getString("reason");
        s.disabledCount = item.getInt("disabledCount"); s.unavailableCount = item.getInt("unavailableCount");
        if (s.lastAt < start || s.lastAt > end || (!s.reason.isEmpty() && (s.endedAt < start || s.endedAt > end)))
            throw new JSONException("Invalid observation time");
        JSONArray segments = item.getJSONArray("segments"); long previous = start;
        for (int i = 0; i < segments.length(); i++) {
            JSONObject v = segments.getJSONObject(i); long from = v.getLong("from"), to = v.getLong("to");
            String state = v.getString("status");
            if (from < previous || to <= from || to > s.lastAt || (!SleepControlTimeline.HEALTHY.equals(state)
                    && !"accessibility_disabled".equals(state) && !"service_unavailable".equals(state) && !"temporary_use".equals(state)))
                throw new JSONException("Invalid observation segment");
            s.segments.add(new SleepControlTimeline.Segment(from, to, state)); previous = to;
        }
        return s;
    }
}
