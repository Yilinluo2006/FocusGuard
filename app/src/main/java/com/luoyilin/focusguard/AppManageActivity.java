package com.luoyilin.focusguard;

import android.app.usage.UsageEvents;
import android.os.Build;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.luoyilin.focusguard.network.AppLimitRequest;
import com.luoyilin.focusguard.network.AppLimitResponse;
import com.luoyilin.focusguard.network.NetworkErrorHelper;
import com.luoyilin.focusguard.network.RetrofitClient;
import com.luoyilin.focusguard.sync.LimitSyncManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AppManageActivity extends AppCompatActivity {
    private boolean applyingRemote;
    private boolean loaded;
    private boolean saving;
    private int editRevision;

    private static final String PREF_NAME = "focus_guard_prefs";
    // SharedPreferences 文件名

    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    // 保存已选择应用包名集合的 key

    private static final String KEY_LIMIT_PREFIX = "limit_minutes_";
    // 保存应用限制时间时使用的 key 前缀

    private static final int DEFAULT_LIMIT_MINUTES = 30;
    // 默认限制时间为 30 分钟

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manage);
        // 加载应用管理页面

        Button btnAddLimitedApp = findViewById(R.id.btnAddLimitedApp);
        TextView tvSelectedCount = findViewById(R.id.tvSelectedCount);
        RecyclerView rvAppList = findViewById(R.id.rvAppList);
        // 获取页面控件

        List<AppInfo> appList = loadInstalledApps();
        // 读取手机中已安装的应用

        tvSelectedCount.setText(
                "已选择 " + countSelectedApps(appList) + " 个应用"
        );
        // 显示当前已选择应用数量

        AppListAdapter adapter = new AppListAdapter(appList);
        // 创建列表适配器

        adapter.setOnSelectionChangedListener(selectedCount -> {
            tvSelectedCount.setText(
                    "已选择 " + selectedCount + " 个应用"
            );

            saveSelectedApps(appList);
            if (!applyingRemote) {
                editRevision++;
                LimitSyncManager.markPending(this);
                if (loaded) saveLimitsToServer(appList);
            }
            // 选择状态改变后保存到手机本地
        });

        adapter.setOnDeleteRequestListener(appInfo ->
                showDeleteConfirmationDialog(
                        appInfo,
                        appList,
                        adapter
                )
        );
        // 长按应用时显示删除后端记录的确认弹窗

        rvAppList.setLayoutManager(new LinearLayoutManager(this));
        rvAppList.setAdapter(adapter);
        // 设置 RecyclerView 为竖向列表并绑定适配器

        loadLimitsFromServer(appList, adapter);
        // 从 Spring Boot 后端读取已有的限制记录

        btnAddLimitedApp.setOnClickListener(view -> {
            if (!loaded) {
                loadLimitsFromServer(appList, adapter);
                return;
            }
            saveSelectedApps(appList);
            // 先保存到手机本地

            saveLimitsToServer(appList);
            // 再同步到 Spring Boot 和 MySQL
        });
    }

    private void loadLimitsFromServer(
            List<AppInfo> appList,
            AppListAdapter adapter
    ) {
        RetrofitClient.getApi()
                .getAppLimits()
                .enqueue(new Callback<List<AppLimitResponse>>() {

                    @Override
                    public void onResponse(
                            Call<List<AppLimitResponse>> call,
                            Response<List<AppLimitResponse>> response
                    ) {
                        if (!response.isSuccessful()
                                || response.body() == null) {

                            Toast.makeText(
                                    AppManageActivity.this,
                                    "读取后端限制失败，状态码："
                                            + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        // 检查后端响应是否成功

                        boolean keepLocal = LimitSyncManager.hasPending(AppManageActivity.this);
                        Map<String, AppLimitResponse> limitMap =
                                new HashMap<>();

                        for (AppLimitResponse limit : response.body()) {
                            limitMap.put(
                                    limit.getPackageName(),
                                    limit
                            );
                        }
                        // 使用包名作为 key 保存后端记录

                        for (AppInfo appInfo : appList) {
                            AppLimitResponse limit =
                                    limitMap.get(
                                            appInfo.getPackageName()
                                    );

                            if (limit == null) {
                                appInfo.setServerId(null);
                                if (!keepLocal) appInfo.setSelected(false);
                                continue;
                            }
                            // 登录后以当前账号的后端记录为准，清除其他账号留下的本地状态

                            if (keepLocal) appInfo.setServerId(limit.getId());
                            else applyServerLimit(appInfo, limit);
                            // 同时恢复数据库 ID、启用状态和真实限制分钟数
                        }

                        saveSelectedApps(appList);
                        // 立即写入本地缓存，供首页和无障碍服务读取同一份限制时间

                        applyingRemote = true;
                        adapter.refreshAppList();
                        applyingRemote = false;
                        loaded = true;
                        if (keepLocal) saveLimitsToServer(appList);
                        // 重新排序并刷新应用列表

                    }

                    @Override
                    public void onFailure(
                            Call<List<AppLimitResponse>> call,
                            Throwable throwable
                    ) {
                        Toast.makeText(
                                AppManageActivity.this,
                                "读取限制失败："
                                        + NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 将底层网络异常转换为用户容易理解的提示
                    }
                });
        // enqueue 会异步发送请求，不会阻塞界面
    }

    private void saveLimitsToServer(List<AppInfo> appList) {
        if (!loaded || saving) return;
        saving = true;
        final int revision = editRevision;
        final int[] remaining = {0};
        final boolean[] failed = {false};
        for (AppInfo app : appList) {
            if (app.isSelected() || app.getServerId() != null) remaining[0]++;
        }
        int requestCount = 0;
        // 统计需要发送的请求数量

        for (AppInfo appInfo : appList) {
            if (!appInfo.isSelected()
                    && appInfo.getServerId() == null) {
                continue;
            }
            // 从未上传且未选中的应用不需要发送

            int limitMinutes = appInfo.getLimitMinutes();

            if (limitMinutes <= 0) {
                limitMinutes = DEFAULT_LIMIT_MINUTES;
            }
            // 后端要求限制时间至少为 1 分钟

            AppLimitRequest request = new AppLimitRequest(
                    appInfo.getPackageName(),
                    limitMinutes,
                    appInfo.isSelected()
            );
            // 将 AppInfo 转换成后端请求对象

            Call<AppLimitResponse> call;

            if (appInfo.getServerId() == null) {
                call = RetrofitClient.getApi()
                        .createAppLimit(request);
                // 没有 serverId，使用 POST 创建新记录
            } else {
                call = RetrofitClient.getApi()
                        .updateAppLimit(
                                appInfo.getServerId(),
                                request
                        );
                // 已有 serverId，使用 PUT 修改原记录
            }

            requestCount++;

            call.enqueue(new Callback<AppLimitResponse>() {
                @Override
                public void onResponse(
                        Call<AppLimitResponse> call,
                        Response<AppLimitResponse> response
                ) {
                    if (response.isSuccessful()
                            && response.body() != null) {

                        appInfo.setServerId(response.body().getId());
                        // 使用后端最终保存的状态覆盖当前应用对象

                        saveSelectedApps(appList);
                        finishSave(appList, revision, remaining, failed);
                        return;
                    }

                    Toast.makeText(
                            AppManageActivity.this,
                            "同步失败，本地修改已保留，状态码："
                                    + response.code(),
                            Toast.LENGTH_LONG
                    ).show();
                    failed[0] = true;
                    finishSave(appList, revision, remaining, failed);
                }

                @Override
                public void onFailure(
                        Call<AppLimitResponse> call,
                        Throwable throwable
                ) {
                    Toast.makeText(
                            AppManageActivity.this,
                            "保存限制失败，本地修改已保留："
                                    + NetworkErrorHelper.getMessage(throwable),
                            Toast.LENGTH_LONG
                    ).show();
                    failed[0] = true;
                    finishSave(appList, revision, remaining, failed);
                }
            });
        }

        if (requestCount == 0) {
            saving = false;
            LimitSyncManager.markSynced(this);
            Toast.makeText(
                    this,
                    "没有需要同步的限制记录",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Toast.makeText(
                this,
                "正在同步 " + requestCount + " 条限制记录",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void finishSave(List<AppInfo> appList, int revision,
                            int[] remaining, boolean[] failed) {
        if (--remaining[0] != 0) return;
        saving = false;
        if (revision != editRevision) {
            saveLimitsToServer(appList);
        } else if (!failed[0]) {
            LimitSyncManager.markSynced(this);
            Toast.makeText(this, "限制配置已同步保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyServerLimit(
            AppInfo appInfo,
            AppLimitResponse limit
    ) {
        appInfo.setServerId(limit.getId());
        // 保存数据库记录 ID，后续修改时才能使用 PUT

        if (!limit.isEnabled()) {
            appInfo.setSelected(false);
            return;
        }

        int limitMinutes = limit.getLimitMinutes();

        if (limitMinutes <= 0) {
            limitMinutes = DEFAULT_LIMIT_MINUTES;
        }
        // 防止异常数据把有效限制时间写成 0 分钟

        appInfo.setLimitMinutes(limitMinutes);
        appInfo.setSelected(true);
        // 必须先写分钟数再选中，避免 setSelected 自动补成默认的 30 分钟
    }

    private void showDeleteConfirmationDialog(
            AppInfo appInfo,
            List<AppInfo> appList,
            AppListAdapter adapter
    ) {
        if (appInfo.getServerId() == null) {
            Toast.makeText(
                    this,
                    "该应用还没有后端记录，无需删除",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        // serverId 为 null 表示 MySQL 中还没有该记录

        new AlertDialog.Builder(this)
                .setTitle("删除限制记录")
                .setMessage(
                        "确定要删除“"
                                + appInfo.getAppName()
                                + "”的后端限制记录吗？"
                )
                .setPositiveButton(
                        "删除",
                        (dialog, which) ->
                                deleteLimitFromServer(
                                        appInfo,
                                        appList,
                                        adapter
                                )
                )
                .setNegativeButton("取消", null)
                .show();
        // 显示删除确认弹窗
    }

    private void deleteLimitFromServer(
            AppInfo appInfo,
            List<AppInfo> appList,
            AppListAdapter adapter
    ) {
        Long serverId = appInfo.getServerId();

        if (serverId == null) {
            return;
        }

        RetrofitClient.getApi()
                .deleteAppLimit(serverId)
                .enqueue(new Callback<Void>() {

                    @Override
                    public void onResponse(
                            Call<Void> call,
                            Response<Void> response
                    ) {
                        if (!response.isSuccessful()) {
                            Toast.makeText(
                                    AppManageActivity.this,
                                    "删除失败，状态码："
                                            + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        appInfo.setServerId(null);
                        // 清除该应用对应的数据库 ID

                        appInfo.setSelected(false);
                        // 取消该应用的限制状态

                        saveSelectedApps(appList);
                        // 更新手机本地保存的数据

                        adapter.refreshAppList();
                        // 重新排序并刷新列表

                        Toast.makeText(
                                AppManageActivity.this,
                                "已删除“"
                                        + appInfo.getAppName()
                                        + "”的限制记录",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onFailure(
                            Call<Void> call,
                            Throwable throwable
                    ) {
                        Toast.makeText(
                                AppManageActivity.this,
                                "删除限制失败："
                                        + NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 将删除请求的网络异常转换为中文提示
                    }
                });
        // 向后端发送 DELETE 请求
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> appList = new ArrayList<>();
        // 保存最终展示的应用

        Set<String> selectedPackageNames =
                getSavedSelectedPackageNames();
        // 获取本地保存的已选择包名

        Map<String, Long> todayUsageMap =
                loadTodayUsageMap();
        // 获取今日应用使用时长

        PackageManager packageManager =
                getPackageManager();

        Intent intent = new Intent(
                Intent.ACTION_MAIN,
                null
        );

        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // 只查询能够从桌面启动的应用

        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(
                        intent,
                        0
                );

        Set<String> addedPackages = new HashSet<>();
        // 防止同一个应用因为多个启动入口而重复显示

        for (ResolveInfo resolveInfo : resolveInfoList) {
            ApplicationInfo applicationInfo =
                    resolveInfo.activityInfo.applicationInfo;

            if ((applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            // 跳过系统应用

            String packageName =
                    applicationInfo.packageName;

            if (!addedPackages.add(packageName)) {
                continue;
            }
            // 已加入列表的包名不再重复添加

            String appName = packageManager
                    .getApplicationLabel(applicationInfo)
                    .toString();

            Drawable appIcon = packageManager
                    .getApplicationIcon(applicationInfo);

            AppInfo appInfo = new AppInfo(
                    appName,
                    packageName,
                    appIcon
            );

            boolean isSelected =
                    selectedPackageNames.contains(packageName);

            appInfo.setSelected(isSelected);
            // 恢复本地选择状态

            if (isSelected) {
                appInfo.setLimitMinutes(
                        getSavedLimitMinutes(packageName)
                );
            }
            // 恢复本地限制时间

            Long usageMillis =
                    todayUsageMap.get(packageName);

            appInfo.setTodayUsageMillis(
                    usageMillis == null ? 0 : usageMillis
            );
            // 保存今日使用时长

            appList.add(appInfo);
        }

        return appList;
    }

    private Map<String, Long> loadTodayUsageMap() {
        Map<String, Long> usageMap = new HashMap<>();
        // 保存每个应用今天累计使用的毫秒数

        Map<String, Long> foregroundStartMap = new HashMap<>();
        // 保存每个应用最近一次进入前台的时间

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) getSystemService(
                        Context.USAGE_STATS_SERVICE
                );

        if (usageStatsManager == null) {
            return usageMap;
        }
        // 无法获取系统使用统计服务时返回空结果

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // 把统计开始时间设置为今天凌晨 00:00

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();
        // 只统计今天凌晨到当前时刻

        UsageEvents usageEvents =
                usageStatsManager.queryEvents(startTime, endTime);
        // 获取指定时间范围内的应用前后台切换事件

        if (usageEvents == null) {
            return usageMap;
        }

        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            String packageName = event.getPackageName();

            if (packageName == null) {
                continue;
            }

            int eventType = event.getEventType();

            boolean enteredForeground =
                    eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                            || (Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.Q
                            && eventType
                            == UsageEvents.Event.ACTIVITY_RESUMED);
            // 判断应用是否进入前台

            boolean enteredBackground =
                    eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                            || (Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.Q
                            && eventType
                            == UsageEvents.Event.ACTIVITY_PAUSED);
            // 判断应用是否离开前台

            if (enteredForeground) {
                if (!foregroundStartMap.containsKey(packageName)) {
                    foregroundStartMap.put(
                            packageName,
                            event.getTimeStamp()
                    );
                }
                // 第一次进入前台时记录开始时间

            } else if (enteredBackground) {
                Long foregroundStart =
                        foregroundStartMap.remove(packageName);

                if (foregroundStart == null) {
                    continue;
                }

                long duration =
                        event.getTimeStamp() - foregroundStart;

                if (duration <= 0) {
                    continue;
                }

                long oldUsage = usageMap.containsKey(packageName)
                        ? usageMap.get(packageName)
                        : 0L;

                usageMap.put(
                        packageName,
                        oldUsage + duration
                );
                // 应用离开前台时累计本次使用时间
            }
        }

        for (Map.Entry<String, Long> entry
                : foregroundStartMap.entrySet()) {

            long duration = endTime - entry.getValue();

            if (duration <= 0) {
                continue;
            }

            long oldUsage = usageMap.containsKey(entry.getKey())
                    ? usageMap.get(entry.getKey())
                    : 0L;

            usageMap.put(
                    entry.getKey(),
                    oldUsage + duration
            );
        }
        // 当前仍在前台的应用，累计到现在为止

        long maximumPossibleUsage = endTime - startTime;

        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
            long safeUsage = Math.max(
                    0L,
                    Math.min(entry.getValue(), maximumPossibleUsage)
            );

            entry.setValue(safeUsage);
        }
        // 最终保护：单个应用的今日时长不能超过今天已经经过的时间

        return usageMap;
    }
    private Set<String> getSavedSelectedPackageNames() {
        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        Set<String> savedSet = preferences.getStringSet(
                KEY_SELECTED_PACKAGES,
                new HashSet<>()
        );

        return new HashSet<>(savedSet);
        // 返回副本，避免直接修改 SharedPreferences 内部集合
    }

    private int getSavedLimitMinutes(String packageName) {
        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        return preferences.getInt(
                KEY_LIMIT_PREFIX + packageName,
                DEFAULT_LIMIT_MINUTES
        );
    }

    private void saveSelectedApps(List<AppInfo> appList) {
        Set<String> selectedPackageNames =
                new HashSet<>();

        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        SharedPreferences.Editor editor =
                preferences.edit();

        for (AppInfo appInfo : appList) {
            String packageName =
                    appInfo.getPackageName();

            if (appInfo.isSelected()) {
                selectedPackageNames.add(packageName);

                editor.putInt(
                        KEY_LIMIT_PREFIX + packageName,
                        appInfo.getLimitMinutes()
                );
            } else {
                editor.remove(
                        KEY_LIMIT_PREFIX + packageName
                );
            }
        }

        editor.putStringSet(
                KEY_SELECTED_PACKAGES,
                selectedPackageNames
        );

        editor.apply();
        LimitSnapshot.publish(this);
        // 异步保存到手机本地
    }

    private int countSelectedApps(List<AppInfo> appList) {
        int count = 0;

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }

        return count;
    }

}
