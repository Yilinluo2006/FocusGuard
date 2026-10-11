package com.luoyilin.focusguard;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class CoverageStore {
    private static final String SESSION = java.util.UUID.randomUUID().toString();
    private static final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();
    private static synchronized Properties access(Context c, StreakStore.Edit edit) throws IOException {
        File dir = c.getNoBackupFilesDir();
        try (RandomAccessFile lock = new RandomAccessFile(new File(dir, "coverage.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(dir, "coverage.properties"));
            Properties p = new Properties();
            try (InputStream in = file.openRead()) { p.load(in); }
            catch (FileNotFoundException ignored) { }
            if (edit != null) {
                edit.run(p);
                FileOutputStream out = null;
                try { out = file.startWrite(); p.store(out, null); file.finishWrite(out); }
                catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
            }
            return p;
        }
    }

    static void sample(Context c, boolean eligible) {
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        boolean unlocked = c.getSystemService(PowerManager.class).isInteractive()
                && !c.getSystemService(KeyguardManager.class).isKeyguardLocked();
        boolean protectedNow = eligible && ProtectionWarning.healthy(c);
        ZoneId zone = ZoneId.systemDefault();
        worker.execute(() -> {
            try { access(app, p -> CoverageLedger.sample(p, now, elapsed, boot, SESSION, unlocked, protectedNow, zone)); }
            catch (IOException e) { android.util.Log.w("Coverage", "Sample not saved", e); }
        });
    }

    static void recordUnlockCheck(Context c, long at, boolean healthy) {
        Context app = c.getApplicationContext();
        ZoneId zone = ZoneId.systemDefault();
        worker.execute(() -> {
            try {
                access(app, p -> {
                    long day = StreakLedger.day(at, zone);
                    String prefix = "check." + day + ".";
                    long count = StreakLedger.n(p, prefix + "count");
                    if (count >= 500 || at - StreakLedger.n(p, "check.last") < 2000) return;
                    if (!p.containsKey("check.started")) StreakLedger.put(p, "check.started", at);
                    p.setProperty(prefix + count, at + ":" + (healthy ? "1" : "0"));
                    StreakLedger.put(p, prefix + "count", count + 1);
                    StreakLedger.put(p, "check.last", at);
                    long expired = day - 3;
                    String old = "check." + expired + ".";
                    long oldCount = StreakLedger.n(p, old + "count");
                    for (long i = 0; i < oldCount; i++) p.remove(old + i);
                    p.remove(old + "count");
                });
            } catch (IOException error) {
                android.util.Log.w("Coverage", "Unlock check not saved", error);
            }
        });
    }

    static void readAsync(Context c, boolean fullDetails, Consumer<String> callback) {
        Context app = c.getApplicationContext();
        reader.execute(() -> {
            String result;
            try { result = fullDetails ? details(app) : summary(app); }
            catch (IOException error) { result = "暂时无法读取计时记录"; }
            String message = result;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.accept(message));
        });
    }

    private static String summary(Context c) throws IOException {
        Properties p = access(c, null);
        long now = System.currentTimeMillis();
        long day = StreakLedger.day(now, ZoneId.systemDefault());
        ScreenUsageReader.Snapshot system = ScreenUsageReader.read(c,
                StreakLedger.boundary(day, ZoneId.systemDefault()), now);
        if (system.usage == null) return "亮屏保护 · " + system.unavailableReason;
        CoverageScore score = score(p, day, system.usage);
        if (score.unknownScreenStateMillis > 0 || score.inconsistent) return "亮屏保护 · 待核对";
        if (!score.hasRate()) return "亮屏保护 · 暂无使用";
        return String.format(java.util.Locale.getDefault(), "亮屏保护 %.1f%% · 试运行", score.percent());
    }

    private static CoverageScore score(Properties p, long day, ScreenUsageLedger.Result usage) {
        List<CoverageScore.Check> checks = new ArrayList<>();
        String prefix = "check." + day + ".";
        long count = Math.min(500, StreakLedger.n(p, prefix + "count"));
        for (long i = 0; i < count; i++) {
            String[] fields = p.getProperty(prefix + i, "").split(":");
            if (fields.length != 2) continue;
            try { checks.add(new CoverageScore.Check(Long.parseLong(fields[0]), "1".equals(fields[1]))); }
            catch (NumberFormatException ignored) { }
        }
        long checksStarted = StreakLedger.n(p, "check.started");
        List<Long> sessions = new ArrayList<>();
        if (checksStarted > 0) {
            for (long at : usage.sessionStarts) if (at >= checksStarted - 30_000L) sessions.add(at);
        }
        return CoverageScore.calculate(new ScreenUsageLedger.Result(usage.unlockedMillis,
                        usage.uncertainStateMillis, sessions),
                StreakLedger.n(p, "protected." + day),
                StreakLedger.n(p, "unprotected." + day), checks);
    }

    static String details(Context c) throws IOException {
        Properties p = access(c, null);
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        long day = StreakLedger.day(now, zone);
        ScreenUsageReader.Snapshot system = ScreenUsageReader.read(c, StreakLedger.boundary(day, zone), now);
        StringBuilder trial = new StringBuilder("亮屏保护率 · 试运行\n");
        if (system.usage == null) {
            trial.append(system.unavailableReason);
        } else {
            CoverageScore score = score(p, day, system.usage);
            if (score.unknownScreenStateMillis > 0) {
                trial.append("系统屏幕状态有 ").append(duration(score.unknownScreenStateMillis))
                        .append(" 无法还原，暂不显示百分比");
            } else if (score.inconsistent) {
                trial.append("系统时长与采样记录不一致，暂不显示百分比");
            } else if (score.hasRate()) {
                trial.append(String.format(java.util.Locale.getDefault(), "%.1f%%", score.percent()));
            } else {
                trial.append("今天尚无可计算的解锁亮屏时长");
            }
            trial.append("\n解锁亮屏：").append(duration(score.screenMillis))
                    .append("\n已确认保护：").append(duration(score.protectedMillis))
                    .append("\n已确认未保护：").append(duration(score.unprotectedMillis))
                    .append("\n亮屏待确认：").append(duration(score.unconfirmedMillis));
            if (p.containsKey("check.started")) {
                trial.append("\n解锁检查正常：").append(score.checksHealthy).append(" / ")
                        .append(score.unlocks).append(" 次");
                if (score.unlocks > score.checksReceived) {
                    trial.append("（漏检 ").append(score.unlocks - score.checksReceived).append(" 次）");
                }
            } else {
                trial.append("\n解锁检查：等待首次解锁");
            }
        }
        long yes = StreakLedger.n(p, "protected." + day);
        long no = StreakLedger.n(p, "unprotected." + day);
        long unknown = StreakLedger.n(p, "unknown." + day);
        long last = StreakLedger.n(p, "wall");
        if (last > 0 && now > last + 30_000)
            unknown += now - Math.max(last, StreakLedger.boundary(day, zone));
        long total = yes + no;
        String since = p.containsKey("started") ? java.time.Instant.ofEpochMilli(StreakLedger.n(p, "started"))
                .atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "尚未开始";
        return trial + "\n\n采样记录（仅供排查，不代表全天覆盖率）\n已确认解锁使用：" + duration(total)
                + "\n其中已保护：" + duration(yes) + "\n其中未保护：" + duration(no)
                + "\n待确认间隔：" + duration(unknown)
                + "\n已确认锁屏／熄屏：" + duration(StreakLedger.n(p, "locked." + day))
                + "\n\n记录起点：" + since
                + "\n统计周期：每日 04:00 至次日 04:00。"
                + "\n仅统计安装本版后观测到的时间，不回填之前的使用。"
                + "\n待确认间隔可能包含休眠、重启或状态切换，不等于未保护时间。"
                + "\n亮屏保护率以系统解锁亮屏时长为分母，待确认亮屏时间不会算作已保护。"
                + "\n当前仍为试运行，不用于达标或打卡结算。"
                + CoverageTimeline.details(p, now, zone);
    }

    private static String duration(long ms) {
        long seconds = ms / 1000;
        return seconds / 3600 + "小时" + seconds / 60 % 60 + "分" + seconds % 60 + "秒";
    }
}
