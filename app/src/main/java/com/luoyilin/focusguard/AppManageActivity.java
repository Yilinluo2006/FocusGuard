package com.luoyilin.focusguard;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppManageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manage);

        Button btnAddLimitedApp = findViewById(R.id.btnAddLimitedApp);
        RecyclerView rvAppList = findViewById(R.id.rvAppList);

        List<AppInfo> appList = new ArrayList<>();
        appList.add(new AppInfo("微信", "com.tencent.mm"));
        appList.add(new AppInfo("QQ", "com.tencent.mobileqq"));
        appList.add(new AppInfo("哔哩哔哩", "tv.danmaku.bili"));
        appList.add(new AppInfo("抖音", "com.ss.android.ugc.aweme"));
        appList.add(new AppInfo("小红书", "com.xingin.xhs"));

        AppListAdapter adapter = new AppListAdapter(appList);
        rvAppList.setLayoutManager(new LinearLayoutManager(this));
        rvAppList.setAdapter(adapter);

        btnAddLimitedApp.setOnClickListener(v ->
                Toast.makeText(this, "应用列表功能即将开发", Toast.LENGTH_SHORT).show()
        );
    }
}