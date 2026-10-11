package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.ZoneId;
import org.json.*;

/** Provider-serialized local grants: configuration sync never decides whether time is available. */
final class SleepTemporaryUse {
    private static final String P = "sleepTemporary";
    static Bundle state(Context c) { return callProvider(c, "sleepTemporaryState", null); }
    static Bundle request(Context c) { return callProvider(c, "sleepTemporaryRequest", null); }
    static Bundle callProvider(Context c, String operation, Bundle args) {
        Bundle result = c.getContentResolver().call(LimitConfigProvider.URI, operation, "focus_mode", args);
        if (result == null) throw new IllegalStateException("无法读取睡眠全局临时使用状态");
        return result;
    }
    private static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    private static long start(Context c, SharedPreferences p, long now) {
        return p.getBoolean("daily", false) ? FocusSchedule.activeStart(now, p.getInt("minute", 1350),
                p.getInt("duration", 180), p.getInt(FocusSchedule.WEEKDAYS_KEY, 127), ProtectionClock.zone(c)) : 0;
    }
    private static void migrate(Context c, SharedPreferences p, long start) {
        if (start <= 0 || p.getLong(P + "LocalStart", 0) == start) return;
        int used = 0;
        // Local history, rather than the other device's account balance, proves consumed grants.
        try {
            JSONArray history = new JSONArray(p.getString(P + "History", "[]"));
            for (int i = 0; i < history.length(); i++) if (history.getJSONObject(i).optLong("start") == start) used++;
        } catch (JSONException e) { throw new IllegalStateException("睡眠全局临时使用记录无法核实，请检查存储", e); }
        if (p.getInt(P + "LocalVersion", 0) < 2
                && p.getLong(P + "Day", Long.MIN_VALUE) == StreakLedger.day(start, ProtectionClock.zone(c)))
            used = Math.max(used, p.getInt(P + "Used", 0));
        if (p.getLong(P + "GrantStart", 0) == start) used = Math.max(used, 1);
        // A clock rollback to an earlier occurrence must not replenish a consumed budget.
        if (start < p.getLong(P + "LocalStart", 0)) used = Math.max(used, p.getInt(P + "Used", 0));
        SharedPreferences.Editor edit = p.edit().putLong(P + "LocalStart", start)
                .putInt(P + "LocalVersion", 2).putInt(P + "Used", Math.min(5, Math.max(0, used)));
        for (String key : new String[]{"SharedStart", "RemoteStart", "Left", "PendingId", "PendingStart", "PendingEnd", "PendingBoot", "Message"}) edit.remove(P + key);
        if (!edit.commit()) throw new IllegalStateException("睡眠全局临时使用记录保存失败，请重试");
    }
    private static Bundle state(Context c, SharedPreferences p) {
        long now = ProtectionClock.now(c), start = start(c, p, now);
        long end = start > 0 ? start + p.getInt("duration", 180) * 60_000L : 0;
        migrate(c, p, start);
        long sampledElapsed = SystemClock.elapsedRealtime();
        long remaining = SleepTemporaryPolicy.remaining(start, end, p.getLong(P + "GrantStart", 0), now,
                sampledElapsed, boot(c), p.getLong(P + "Until", 0),
                p.getLong(P + "ElapsedUntil", 0), p.getInt(P + "Boot", -2));
        int left = start > 0 ? Math.max(0, 5 - p.getInt(P + "Used", 0)) : 5;
        Bundle b = new Bundle();
        b.putBoolean("sleeping", start > 0); b.putLong("start", start); b.putLong("end", end);
        b.putBoolean("shared", false); b.putBoolean("pending", false); b.putString("message", "");
        b.putLong("remaining", remaining); b.putLong("sampledElapsed", sampledElapsed);
        b.putInt("left", left); return b;
    }
    static Bundle call(Context c, SharedPreferences p, String operation, Bundle args) {
        Bundle s = state(c, p);
        if ("sleepTemporaryState".equals(operation)) return s;
        // Late replies from the removed shared workflow must not change counts or revive time.
        if (!"sleepTemporaryRequest".equals(operation)) return failure("睡眠全局临时次数按本机计算");
        if (!s.getBoolean("sleeping")) return failure("当前不在睡眠模式中");
        if (s.getLong("remaining") > 0) return failure("睡眠全局临时使用正在计时");
        if (s.getInt("left") <= 0) return failure("本机本次睡前计划的睡眠全局临时次数已用完，请等待计划结束");
        int boot = boot(c); if (boot < 0) return failure("无法确认设备状态，暂不能申请");
        long now = ProtectionClock.now(c), elapsed = SystemClock.elapsedRealtime();
        long duration = Math.min(120_000, s.getLong("end") - now);
        if (duration <= 0) return failure("睡眠计划已结束");
        SharedPreferences.Editor edit = p.edit().putInt(P + "Used", 6 - s.getInt("left"))
                .putLong(P + "GrantStart", s.getLong("start")).putLong(P + "Until", now + duration)
                .putLong(P + "ElapsedUntil", elapsed + duration).putInt(P + "Boot", boot);
        if (!history(p, edit, s.getLong("start"), now, now + duration)) return failure("睡眠全局临时使用记录保存失败，请重试");
        if (!edit.commit()) return failure("保存失败，请重试");
        s = state(c, p); s.putBoolean("saved", true); return s;
    }
    private static boolean history(SharedPreferences p, SharedPreferences.Editor edit, long start, long from, long until) {
        try {
            JSONArray old = new JSONArray(p.getString(P + "History", "[]")), kept = new JSONArray();
            long oldest = from - 14 * 86_400_000L;
            for (int i = 0; i < old.length(); i++) if (old.getJSONObject(i).optLong("until") >= oldest) kept.put(old.getJSONObject(i));
            if (until > from) kept.put(new JSONObject().put("start", start).put("from", from).put("until", until));
            edit.putString(P + "History", kept.toString()); return true;
        } catch (JSONException e) { return false; }
    }
    private static Bundle failure(String message) {
        Bundle b = new Bundle(); b.putBoolean("saved", false); b.putString("message", message); return b;
    }
    static String button(Bundle state) {
        if (state.getLong("remaining") > 0) return "睡眠全局临时使用 · 剩余 " + RemainingTimeFormat.format(state.getLong("remaining"));
        return state.getInt("left") == 0 ? "睡眠全局临时次数已用完" : "睡眠全局临时使用 2 分钟 · 本次剩余 " + state.getInt("left") + " 次";
    }
    static String explanation(Bundle state) {
        return "本机本次睡前计划的睡眠全局临时次数剩余 " + state.getInt("left") + " 次。每台设备每次计划最多 5 次，与应用临时次数分别计数，离线也可申请。"
                + "\n\n每次 2 分钟连续计时，退出 App、切换应用或锁屏也不暂停，到时自动恢复睡眠管控；应用自身的每日额度继续生效。"
                + "\n\n第五次到时后不能再临时退出，返回桌面仍受管控，直到本次计划结束。";
    }
    static boolean canRequest(Bundle state) {
        return state.getBoolean("sleeping") && state.getLong("remaining") <= 0 && state.getInt("left") > 0;
    }
}
