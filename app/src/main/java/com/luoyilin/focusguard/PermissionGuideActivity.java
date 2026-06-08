package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PermissionGuideActivity extends AppCompatActivity {
    private TextView tvPermissionStatus;
    private Button btnOpenUsageSettings;
    // 保存状态文字和设置按钮控件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);
        // 加载权限引导页面

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        // 找到显示权限状态的 TextView

        btnOpenUsageSettings = findViewById(R.id.btnOpenUsageSettings);
        // 找到前往系统设置的按钮

        btnOpenUsageSettings.setOnClickListener(v -> {

            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            // 创建打开使用情况访问设置页的 Intent

            startActivity(intent);
            // 跳转到系统设置
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        updatePermissionStatus();
        // 页面出现或从系统设置返回时，重新检查权限
    }

    private void updatePermissionStatus() {
        boolean permissionGranted = hasUsageAccessPermission();
        // 检查使用情况访问权限是否已开启

        if (permissionGranted) {
            tvPermissionStatus.setText("权限已开启");
            tvPermissionStatus.setTextColor(Color.parseColor("#16A34A"));
            btnOpenUsageSettings.setText("查看系统设置");
            // 权限已开启时显示绿色状态
        } else {
            tvPermissionStatus.setText("权限未开启");
            tvPermissionStatus.setTextColor(Color.parseColor("#DC2626"));
            btnOpenUsageSettings.setText("前往系统设置");
            // 权限未开启时显示红色状态
        }
    }

    private boolean hasUsageAccessPermission() {

        AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        // 获取 AppOpsManager，用来检查特殊权限状态

        if (appOpsManager == null) {
            return false;
        }
        // 如果无法获得系统服务，则认为权限未开启

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
        // MODE_ALLOWED 表示用户已经开启权限
    }
}