package com.luoyilin.focusguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class PermissionGuideActivity extends AppCompatActivity {

    private TextView tvPermissionStatus;
    private TextView tvAccessibilityStatus;
    private TextView tvProtectionStatus;
    private TextView tvBatteryStatus;

    private Button btnOpenUsageSettings;
    private Button btnOpenAccessibilitySettings;
    private Button btnStartProtectionService;
    private Button btnOpenBatterySettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);
        // 加载保护状态中心页面。

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        // 找到四项保护状态对应的文字控件。

        btnOpenUsageSettings = findViewById(R.id.btnOpenUsageSettings);
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings);
        btnStartProtectionService = findViewById(R.id.btnStartProtectionService);
        btnOpenBatterySettings = findViewById(R.id.btnOpenBatterySettings);
        // 找到四项保护设置对应的操作按钮。

        btnOpenUsageSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            // 打开系统的“使用情况访问”设置页面。
        });

        btnOpenAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            // 打开无障碍设置，让用户开启 FocusGuard 服务。
        });

        btnStartProtectionService.setOnClickListener(v -> {
            startProtectionService();
            updatePermissionStatus();
            // 手动启动后台保护服务，并立即刷新页面状态。
        });

        btnOpenBatterySettings.setOnClickListener(v -> {
            openBatteryOptimizationSettings();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityServiceEnabled()) {
            ProtectionService.start(this);
        }
        updatePermissionStatus();
        tvProtectionStatus.postDelayed(this::updatePermissionStatus, 800L);
        // 页面首次出现或从系统设置返回时，重新检查全部保护状态。
    }

    private void updatePermissionStatus() {
        updateStatus(
                tvPermissionStatus,
                btnOpenUsageSettings,
                hasUsageAccessPermission(),
                "使用情况访问：已开启",
                "使用情况访问：未开启",
                "查看设置",
                "去开启"
        );

        updateAccessibilityStatus();

        updateStatus(
                tvProtectionStatus,
                btnStartProtectionService,
                ProtectionService.isRunning(this),
                "后台保护服务：运行中",
                "后台保护服务：未运行",
                "重新启动",
                "启动服务"
        );

        updateStatus(
                tvBatteryStatus,
                btnOpenBatterySettings,
                isIgnoringBatteryOptimizations(),
                "电池优化限制：已放行",
                "电池优化限制：可能影响后台运行",
                "查看设置",
                "去设置"
        );
    }

    private void updateStatus(
            TextView statusView,
            Button actionButton,
            boolean enabled,
            String enabledText,
            String disabledText,
            String enabledButtonText,
            String disabledButtonText
    ) {
        statusView.setText(enabled ? enabledText : disabledText);
        statusView.setTextColor(Color.parseColor(enabled ? "#15803D" : "#B91C1C"));
        actionButton.setText(enabled ? enabledButtonText : disabledButtonText);
        // 根据真实状态统一更新文字、颜色和按钮提示。
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        boolean healthy = enabled && FocusAccessibilityService.isRunning(this);

        if (healthy) {
            tvAccessibilityStatus.setText("强制限制服务：运行中");
            tvAccessibilityStatus.setTextColor(Color.parseColor("#15803D"));
            btnOpenAccessibilitySettings.setText("查看设置");
        } else if (enabled) {
            tvAccessibilityStatus.setText("强制限制服务：已授权，正在连接");
            tvAccessibilityStatus.setTextColor(Color.parseColor("#B45309"));
            btnOpenAccessibilitySettings.setText("检查设置");
        } else {
            tvAccessibilityStatus.setText("强制限制服务：未开启");
            tvAccessibilityStatus.setTextColor(Color.parseColor("#B91C1C"));
            btnOpenAccessibilitySettings.setText("去开启");
        }
    }

    private void startProtectionService() {
        ProtectionService.start(this);
        tvProtectionStatus.postDelayed(this::updatePermissionStatus, 800L);
    }

    private void openBatteryOptimizationSettings() {
        Intent intent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !isIgnoringBatteryOptimizations()) {
            intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        }

        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        PowerManager powerManager =
                (PowerManager) getSystemService(Context.POWER_SERVICE);

        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        // 判断 FocusGuard 是否已被系统电池优化策略放行。
    }

    private boolean hasUsageAccessPermission() {
        AppOpsManager appOpsManager =
                (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);

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
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
        }

        return mode == AppOpsManager.MODE_ALLOWED;
        // AppOpsManager 会返回当前应用是否获准读取使用情况数据。
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (accessibilityManager == null) {
            return false;
        }

        List<AccessibilityServiceInfo> enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                );

        String packageName = getPackageName();
        String serviceClassName = FocusAccessibilityService.class.getName();

        for (AccessibilityServiceInfo serviceInfo : enabledServices) {
            if (serviceInfo.getResolveInfo() == null
                    || serviceInfo.getResolveInfo().serviceInfo == null) {
                continue;
            }

            String enabledPackage =
                    serviceInfo.getResolveInfo().serviceInfo.packageName;
            String enabledClass =
                    serviceInfo.getResolveInfo().serviceInfo.name;

            if (packageName.equals(enabledPackage)
                    && serviceClassName.equals(enabledClass)) {
                return true;
            }
        }

        return false;
        // 在系统已开启的无障碍服务中寻找 FocusGuard 服务。
    }
}
