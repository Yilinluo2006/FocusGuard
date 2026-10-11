package com.luoyilin.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.Locale;

final class FocusModeController {
    private static final String SYSTEM_CONFIRMATION_PACKAGE = "com.oplus.safecenter";
    private final FocusAccessibilityService service;
    private final FocusModeStore store;
    private final SleepReminder sleepReminder;
    private final RemainingTimeOverlay sleepTemporaryOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String foreground = "";
    private boolean privacyControls;
    private final java.util.Set<String> launchers = new java.util.HashSet<>();
    private View overlay;
    private android.content.Context ui;
    private boolean overlayDark;
    private int overlayWallpaper;
    private int overlayType;
    private TextView timer;
    private FocusProgressDialView progressDial;
    private TextView phaseTitle;
    private TextView roundStatus;
    private Button finishButton;
    private String lastPhase = "";
    private TextView dailyLimitNotice;
    private String dailyLimitedApp;
    private long dailyLimitNoticeUntil;
    private long dailyLimitNoticeDay = Long.MIN_VALUE;
    private boolean confirming;
    private long recordedEnd;
    private boolean wasActive;
    private long lastWindowAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    FocusModeController(FocusAccessibilityService service) {
        this.service = service;
        store = new FocusModeStore(service);
        sleepTemporaryOverlay = new RemainingTimeOverlay(service);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (android.content.pm.ResolveInfo app : service.getPackageManager().queryIntentActivities(home, 0))
            launchers.add(app.activityInfo.packageName);
        android.content.pm.ResolveInfo homeApp = service.getPackageManager().resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (homeApp != null) launchers.add(homeApp.activityInfo.packageName);
        sleepReminder = new SleepReminder(service, store);
        handler.post(tick);
    }

    boolean onWindow(AccessibilityEvent event) {
        String pkg = event.getPackageName().toString();
        CharSequence type = event.getClassName();
        String keyboard = Settings.Secure.getString(service.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        // Transient system/IME windows do not grant access to the app underneath.
        if (pkg.equals("com.android.systemui") || (keyboard != null && keyboard.startsWith(pkg + "/"))) {
            refresh();
            return active();
        }
        // Our own overlay events must not replace the underlying foreground package.
        if (!pkg.equals(service.getPackageName()) || (type != null && (type.toString().startsWith(pkg + ".")
                || type.toString().equals(PageMonitoringActivity.class.getName())))) {
            foreground = pkg;
            privacyControls = pkg.equals(service.getPackageName()) && type != null
                    && type.toString().equals(PageMonitoringActivity.class.getName());
            lastWindowAt = android.os.SystemClock.elapsedRealtime();
        }
        refresh();
        return active();
    }

    boolean active() { return SleepTemporaryPolicy.controlling(store.end(ProtectionClock.now(service)), SleepTemporaryUse.state(service).getLong("remaining")); }

    boolean whitelisted(String pkg) { return pkg != null && SleepTemporaryPolicy.permitsApp(
            store.scheduledStart(ProtectionClock.now(service)) > 0, launchers.contains(pkg), store.allowed().contains(pkg), false); }

    String whitelistedForeground() {
        return active() && whitelisted(foreground) ? foreground : null;
    }

    private boolean permitted(String pkg) {
        if (privacyControls && pkg.equals(service.getPackageName())) return true;
        TelecomManager telecom = service.getSystemService(TelecomManager.class);
        if (telecom != null && pkg.equals(telecom.getDefaultDialerPackage())) return true;
        String keyboard = Settings.Secure.getString(service.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean essential = pkg.equals("com.android.settings")
                || pkg.equals("com.android.packageinstaller") || pkg.equals("com.google.android.packageinstaller")
                || pkg.equals("com.oplus.appinstaller") || pkg.equals("com.oplus.safecenter") || pkg.equals("com.coloros.safecenter")
                || pkg.equals("com.oplus.appdetail")
                || pkg.equals("com.android.systemui") || pkg.equals("com.android.phone")
                || pkg.equals(SYSTEM_CONFIRMATION_PACKAGE)
                || pkg.equals("com.android.incallui") || pkg.equals("com.android.dialer")
                || pkg.equals("com.google.android.dialer")
                || pkg.equals("com.oplus.securitypermission")
                || pkg.equals("com.android.server.telecom") || pkg.equals("com.android.emergency")
                || pkg.equals("com.android.permissioncontroller") || pkg.equals("com.google.android.permissioncontroller")
                || (keyboard != null && keyboard.startsWith(pkg + "/"));
        return SleepTemporaryPolicy.permitsApp(store.scheduledStart(ProtectionClock.now(service)) > 0,
                launchers.contains(pkg), store.allowed().contains(pkg), essential);
    }

    private void refresh() {
        Appearance.refreshSleep(service);
        long now = ProtectionClock.now(service);
        sleepReminder.refresh(now);
        long end = store.end(now);
        android.os.Bundle pomodoro = store.pomodoro();
        android.os.Bundle timed = store.timed();
        android.os.Bundle sleep = SleepTemporaryUse.state(service);
        sleepTemporaryOverlay.showSleepTemporary(sleep);
        boolean controlling = SleepTemporaryPolicy.controlling(end, sleep.getLong("remaining"));
        if (controlling != wasActive) {
            wasActive = controlling;
            service.onFocusModeChanged(wasActive, foreground);
        }
        if (end > 0 && sleep.getLong("remaining") > 0) {
            reconcileForeground(now);
            hide(); return;
        }
        if (end == 0) {
            if (recordedEnd > 0 && "completed".equals(store.prefs.getString("pomodoroLastReason", ""))
                    && !lastPhase.isEmpty())
                Toast.makeText(service, "本组番茄钟已完成，做得很好！", Toast.LENGTH_LONG).show();
            lastPhase = "";
            if (recordedEnd > 0 && now >= recordedEnd) {
                store.prefs.edit().putLong("lastCompletedAt", recordedEnd).apply();
            }
            recordedEnd = 0;
            dailyLimitedApp = null;
            dailyLimitNoticeDay = Long.MIN_VALUE;
            hide(); return;
        }
        recordedEnd = end;
        reconcileForeground(now);
        PowerManager power = service.getSystemService(PowerManager.class);
        KeyguardManager keyguard = service.getSystemService(KeyguardManager.class);
        if (!power.isInteractive() || keyguard.isKeyguardLocked() || permitted(foreground)) {
            hide(); return;
        }
        if (overlay != null && (overlayDark != Appearance.dark(service)
                || overlayType != GuardWindow.type(service)
                || overlayWallpaper != AppWallpaper.selectedResource(service))) hide();
        if (overlay == null) show();
        ComplianceAudit.controlling(service, overlay == null ? null : foreground);
        boolean strict = pomodoro.getBoolean("active");
        boolean countUp = !strict && timed.getBoolean("active") && timed.getBoolean("countUp");
        String phase = strict ? pomodoro.getInt("round") + ":" + pomodoro.getBoolean("resting") : "";
        if (strict && !lastPhase.isEmpty() && !lastPhase.equals(phase))
            Toast.makeText(service, pomodoro.getBoolean("resting") ? "本轮完成，休息 5 分钟" : "休息结束，开始下一轮专注", Toast.LENGTH_LONG).show();
        lastPhase = phase;
        if (phaseTitle != null) {
            String name = strict ? pomodoro.getString("name", "") : timed.getString("name", "");
            String title = strict ? (pomodoro.getBoolean("resting") ? "休息一下" : "正在专注")
                    : store.scheduledStart(now) > 0 ? "睡眠时间" : "正在专注";
            phaseTitle.setText(name.isEmpty() || "睡眠时间".equals(title) ? title : name + " · " + title);
        }
        if (roundStatus != null) {
            roundStatus.setVisibility(strict ? View.VISIBLE : View.GONE);
            roundStatus.setText(strict ? "第 " + pomodoro.getInt("round") + " / " + pomodoro.getInt("rounds") + " 轮" : "");
        }
        if (finishButton != null && !confirming) {
            finishButton.setText(sleep.getBoolean("sleeping") ? SleepTemporaryUse.button(sleep) : strict ? "紧急结束" : countUp ? "完成专注" : "提前结束");
            finishButton.setEnabled(!sleep.getBoolean("sleeping") || SleepTemporaryUse.canRequest(sleep));
        }
        long millis = strict ? pomodoro.getLong("remaining") : countUp ? timed.getLong("elapsed") : end - now;
        long seconds = Math.max(0, (millis + (countUp ? 0 : 999)) / 1000);
        if (timer != null) timer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60));
        if (progressDial != null) {
            long total = strict ? (pomodoro.getBoolean("resting") ? PomodoroTimeline.REST : PomodoroTimeline.WORK)
                    : timed.getBoolean("active") ? timed.getLong("total")
                    : store.scheduledStart(now) > 0 ? store.prefs.getInt("duration", 180) * 60_000L : 0;
            long remaining = strict ? pomodoro.getLong("remaining") : timed.getBoolean("active")
                    ? timed.getLong("remaining") : Math.max(0, end - now);
            progressDial.setProgress(countUp ? (millis % 60_000L) / 60_000f
                    : total > 0 ? 1f - remaining / (float) total : 0f, countUp);
        }
        if (dailyLimitNotice != null) {
            boolean showNotice = dailyLimitedApp != null
                    && android.os.SystemClock.elapsedRealtime() < dailyLimitNoticeUntil
                    && dailyLimitNoticeDay == ProtectionClock.day(service);
            dailyLimitNotice.setVisibility(showNotice ? View.VISIBLE : View.GONE);
            if (showNotice) dailyLimitNotice.setText(dailyLimitedApp
                    + (DeviceSyncManager.strictQuota(service) ? " 共用每日额度已用完" : " 本机每日额度已用完"));
        }
    }

    void notifyDailyLimit(String appName) {
        dailyLimitedApp = appName;
        dailyLimitNoticeUntil = android.os.SystemClock.elapsedRealtime() + 10_000L;
        dailyLimitNoticeDay = ProtectionClock.day(service);
    }

    private void reconcileForeground(long now) {
        if (!ProtectionClock.nativeReliable(service)) return;
        if (android.os.SystemClock.elapsedRealtime() - lastWindowAt < 700) return;
        // OEM dialogs can omit or reorder window callbacks. Reconcile with activity lifecycle events.
        UsageStatsManager usage = service.getSystemService(UsageStatsManager.class);
        if (usage == null) return;
        UsageEvents events = usage.queryEvents(now - 60_000L, now);
        if (events == null) return;
        UsageEvents.Event event = new UsageEvents.Event();
        String candidate = null;
        long candidateAt = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                candidate = event.getPackageName();
                candidateAt = event.getTimeStamp();
            } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND
                    && event.getPackageName() != null && event.getPackageName().equals(candidate)) {
                candidate = "";
                candidateAt = event.getTimeStamp();
            }
        }
        long lastWindowWallAt = now - (android.os.SystemClock.elapsedRealtime() - lastWindowAt);
        // An earlier usage event must not put the cover back over the OEM confirmation.
        if (candidate != null && (!SYSTEM_CONFIRMATION_PACKAGE.equals(foreground)
                || candidateAt > lastWindowWallAt)) foreground = candidate;
    }

    private void show() {
        ui = Appearance.overlayContext(service);
        overlayDark = Appearance.dark(service);
        LinearLayout body = new LinearLayout(ui);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int padding = Math.round(24 * service.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        body.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        TextView title = new TextView(ui);
        phaseTitle = title;
        title.setText(store.scheduledStart(ProtectionClock.now(service)) > 0 ? "睡眠时间" : "正在专注");
        title.setTextColor(ui.getColor(R.color.text_primary)); title.setTextSize(20);
        body.addView(title);
        roundStatus = new TextView(ui);
        roundStatus.setTextSize(16); roundStatus.setTextColor(ui.getColor(R.color.text_secondary));
        body.addView(roundStatus);
        progressDial = new FocusProgressDialView(ui);
        int dialSize = Math.round(224 * service.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(dialSize, dialSize);
        dialParams.topMargin = Math.round(16 * service.getResources().getDisplayMetrics().density);
        body.addView(progressDial, dialParams);
        timer = new TextView(ui); timer.setTextSize(22); timer.setTextColor(ui.getColor(R.color.success_text));
        timer.setTypeface(android.graphics.Typeface.MONOSPACE);
        timer.setGravity(android.view.Gravity.CENTER);
        timer.setMaxLines(1);
        timer.setPadding(0, 0, 0, Math.round(12 * service.getResources().getDisplayMetrics().density));
        body.addView(timer, new LinearLayout.LayoutParams(-1, -2));
        dailyLimitNotice = new TextView(ui);
        dailyLimitNotice.setTextSize(16);
        dailyLimitNotice.setTextColor(ui.getColor(R.color.danger_text));
        dailyLimitNotice.setGravity(android.view.Gravity.CENTER);
        dailyLimitNotice.setVisibility(View.GONE);
        body.addView(dailyLimitNotice, new LinearLayout.LayoutParams(-1, -2));
        body.addView(new View(ui), new LinearLayout.LayoutParams(1, 0, 1));

        FocusToolbox toolbox = new FocusToolbox(ui, store.allowed(), this::launch,
                () -> handler.post(this::refresh));
        LinearLayout actions = new LinearLayout(ui);
        actions.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.Drawable toolsIcon = ui.getDrawable(R.drawable.ic_focus_toolbox).mutate();
        toolsIcon.setTint(ui.getColor(R.color.accent_text));
        actions.addView(FocusToolbox.iconAction(ui, toolsIcon, "工具箱", () -> toolbox.setVisibility(View.VISIBLE)),
                new LinearLayout.LayoutParams(padding * 4, padding * 4));
        android.graphics.drawable.Drawable phoneIcon = ui.getDrawable(R.drawable.ic_focus_phone).mutate();
        phoneIcon.setTint(ui.getColor(R.color.accent_text));
        actions.addView(FocusToolbox.iconAction(ui, phoneIcon, "电话", () -> {
            Intent dial = new Intent(Intent.ACTION_DIAL);
            android.content.pm.ResolveInfo resolved = service.getPackageManager().resolveActivity(dial, 0);
            launch(resolved == null ? "com.android.phone" : resolved.activityInfo.packageName, dial);
        }), new LinearLayout.LayoutParams(padding * 4, padding * 4));
        body.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        TextView warning = new TextView(ui);
        warning.setTextSize(15);
        warning.setTextColor(ui.getColor(R.color.danger_text));
        warning.setPadding(0, padding, 0, padding);
        warning.setVisibility(View.GONE);
        body.addView(warning);
        Button end = addButton(body, "提前结束", () -> {});
        finishButton = end;
        Button cancel = addButton(body, "继续专注", () -> {
            confirming = false; end.setText("提前结束");
        });
        cancel.setVisibility(View.GONE);
        end.setOnClickListener(v -> {
            android.os.Bundle sleep = SleepTemporaryUse.state(service);
            if (sleep.getBoolean("sleeping")) {
                if (!confirming) {
                    confirming = true;
                    warning.setText(SleepTemporaryUse.explanation(sleep));
                    warning.setVisibility(View.VISIBLE);
                    end.setText("确认睡眠全局临时使用 2 分钟");
                    cancel.setText("继续睡眠"); cancel.setVisibility(View.VISIBLE);
                } else {
                    android.os.Bundle result = SleepTemporaryUse.request(service);
                    confirming = false; warning.setVisibility(View.GONE); cancel.setVisibility(View.GONE);
                    Toast.makeText(service, result.getBoolean("saved")
                            ? "睡眠全局临时使用已开始，2 分钟后恢复睡眠管控"
                            : result.getString("message", "申请失败，请重试"), Toast.LENGTH_LONG).show();
                    refresh();
                }
                return;
            }
            if (!confirming) {
                confirming = true;
                boolean countUp = store.timed().getBoolean("countUp");
                warning.setText(countUp ? "结束并保存本次正计时记录？"
                        : StreakConfirmation.message(service, "提前结束当前专注。"));
                warning.setVisibility(View.VISIBLE);
                end.setText(countUp ? "确认完成" : "确认中断并结束"); cancel.setVisibility(View.VISIBLE);
            } else if (store.finishEarly(ProtectionClock.now(service))) {
                recordedEnd = 0; hide(); refresh();
            } else Toast.makeText(service, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        });
        cancel.setOnClickListener(v -> {
            confirming = false; end.setText("提前结束"); cancel.setVisibility(View.GONE);
            warning.setVisibility(View.GONE);
            refresh();
        });
        ScrollView scroll = new ScrollView(ui); scroll.setFillViewport(true); scroll.addView(body);
        FrameLayout content = new FrameLayout(ui);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        content.addView(toolbox, new FrameLayout.LayoutParams(-1, -1));
        overlayWallpaper = AppWallpaper.selectedResource(service);
        View screen = AppWallpaper.wrap(ui, content, overlayWallpaper);
        overlayType = GuardWindow.type(service);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
        // Keep the system's default inset fitting: this overlay must stay below system bars.
        params.setTitle("FocusGuard focus session");
        try {
            GuardWindow.manager(service).addView(screen, params); overlay = screen;
        } catch (RuntimeException error) {
            android.util.Log.e("FocusMode", "Cannot display focus screen", error); timer = null; progressDial = null;
        }
    }
    private Button addButton(LinearLayout body, String text, Runnable action) {
        Button b = new Button(ui); b.setText(text); b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.focus_action_background);
        b.setTextSize(14);
        b.setTextColor(ui.getColor(R.color.text_primary));
        b.setMinHeight(Math.round(48 * ui.getResources().getDisplayMetrics().density));
        b.setOnClickListener(v -> action.run());
        body.addView(b, new LinearLayout.LayoutParams(-2, -2)); return b;
    }
    private void launch(String pkg, Intent intent) {
        try {
            if (!Intent.ACTION_DIAL.equals(intent.getAction()) && whitelisted(pkg)
                    && service.dailyLimitReached(pkg)) {
                if (DailyTemporaryAccess.allowed(service, pkg)) {
                    service.requestDailyTemporaryFromToolbox(pkg);
                    return;
                }
                Toast.makeText(service, DeviceSyncManager.strictQuota(service)
                        ? "该应用共用每日额度已用完，04:00 恢复"
                        : "该应用本机每日额度已用完，04:00 恢复", Toast.LENGTH_SHORT).show();
                return;
            }
            service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            foreground = pkg; lastWindowAt = android.os.SystemClock.elapsedRealtime(); hide();
        } catch (RuntimeException error) {
            Toast.makeText(service, "无法打开此应用", Toast.LENGTH_SHORT).show();
        }
    }
    private void hide() {
        ComplianceAudit.controlling(service, null);
        if (overlay != null) {
            try { service.getSystemService(WindowManager.class).removeViewImmediate(overlay); }
            catch (RuntimeException ignored) { }
        }
        overlay = null; timer = null; progressDial = null; phaseTitle = null; roundStatus = null; finishButton = null;
        dailyLimitNotice = null; confirming = false;
    }
    void close() { handler.removeCallbacks(tick); sleepReminder.close(); sleepTemporaryOverlay.hide(); hide(); }
}
