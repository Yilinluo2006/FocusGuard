package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.time.*;
import java.util.Properties;

final class RestrictionStore {
    static java.util.Map<String, Object> backupConfig(Context c) throws IOException {
        return access(c, false, p -> {
            java.util.Map<String, Object> out = new java.util.TreeMap<>();
            for (String key : new String[]{"start", "end", "nextStart", "nextEnd", "windowDay"})
                if (p.containsKey(key)) out.put(key, Long.parseLong(p.getProperty(key)));
            return out;
        });
    }
    static void validateBackupConfig(org.json.JSONObject config) throws Exception {
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList("start", "end", "nextStart", "nextEnd", "windowDay"));
        for (java.util.Iterator<String> keys = config.keys(); keys.hasNext();) {
            String key = keys.next(); long value = config.getLong(key);
            if (!allowed.contains(key) || value < 0 || (!"windowDay".equals(key) && value >= 1440)) throw new IOException("修改窗口数据无效");
        }
        for (String[] pair : new String[][]{{"start", "end"}, {"nextStart", "nextEnd"}})
            if (config.has(pair[0]) && config.has(pair[1]) && config.getLong(pair[0]) == config.getLong(pair[1]))
                throw new IOException("开始和结束时间不能相同");
    }
    static void restoreMissingConfig(Context c, org.json.JSONObject config) throws Exception {
        validateBackupConfig(config);
        access(c, true, p -> {
            for (java.util.Iterator<String> keys = config.keys(); keys.hasNext();) {
                String key = keys.next(); if (!p.containsKey(key)) p.setProperty(key, Long.toString(config.optLong(key)));
            }
            return null;
        });
    }
    interface Work<T> { T run(Properties p) throws IOException; }
    private static long number(Properties p, String key, long fallback) {
        return Long.parseLong(p.getProperty(key, Long.toString(fallback)));
    }
    // File lock serializes UI and guard access; AtomicFile preserves the last committed state.
    private static synchronized <T> T access(Context c, boolean write, Work<T> work) throws IOException {
        File dir = c.getNoBackupFilesDir();
        try (RandomAccessFile lock = new RandomAccessFile(new File(dir, "restriction.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(dir, "restriction.properties"));
            Properties p = new Properties();
            try (InputStream in = file.openRead()) { p.load(in); }
            catch (FileNotFoundException ignored) { }
            ProtectionTimeLedger.Sample clock = ProtectionClock.read(c);
            long day = Instant.ofEpochMilli(clock.millis).atZone(clock.zone).toLocalDate().toEpochDay();
            if (clock.verified && day >= number(p, "windowDay", Long.MAX_VALUE)) {
                p.setProperty("start", p.getProperty("nextStart", "360"));
                p.setProperty("end", p.getProperty("nextEnd", "420"));
                p.remove("windowDay");
            }
            T result = work.run(p);
            if (write) {
                FileOutputStream out = null;
                try {
                    out = file.startWrite();
                    p.store(out, null);
                    file.finishWrite(out);
                } catch (IOException e) {
                    if (out != null) file.failWrite(out);
                    throw e;
                }
            }
            return result;
        }
    }
    static boolean canRelax(Context c) {
        try {
            return access(c, false, p -> {
                return ProtectionTimeLedger.canRelax(ProtectionClock.read(c),
                        (int) number(p, "start", 360), (int) number(p, "end", 420));
            });
        } catch (Exception e) { return false; }
    }
    static String windowText(Context c) {
        try {
            return access(c, false, p -> "每日 " + time(number(p, "start", 360))
                    + "–" + time(number(p, "end", 420))
                    + (p.containsKey("windowDay") ? "\n待生效：" + time(number(p, "nextStart", 360))
                    + "–" + time(number(p, "nextEnd", 420)) + "（次日）" : ""));
        } catch (Exception e) { return "规则读取失败，暂不可放宽限制"; }
    }
    private static String time(long minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
    static void setWindow(Context c, int start, int end) throws IOException {
        if (start < 0 || end < 0 || start >= 1440 || end >= 1440 || start == end)
            throw new IOException("开始和结束时间不能相同");
        ProtectionTimeLedger.Sample clock = ProtectionClock.read(c);
        if (!clock.verified || !clock.aligned) throw new IOException("时间待校验，请恢复正确的日期、时间和时区后重试");
        access(c, true, p -> {
            p.setProperty("nextStart", "" + start);
            p.setProperty("nextEnd", "" + end);
            p.setProperty("windowDay", "" + Instant.ofEpochMilli(clock.millis).atZone(clock.zone).toLocalDate().plusDays(1).toEpochDay());
            return null;
        });
    }
    private static int boot(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    private static long quotaDay(Context c) {
        return ProtectionClock.day(c);
    }
    private static long remaining(Context c, Properties p) {
        return RestrictionRules.remaining(ProtectionClock.now(c), SystemClock.elapsedRealtime(),
                boot(c), number(p, "until", 0), number(p, "elapsedUntil", 0),
                (int) number(p, "boot", -2));
    }
    private static int used(Context c, Properties p, long day) {
        if (!ProtectionClock.read(c).verified) return (int) number(p, "used", 0);
        return RestrictionRules.quotaUsed(day, number(p, "quotaDay", Long.MIN_VALUE),
                (int) number(p, "used", 0));
    }
    static int left(Context c) throws IOException {
        return access(c, false, p -> Math.max(0, 5 - used(c, p, quotaDay(c))));
    }
    static Properties complianceGrants(Context c) throws IOException {
        return access(c, true, p -> { Properties result = new Properties();
            if (!p.containsKey("bonusMigrated")) {
                long remaining = remaining(c, p); String pkg = p.getProperty("active", "");
                if (remaining > 0 && !pkg.isEmpty()) {
                    long now = ProtectionClock.now(c);
                    ComplianceLedger.grant(p, pkg, now, now + remaining, ProtectionClock.zone(c));
                }
                p.setProperty("bonusMigrated", "true");
            }
            for (String key : p.stringPropertyNames()) if (key.startsWith("bonus.")) result.setProperty(key, p.getProperty(key));
            return result;
        });
    }
    static long allowance(Context c, String pkg) {
        try {
            return access(c, false, p -> {
                return pkg.equals(p.getProperty("active")) ? remaining(c, p) : 0L;
            });
        } catch (Exception e) { return 0; }
    }
    // Exhausted rules remain effective until 04:00, even if a synced setting removes the app.
    static void lockExhausted(Context c, String pkg, int minutes) throws IOException {
        if (minutes <= 0) return;
        access(c, true, p -> {
            long day = quotaDay(c);
            if (used(c, p, day) >= 5 && (!pkg.equals(p.getProperty("active")) || remaining(c, p) <= 0)) {
                p.setProperty("lockedDay." + pkg, Long.toString(day));
                p.setProperty("lockedMinutes." + pkg, Integer.toString(minutes));
            }
            return null;
        });
    }
    static Properties lockedLimits(Context c) throws IOException {
        return access(c, false, p -> {
            Properties locked = new Properties();
            long day = quotaDay(c);
            for (String key : p.stringPropertyNames()) if (key.startsWith("lockedDay.")
                    && (!ProtectionClock.read(c).verified || day <= number(p, key, Long.MIN_VALUE))) {
                String pkg = key.substring("lockedDay.".length());
                locked.setProperty(pkg, p.getProperty("lockedMinutes." + pkg, "30"));
            }
            return locked;
        });
    }
    static void grant(Context c, String pkg) throws IOException {
        int minutes = Integer.parseInt(LimitSnapshot.read(c).getProperty(pkg, "0"));
        access(c, true, p -> {
            if (remaining(c, p) > 0) throw new IOException("已有应用临时使用正在连续计时，请结束后再申请");
            long day = quotaDay(c);
            int used = used(c, p, day);
            int next = RestrictionRules.consume(used, remaining(c, p));
            p.setProperty("quotaDay", "" + day);
            p.setProperty("used", "" + next);
            if (next == 5 && minutes > 0) {
                p.setProperty("lockedDay." + pkg, Long.toString(day));
                p.setProperty("lockedMinutes." + pkg, Integer.toString(minutes));
            }
            p.setProperty("active", pkg);
            p.setProperty("until", "" + (ProtectionClock.now(c) + 120_000L));
            p.setProperty("elapsedUntil", "" + (SystemClock.elapsedRealtime() + 120_000L));
            p.setProperty("boot", "" + boot(c));
            long grantedAt = ProtectionClock.now(c);
            p.setProperty("bonusMigrated", "true");
            ComplianceLedger.grant(p, pkg, grantedAt, grantedAt + 120_000L, ProtectionClock.zone(c));
            return null;
        });
    }
}
