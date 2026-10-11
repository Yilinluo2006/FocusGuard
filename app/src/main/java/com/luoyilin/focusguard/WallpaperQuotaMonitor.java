package com.luoyilin.focusguard;

import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.util.*;

/** One guard-process poller shared by wallpaper and foreground-service lifetimes. */
final class WallpaperQuotaMonitor {
    private static WallpaperQuotaMonitor instance;
    private final Set<Object> owners = new HashSet<>();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("wallpaper-quota");
    private final Handler worker;
    private volatile boolean closed;
    private View overlay;
    private String overlayPackage;
    private boolean confirming;
    private volatile boolean overlayFailed;
    private boolean fallbackCounting;
    private long lastWall, cursor, lastReadAt;
    private final WallpaperForeground foreground = new WallpaperForeground();
    private String nativePackage;
    private long nativeUsed;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed) return;
            try { check(); }
            catch (Exception failure) { WallpaperMonitorState.publish(context, WallpaperMonitorState.ERROR); pause(); main.post(() -> hide()); }
            finally { if (!closed) worker.postDelayed(this, 750); }
        }
    };
    static synchronized void acquire(Context c, Object owner) {
        if (instance == null) instance = new WallpaperQuotaMonitor(c.getApplicationContext());
        instance.owners.add(owner);
    }
    static synchronized void release(Object owner) {
        if (instance == null) return;
        instance.owners.remove(owner);
        if (instance.owners.isEmpty()) {
            WallpaperQuotaMonitor old = instance; instance = null; old.closed = true;
            old.worker.removeCallbacksAndMessages(null);
            old.worker.post(() -> { old.pause(); WallpaperMonitorState.publish(old.context, WallpaperMonitorState.OFF); old.thread.quitSafely(); }); old.main.post(old::hide);
        }
    }
    private WallpaperQuotaMonitor(Context c) { context = c; thread.start(); worker = new Handler(thread.getLooper()); worker.post(poll); }
    private void pause() { if (fallbackCounting) { UsageBudgetStore.pause(context); fallbackCounting = false; } }
    private void check() throws Exception {
        boolean accessibility = FocusAccessibilityService.isEnabled(context) && FocusAccessibilityService.isRunning(context);
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager lock = context.getSystemService(KeyguardManager.class);
        boolean available = WallpaperGuard.available(context);
        if (accessibility || !available || !power.isInteractive() || lock.isKeyguardLocked()) {
            WallpaperMonitorState.publish(context, !available ? WallpaperMonitorState.OFF
                    : accessibility ? WallpaperMonitorState.STANDBY : WallpaperMonitorState.LOCKED);
            // Accessibility owns the same monotonic counter when healthy; do not pause its session.
            if (accessibility) fallbackCounting = false; else pause();
            foreground.clear(); cursor = 0; main.post(this::hide); return;
        }
        long wall = System.currentTimeMillis();
        if (cursor == 0) { cursor = wall - 60_000; foreground.clear(); }
        else if (wall < lastWall || wall - lastWall > 10_000) { cursor = wall; }
        // A manual clock jump must not substitute an old morning activity for the current one.
        UsageEvents events = context.getSystemService(UsageStatsManager.class).queryEvents(cursor, wall);
        if (events == null) throw new java.io.IOException("Usage events unavailable");
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) foreground.resume(event.getPackageName(), event.getClassName());
            else if ((event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED || event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED)
                     ) foreground.pause(event.getPackageName(), event.getClassName());
            else if (event.getEventType() == UsageEvents.Event.SCREEN_NON_INTERACTIVE) foreground.clear();
        }
        cursor = wall; lastWall = wall;
        Properties limits = LimitSnapshot.read(context);
        String pkg = foreground.packageName;
        int minutes;
        try { minutes = Integer.parseInt(limits.getProperty(pkg == null ? "" : pkg, "0")); }
        catch (NumberFormatException e) { minutes = 0; }
        if (pkg == null || minutes <= 0 || pkg.equals(context.getPackageName())) { WallpaperMonitorState.publish(context, WallpaperMonitorState.RUNNING); pause(); main.post(this::hide); return; }
        if (!pkg.equals(nativePackage) || SystemClock.elapsedRealtime() - lastReadAt >= 5000) {
            ProtectionTimeLedger.Sample time = ProtectionClock.read(context);
            long from = StreakLedger.boundary(time.day, time.zone) + wall - time.millis;
            AppUsageTimeline timeline = AppUsageReader.readRange(context, from, wall, Collections.singleton(pkg));
            UsageSession session = timeline == null ? null : timeline.session(pkg);
            nativeUsed = session == null ? 0 : session.totalAt(wall); nativePackage = pkg; lastReadAt = SystemClock.elapsedRealtime();
        }
        long used = UsageBudgetStore.tick(context, pkg, nativeUsed); fallbackCounting = true;
        // Sleep allows app-only grants for the whitelist; other active focus grants remain blocked.
        boolean blocked = WallpaperForeground.blocked(used, minutes * 60_000L, SharedQuota.remaining(context, pkg, used),
                RestrictionStore.allowance(context, pkg), !DailyTemporaryAccess.allowed(context, pkg));
        WallpaperMonitorState.publish(context, blocked && overlayFailed ? WallpaperMonitorState.ERROR : WallpaperMonitorState.RUNNING);
        if (blocked) {
            RestrictionStore.lockExhausted(context, pkg, minutes);
            final int limit = minutes; main.post(() -> show(pkg, limit));
        } else main.post(this::hide);
    }
    private void show(String pkg, int limit) {
        if (closed || !WallpaperGuard.available(context)
                || (FocusAccessibilityService.isEnabled(context) && FocusAccessibilityService.isRunning(context))) { hide(); return; }
        try {
            if (!pkg.equals(overlayPackage)) hide();
            if (overlay == null) {
                overlay = LayoutInflater.from(Appearance.overlayContext(context)).inflate(R.layout.activity_blocked, null);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                context.getSystemService(WindowManager.class).addView(overlay, params); overlayPackage = pkg;
                overlay.findViewById(R.id.btnKeepRestricted).setOnClickListener(v -> goHome());
            }
            overlayFailed = false;
            if (confirming) return;
            String label;
            try { label = context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception e) { label = "该应用"; }
            TextView message = overlay.findViewById(R.id.tvBlockedMessage);
            message.setText("“" + label + "”本机每日额度已用尽。壁纸守护正在保持限制。\n本机每日额度在 04:00 恢复。");
            Button request = overlay.findViewById(R.id.btnEmergency);
            int left = RestrictionStore.left(context);
            boolean focusing = !DailyTemporaryAccess.allowed(context, pkg);
            request.setEnabled(left > 0 && !focusing && !SharedQuota.enabled(context));
            request.setText(focusing ? "当前不可申请应用临时使用" : QuotaPresentation.appButton(left));
            request.setOnClickListener(v -> {
                confirming = true;
                message.setText(QuotaPresentation.APP_POOL+"\n"+QuotaPresentation.CONTINUOUS_NOTE+"\n仅放行这个应用，不扣睡眠全局临时次数；应用临时次数用完后，额度已耗尽的应用持续限制至次日 04:00。");
                request.setText("确认应用临时使用 2 分钟");
                request.setOnClickListener(confirm -> {
                    try {
                        if (!DailyTemporaryAccess.allowed(context, pkg)) throw new java.io.IOException("当前仅睡眠白名单应用可申请应用临时使用");
                        RestrictionStore.grant(context, pkg); hide();
                    } catch (Exception error) { android.widget.Toast.makeText(context, "暂时无法放行，请检查剩余次数", android.widget.Toast.LENGTH_LONG).show(); hide(); }
                });
            });
        } catch (Exception e) { overlayFailed = true; WallpaperMonitorState.publish(context, WallpaperMonitorState.ERROR); hide(); }
    }
    private void goHome() {
        try { context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException e) { return; }
        // Keep the shield until usage events confirm a real foreground change.
    }
    private void hide() {
        if (overlay != null) { try { context.getSystemService(WindowManager.class).removeViewImmediate(overlay); } catch (RuntimeException ignored) {} }
        overlay = null; overlayPackage = null; confirming = false;
    }
}
