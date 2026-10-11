package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.channels.FileLock;
import java.time.ZoneId;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class StreakStore {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    interface Edit { void run(Properties p); }
    static synchronized Properties access(Context c, Edit edit) throws IOException {
        File dir = c.getNoBackupFilesDir();
        try (RandomAccessFile lock = new RandomAccessFile(new File(dir, "streak.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(dir, "streak.properties"));
            Properties p = new Properties();
            try (InputStream in = file.openRead()) { p.load(in); }
            catch (FileNotFoundException ignored) { }
            edit.run(p);
            FileOutputStream out = null;
            try {
                out = file.startWrite(); p.store(out, null); file.finishWrite(out);
            } catch (IOException e) {
                if (out != null) file.failWrite(out);
                throw e;
            }
            return p;
        }
    }
    static Properties snapshot(Context c) throws IOException {
        return access(c, p -> {
            ProtectionTimeLedger.Sample time = ProtectionClock.read(c);
            p.setProperty("guardDay", Long.toString(time.day));
            ComplianceLedger.activate(p, time.millis, time.zone);
            if (time.verified) StreakLedger.settle(p, time.millis, time.zone);
        });
    }
    static void pulse(Context c, String session, boolean eligible) {
        ComplianceAudit.request(c, false);
        long now = ProtectionClock.now(c), elapsed = SystemClock.elapsedRealtime();
        worker.execute(() -> {
            try {
                access(c, p -> {
                    ComplianceLedger.activate(p, now, ProtectionClock.zone(c));
                    if (eligible) p.setProperty("c.live." + ProtectionClock.day(c), "true");
                    if (ProtectionClock.read(c).verified) StreakLedger.pulse(p, now, elapsed, session, eligible, ProtectionClock.zone(c));
                });
                // Widget redraws at most once per minute, even though accounting is finer.
                if (now / 60_000 != lastWidgetMinute) {
                    lastWidgetMinute = now / 60_000;
                    StreakWidget.updateAll(c);
                }
            } catch (Exception e) { Log.e("FocusStreak", "Heartbeat not saved", e); }
        });
    }
    private static long lastWidgetMinute;
    static boolean breakToday(Context c, String reason) {
        try {
            access(c, p -> {
                ComplianceLedger.activate(p, ProtectionClock.now(c), ProtectionClock.zone(c));
                StreakLedger.breakToday(p, ProtectionClock.now(c), ProtectionClock.zone(c), reason);
            });
            StreakWidget.updateAll(c);
            return true;
        } catch (Exception e) { Log.e("FocusStreak", "Break not saved", e); return false; }
    }
    static long current(Properties p) {
        long day = Long.parseLong(p.getProperty("guardDay", Long.toString(StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault()))));
        return p.containsKey("broken." + day) || p.containsKey("c.failure." + day) || "未完成".equals(p.getProperty("c.remote." + day))
                ? 0 : StreakLedger.n(p, "streak");
    }
    static String today(Properties p) {
        long d = Long.parseLong(p.getProperty("guardDay", Long.toString(StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault()))));
        if (p.containsKey("broken." + d)) return "今日已中断：" + p.getProperty("broken." + d);
        if (p.containsKey("c.failure." + d)) return "保护需检查\n" + p.getProperty("c.failure." + d);
        if ("未完成".equals(p.getProperty("c.remote." + d))) return "保护需检查\n另一台设备今日有未守约记录";
        if (!p.containsKey("c.checked." + d)) return "今日待确认\n正在检查使用记录";
        String apps = "", sleep = "", focus = "";
        for (String key : p.stringPropertyNames()) if (key.startsWith("c.rule." + d + ".")) {
            String state = p.getProperty(key);
            if (key.contains(".app:")) apps = ComplianceLedger.PENDING.equals(state) ? "应用用时待确认" : "应用额度正常";
            if (key.contains(".sleep:")) sleep = ComplianceLedger.PENDING.equals(state) ? "睡前计划待完成" : "睡前计划已完成";
            if (key.contains(".focus:")) focus = ComplianceLedger.PENDING.equals(state) ? "专注进行中" : "专注已完成";
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        if (!apps.isEmpty()) labels.add(apps); if (!sleep.isEmpty()) labels.add(sleep); if (!focus.isEmpty()) labels.add(focus);
        String summary = labels.isEmpty() ? "使用应用限制、专注或睡前计划即可参与" : String.join(" · ", labels);
        return ("true".equals(p.getProperty("c.active." + d)) || !sleep.isEmpty() ? "今日守约中" : "今天还未开始") + "\n" + summary;
    }
    static String today(Context c, Properties p) {
        return ProtectionWarning.healthy(c) && ComplianceAudit.usagePermission(c) ? today(p) : "保护需检查\n点击检查必要设置 · 今日记录待确认";
    }
    static String week(Properties p) {
        long d = Long.parseLong(p.getProperty("guardDay", Long.toString(StreakLedger.day(System.currentTimeMillis(), ZoneId.systemDefault()))));
        StringBuilder s = new StringBuilder();
        for (long i = d - 6; i <= d; i++) {
            java.time.LocalDate date = java.time.LocalDate.ofEpochDay(i);
            s.append(date.getDayOfMonth()).append("日")
                    .append(i == d ? "·" : "完成".equals(p.getProperty("result." + i)) ? "✓" : "—");
            if (i != d) s.append("  ");
        }
        return s.toString();
    }
}
