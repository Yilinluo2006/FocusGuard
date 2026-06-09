package com.luoyilin.focusguard;

import android.app.usage.UsageStats;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppManageActivity extends AppCompatActivity {
    private static final String PREF_NAME = "focus_guard_prefs";
    // SharedPreferences 文件名，用来保存本地配置

    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    // 保存已选择应用包名集合的 key

    private static final String KEY_LIMIT_PREFIX = "limit_minutes_";
    // 保存每个应用限制分钟数时使用的 key 前缀

    private static final int DEFAULT_LIMIT_MINUTES = 30;
    // 默认限制时间：30 分钟

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manage);
        // 加载应用管理页面布局

        Button btnAddLimitedApp = findViewById(R.id.btnAddLimitedApp);
        // 找到保存按钮

        TextView tvSelectedCount = findViewById(R.id.tvSelectedCount);
        // 找到显示已选择数量的 TextView

        RecyclerView rvAppList = findViewById(R.id.rvAppList);
        // 找到应用列表 RecyclerView

        List<AppInfo> appList = loadInstalledApps();
        // 读取手机应用，并恢复已保存的选择状态、限制时间、今日使用时长

        tvSelectedCount.setText("已选择 " + countSelectedApps(appList) + " 个应用");
        // 页面打开时显示当前已选择数量

        AppListAdapter adapter = new AppListAdapter(appList);
        // 创建适配器

        adapter.setOnSelectionChangedListener(selectedCount -> {
            tvSelectedCount.setText("已选择 " + selectedCount + " 个应用");
            // 更新顶部已选择数量

            saveSelectedApps(appList);
            // 每次选择或限制时间变化后，保存最新状态
        });

        rvAppList.setLayoutManager(new LinearLayoutManager(this));
        // 设置 RecyclerView 为竖向列表

        rvAppList.setAdapter(adapter);
        // 绑定适配器，让列表显示出来

        btnAddLimitedApp.setOnClickListener(v -> {
            saveSelectedApps(appList);
            // 手动保存当前选择和限制时间

            Toast.makeText(this, "已保存 " + countSelectedApps(appList) + " 个限制应用", Toast.LENGTH_SHORT).show();
            // 显示保存提示
        });
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> appList = new ArrayList<>();
        // 创建应用列表

        Set<String> selectedPackageNames = getSavedSelectedPackageNames();
        // 读取之前保存过的已选择应用包名

        Map<String, Long> todayUsageMap = loadTodayUsageMap();
        // 读取今天每个应用的使用时长，key 是包名，value 是毫秒

        PackageManager packageManager = getPackageManager();
        // 获取系统包管理器

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        // 创建查询可启动应用的 Intent

        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // 只查询桌面启动器中的应用

        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        // 查询所有可从桌面启动的应用

        for (ResolveInfo resolveInfo : resolveInfoList) {
            ApplicationInfo applicationInfo = resolveInfo.activityInfo.applicationInfo;
            // 获取应用信息

            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            // 跳过系统应用

            String appName = packageManager.getApplicationLabel(applicationInfo).toString();
            // 获取应用名称

            String packageName = applicationInfo.packageName;
            // 获取应用包名

            Drawable appIcon = packageManager.getApplicationIcon(applicationInfo);
            // 获取应用图标

            AppInfo appInfo = new AppInfo(appName, packageName, appIcon);
            // 创建 AppInfo 对象

            boolean isSelected = selectedPackageNames.contains(packageName);
            // 判断这个应用之前是否被选中过

            appInfo.setSelected(isSelected);
            // 恢复选中状态

            if (isSelected) {
                appInfo.setLimitMinutes(getSavedLimitMinutes(packageName));
            }
            // 如果之前被选中过，就恢复它保存的限制分钟数

            long todayUsageMillis = todayUsageMap.containsKey(packageName)
                    ? todayUsageMap.get(packageName)
                    : 0;
            // 根据包名读取这个应用今天的使用时长

            appInfo.setTodayUsageMillis(todayUsageMillis);
            // 保存今日使用时长到 AppInfo

            appList.add(appInfo);
            // 加入列表
        }

        return appList;
        // 返回最终应用列表
    }

    private Map<String, Long> loadTodayUsageMap() {
        Map<String, Long> usageMap = new HashMap<>();
        // 创建使用时长 Map，用来保存每个包名对应的今日使用时长

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        // 获取 UsageStatsManager 系统服务

        if (usageStatsManager == null) {
            return usageMap;
        }
        // 如果无法获取系统服务，返回空 Map

        Calendar calendar = Calendar.getInstance();
        // 获取当前时间

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // 设置为今天凌晨 00:00

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();
        // 统计范围：今天凌晨到当前时间

        Map<String, UsageStats> usageStatsMap =
                usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        // 查询并聚合今天每个应用的使用数据

        if (usageStatsMap == null || usageStatsMap.isEmpty()) {
            return usageMap;
        }
        // 没有数据时返回空 Map

        for (Map.Entry<String, UsageStats> entry : usageStatsMap.entrySet()) {
            String packageName = entry.getKey();
            // 获取应用包名

            long usageTimeMillis = entry.getValue().getTotalTimeInForeground();
            // 获取该应用今天在前台的使用时长

            usageMap.put(packageName, usageTimeMillis);
            // 保存到 Map 中，方便后面按包名查找
        }

        return usageMap;
        // 返回今日使用时长 Map
    }

    private Set<String> getSavedSelectedPackageNames() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // 获取 SharedPreferences

        Set<String> savedSet = preferences.getStringSet(KEY_SELECTED_PACKAGES, new HashSet<>());
        // 读取已保存的包名集合

        return new HashSet<>(savedSet);
        // 返回新的 HashSet，避免直接修改 SharedPreferences 内部集合
    }

    private int getSavedLimitMinutes(String packageName) {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // 获取 SharedPreferences

        return preferences.getInt(KEY_LIMIT_PREFIX + packageName, DEFAULT_LIMIT_MINUTES);
        // 根据包名读取限制分钟数，如果没有保存过就返回默认 30 分钟
    }

    private void saveSelectedApps(List<AppInfo> appList) {
        Set<String> selectedPackageNames = new HashSet<>();
        // 保存当前选中的应用包名

        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // 获取 SharedPreferences

        SharedPreferences.Editor editor = preferences.edit();
        // 创建编辑器，用来写入数据

        for (AppInfo appInfo : appList) {
            String packageName = appInfo.getPackageName();
            // 获取当前应用包名

            if (appInfo.isSelected()) {
                selectedPackageNames.add(packageName);
                // 如果应用被选中，就保存它的包名

                editor.putInt(KEY_LIMIT_PREFIX + packageName, appInfo.getLimitMinutes());
                // 保存这个应用对应的限制分钟数
            } else {
                editor.remove(KEY_LIMIT_PREFIX + packageName);
                // 如果应用未选中，就删除它之前保存的限制分钟数
            }
        }

        editor.putStringSet(KEY_SELECTED_PACKAGES, selectedPackageNames);
        // 保存所有已选择应用的包名集合

        editor.apply();
        // 异步提交保存
    }

    private int countSelectedApps(List<AppInfo> appList) {
        int count = 0;
        // 计数器

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }
        // 统计 selected 为 true 的应用数量

        return count;
        // 返回已选择数量
    }
}