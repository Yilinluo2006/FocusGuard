package com.luoyilin.focusguard;

import java.util.List;
import java.util.Locale;

/** Window metadata only. Ambiguous pages and unrelated apps do not trigger a reminder. */
final class SystemExitPagePolicy {
    static final int NONE = 0, UNINSTALL = 1, ACCESSIBILITY = 2;

    static boolean systemPackage(String pkg) {
        return "com.android.settings".equals(pkg) || "com.android.packageinstaller".equals(pkg)
                || "com.google.android.packageinstaller".equals(pkg) || "com.oplus.appinstaller".equals(pkg)
                || "com.oplus.safecenter".equals(pkg) || "com.coloros.safecenter".equals(pkg)
                || "com.oplus.appdetail".equals(pkg);
    }

    static boolean accessibilityContext(String type, List<String> titles) {
        if (lower(type).contains("accessibility")) return true;
        for (String title : titles) if ("无障碍".equals(title) || "已安装的服务".equals(title)
                || "已下载的应用".equals(title) || "accessibility".equals(lower(title))) return true;
        return false;
    }

    static boolean nodeLookupAllowed(String pkg, String type, List<String> titles, String appName,
                                     String appPackage, boolean accessibilityContext) {
        if (!systemPackage(pkg)) return false;
        String page = lower(type);
        if (page.contains("uninstall")) return true;
        if (!"com.android.settings".equals(pkg)) return false;
        if (page.contains("accessibility")) return true;
        if (!page.contains("subsettings") && !page.contains("preference")) return false;
        if (accessibilityContext) return true;
        for (String title : titles) if (title.equals(appName) || title.equals(appPackage)) return true;
        return false;
    }

    static int detect(String pkg, String type, List<String> titles, String appName,
                      String appPackage, boolean accessibilityContext) {
        if (!systemPackage(pkg)) return NONE;
        boolean own = false, uninstall = false;
        for (String title : titles) {
            String text = lower(title);
            boolean exit = text.contains("卸载") || text.contains("停用") || text.contains("关闭")
                    || text.contains("uninstall") || text.contains("disable");
            uninstall |= text.contains("卸载") || text.contains("uninstall");
            own |= title.equals(appName) || title.equals(appPackage)
                    || title.length() <= 160 && exit && !appName.isEmpty() && title.contains(appName);
        }
        if (!own) return NONE;
        String page = lower(type);
        if (page.contains("uninstall") || page.contains("appdetails") && uninstall) return UNINSTALL;
        if ("com.android.settings".equals(pkg) && (page.contains("accessibility")
                || accessibilityContext && (page.contains("subsettings") || page.contains("preference"))))
            return ACCESSIBILITY;
        return NONE;
    }

    private static String lower(String text) { return text == null ? "" : text.toLowerCase(Locale.ROOT); }
}
