package com.luoyilin.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import com.luoyilin.focusguard.sync.LimitSyncManager;
// 导入刚刚创建的后端限制同步工具

public class FocusAccessibilityService extends AccessibilityService {

    private static final String PREF_NAME = "focus_guard_prefs";
    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    private static final String KEY_LIMIT_PREFIX = "limit_minutes_";
    // 这些 key 必须和 AppManageActivity 保持一致，才能读取用户选择的限制应用

    private static final long CHECK_INTERVAL_MILLIS = 2000;
    // 每隔 2 秒检查一次当前应用是否超时，测试时反应更明显

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentPackageName;
    // 保存当前正在前台的应用包名

    private final Runnable usageCheckTask = new Runnable() {
        @Override
        public void run() {
            checkCurrentAppUsage();
            // 定时检查当前应用是否达到限制时间
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        Toast.makeText(
                this,
                "FocusGuard 强制限制服务已启动",
                Toast.LENGTH_SHORT
        ).show();
        // 无障碍服务启动成功时显示提示

        LimitSyncManager.syncFromServer(
                this,
                new LimitSyncManager.SyncCallback() {

                    @Override
                    public void onSuccess(int enabledCount) {
                        Toast.makeText(
                                FocusAccessibilityService.this,
                                "已从后端同步 "
                                        + enabledCount
                                        + " 个限制",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                    // 同步成功后显示已启用的限制数量

                    @Override
                    public void onFailure(String message) {
                        Toast.makeText(
                                FocusAccessibilityService.this,
                                "后端同步失败，继续使用本地配置："
                                        + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                    // 同步失败时不清空缓存，继续使用上一次配置
                }
        );
        // 无障碍服务启动时异步获取后端限制
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
        handler.post(usageCheckTask);
        // 当前应用是受限应用，立刻开始检查使用时长
    }

    private void checkCurrentAppUsage() {
        String packageName = currentPackageName;

        if (packageName == null || !isLimitedPackage(packageName)) {
            stopMonitoring();
            return;
        }
        // 当前没有监控对象，或者应用已取消限制时，停止检查

        int limitMinutes = getLimitMinutes(packageName);
        long limitMillis = limitMinutes * 60L * 1000L;
        // 把限制分钟数转换成毫秒

        long todayUsageMillis = getTodayUsageMillis(packageName);
        // 读取这个应用今天的实际前台使用时长

        if (todayUsageMillis >= limitMillis) {
            blockCurrentApp(packageName);
            return;
        }
        // 如果今日使用时长已经达到限制，就执行强制阻断

        handler.postDelayed(usageCheckTask, CHECK_INTERVAL_MILLIS);
        // 还没超时就继续定时检查
    }

    private void blockCurrentApp(String packageName) {
        String appName = getAppName(packageName);
        // 根据包名获取应用名称，用于阻断页面展示

        stopMonitoring();
        // 清理当前监控任务，避免重复触发

        performGlobalAction(GLOBAL_ACTION_HOME);
        // 通过无障碍服务执行系统“返回桌面”操作

        handler.postDelayed(() -> openBlockedPage(appName), 300);
        // 稍等系统回到桌面后，再打开 FocusGuard 的阻断提示页面
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
        SharedPreferences preferences =
                getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        Set<String> savedPackages = preferences.getStringSet(
                KEY_SELECTED_PACKAGES,
                new HashSet<>()
        );
        // 读取用户已经选择限制的应用包名集合

        return savedPackages != null && savedPackages.contains(packageName);
        // 判断当前应用是否属于受限应用
    }

    private int getLimitMinutes(String packageName) {
        SharedPreferences preferences =
                getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        return preferences.getInt(
                KEY_LIMIT_PREFIX + packageName,
                30
        );
        // 读取当前应用的限制分钟数，默认是 30 分钟
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
        currentPackageName = null;
        handler.removeCallbacks(usageCheckTask);
        // 停止当前定时检查任务
    }

    @Override
    public void onInterrupt() {
        stopMonitoring();
        // 无障碍服务被系统中断时停止检查
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
        // 服务销毁时清理任务，避免内存泄漏
    }
}