package com.luoyilin.focusguard;

import android.app.Application;

import com.luoyilin.focusguard.network.RetrofitClient;

public class FocusGuardApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 先执行 Android Application 原本的初始化逻辑

        RetrofitClient.initialize(this);
        // 应用启动时初始化 Retrofit，并安装 JWT 拦截器
    }
}