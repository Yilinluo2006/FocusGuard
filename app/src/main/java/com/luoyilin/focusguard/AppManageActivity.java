package com.luoyilin.focusguard;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.content.pm.ResolveInfo;

import android.graphics.drawable.Drawable;
// 引入 Drawable 类型，用来保存应用图标

import android.widget.TextView;
// 引入 TextView 控件，用来显示已选择数量

public class AppManageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manage);
        // 加载应用管理页面布局

        Button btnAddLimitedApp = findViewById(R.id.btnAddLimitedApp);
        // 找到“添加限制应用”按钮

        RecyclerView rvAppList = findViewById(R.id.rvAppList);
        // 找到用于显示应用列表的 RecyclerView

        List<AppInfo> appList = loadInstalledApps();
        // 从手机中读取真实已安装应用列表

        AppListAdapter adapter = new AppListAdapter(appList);
        // 创建适配器，把应用数据交给 RecyclerView 使用

        TextView tvSelectedCount = findViewById(R.id.tvSelectedCount);
// 找到显示已选择数量的 TextView

        adapter.setOnSelectionChangedListener(selectedCount ->
                tvSelectedCount.setText("已选择 " + selectedCount + " 个应用")
        );
// 当选择数量变化时，更新页面上的文字

        rvAppList.setLayoutManager(new LinearLayoutManager(this));
        // 设置 RecyclerView 使用竖向列表布局

        rvAppList.setAdapter(adapter);
        // 绑定适配器，让 RecyclerView 显示应用列表

        btnAddLimitedApp.setOnClickListener(v ->
                Toast.makeText(this, "已读取手机应用列表", Toast.LENGTH_SHORT).show()
        );
        // 点击按钮时弹出提示，后续这里会改成添加筛选或选择逻辑
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> appList = new ArrayList<>();
        // 创建一个空列表，用来保存最终要显示的应用

        PackageManager packageManager = getPackageManager();
        // 获取系统包管理器，用来读取应用信息

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        // 创建一个用于查找“可启动应用”的 Intent

        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // 只查找会出现在桌面启动器里的应用

        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        // 查询手机中所有可以从桌面启动的应用

        for (ResolveInfo resolveInfo : resolveInfoList) {
            ApplicationInfo applicationInfo = resolveInfo.activityInfo.applicationInfo;
            // 获取当前应用的 ApplicationInfo 信息

            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            // 如果是系统应用，就跳过，不加入列表

            String appName = packageManager.getApplicationLabel(applicationInfo).toString();
            // 获取应用名称，比如“微信”

            String packageName = applicationInfo.packageName;
            // 获取应用包名，比如 com.tencent.mm

            Drawable appIcon = packageManager.getApplicationIcon(applicationInfo);
// 获取应用图标

            appList.add(new AppInfo(appName, packageName, appIcon));
// 把应用名称、包名和图标保存到列表里
       }

        return appList;
        // 返回过滤后的用户应用列表
    }
}