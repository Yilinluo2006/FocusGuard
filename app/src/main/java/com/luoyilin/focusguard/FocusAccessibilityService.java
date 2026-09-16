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
import android.widget.Toast;

import java.util.Calendar;
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
    private String currentPackageName;
    private Properties latestLimits = new Properties();
    private View blockingOverlay;
    private RemainingTimeOverlay remainingTimeOverlay;
    private boolean blockInProgress;
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
            handler.postDelayed(this, HEALTH_INTERVAL_MILLIS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (remainingTimeOverlay == null) {
            remainingTimeOverlay = new RemainingTimeOverlay(this);
        }
        ProtectionStateStore.markAccessibilityConnected(this, "accessibility_connected");
        handler.removeCallbacks(serviceHealthTask);
        handler.post(serviceHealthTask);
        ProtectionService.start(this);
        // 前台服务与无障碍服务共用 :guard 进程，保护的正是执行限制的进程。

        Toast.makeText(
                this,
                "FocusGuard 强制限制服务已启动",
                Toast.LENGTH_SHORT
        ).show();
        // 无障碍服务启动成功时显示提示
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
        // 忽略无效事件

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        // 只处理窗口切换事件，也就是用户打开或切换应用时触发

        String packageName = event.getPackageName().toString();
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
            stopMonitoring();
            return;
        }
        // 如果当前应用没有被用户勾选限制，就停止监控

        currentPackageName = packageName;
        handler.removeCallbacks(usageCheckTask);
        if (blockInProgress) {
            Log.d("FocusAccessibility", "Coalesced window event for " + packageName);
            return;
        }
        checkCurrentAppUsage();
        // 当前应用是受限应用，立刻开始检查使用时长
    }

    private void checkCurrentAppUsage() {
        long started = SystemClock.elapsedRealtime();
        String packageName = currentPackageName;

        int limitMinutes = packageName == null ? 0 : getLimitMinutes(packageName);
        if (limitMinutes <= 0) {
            stopMonitoring();
            return;
        }
        // 当前没有监控对象，或者应用已取消限制时，停止检查

        long limitMillis = limitMinutes * 60L * 1000L;
        // 把限制分钟数转换成毫秒

        long todayUsageMillis = getTodayUsageMillis(packageName);
        Log.d("FocusAccessibility", "Checking " + packageName + " usageMs="
                + todayUsageMillis + " limitMinutes=" + limitMinutes);
        // 读取这个应用今天的实际前台使用时长

        if (todayUsageMillis >= limitMillis) {
            blockCurrentApp(packageName);
            Log.i("FocusAccessibility", "Blocked " + packageName + " decisionMs="
                    + (SystemClock.elapsedRealtime() - started));
            return;
        }
        // 如果今日使用时长已经达到限制，就执行强制阻断

        if (remainingTimeOverlay != null) {
            remainingTimeOverlay.show(limitMillis - todayUsageMillis);
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
        String appName = getAppName(packageName);
        // 根据包名获取应用名称，用于阻断页面展示

        stopMonitoring();
        // 清理当前监控任务，避免重复触发

        showOverlay(appName);
        boolean sent = performGlobalAction(GLOBAL_ACTION_HOME);
        Log.i("FocusAccessibility", "HOME accepted=" + sent);
        // 通过无障碍服务执行系统“返回桌面”操作

        handler.removeCallbacks(hideOverlayTask);
        handler.postDelayed(hideOverlayTask, 800);
        // Keep the transition covered; do not send HOME again for queued window events.
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

    private void showOverlay(String appName) {
        try {
            if (blockingOverlay == null) {
                blockingOverlay = LayoutInflater.from(this).inflate(R.layout.activity_blocked, null);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
                getSystemService(WindowManager.class).addView(blockingOverlay, params);
            }
            ((TextView) blockingOverlay.findViewById(R.id.tvBlockedMessage))
                    .setText(appName + " 今日使用时间已达到限制");
        } catch (RuntimeException error) {
            Log.e("FocusAccessibility", "Cannot display blocking overlay", error);
            hideOverlay();
        }
    }

    private void hideOverlay() {
        if (blockingOverlay != null) {
            try { getSystemService(WindowManager.class).removeViewImmediate(blockingOverlay); }
            catch (RuntimeException ignored) { }
            blockingOverlay = null;
        }
    }

    private long getTodayUsageMillis(String targetPackageName) {
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null) {
            return 0;
        }
        // 如果系统服务不可用，就返回 0

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // 统计起点设置为今天凌晨 00:00

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();
        // 统计范围是今天凌晨到当前时间

        UsageEvents usageEvents =
                usageStatsManager.queryEvents(startTime, endTime);

        UsageEvents.Event event = new UsageEvents.Event();

        long foregroundStartTime = -1;
        long totalUsageMillis = 0;
        // foregroundStartTime 记录进入前台的时间，totalUsageMillis 记录累计时长

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            if (!targetPackageName.equals(event.getPackageName())) {
                continue;
            }
            // 只计算目标应用自己的事件

            if (isForegroundEvent(event)) {
                if (foregroundStartTime == -1) {
                    foregroundStartTime = event.getTimeStamp();
                }
                // 应用进入前台，开始记录这一段使用时间

            } else if (isBackgroundEvent(event)) {
                if (foregroundStartTime != -1) {
                    totalUsageMillis += event.getTimeStamp() - foregroundStartTime;
                    foregroundStartTime = -1;
                }
                // 应用离开前台，把这一段使用时间加入总时长
            }
        }

        if (foregroundStartTime != -1) {
            totalUsageMillis += endTime - foregroundStartTime;
        }
        // 如果应用当前仍在前台，就把“进入前台到现在”的时间也算进去

        return totalUsageMillis;
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
                && eventType == UsageEvents.Event.ACTIVITY_PAUSED;
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
        blockInProgress = false;
        handler.removeCallbacks(hideOverlayTask);
        hideOverlay();
        handler.removeCallbacks(serviceHealthTask);
        ProtectionStateStore.markAccessibilityDisconnected(this, "accessibility_unbound");
        stopMonitoring();
        return super.onUnbind(intent);
    }
    // 系统解除绑定时立即更新运行状态

    @Override
    public void onDestroy() {
        blockInProgress = false;
        handler.removeCallbacks(hideOverlayTask);
        hideOverlay();
        handler.removeCallbacks(serviceHealthTask);
        ProtectionStateStore.markAccessibilityDisconnected(this, "accessibility_destroyed");
        stopMonitoring();
        super.onDestroy();
        // 服务销毁时清理任务，避免内存泄漏
    }
}
