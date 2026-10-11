package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import java.time.*;
import java.util.*;
import org.json.*;

/** UI-process preferences; unrelated to guard limits and sleep state. */
final class LocationCheckInStore {
    static final class Target {
        String id, name;
        double lat, lon, accuracy;
        int radius, from, to;
        long effectiveFrom;
    }
    static final class Record {
        final String name;
        final long at;
        final double distance;
        Record(String name, long at, double distance) { this.name = name; this.at = at; this.distance = distance; }
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("location_checkin", Context.MODE_PRIVATE); }
    static Target active(Context context, long now) throws JSONException {
        SharedPreferences p = prefs(context);
        Target pending = decode(p.getString("pending", ""));
        return pending != null && now >= pending.effectiveFrom ? pending : decode(p.getString("target", ""));
    }
    static Target pending(Context context) throws JSONException { return decode(prefs(context).getString("pending", "")); }
    static synchronized boolean save(Context context, String name, Location fix, int radius, int from, int to) throws JSONException {
        long now = System.currentTimeMillis();
        Target previous = active(context, now);
        JSONObject target = new JSONObject();
        target.put("id", UUID.randomUUID().toString()); target.put("name", name);
        target.put("lat", fix.getLatitude()); target.put("lon", fix.getLongitude()); target.put("accuracy", fix.getAccuracy());
        target.put("radius", radius); target.put("from", from); target.put("to", to);
        target.put("effectiveFrom", FocusStatistics.boundary(FocusStatistics.day(now, ZoneId.systemDefault()).plusDays(1), ZoneId.systemDefault()));
        // Keep today's target; edits apply from the next 04:00 boundary.
        SharedPreferences.Editor editor = prefs(context).edit().putString("pending", target.toString());
        if (previous != null) editor.putString("target", encode(previous).toString());
        return editor.commit();
    }
    static synchronized String checkIn(Context context, Target requested, Location fix) throws JSONException {
        long now = System.currentTimeMillis(); ZoneId zone = ZoneId.systemDefault();
        Target current = active(context, now);
        if (current == null || !current.id.equals(requested.id)) return "地点计划已变化，请重新定位";
        LocalTime time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime();
        if (!LocationCheckInPolicy.inWindow(time.getHour() * 60 + time.getMinute(), current.from, current.to)) return "当前不在签到时间内";
        if (signedToday(context, now)) return "今日已经签到";
        String error = LocationCheckInPolicy.sampleError(fix.hasAccuracy() ? fix.getAccuracy() : Double.NaN,
                (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1_000_000,
                fix.isFromMockProvider(), current.radius);
        if (!error.isEmpty()) return error;
        double distance = LocationCheckInPolicy.distance(current.lat, current.lon, fix.getLatitude(), fix.getLongitude());
        error = LocationCheckInPolicy.rangeError(distance, fix.getAccuracy(), current.accuracy, current.radius);
        if (!error.isEmpty()) return error;
        SharedPreferences p = prefs(context);
        JSONArray history = new JSONArray(p.getString("history", "[]")); JSONObject record = new JSONObject();
        record.put("name", current.name); record.put("targetId", current.id); record.put("at", now);
        record.put("distance", distance); record.put("accuracy", fix.getAccuracy()); record.put("radius", current.radius);
        history.put(record);
        return p.edit().putString("history", history.toString()).commit() ? "" : "记录保存失败，请重试";
    }
    static boolean signedToday(Context context, long now) throws JSONException {
        LocalDate today = FocusStatistics.day(now, ZoneId.systemDefault());
        for (Record record : history(context)) if (FocusStatistics.day(record.at, ZoneId.systemDefault()).equals(today)) return true;
        return false;
    }
    static List<Record> history(Context context) throws JSONException {
        JSONArray values = new JSONArray(prefs(context).getString("history", "[]")); List<Record> records = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i); long at = item.getLong("at"); double distance = item.getDouble("distance");
            if (at <= 0 || !Double.isFinite(distance) || distance < 0) throw new JSONException("Invalid arrival record");
            records.add(new Record(item.getString("name"), at, distance));
        }
        records.sort((a, b) -> Long.compare(b.at, a.at)); return records;
    }
    private static Target decode(String raw) throws JSONException {
        if (raw.isEmpty()) return null;
        JSONObject item = new JSONObject(raw); Target t = new Target();
        t.id = item.getString("id"); t.name = item.getString("name"); t.lat = item.getDouble("lat"); t.lon = item.getDouble("lon");
        t.accuracy = item.getDouble("accuracy"); t.radius = item.getInt("radius"); t.from = item.getInt("from"); t.to = item.getInt("to");
        t.effectiveFrom = item.getLong("effectiveFrom");
        if (t.name.trim().isEmpty() || !Double.isFinite(t.lat) || Math.abs(t.lat) > 90 || !Double.isFinite(t.lon) || Math.abs(t.lon) > 180
                || t.accuracy <= 0 || !Double.isFinite(t.accuracy) || t.radius < 100 || t.radius > 1000 || t.from < 0 || t.to > 1440 || t.from >= t.to)
            throw new JSONException("Invalid arrival target");
        return t;
    }
    private static JSONObject encode(Target t) throws JSONException {
        JSONObject item = new JSONObject(); item.put("id", t.id); item.put("name", t.name); item.put("lat", t.lat); item.put("lon", t.lon);
        item.put("accuracy", t.accuracy); item.put("radius", t.radius); item.put("from", t.from); item.put("to", t.to); item.put("effectiveFrom", t.effectiveFrom); return item;
    }
}
