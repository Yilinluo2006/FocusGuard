package com.luoyilin.focusguard;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private static final int DEFAULT_LIMIT_MINUTES = 30;
    // 默认限制时间：30 分钟

    private String appName;
    private String packageName;
    private Drawable appIcon;
    private boolean selected;
    private int limitMinutes;
    private long todayUsageMillis;
    // 保存应用名称、包名、图标、是否选中、限制分钟数、今日使用时长

    public AppInfo(String appName, String packageName, Drawable appIcon) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.selected = false;
        this.limitMinutes = 0;
        this.todayUsageMillis = 0;
        // 创建应用对象时，默认未选中、未限制、今日使用时长为 0
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
        // 更新当前应用是否被选中

        if (selected && limitMinutes == 0) {
            limitMinutes = DEFAULT_LIMIT_MINUTES;
        }
        // 如果应用被选中且还没有限制时间，就默认设置为 30 分钟

        if (!selected) {
            limitMinutes = 0;
        }
        // 如果应用取消选中，就清空限制时间
    }

    public int getLimitMinutes() {
        return limitMinutes;
        // 返回每日限制分钟数
    }

    public void setLimitMinutes(int limitMinutes) {
        this.limitMinutes = limitMinutes;
        // 设置每日限制分钟数
    }

    public long getTodayUsageMillis() {
        return todayUsageMillis;
        // 返回今日使用时长，单位是毫秒
    }

    public void setTodayUsageMillis(long todayUsageMillis) {
        this.todayUsageMillis = todayUsageMillis;
        // 设置今日使用时长
    }

    public String getUsageAndLimitText() {
        return "今日使用：" + formatUsageTime(todayUsageMillis) + " / " + getLimitText();
        // 返回列表中展示的使用时长和限制时间
    }

    public String getLimitText() {
        if (limitMinutes <= 0) {
            return "未限制";
        }
        // 如果限制分钟数小于等于 0，显示未限制

        return "限制：" + limitMinutes + " 分钟";
        // 返回限制时间文字
    }

    private String formatUsageTime(long usageTimeMillis) {
        long totalMinutes = usageTimeMillis / (1000 * 60);
        // 把毫秒转换成分钟

        if (totalMinutes < 60) {
            return totalMinutes + " 分钟";
        }
        // 小于 1 小时时，只显示分钟

        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        // 拆分小时和剩余分钟

        return hours + " 小时 " + minutes + " 分钟";
        // 大于等于 1 小时时，显示小时和分钟
    }
}