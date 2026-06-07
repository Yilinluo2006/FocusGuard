package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PermissionGuideActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);
        // 加载使用情况访问权限引导页面

        Button btnOpenUsageSettings =
                findViewById(R.id.btnOpenUsageSettings);
        // 找到“前往系统设置”按钮

        btnOpenUsageSettings.setOnClickListener(v -> {
            Intent intent =
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            // 创建打开“使用情况访问权限”设置页的 Intent

            startActivity(intent);
            // 跳转到 Android 系统设置页面
        });
    }
}