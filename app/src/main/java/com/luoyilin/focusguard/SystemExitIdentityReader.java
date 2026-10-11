package com.luoyilin.focusguard;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Reads only known identity fields on an already-qualified system exit page. Never retains nodes or text. */
final class SystemExitIdentityReader {
    private SystemExitIdentityReader() { }

    static int detect(AccessibilityNodeInfo root, String pkg, String type, String appName,
                      String appPackage, int eventWindowId) {
        if (root == null) return SystemExitPagePolicy.NONE;
        try {
            if (!pkg.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
                    || root.getWindowId() != eventWindowId) return SystemExitPagePolicy.NONE;
            if (type.toLowerCase(java.util.Locale.ROOT).contains("uninstall")) {
                String[] ids = "com.oplus.appdetail".equals(pkg)
                        ? new String[]{"com.oplus.appdetail:id/app_name"}
                        : new String[]{pkg + ":id/app_name", "android:id/alertTitle"};
                for (String id : ids) if (identity(root, id, appName, appPackage, false))
                    return SystemExitPagePolicy.UNINSTALL;
            } else if ("com.android.settings".equals(pkg)
                    && identity(root, "android:id/title", appName, appPackage, true)) {
                return SystemExitPagePolicy.ACCESSIBILITY;
            }
            return SystemExitPagePolicy.NONE;
        } finally { root.recycle(); }
    }

    private static boolean identity(AccessibilityNodeInfo root, String id, String name, String pkg,
                                    boolean requireServiceSwitch) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null) return false;
        boolean matched = false;
        try {
            for (int i = 0; i < Math.min(nodes.size(), 16); i++) {
                AccessibilityNodeInfo node = nodes.get(i);
                if (node == null || !node.isVisibleToUser()) continue;
                CharSequence text = node.getText();
                if (text == null || text.length() > 160 || !(name.contentEquals(text) || pkg.contentEquals(text))) continue;
                if (!requireServiceSwitch || serviceSwitch(node)) { matched = true; break; }
            }
            return matched;
        } finally { for (AccessibilityNodeInfo node : nodes) if (node != null) node.recycle(); }
    }

    private static boolean serviceSwitch(AccessibilityNodeInfo label) {
        android.graphics.Rect labelBounds = new android.graphics.Rect();
        label.getBoundsInScreen(labelBounds);
        AccessibilityNodeInfo parent = label.getParent();
        try {
            // The service-name row must own its switch. An installed-services list or toolbar is insufficient.
            for (int depth = 0; parent != null && depth < 3; depth++) {
                List<AccessibilityNodeInfo> switches = parent.findAccessibilityNodeInfosByViewId("android:id/switch_widget");
                boolean found = false;
                if (switches != null) {
                    for (AccessibilityNodeInfo toggle : switches) {
                        if (toggle != null) {
                            android.graphics.Rect bounds = new android.graphics.Rect(); toggle.getBoundsInScreen(bounds);
                            found |= toggle.isVisibleToUser() && toggle.isCheckable()
                                    && bounds.bottom > labelBounds.top && bounds.top < labelBounds.bottom;
                            toggle.recycle();
                        }
                    }
                }
                if (found) return true;
                AccessibilityNodeInfo ancestor = parent.getParent();
                parent.recycle(); parent = ancestor;
            }
            return false;
        } finally { if (parent != null) parent.recycle(); }
    }
}
