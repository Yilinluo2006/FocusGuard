package com.luoyilin.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.graphics.PixelFormat;
import android.widget.TextView;
import java.util.Properties;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

public class FocusAccessibilityService extends AccessibilityService {

    private static final String PREF_NAME = "focus_guard_prefs";
    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    private static final String KEY_LIMIT_PREFIX = "limit_minutes_";
    // 这些 key 必须和 AppManageActivity 保持一致，才能读取用户选择的限制应用

    private static final long CHECK_INTERVAL_MILLIS = 2000;
    // 每隔 2 秒检查一次当前应用是否超时，测试时反应更明显

    private static final long HEALTH_INTERVAL_MILLIS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String streakSession = java.util.UUID.randomUUID().toString();
    private String currentPackageName;
    private long lastAppWindowAt;
    private Properties latestLimits = new Properties();
    private View blockingOverlay;
    private long blockedQuotaDay = Long.MIN_VALUE;
    private RemainingTimeOverlay remainingTimeOverlay;
    private RecordedPageController recordedPages;
    private boolean blockInProgress;
    private final Runnable finishBlockTransition = () -> {
        blockInProgress = false;
        if (currentPackageName != null) checkCurrentAppUsage();
    };
    private FocusModeController focusMode;
    private final Runnable recoveryCheck = new Runnable() {
        @Override public void run() {
            try {
                boolean focusing = focusMode != null && focusMode.active();
                if (focusing && focusMode.whitelistedForeground() == null) return;
                android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
                android.app.KeyguardManager keyguard = getSystemService(android.app.KeyguardManager.class);
                if (!power.isInteractive() || keyguard.isKeyguardLocked()) {
                    stopMonitoring(); return;
                }
                if (currentPackageName != null || blockInProgress) return;
                UsageStatsManager manager = getSystemService(UsageStatsManager.class);
                long now = System.currentTimeMillis();
                UsageEvents events = manager.queryEvents(now - 60_000L, now);
                if (events == null) return;
                UsageEvents.Event event = new UsageEvents.Event();
                String candidate = null;
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (isForegroundEvent(event)) candidate = event.getPackageName();
                    else if (isBackgroundEvent(event) && event.getPackageName() != null
                            && event.getPackageName().equals(candidate)) candidate = null;
                }
                if (candidate != null && isLimitedPackage(candidate)
                        && FocusLimitPolicy.monitorDailyLimit(focusing,
                                focusMode != null && focusMode.whitelisted(candidate))) {
                    currentPackageName = candidate;
                    checkCurrentAppUsage();
                }
            } catch (RuntimeException error) {
                Log.e("FocusAccessibility", "Foreground recovery failed", error);
            } finally {
                handler.postDelayed(this, CHECK_INTERVAL_MILLIS);
            }
        }
    };
    private final Runnable confirmAppExit = () -> {
        if (currentPackageName == null) return;
        handler.removeCallbacks(this.usageCheckTask);
        checkCurrentAppUsage();
    };
    private final Runnable hideOverlayTask = () -> {
        hideOverlay();
        blockInProgress = false;
        // A new launch during the transition must be checked, not silently exempted.
        if (currentPackageName != null) {
            checkCurrentAppUsage();
        }
    };
    // 保存当前正在前台的应用包名

    private final Runnable usageCheckTask = new Runnable() {
        @Override
        public void run() {
            checkCurrentAppUsage();
            // 定时检查当前应用是否达到限制时间
        }
    };

    private final Runnable serviceHealthTask = new Runnable() {
        @Override
        public void run() {
            ProtectionStateStore.touchAccessibility(FocusAccessibilityService.this);
            SleepControlSession.observe(FocusAccessibilityService.this);
            if (blockingOverlay != null && blockedQuotaDay != ProtectionClock.day(FocusAccessibilityService.this)) hideOverlay();
            boolean eligible = new FocusModeStore(FocusAccessibilityService.this).prefs.getBoolean("daily", false)
                    || (focusMode != null && focusMode.active());
            try { eligible |= !LimitSnapshot.read(FocusAccessibilityService.this).isEmpty(); }
            catch (java.io.IOException ignored) { }
            StreakStore.pulse(FocusAccessibilityService.this, streakSession,
                    eligible && isEnabled(FocusAccessibilityService.this));
            CoverageStore.sample(FocusAccessibilityService.this, eligible);
            handler.postDelayed(this, HEALTH_INTERVAL_MILLIS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler.removeCallbacks(hideOverlayTask);
        handler.removeCallbacks(recoveryCheck);
        handler.removeCallbacks(finishBlockTransition);
        stopMonitoring();
        hideOverlay();
        blockInProgress = false;
        StreakStore.pulse(this, streakSession, false);
        if (focusMode != null) focusMode.close();
        if (recordedPages != null) recordedPages.close();
        try { recordedPages = new RecordedPageController(this); }
        catch (RuntimeException unavailable) { recordedPages = null; }
        focusMode = new FocusModeController(this);
        remainingTimeOverlay = new RemainingTimeOverlay(this);
        handler.post(recoveryCheck);
        ProtectionStateStore.markAccessibilityConnected(this, "accessibility_connected");
        ProtectionRecoveryStore.record(this, "connected", ProtectionRecovery.capabilities(this));
        ProtectionWarning.refresh(this);
        handler.removeCallbacks(serviceHealthTask);
        handler.post(serviceHealthTask);
        ProtectionService.start(this);
        // 前台服务与无障碍服务共用 :guard 进程，保护的正是执行限制的进程。

        Log.i("FocusAccessibility", "Protection service connected");
    }

    public static boolean isRunning(Context context) {
        return ProtectionStateStore.isAccessibilityHealthy(context);
    }
    // 返回无障碍服务是否已经被系统真实绑定并运行

    public static boolean isEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        ComponentName componentName = new ComponentName(
                context,
                FocusAccessibilityService.class
        );

        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            String enabledService = splitter.next();
            ComponentName enabledComponent =
                    ComponentName.unflattenFromString(enabledService);

            if (componentName.equals(enabledComponent)
                    || componentName.flattenToString().equalsIgnoreCase(enabledService)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        if (recordedPages != null) {
            try { recordedPages.onEvent(event); } catch (RuntimeException unavailable) { recordedPages.close(); recordedPages = null; }
        }
        // 忽略无效事件

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        // 只处理窗口切换事件，也就是用户打开或切换应用时触发

        String packageName = event.getPackageName().toString();
        if (focusMode != null && focusMode.onWindow(event)) {
            String allowed = focusMode.whitelistedForeground();
            if (allowed == null || allowed.equals(getPackageName()) || !isLimitedPackage(allowed)) {
                stopMonitoring();
                return;
            }
            currentPackageName = allowed;
            lastAppWindowAt = SystemClock.elapsedRealtime();
            handler.removeCallbacks(confirmAppExit);
            handler.removeCallbacks(usageCheckTask);
            if (!blockInProgress) checkCurrentAppUsage();
            return;
        }
        // 获取当前前台窗口所属应用的包名

        if (packageName.equals(getPackageName())) {
            // Our overlay may also emit window events; only app activities stop tracking.
            CharSequence className = event.getClassName();
            if (className != null && className.toString().startsWith(getPackageName() + ".")) {
                stopMonitoring();
            }
            return;
        }
        // FocusGuard 自己不限制自己，否则会把阻断页面也拦掉

        if (!isLimitedPackage(packageName)) {
            String keyboard = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (!ProtectionClock.nativeReliable(this) && !packageName.equals("com.android.systemui")
                    && (keyboard == null || !keyboard.startsWith(packageName + "/"))) {
                stopMonitoring(); return;
            }
            // System/keyboard/overlay windows do not necessarily change the foreground app.
            handler.removeCallbacks(confirmAppExit);
            handler.postDelayed(confirmAppExit, 200);
            return;
        }
        // 如果当前应用没有被用户勾选限制，就停止监控

        currentPackageName = packageName;
        lastAppWindowAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(confirmAppExit);
        handler.removeCallbacks(usageCheckTask);
        if (blockInProgress) {
            Log.d("FocusAccessibility", "Coalesced window event for " + packageName);
            return;
        }
        checkCurrentAppUsage();
        // 当前应用是受限应用，立刻开始检查使用时长
    }

    void onFocusModeChanged(boolean active, String packageName) {
        if (active) {
            stopMonitoring();
            handler.removeCallbacks(hideOverlayTask);
            hideOverlay();
            blockInProgress = false;
            if (packageName != null && focusMode != null && focusMode.whitelisted(packageName)
                    && isLimitedPackage(packageName)) {
                currentPackageName = packageName;
                lastAppWindowAt = SystemClock.elapsedRealtime();
                handler.post(usageCheckTask);
            }
        } else if (packageName != null && isLimitedPackage(packageName)) {
            currentPackageName = packageName;
            lastAppWindowAt = SystemClock.elapsedRealtime();
            handler.post(usageCheckTask);
        }
    }

    private void checkCurrentAppUsage() {
        boolean focusing = focusMode != null && focusMode.active();
        String packageName = currentPackageName;
        if (!FocusLimitPolicy.monitorDailyLimit(focusing,
                focusMode != null && focusMode.whitelisted(packageName))) {
            stopMonitoring();
            return;
        }
        long started = SystemClock.elapsedRealtime();

        int limitMinutes = packageName == null ? 0 : getLimitMinutes(packageName);
        if (limitMinutes <= 0) {
            stopMonitoring();
            return;
        }
        // 当前没有监控对象，或者应用已取消限制时，停止检查

        long limitMillis = limitMinutes * 60L * 1000L;
        // 把限制分钟数转换成毫秒

        ProtectionTimeLedger.Sample clock = ProtectionClock.read(this);
        boolean nativeReliable = ProtectionClock.nativeReliable(this);
        UsageSession session = nativeReliable ? getTodayUsageSession(packageName) : null;
        if (nativeReliable && (session == null || !session.isActive())) {
            if (remainingTimeOverlay != null) remainingTimeOverlay.hide();
            handler.removeCallbacks(usageCheckTask);
            // UsageEvents can lag behind a new accessibility window event briefly.
            if (SystemClock.elapsedRealtime() - lastAppWindowAt < 1500) {
                handler.postDelayed(usageCheckTask, 250);
            } else {
                Log.d("FocusAccessibility", "Usage session ended for " + packageName);
                stopMonitoring();
            }
            return;
        }
        long todayUsageMillis;
        try { todayUsageMillis = UsageBudgetStore.tick(this, packageName, session == null ? 0 : session.totalAt(System.currentTimeMillis())); }
        catch (java.io.IOException e) { Log.e("FocusAccessibility", "Cannot verify remaining usage", e); blockCurrentApp(packageName); return; }
        long sharedRemaining = SharedQuota.remaining(this, packageName, todayUsageMillis);
        if (sharedRemaining != Long.MAX_VALUE) limitMillis = Math.min(limitMillis, todayUsageMillis + sharedRemaining);
        Log.d("FocusAccessibility", "Checking " + packageName + " usageMs="
                + todayUsageMillis + " limitMinutes=" + limitMinutes);
        // 读取这个应用今天的实际前台使用时长

        long allowance = DailyTemporaryAccess.allowed(this, packageName)
                ? RestrictionStore.allowance(this, packageName) : 0;
        if (allowance > 0 && !SharedQuota.enabled(this)) {
            if (remainingTimeOverlay != null) {
                remainingTimeOverlay.showTemporary(packageName, allowance);
            }
            handler.removeCallbacks(usageCheckTask);
            handler.postDelayed(usageCheckTask, Math.min(1000L, allowance));
            return;
        }
        if (todayUsageMillis >= limitMillis) {
            blockCurrentApp(packageName);
            Log.i("FocusAccessibility", "Blocked " + packageName + " decisionMs="
                    + (SystemClock.elapsedRealtime() - started));
            return;
        }
        // 如果今日使用时长已经达到限制，就执行强制阻断

        if (remainingTimeOverlay != null) {
            if (sharedRemaining == Long.MAX_VALUE) remainingTimeOverlay.show(packageName, limitMillis - todayUsageMillis);
            else {
                SharedQuota.Timer timer = SharedQuota.timer(this, packageName, todayUsageMillis, limitMinutes * 60_000L, sharedRemaining);
                remainingTimeOverlay.show(packageName, timer.millis, timer.offline);
            }
        }
        handler.removeCallbacks(usageCheckTask);
        handler.postDelayed(usageCheckTask,
                Math.min(CHECK_INTERVAL_MILLIS, limitMillis - todayUsageMillis));
        // 还没超时就继续定时检查
    }

    private void blockCurrentApp(String packageName) {
        if (blockInProgress) {
            return;
        }
        blockInProgress = true;
        try { RestrictionStore.lockExhausted(this, packageName, getLimitMinutes(packageName)); }
        catch (java.io.IOException e) { Log.e("FocusAccessibility", "Cannot persist exhausted limit", e); }
        String appName = getAppName(packageName);
        blockedQuotaDay = ProtectionClock.day(this);
        // 根据包名获取应用名称，用于阻断页面展示

        stopMonitoring();
        // 清理当前监控任务，避免重复触发

        boolean focusing = focusMode != null && focusMode.active();
        if (focusing) focusMode.notifyDailyLimit(appName);
        else showOverlay(appName, packageName);
        boolean sent = performGlobalAction(GLOBAL_ACTION_HOME);
        if (!sent && focusing) showOverlay(appName, packageName);
        Log.i("FocusAccessibility", "HOME accepted=" + sent);
        if (focusing && DailyTemporaryAccess.allowed(this, packageName)) {
            handler.postDelayed(() -> {
                if (focusMode != null && focusMode.active() && DailyTemporaryAccess.allowed(this, packageName)) {
                    hideOverlay(); showOverlay(appName, packageName);
                }
            }, 400);
        }
        // 通过无障碍服务执行系统“返回桌面”操作

        handler.removeCallbacks(hideOverlayTask);
        // The prompt can be hidden by an OEM. Never wait for its dismissal to re-arm blocking.
        handler.removeCallbacks(finishBlockTransition);
        handler.postDelayed(finishBlockTransition, 800);
    }

    private void openBlockedPage(String appName) {
        Intent intent = new Intent(this, BlockedActivity.class);
        intent.putExtra(BlockedActivity.EXTRA_APP_NAME, appName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Service 启动 Activity 必须加 FLAG_ACTIVITY_NEW_TASK

        startActivity(intent);
        // 打开“已限制使用”页面
    }

    private boolean isLimitedPackage(String packageName) {
        return getLimitMinutes(packageName) > 0;
        // 判断当前应用是否属于受限应用
    }

    boolean dailyLimitReached(String packageName) {
        if (DailyTemporaryAccess.allowed(this, packageName) && RestrictionStore.allowance(this, packageName) > 0) return false;
        int limitMinutes = getLimitMinutes(packageName);
        if (limitMinutes <= 0) return false;
        UsageSession session = getTodayUsageSession(packageName);
        try {
            long used = UsageBudgetStore.totals(this, java.util.Collections.singleton(packageName),
                    java.util.Collections.singletonMap(packageName, session == null ? 0 : session.totalAt(System.currentTimeMillis()))).getOrDefault(packageName, 0L);
            return used >= limitMinutes * 60_000L || SharedQuota.remaining(this, packageName, used) <= 0;
        } catch (java.io.IOException e) { return true; }
    }

    private int getLimitMinutes(String packageName) {
        try {
            latestLimits = LimitSnapshot.read(this);
        } catch (java.io.IOException exception) {
            Log.e("FocusAccessibility", "Cannot read current limit configuration", exception);
        }
        try { return Integer.parseInt(latestLimits.getProperty(packageName, "0")); }
        catch (NumberFormatException ignored) { return 0; }
        // 读取当前应用的限制分钟数，默认是 30 分钟
    }

    void requestDailyTemporaryFromToolbox(String pkg) {
        if (getLimitMinutes(pkg) <= 0 || !DailyTemporaryAccess.allowed(this, pkg)) return;
        hideOverlay(); showOverlay(getAppName(pkg), pkg);
    }

    private void showOverlay(String appName, String packageName) {
        try {
            if (blockingOverlay == null) {
                blockingOverlay = LayoutInflater.from(Appearance.overlayContext(this)).inflate(R.layout.activity_blocked, null);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        -1, -2, GuardWindow.type(this),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
                params.dimAmount = 0.5f;
                GuardWindow.manager(this).addView(blockingOverlay, params);
            }
            ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage))
                    .setText(SharedQuota.blockedMessage(this, appName));
            android.widget.Button request = blockingOverlay.findViewById(R.id.btnEmergency);
            int left;
            try { left = RestrictionStore.left(this); } catch (java.io.IOException e) { left = -1; }
            request.setEnabled(left > 0 && DailyTemporaryAccess.allowed(this, packageName));
            request.setText(QuotaPresentation.appButton(left));
            if (left == 0) ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage))
                    .setText(nameExhausted(appName));
            request.setVisibility(SharedQuota.enabled(this) ? android.view.View.GONE : android.view.View.VISIBLE);
            request.setOnClickListener(v -> confirmEmergency(appName, packageName, request));
            blockingOverlay.findViewById(R.id.btnKeepRestricted).setOnClickListener(v -> {
                handler.post(hideOverlayTask);
            });
        } catch (RuntimeException error) {
            Log.e("FocusAccessibility", "Cannot display blocking overlay", error);
            hideOverlay();
            handler.postDelayed(hideOverlayTask, 800);
        }
    }

    private void confirmEmergency(String name, String pkg, android.widget.Button request) {
        try {
            int left = RestrictionStore.left(this);
            if (left <= 0) {
                request.setEnabled(false);
                request.setText("应用临时次数已用完 · 04:00 恢复");
                ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage)).setText(nameExhausted(name));
                return;
            }
            ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage)).setText("应用临时次数：剩余 " + left + " / 5。"+QuotaPresentation.APP_POOL+"确认后“" + name
                        + "”使用 2 分钟。"+QuotaPresentation.CONTINUOUS_NOTE+"应用临时次数用完后，本机每日额度已耗尽的应用持续限制至次日 04:00。"
                        + (SleepTemporaryUse.state(this).getBoolean("sleeping") ? "\n\n仅放行这个白名单应用，睡眠模式继续生效，不扣除睡眠全局临时次数。" : ""));
            request.setText("确认应用临时使用 2 分钟");
            request.setOnClickListener(v -> {
                try {
                    if (!DailyTemporaryAccess.allowed(this, pkg))
                        throw new java.io.IOException("当前仅睡眠白名单应用可申请应用临时使用");
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch == null) throw new java.io.IOException("无法打开该应用");
                    RestrictionStore.grant(this, pkg);
                    hideOverlay();
                    blockInProgress = false;
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            request.setEnabled(false);
            ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage))
                    .setText("无法读取应用临时次数，请稍后重试");
        }
    }

    private static String nameExhausted(String name) {
        return "“" + name + "”的本机每日额度和应用临时次数均已耗尽。\n将持续限制至次日 04:00，今天不能再解除限制。";
    }

    private void hideOverlay() {
        if (blockingOverlay != null) {
            try { getSystemService(WindowManager.class).removeViewImmediate(blockingOverlay); }
            catch (RuntimeException ignored) { }
            blockingOverlay = null;
        }
        blockedQuotaDay = Long.MIN_VALUE;
    }

    private UsageSession getTodayUsageSession(String targetPackageName) {
        ProtectionTimeLedger.Sample time = ProtectionClock.read(this);
        long wallNow = System.currentTimeMillis();
        long from = StreakLedger.boundary(time.day, time.zone) + wallNow - time.millis;
        AppUsageTimeline timeline = AppUsageReader.readRange(this,
                from, wallNow, java.util.Collections.singleton(targetPackageName));
        return timeline == null ? null : timeline.session(targetPackageName);
    }

    private boolean isForegroundEvent(UsageEvents.Event event) {
        int eventType = event.getEventType();

        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            return true;
        }
        // 兼容旧版本的进入前台事件

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && eventType == UsageEvents.Event.ACTIVITY_RESUMED;
        // Android 10 及以上使用新的进入前台事件
    }

    private boolean isBackgroundEvent(UsageEvents.Event event) {
        int eventType = event.getEventType();

        if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            return true;
        }
        // 兼容旧版本的离开前台事件

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && (eventType == UsageEvents.Event.ACTIVITY_PAUSED
                || eventType == UsageEvents.Event.ACTIVITY_STOPPED);
        // Android 10 及以上使用新的离开前台事件
    }

    private String getAppName(String packageName) {
        PackageManager packageManager = getPackageManager();

        try {
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(packageName, 0);

            return packageManager
                    .getApplicationLabel(applicationInfo)
                    .toString();
            // 根据包名获取应用名称

        } catch (PackageManager.NameNotFoundException e) {
            return "该应用";
        }
        // 如果找不到应用名称，就返回默认文字
    }

    private void stopMonitoring() {
        UsageBudgetStore.pause(this);
        handler.removeCallbacks(confirmAppExit);
        if (remainingTimeOverlay != null) {
            remainingTimeOverlay.hide();
        }
        currentPackageName = null;
        handler.removeCallbacks(usageCheckTask);
        // 停止当前定时检查任务
    }

    @Override
    public void onInterrupt() {
        ProtectionStateStore.markAccessibilityConnected(this, "accessibility_interrupted");
        Log.i("FocusAccessibility", "Accessibility feedback interrupted");
        // onInterrupt 可能在服务仍保持绑定时多次调用，不能把它当作服务故障。
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (recordedPages != null) { recordedPages.close(); recordedPages = null; }
        handler.removeCallbacks(finishBlockTransition);
        handler.removeCallbacks(recoveryCheck);
        StreakStore.pulse(this, streakSession, false);
        if (focusMode != null) { focusMode.close(); focusMode = null; }
        blockInProgress = false;
        handler.removeCallbacks(hideOverlayTask);
        hideOverlay();
        handler.removeCallbacks(serviceHealthTask);
        ProtectionStateStore.markAccessibilityDisconnected(this, "accessibility_unbound");
        ProtectionRecoveryStore.record(this, "disconnect", ProtectionRecovery.capabilities(this));
        ProtectionWarning.refresh(this);
        SleepControlSession.observe(this);
        stopMonitoring();
        return super.onUnbind(intent);
    }
    // 系统解除绑定时立即更新运行状态

    @Override
    public void onDestroy() {
        if (recordedPages != null) { recordedPages.close(); recordedPages = null; }
        handler.removeCallbacks(finishBlockTransition);
        handler.removeCallbacks(recoveryCheck);
        StreakStore.pulse(this, streakSession, false);
        if (focusMode != null) { focusMode.close(); focusMode = null; }
        blockInProgress = false;
        handler.removeCallbacks(hideOverlayTask);
        hideOverlay();
        handler.removeCallbacks(serviceHealthTask);
        ProtectionStateStore.markAccessibilityDisconnected(this, "accessibility_destroyed");
        ProtectionRecoveryStore.record(this, "disconnect", ProtectionRecovery.capabilities(this));
        ProtectionWarning.refresh(this);
        SleepControlSession.observe(this);
        stopMonitoring();
        super.onDestroy();
        // 服务销毁时清理任务，避免内存泄漏
    }
}
