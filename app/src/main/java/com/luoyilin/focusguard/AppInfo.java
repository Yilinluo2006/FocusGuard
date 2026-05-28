package com.luoyilin.focusguard;

public class AppInfo {
    private String appName;
    private String packageName;

    public AppInfo(String appName, String packageName) {
        this.appName = appName;
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }
}
