package com.luoyilin.focusguard;

/** Pure rules for a voluntary morning arrival check-in. */
final class LocationCheckInPolicy {
    static final long MAX_AGE = 30_000;
    static String sampleError(double accuracy, long age, boolean mock, double radius) {
        if (mock) return "无法使用模拟位置签到";
        if (age < 0 || age > MAX_AGE) return "位置已过期，请重新定位";
        if (!Double.isFinite(accuracy) || accuracy <= 0 || accuracy > Math.min(100, radius / 2))
            return "定位精度不足，请到窗边或室外重试";
        return "";
    }
    static String rangeError(double distance, double accuracy, double targetAccuracy, double radius) {
        if (!Double.isFinite(distance) || !Double.isFinite(targetAccuracy) || targetAccuracy <= 0
                || !Double.isFinite(radius) || radius < 100 || radius > 1000) return "地点数据无效，请重新设置";
        if (distance > radius) return "尚未进入签到范围";
        return distance + accuracy + targetAccuracy > radius ? "当前位置接近范围边界，请靠近地点后重试" : "";
    }
    static boolean inWindow(int minute, int from, int to) { return from >= 0 && to <= 1440 && to > from && minute >= from && minute < to; }
    static double distance(double latA, double lonA, double latB, double lonB) {
        double a = Math.toRadians(latA), b = Math.toRadians(latB);
        double dLat = b - a, dLon = Math.toRadians(lonB - lonA);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(dLon / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }
}
