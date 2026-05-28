package com.luoyilin.focusguard;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable appIcon;
    private boolean selected;
    // 保存当前应用是否被选中

    public AppInfo(String appName, String packageName, Drawable appIcon) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.selected = false;
        // 默认新应用未被选中
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

    public boolean isSelected() {
        return selected;
        // 返回当前应用是否被选中
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        // 修改当前应用的选中状态
    }
}