package com.luoyilin.focusguard.network;

public class AppLimitResponse {

    private Long id;
    // 保存后端数据库中的限制记录 ID

    private String packageName;
    // 保存被限制应用的包名

    private int limitMinutes;
    // 保存每天允许使用的分钟数

    private boolean enabled;
    // 保存这条限制是否启用

    public Long getId() {
        return id;
    }
    // 返回限制记录 ID

    public String getPackageName() {
        return packageName;
    }
    // 返回应用包名

    public int getLimitMinutes() {
        return limitMinutes;
    }
    // 返回限制分钟数

    public boolean isEnabled() {
        return enabled;
    }
    // 返回限制是否启用
}