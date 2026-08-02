package com.luoyilin.focusguard.network;

public class AppLimitRequest {

    private final String packageName;
    // 要限制的应用包名

    private final int limitMinutes;
    // 每天允许使用的分钟数

    private final boolean enabled;
    // 该限制是否启用

    public AppLimitRequest(
            String packageName,
            int limitMinutes,
            boolean enabled
    ) {
        this.packageName = packageName;
        this.limitMinutes = limitMinutes;
        this.enabled = enabled;
    }
    // 构造方法：创建准备发送给后端的限制数据

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