package com.luoyilin.focusguard;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable appIcon;
    // 保存应用图标

    public AppInfo(String appName, String packageName, Drawable appIcon) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        // 创建应用信息对象时，同时保存名称、包名和图标
    }

    public String getAppName() {
        return appName;
        // 返回应用名称
    }

    public String getPackageName() {
        return packageName;
        // 返回应用包名
    }

    public Drawable getAppIcon() {
        return appIcon;
        // 返回应用图标
    }
}