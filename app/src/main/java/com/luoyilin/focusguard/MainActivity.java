//首页逻辑

package com.luoyilin.focusguard;

import android.os.Bundle;
import android.widget.Button;
//import android.widget.Toast; 被替换后不需要了//

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent; //引入 Intent，它用于从一个页面跳转到另一个页面。//

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Button btnManageApps = findViewById(R.id.btnManageApps);

        Button btnUsagePermission =
                findViewById(R.id.btnUsagePermission);
// 找到权限设置按钮

        btnManageApps.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppManageActivity.class);
            startActivity(intent);
        });     //跳转到AppManageActivity

        btnUsagePermission.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    PermissionGuideActivity.class
            );
            // 创建从首页跳转到权限引导页的 Intent

            startActivity(intent);
            // 打开权限引导页
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}