package com.luoyilin.focusguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
    private Button btnOpenUsageSettings;
    private Button btnOpenAccessibilitySettings;
    // 保存两项权限的状态文字和设置按钮

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);
        // 加载权限引导页面

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        // 找到两项权限的状态文字

        btnOpenUsageSettings = findViewById(R.id.btnOpenUsageSettings);
        btnOpenAccessibilitySettings =
                findViewById(R.id.btnOpenAccessibilitySettings);
        // 找到两个系统设置按钮

        btnOpenUsageSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            // 创建打开使用情况访问设置页的 Intent

            startActivity(intent);
            // 跳转到使用情况访问设置
        });

        btnOpenAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            // 创建打开无障碍设置页的 Intent

            startActivity(intent);
            // 跳转到无障碍设置，让用户手动开启 FocusGuard
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        updatePermissionStatus();
        // 页面出现或从系统设置返回时，重新检查两项权限
    }

    private void updatePermissionStatus() {
        boolean usageAccessGranted = hasUsageAccessPermission();
        // 检查使用情况访问权限

        if (usageAccessGranted) {
            tvPermissionStatus.setText("使用情况访问：已开启");
            tvPermissionStatus.setTextColor(Color.parseColor("#16A34A"));
            btnOpenUsageSettings.setText("查看使用情况设置");
        } else {
            tvPermissionStatus.setText("使用情况访问：未开启");
            tvPermissionStatus.setTextColor(Color.parseColor("#DC2626"));
            btnOpenUsageSettings.setText("开启使用情况访问");
        }
        // 根据权限状态更新文字、颜色和按钮内容

        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        // 检查 FocusGuard 无障碍服务

        if (accessibilityEnabled) {
            tvAccessibilityStatus.setText("强制限制服务：已开启");
            tvAccessibilityStatus.setTextColor(Color.parseColor("#16A34A"));
            btnOpenAccessibilitySettings.setText("查看无障碍设置");
        } else {
            tvAccessibilityStatus.setText("强制限制服务：未开启");
            tvAccessibilityStatus.setTextColor(Color.parseColor("#DC2626"));
            btnOpenAccessibilitySettings.setText("开启强制限制服务");
        }
        // 根据无障碍服务状态更新文字、颜色和按钮内容
    }

    private boolean hasUsageAccessPermission() {
        AppOpsManager appOpsManager =
                (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        // 获取特殊权限管理服务

        if (appOpsManager == null) {
            return false;
        }
        // 无法获得系统服务时认为权限未开启

        int mode;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
            // Android 10 及以上使用新的检查方法
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
            // Android 10 以下使用兼容检查方法
        }

        return mode == AppOpsManager.MODE_ALLOWED;
        // MODE_ALLOWED 表示使用情况访问权限已开启
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getSystemService(
                        Context.ACCESSIBILITY_SERVICE
                );
        // 获取系统无障碍服务管理器

        if (accessibilityManager == null
                || !accessibilityManager.isEnabled()) {
            return false;
        }
        // 系统无障碍功能整体未启用时直接返回 false

        List<AccessibilityServiceInfo> enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                );
        // 获取当前手机已经启用的所有无障碍服务

        String targetServiceName =
                FocusAccessibilityService.class.getName();
        // 获取 FocusGuard 无障碍服务的完整类名

        for (AccessibilityServiceInfo serviceInfo : enabledServices) {
            if (serviceInfo.getResolveInfo() == null
                    || serviceInfo.getResolveInfo().serviceInfo == null) {
                continue;
            }
            // 跳过缺少服务信息的无效记录

            String packageName =
                    serviceInfo.getResolveInfo().serviceInfo.packageName;
            String serviceName =
                    serviceInfo.getResolveInfo().serviceInfo.name;
            // 读取当前已启用服务的包名和类名

            if (getPackageName().equals(packageName)
                    && targetServiceName.equals(serviceName)) {
                return true;
            }
            // 包名和类名都匹配时，说明 FocusGuard 服务已开启
        }

        return false;
        // 遍历后仍未找到，说明服务没有开启
    }
}
