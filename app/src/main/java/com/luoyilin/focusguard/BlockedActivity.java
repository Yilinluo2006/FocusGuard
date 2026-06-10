package com.luoyilin.focusguard;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BlockedActivity extends AppCompatActivity {

    public static final String EXTRA_APP_NAME = "extra_app_name";
    // Intent 传递应用名称时使用的 key

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);
        // 加载阻断提示页面

        TextView tvBlockedMessage = findViewById(R.id.tvBlockedMessage);
        // 找到提示文字控件

        String appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        // 读取被限制应用的名称

        if (appName == null || appName.trim().isEmpty()) {
            appName = "该应用";
        }
        // 如果没有传入应用名称，就使用默认文字

        tvBlockedMessage.setText(appName + " 今日使用时间已达到限制");
        // 显示阻断原因
    }
}