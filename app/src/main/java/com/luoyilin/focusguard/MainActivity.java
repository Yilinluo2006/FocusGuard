package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.widget.Toast;

import com.luoyilin.focusguard.sync.LimitSyncManager;
// 统一负责从后端下载限制规则并保存到 SharedPreferences

//        跳转到应用管理页
//        跳转到权限引导页
//        显示所有受限应用的今日总使用时长
//        处理状态栏和导航栏边距

import com.luoyilin.focusguard.auth.SessionManager;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_NAME = "focus_guard_prefs";
    // SharedPreferences 文件名

    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    // 已选择应用包名集合的 key

    private TextView tvTodayUsage;
    // 保存首页“今日使用时长”控件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);
        // 创建登录状态管理对象

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(
                    MainActivity.this,
                    LoginActivity.class
            );
            // 创建跳转到登录页面的 Intent

            startActivity(intent);
            // 打开登录页面

            finish();
            // 关闭当前首页，防止按返回键绕过登录页面

            return;
            // 停止继续执行首页的初始化代码
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // 加载首页布局

        tvTodayUsage = findViewById(R.id.tvTodayUsage);
        // 找到显示今日使用时长的 TextView

        Button btnManageApps = findViewById(R.id.btnManageApps);
        // 找到应用限制管理按钮

        Button btnUsagePermission = findViewById(R.id.btnUsagePermission);
        // 找到使用情况访问权限按钮

        btnManageApps.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppManageActivity.class);
            // 创建跳转到应用限制管理页面的 Intent

            startActivity(intent);
            // 打开应用限制管理页面
        });

        btnUsagePermission.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PermissionGuideActivity.class);
            // 创建跳转到权限引导页面的 Intent

            startActivity(intent);
            // 打开权限引导页面
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 获取状态栏和导航栏占用区域

            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            // 给页面设置内边距，避免内容被系统栏遮挡

            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次首页显示时都会执行，例如首次打开应用或从其他页面返回

        updateTodayUsage();
        // 先使用当前本地缓存更新页面，避免等待网络时页面没有内容

        LimitSyncManager.syncFromServer(
                this,
                new LimitSyncManager.SyncCallback() {

                    @Override
                    public void onSuccess(int enabledCount) {
                        updateTodayUsage();
                        // 同步完成后重新计算，因为受限制应用可能已经发生变化

                        Toast.makeText(
                                MainActivity.this,
                                "已同步 " + enabledCount + " 个限制",
                                Toast.LENGTH_SHORT
                        ).show();
                        // 显示本次从后端同步到的启用规则数量
                    }

                    @Override
                    public void onFailure(String message) {
                        Toast.makeText(
                                MainActivity.this,
                                "同步失败，继续使用本地配置：" + message,
                                Toast.LENGTH_LONG
                        ).show();
                        // 网络失败时不清空旧规则，继续使用上一次成功同步的数据
                    }
                }
        );
        // 异步请求后端，不会阻塞安卓界面
    }

    private void updateTodayUsage() {
        if (!hasUsageAccessPermission()) {
            tvTodayUsage.setText("请先开启权限");
            return;
        }
        // 没有使用情况访问权限时，显示权限提示

        long usageTimeMillis = getTodayTotalUsageTime();
        // 获取已限制应用的今日总使用时间

        tvTodayUsage.setText(formatUsageTime(usageTimeMillis));
        // 把毫秒转换成小时和分钟后显示
    }

    private long getTodayTotalUsageTime() {
        Set<String> selectedPackages = getSavedSelectedPackageNames();
        // 读取已选择限制的应用包名集合

        if (selectedPackages.isEmpty()) {
            return 0;
        }
        // 如果还没有选择任何限制应用，返回 0

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        // 获取 UsageStatsManager 系统服务

        if (usageStatsManager == null) {
            return 0;
        }
        // 无法获取系统服务时返回 0

        Calendar calendar = Calendar.getInstance();
        // 获取当前日期和时间

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // 将时间调整到今天凌晨 00:00

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();
        // 设置统计时间范围：今天凌晨到当前时间

        Map<String, UsageStats> usageStatsMap =
                usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        // 按包名查询并合并今天的应用使用数据

        if (usageStatsMap == null || usageStatsMap.isEmpty()) {
            return 0;
        }
        // 没有读取到数据时返回 0

        long totalUsageTime = 0;

        for (Map.Entry<String, UsageStats> entry : usageStatsMap.entrySet()) {
            String packageName = entry.getKey();
            // 获取当前统计项的包名

            if (!selectedPackages.contains(packageName)) {
                continue;
            }
            // 只统计已勾选的受限应用

            totalUsageTime += entry.getValue().getTotalTimeInForeground();
            // 累加该应用在前台的使用时间
        }

        return totalUsageTime;
        // 返回已限制应用总使用时长，单位为毫秒
    }

    private Set<String> getSavedSelectedPackageNames() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // 获取本地配置

        Set<String> savedSet = preferences.getStringSet(KEY_SELECTED_PACKAGES, new HashSet<>());
        // 读取已选择应用包名集合

        return new HashSet<>(savedSet);
        // 返回新集合，避免直接修改 SharedPreferences 内部数据
    }

    private String formatUsageTime(long usageTimeMillis) {
        long totalMinutes = usageTimeMillis / (1000 * 60);
        // 把毫秒转换成总分钟数

        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        // 分别计算小时和剩余分钟数

        return hours + " 小时 " + minutes + " 分钟";
        // 返回适合页面显示的文字
    }

    private boolean hasUsageAccessPermission() {
        AppOpsManager appOpsManager =
                (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        // 获取特殊权限管理服务

        if (appOpsManager == null) {
            return false;
        }

        int mode;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
            // Android 10 及以上检查方法
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
            // Android 10 以下兼容方法
        }

        return mode == AppOpsManager.MODE_ALLOWED;
        // 返回权限是否已经开启
    }
}