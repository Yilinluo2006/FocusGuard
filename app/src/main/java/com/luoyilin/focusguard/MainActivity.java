package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;



//        跳转到应用管理页
//        跳转到权限引导页
//        显示所有受限应用的今日总使用时长
//        处理状态栏和导航栏边距


public class MainActivity extends AppCompatActivity {
    private HomeNavigation navigation;
    private ThemeReveal.Cover themeReveal;
    @Override protected void onSaveInstanceState(Bundle out) {
        if (navigation != null) navigation.save(out);
        super.onSaveInstanceState(out);
    }
    private final android.os.Handler protectionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long shownQuotaDay = Long.MIN_VALUE;
    private boolean protectionWarningShown;
    private androidx.appcompat.app.AlertDialog protectionDialog;
    private final SharedPreferences.OnSharedPreferenceChangeListener syncChanges = (prefs, key) -> {
        if (navigation != null && ("revision".equals(key) || "pendingSleep".equals(key))) navigation.refresh();
    };
    private final Runnable protectionCheck = new Runnable() {
        @Override public void run() {
            updateAccessibilityStatus();
            long quotaDay = StreakLedger.day(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
            if (shownQuotaDay != quotaDay) {
                shownQuotaDay = quotaDay;
                updateTodayUsage();
                if (navigation != null) navigation.refresh();
            }
            if (navigation != null) navigation.refreshStatus();
            StreakUi.refresh(MainActivity.this);
            ProtectionWarning.refresh(MainActivity.this);
            ProtectionCapabilities availability = ProtectionRecovery.capabilities(MainActivity.this);
            boolean healthy = availability.daily && availability.sleep && availability.focus;
            if (!healthy) CoverageStore.sample(MainActivity.this, false);
            if (healthy) {
                protectionWarningShown = false;
                if (protectionDialog != null) protectionDialog.dismiss();
            } else if (!protectionWarningShown && ProtectionRecovery.needed(MainActivity.this)) {
                protectionWarningShown = true;
                protectionDialog = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("保护已暂停")
                        .setMessage(ProtectionRecovery.explanation(MainActivity.this))
                        .setNegativeButton("稍后处理", null)
                        .setPositiveButton("去恢复保护", (d, w) -> startActivity(
                                new Intent(MainActivity.this, ProtectionRecoveryActivity.class))).show();
            }
            protectionHandler.postDelayed(this, 5000);
        }
    };

    @Override protected void onPause() {
        DeviceSyncManager.prefs(this).unregisterOnSharedPreferenceChangeListener(syncChanges);
        if (themeReveal != null) themeReveal.finish();
        protectionHandler.removeCallbacks(protectionCheck);
        if (protectionDialog != null) protectionDialog.dismiss();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (themeReveal != null) themeReveal.finish();
        if (navigation != null) navigation.dispose();
        super.onDestroy();
    }
    private static final String PREF_NAME = "focus_guard_prefs";
    // SharedPreferences 文件名

    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    // 已选择应用包名集合的 key

    private TextView tvTodayUsage;
    private View usageSummaryCard;
    // 保存首页“今日使用时长”控件

    private TextView tvCurrentAccount;
    private Button btnUsagePermission;
    // 保存权限管理按钮，便于页面重新显示时刷新无障碍服务状态
    // 保存首页“当前账号”控件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btnAppearance).setOnClickListener(v -> Appearance.choose(this));
        findViewById(R.id.btnFocusMode).setOnClickListener(v ->
                startActivity(new Intent(this, FocusModeActivity.class)));
        // 加载首页布局

        tvTodayUsage = findViewById(R.id.tvTodayUsage);
        usageSummaryCard = findViewById(R.id.usageSummaryCard);
        // 找到显示今日使用时长的 TextView

        tvCurrentAccount = findViewById(R.id.tvCurrentAccount);
        // 找到显示当前账号的 TextView

        tvCurrentAccount.setText("本地模式 · 无需登录");
        Button btnManageApps = findViewById(R.id.btnManageApps);
        // 找到应用限制管理按钮

        btnUsagePermission = findViewById(R.id.btnUsagePermission);
        // 找到使用情况访问权限按钮

        Button btnLogout = findViewById(R.id.btnLogout);
        // 保留原控件 ID，入口改为本机保存状态页。

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

        btnLogout.setOnClickListener(v ->
                startActivity(new Intent(this, AccountBackupActivity.class)));

        navigation = new HomeNavigation(this, savedInstanceState, () -> {
            updateTodayUsage();
            StreakUi.refresh(this);
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 获取状态栏和导航栏占用区域

            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            );
            // 给页面设置内边距，避免内容被系统栏遮挡

            return insets;
        });
        themeReveal = ThemeReveal.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProtectionRecovery.delayed(this, "app_resume");
        DeviceSyncManager.prefs(this).registerOnSharedPreferenceChangeListener(syncChanges);
        com.luoyilin.focusguard.auth.SessionManager account = new com.luoyilin.focusguard.auth.SessionManager(this);
        tvCurrentAccount.setText(account.isLoggedIn() ? account.getUsername() : account.isSessionExpired() ? "登录已过期" : "未登录");
        CloudBackupManager.reconcile(this);
        DeviceSyncManager.request(this);
        // 每次首页显示时都会执行，例如首次打开应用或从其他页面返回

        if (FocusAccessibilityService.isEnabled(this)) {
            ProtectionService.start(this);
        }
        updateAccessibilityStatus();
        protectionHandler.removeCallbacks(protectionCheck);
        protectionHandler.postDelayed(protectionCheck, 2000L);
        // 页面可见时主动确保前台保护运行，并在跨进程启动完成后再次刷新。

        updateTodayUsage();
        shownQuotaDay = StreakLedger.day(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        StreakUi.refresh(this);
        // 先使用当前本地缓存更新页面，避免等待网络时页面没有内容

        if (navigation != null) navigation.refresh();
        if (SetupStatus.claimIntroduction(this)) {
            startActivity(new Intent(this, PermissionGuideActivity.class)
                    .putExtra(PermissionGuideActivity.EXTRA_FIRST_SETUP, true));
        }
    }

    private void updateAccessibilityStatus() {
        if (btnUsagePermission == null) {
            return;
        }

        SetupStatus setup = new SetupStatus(this);
        int complete = setup.completed();
        btnUsagePermission.setText(complete == 4 ? "查看必要设置  ›" : "去开启必要设置  ›");
        TextView entryStatus = findViewById(R.id.setupEntryStatus);
        if (entryStatus != null) entryStatus.setText(complete == 4 ? "已就绪" : complete + " / 4 已完成");
    }

    private void updateTodayUsage() {
        if (!hasUsageAccessPermission()) {
            tvTodayUsage.setText("请先开启权限");
            usageSummaryCard.setContentDescription("受限应用使用汇总，使用情况权限未开启");
            return;
        }
        // 没有使用情况访问权限时，显示权限提示

        long usageTimeMillis = getTodayTotalUsageTime();
        // 获取已限制应用的今日总使用时间

        tvTodayUsage.setText(MetricText.emphasize(formatUsageTime(usageTimeMillis), 1.2f));
        usageSummaryCard.setContentDescription("受限应用今日已用 "
                + formatUsageTime(usageTimeMillis));
        // 把毫秒转换成小时和分钟后显示
    }

    private long getTodayTotalUsageTime() {
        Set<String> selectedPackages = getSavedSelectedPackageNames();
        if (selectedPackages.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        Map<String, Long> usage = AppUsageReader.totals(this, now, selectedPackages);
        if (usage == null) return 0;
        if (DeviceSyncManager.strictQuota(this)) usage = DeviceSyncManager.combinedToday(this, usage);
        long total = 0;
        for (String pkg : selectedPackages) total += usage.getOrDefault(pkg, 0L);
        if (DeviceSyncManager.strictQuota(this)) return total;
        long start = StreakLedger.boundary(StreakLedger.day(now, java.time.ZoneId.systemDefault()),
                java.time.ZoneId.systemDefault());
        return Math.max(0, Math.min(total, now - start));
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
