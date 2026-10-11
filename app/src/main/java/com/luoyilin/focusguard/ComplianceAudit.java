package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reconciles system foreground records off the UI thread, outside all provider/file locks. */
final class ComplianceAudit {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static final Object AUDIT_LOCK = new Object();
    private static long lastRequest;
    private static String controlledPackage = "", controlKey = "";
    private static long controlAt, controlFrom;
    // A foreground app covered by our blocking screen is not actual user access.
    static synchronized void controlling(Context context, String pkg) {
        long now = ProtectionClock.now(context); String next = pkg == null ? "" : pkg;
        if (next.equals(controlledPackage) && now - controlAt < 10_000 && now >= controlAt) return;
        if (!controlledPackage.isEmpty() && now > controlAt && now - controlAt <= SleepControlTimeline.MAX_GAP) {
            String previous = controlledPackage, key = controlKey; long from = controlFrom;
            Context app = context.getApplicationContext();
            WORK.execute(() -> {
                try { String value = new JSONObject().put("pkg", previous).put("from", from).put("until", now).toString();
                    StreakStore.access(app, p -> p.setProperty(key, value));
                } catch (Exception e) { android.util.Log.w("Compliance", "Control evidence not saved", e); }
            });
        }
        if (!next.equals(controlledPackage) || now < controlAt || now - controlAt > SleepControlTimeline.MAX_GAP) {
            controlFrom = now; controlKey = "c.cover." + UUID.randomUUID();
        }
        controlledPackage = next; controlAt = now;
    }
    static synchronized void request(Context context, boolean immediate) {
        long now = ProtectionClock.now(context);
        if (!immediate && now >= lastRequest && now - lastRequest < 60_000) return;
        if (!QUEUED.compareAndSet(false, true)) return;
        lastRequest = now; Context app = context.getApplicationContext();
        WORK.execute(() -> {
            try { refresh(app); StreakWidget.updateAll(app); }
            catch (Exception error) { android.util.Log.w("Compliance", "Day evidence unavailable", error); }
            finally { QUEUED.set(false); }
        });
    }

    static void focusStarted(Context c, String id, long start, long end, Set<String> allowed) {
        Context app = c.getApplicationContext(); Set<String> copy = new HashSet<>(allowed);
        WORK.execute(() -> {
            try {
                JSONObject job = new JSONObject().put("start", start).put("end", end)
                        .put("allowed", new JSONArray(copy)).put("reason", "");
                StreakStore.access(app, p -> {
                    ComplianceLedger.activate(p, start, ProtectionClock.zone(app));
                    p.setProperty("c.focus." + id, job.toString());
                    ComplianceLedger.rule(p, StreakLedger.day(start, ProtectionClock.zone(app)), "focus:" + id,
                            ComplianceLedger.PENDING, true, "");
                });
                request(app, true);
            } catch (Exception error) { android.util.Log.w("Compliance", "Focus evidence not saved", error); }
        });
    }
    static void focusEnded(Context c, String id, long start, long endedAt, String reason) {
        Context app = c.getApplicationContext();
        WORK.execute(() -> {
            try {
                StreakStore.access(app, p -> {
                    String key = "c.focus." + id, raw = p.getProperty(key);
                    if (raw == null) return; // Imported histories are not local execution evidence.
                    try { JSONObject job = new JSONObject(raw).put("end", endedAt).put("reason", reason);
                        p.setProperty(key, job.toString());
                    } catch (JSONException error) { throw new IllegalStateException(error); }
                });
                request(app, true);
            } catch (Exception error) { android.util.Log.w("Compliance", "Focus outcome not saved", error); }
        });
    }

    static void refresh(Context c) throws Exception {
        synchronized (AUDIT_LOCK) { refreshLocked(c); }
    }
    private static void refreshLocked(Context c) throws Exception {
        ProtectionTimeLedger.Sample clock = ProtectionClock.read(c);
        if (!clock.verified || !ProtectionClock.nativeReliable(c)) return; // Unreliable timestamps cannot prove a violation.
        long now = clock.millis; ZoneId zone = clock.zone;
        long today = StreakLedger.day(now, zone);
        // Settle running sessions first. Provider calls never occur while holding the streak lock.
        new FocusModeStore(c).timed(); new FocusModeStore(c).pomodoro(); SleepControlSession.observe(c);
        Map<String, ?> focus = FocusPreferences.read(c, "focus_mode");
        Properties limits = LimitSnapshot.read(c), bonuses = RestrictionStore.complianceGrants(c);
        Properties p = StreakStore.snapshot(c);
        if (ProtectionWarning.healthy(c)) p.setProperty("c.live." + today, "true");
        long first = StreakLedger.n(p, "complianceFrom");
        for (String prefix : new String[]{"manual", "pomodoro"}) {
            Object value = focus.get(prefix + "Start");
            long start = value instanceof Number ? ((Number)value).longValue() : 0;
            String id = string(focus, prefix + "Id", "");
            if (start > 0 && !id.isEmpty() && !p.containsKey("c.focus." + id)) {
                long from = Math.max(start, StreakLedger.n(p, "complianceAt"));
                long end = "pomodoro".equals(prefix) ? start + PomodoroTimeline.duration(integer(focus, "pomodoroRounds", 1))
                        : focus.get("manualEnd") instanceof Number ? ((Number)focus.get("manualEnd")).longValue() : now;
                p.setProperty("c.focus." + id, new JSONObject().put("start", from).put("end", Math.max(from, end))
                        .put("allowed", new JSONArray(allowed(focus))).put("reason", "").toString());
            }
        }
        boolean permission = usagePermission(c);
        Map<Long, AppUsageTimeline> timelines = new HashMap<>();
        Set<Long> auditDays = new HashSet<>();
        for (long d = Math.max(first, today - 13); d <= today; d++) {
            if (d < today && StreakLedger.n(p, "c.through." + d) >= StreakLedger.boundary(d + 1, zone)
                    && !"待确认".equals(p.getProperty("c.local." + d, "待确认"))) continue;
            auditDays.add(d);
            if (permission) timelines.put(d, AppUsageReader.readRange(c, StreakLedger.boundary(d, zone),
                    Math.min(now, StreakLedger.boundary(d + 1, zone)), null));
        }
        AppUsageTimeline current = timelines.get(today);
        Map<String, Long> used = current == null ? null : current.totalsAt(now);
        for (String pkg : limits.stringPropertyNames()) {
            if (c.getPackageManager().getLaunchIntentForPackage(pkg) == null) continue;
            String key = "c.limit." + today + "." + pkg;
            if (!p.containsKey(key)) {
                boolean carried = p.containsKey("c.limit." + (today - 1) + "." + pkg);
                long baseline = carried ? 0 : used == null ? -1 : used.getOrDefault(pkg, 0L);
                p.setProperty(key, new JSONObject().put("quota", Long.parseLong(limits.getProperty(pkg)) * 60_000L)
                        .put("baseline", baseline).put("since", carried ? StreakLedger.boundary(today, zone) : now).toString());
            }
        }
        // Freeze each scheduled occurrence, including those that finish after the next 04:00.
        if (Boolean.TRUE.equals(focus.get("daily"))) {
            long from = StreakLedger.boundary(today, zone), to = StreakLedger.boundary(today + 1, zone);
            int minute = integer(focus, "minute", 1350), mask = integer(focus, FocusSchedule.WEEKDAYS_KEY, 127);
            long start = FocusSchedule.nextStart(from - 1, minute, mask, zone);
            if (start >= StreakLedger.n(p, "complianceAt") && start < to) {
                String key = "c.sleep." + start;
                if (!p.containsKey(key)) p.setProperty(key, new JSONObject().put("start", start)
                        .put("end", start + integer(focus, "duration", 180) * 60_000L)
                        .put("allowed", new JSONArray(allowed(focus))).put("reason", "").toString());
            }
        }
        JSONArray sleepHistory = new JSONArray(string(focus, "sleepControlHistory", "[]"));
        JSONObject activeSleep = string(focus, "sleepControlActive", "").isEmpty() ? null
                : new JSONObject(string(focus, "sleepControlActive", ""));
        Set<String> exempt = exemptions(c);
        Set<String> removals = new HashSet<>();
        for (String key : new HashSet<>(p.stringPropertyNames())) {
            if (key.startsWith("c.limit.")) {
                String tail = key.substring(8); int dot = tail.indexOf('.');
                long d = Long.parseLong(tail.substring(0, dot)); String pkg = tail.substring(dot + 1);
                if (d < today - 13 || d > today) continue;
                if (!auditDays.contains(d)) continue;
                JSONObject rule = new JSONObject(p.getProperty(key));
                AppUsageTimeline timeline = timelines.get(d);
                String id = "app:" + pkg;
                if (timeline == null) { ComplianceLedger.rule(p, d, id, ComplianceLedger.PENDING, false, ""); continue; }
                long end = Math.min(now, StreakLedger.boundary(d + 1, zone)), millis = timeline.totalsAt(end).getOrDefault(pkg, 0L);
                long baseline = rule.getLong("baseline");
                if (baseline < 0) {
                    // Permission restoration gives a baseline, not proof of the unavailable interval.
                    rule.put("baseline", millis).put("unknown", true); p.setProperty(key, rule.toString());
                    ComplianceLedger.rule(p, d, id, ComplianceLedger.PENDING, false, ""); continue;
                }
                UsageSession session = timeline.session(pkg);
                if (session != null) { millis = 0;
                    List<long[]> covered = covered(p, pkg);
                    for (long[] interval : session.intervalsAt(end)) millis += unexcused(interval[0], interval[1], covered);
                }
                boolean executed = millis > baseline;
                boolean violation = ComplianceLedger.overrun(millis, rule.getLong("quota"), baseline,
                        StreakLedger.n(bonuses, "bonus." + d + "." + pkg));
                ComplianceLedger.rule(p, d, id, violation ? ComplianceLedger.FAILED
                        : rule.optBoolean("unknown") || executed && !"true".equals(p.getProperty("c.live." + d))
                        ? ComplianceLedger.PENDING : ComplianceLedger.OK,
                        executed, violation ? "受限应用用时超过额度与已领取临时时间" : "");
            } else if (key.startsWith("c.sleep.") || key.startsWith("c.focus.")) {
                boolean sleeping = key.startsWith("c.sleep.");
                JSONObject job = new JSONObject(p.getProperty(key));
                long start = job.getLong("start"), end = job.getLong("end");
                long d = StreakLedger.day(start, zone);
                if (d < today - 13 || d > today) continue;
                if (!auditDays.contains(d)) continue;
                String id = (sleeping ? "sleep:" : "focus:") + key.substring(8);
                String reason = job.optString("reason");
                boolean observed = !reason.isEmpty(), protectedObserved = !sleeping;
                if (sleeping) {
                    for (int i = 0; i < sleepHistory.length(); i++) {
                        JSONObject item = sleepHistory.getJSONObject(i);
                        if (item.optLong("startedAt") == start) { reason = item.optString("reason"); observed = true;
                            JSONArray segments = item.optJSONArray("segments");
                            if (segments != null) for (int s = 0; s < segments.length(); s++) {
                                JSONObject segment = segments.getJSONObject(s);
                                protectedObserved |= SleepControlTimeline.HEALTHY.equals(segment.optString("status"))
                                        || "temporary_use".equals(segment.optString("status"));
                            }
                            break;
                        }
                    }
                    observed |= activeSleep != null && activeSleep.optLong("startedAt") == start;
                    // A plan edited before it starts has not been executed and creates no obligation.
                    if (now < start && !sameFuturePlan(focus, start, end, zone)) {
                        removals.add(key); removals.add("c.rule." + d + "." + id); continue;
                    }
                }
                boolean finished = sleeping ? "planned_end".equals(reason)
                        : "completed".equals(reason) || "sleep_takeover".equals(reason);
                boolean interrupted = "user_interrupted".equals(reason) || "plan_changed".equals(reason);
                Set<String> permitted = new HashSet<>(exempt); JSONArray apps = job.getJSONArray("allowed");
                for (int i = 0; i < apps.length(); i++) permitted.add(apps.getString(i));
                List<long[]> temporary = sleeping ? temporary(focus, start) : Collections.emptyList();
                boolean evidenceMissing = false, violation = false;
                long through = Math.min(now, end);
                for (long scanDay = d; scanDay <= StreakLedger.day(Math.max(start, through - 1), zone); scanDay++) {
                    AppUsageTimeline timeline = timelines.get(scanDay);
                    if (timeline == null) { evidenceMissing = true; continue; }
                    for (Map.Entry<String, List<long[]>> entry : timeline.intervalsAt(Math.min(through, StreakLedger.boundary(scanDay + 1, zone))).entrySet()) {
                        String pkg = entry.getKey();
                        if (permitted.contains(pkg) || c.getPackageManager().getLaunchIntentForPackage(pkg) == null) continue;
                        for (long[] interval : entry.getValue()) {
                            long left = Math.max(start, interval[0]), right = Math.min(through, interval[1]);
                            List<long[]> excused = new ArrayList<>(temporary); excused.addAll(covered(p, pkg));
                            if (unexcused(left, right, excused) > ComplianceLedger.OVERUSE_GRACE) violation = true;
                        }
                    }
                }
                ComplianceLedger.rule(p, d, id, interrupted || violation ? ComplianceLedger.FAILED
                        : finished && observed && protectedObserved && !evidenceMissing ? ComplianceLedger.OK : ComplianceLedger.PENDING,
                        observed, interrupted ? "提前结束专注或睡前计划" : violation ? "管控期间使用了未允许的应用" : "");
            }
        }
        for (String key : removals) p.remove(key);
        for (long d = Math.max(first, today - 13); d <= today; d++) {
            if (timelines.get(d) != null) {
                p.setProperty("c.checked." + d, "true");
                StreakLedger.put(p, "c.through." + d, Math.min(now, StreakLedger.boundary(d + 1, zone)));
            }
        }
        // Apply evidence only. Never replace heartbeat, concurrent breaks or peer confirmations.
        StreakStore.access(c, live -> {
            for (String key : removals) live.remove(key);
            for (String key : p.stringPropertyNames()) if (key.startsWith("c.limit.") || key.startsWith("c.sleep.")
                    || key.startsWith("c.rule.") || key.startsWith("c.checked.") || key.startsWith("c.through.")
                    || key.startsWith("c.active.") || key.startsWith("c.failure.") || key.startsWith("c.live.")) {
                if (key.startsWith("c.rule.") && ComplianceLedger.FAILED.equals(live.getProperty(key))) continue;
                live.setProperty(key, p.getProperty(key));
            }
            for (String key : p.stringPropertyNames()) if (key.startsWith("c.focus.") && !live.containsKey(key)) live.setProperty(key, p.getProperty(key));
            ComplianceLedger.settle(live, now, zone);
        });
    }

    static boolean usagePermission(Context c) {
        AppOpsManager manager = c.getSystemService(AppOpsManager.class);
        return manager != null && manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static long unexcused(long from, long to, List<long[]> grants) {
        return ComplianceLedger.unexcused(from, to, grants);
    }
    private static List<long[]> covered(Properties p, String pkg) throws JSONException {
        List<long[]> result = new ArrayList<>();
        for (String key : p.stringPropertyNames()) if (key.startsWith("c.cover.")) {
            JSONObject item = new JSONObject(p.getProperty(key));
            if (pkg.equals(item.optString("pkg"))) result.add(new long[]{item.getLong("from"), item.getLong("until")});
        }
        return result;
    }
    private static List<long[]> temporary(Map<String, ?> focus, long start) throws JSONException {
        JSONArray history = new JSONArray(string(focus, "sleepTemporaryHistory", "[]")); List<long[]> values = new ArrayList<>();
        for (int i = 0; i < history.length(); i++) { JSONObject item = history.getJSONObject(i);
            if (item.getLong("start") == start) values.add(new long[]{item.getLong("from"), item.getLong("until")}); }
        return values;
    }
    private static Set<String> exemptions(Context c) {
        Set<String> result = new HashSet<>(Arrays.asList(c.getPackageName(), "com.android.settings", "com.android.systemui",
                "com.android.phone", "com.oplus.safecenter", "com.android.incallui", "com.android.dialer",
                "com.google.android.dialer", "com.oplus.securitypermission", "com.android.server.telecom",
                "com.android.emergency", "com.android.permissioncontroller", "com.google.android.permissioncontroller"));
        String keyboard = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (keyboard != null && keyboard.contains("/")) result.add(keyboard.substring(0, keyboard.indexOf('/')));
        android.telecom.TelecomManager telecom = c.getSystemService(android.telecom.TelecomManager.class);
        if (telecom != null && telecom.getDefaultDialerPackage() != null) result.add(telecom.getDefaultDialerPackage());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo item : c.getPackageManager().queryIntentActivities(home, 0)) if (item.activityInfo != null) result.add(item.activityInfo.packageName);
        return result;
    }
    private static boolean sameFuturePlan(Map<String, ?> f, long start, long end, ZoneId zone) {
        return Boolean.TRUE.equals(f.get("daily")) && end - start == integer(f, "duration", 180) * 60_000L
                && FocusSchedule.nextStart(start - 1, integer(f, "minute", 1350), integer(f, FocusSchedule.WEEKDAYS_KEY, 127), zone) == start;
    }
    @SuppressWarnings("unchecked") private static Set<String> allowed(Map<String, ?> p) { return p.get("allowed") instanceof Set ? new HashSet<>((Set<String>)p.get("allowed")) : new HashSet<>(); }
    private static int integer(Map<String, ?> p, String key, int fallback) { return p.get(key) instanceof Number ? ((Number)p.get(key)).intValue() : fallback; }
    private static String string(Map<String, ?> p, String key, String fallback) { return p.get(key) instanceof String ? (String)p.get(key) : fallback; }
}
