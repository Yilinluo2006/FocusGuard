package com.luoyilin.focusguard;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

final class RecordedPageNodes {
    private RecordedPageNodes() { }
    static final class CaptureResult {
        final List<RecordedPageRule.Anchor> anchors;
        final int visited, visible;
        final boolean truncated;
        CaptureResult(List<RecordedPageRule.Anchor> anchors, int visited, int visible, boolean truncated) {
            this.anchors = anchors; this.visited = visited; this.visible = visible; this.truncated = truncated;
        }
        String emptyReason() {
            if (visible == 0) return "此页面未向无障碍提供可见控件，当前无法录入。可稍候重试或选择其他页面。";
            return truncated ? "页面控件较多，尚未找到稳定特征。请滚动到标题或导航区域后重试。"
                    : "已读取当前页面，但没有适合录入的短标题或按钮。请尝试显示页面导航后重试。";
        }
    }
    private static final class Entry {
        final AccessibilityNodeInfo node; final boolean navigationChild;
        Entry(AccessibilityNodeInfo node, boolean navigationChild) { this.node = node; this.navigationChild = navigationChild; }
    }
    static String value(CharSequence s) { return s == null ? "" : s.toString(); }
    static List<RecordedPageRule.Anchor> capture(AccessibilityNodeInfo root) {
        return captureResult(root).anchors;
    }
    static CaptureResult captureResult(AccessibilityNodeInfo root) {
        List<RecordedPageRule.Anchor> result = new ArrayList<>();
        ArrayDeque<Entry> queue = new ArrayDeque<>(); queue.add(new Entry(root, false));
        Set<String> seen = new HashSet<>(); int count = 0, visible = 0; boolean truncated = false;
        try {
            while (!queue.isEmpty() && count++ < 240 && result.size() < 24) {
                Entry entry = queue.remove(); AccessibilityNodeInfo node = entry.node;
                try {
                    if (node.isPassword() || node.isEditable()) continue;
                    if (node.isVisibleToUser()) visible++;
                    String id = value(node.getViewIdResourceName()), widget = value(node.getClassName());
                    String lower = id.toLowerCase(Locale.ROOT);
                    // Prefer headings/navigation/buttons. Avoid text editors and message-body fields.
                    boolean candidate = node.isHeading() || node.isSelected() || widget.endsWith("Button") || node.isClickable() || entry.navigationChild
                            || lower.contains("title") || lower.contains("app_name") || lower.contains("tab") || lower.contains("toolbar");
                    if (node.isVisibleToUser() && candidate && !lower.contains("message") && !lower.contains("chat_content")) {
                        CharSequence text = node.getText(); boolean description = false;
                        if (text == null || text.length() == 0) { text = node.getContentDescription(); description = true; }
                        if (text != null && text.length() > 0 && text.length() <= 80) {
                            String key = id + "\n" + text + "\n" + widget;
                            if (seen.add(key)) result.add(new RecordedPageRule.Anchor(id, text.toString(), widget, description, node.isSelected(),
                                    node.isSelected() ? 90 : node.isHeading() || lower.contains("app_name") ? 70 : lower.contains("title") ? 60 : 0));
                        }
                    }
                    // Some apps expose a clickable layout with a non-clickable text child.
                    // Only inherit one level from small navigation containers, not entire content lists.
                    boolean navigation = node.isVisibleToUser() && node.isClickable() && node.getChildCount() <= 4;
                    if (node.getChildCount() > 32) truncated = true;
                    for (int i = 0; i < Math.min(node.getChildCount(), 32) && queue.size() < 240; i++) {
                        AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(new Entry(child, navigation));
                    }
                } finally { node.recycle(); }
            }
            return new CaptureResult(result, count, visible, truncated || !queue.isEmpty());
        } finally { while (!queue.isEmpty()) queue.remove().node.recycle(); }
    }
    static boolean matches(AccessibilityNodeInfo root, RecordedPageRule rule) {
        for (RecordedPageRule.Anchor anchor : rule.anchors) {
            List<AccessibilityNodeInfo> nodes = anchor.viewId.isEmpty()
                    ? root.findAccessibilityNodeInfosByText(anchor.text) : root.findAccessibilityNodeInfosByViewId(anchor.viewId);
            boolean found = false;
            if (nodes == null) return false;
            try {
                for (int i = 0; i < Math.min(nodes.size(), 32); i++) {
                    AccessibilityNodeInfo node = nodes.get(i);
                    if (node == null || !node.isVisibleToUser() || node.isPassword() || node.isEditable()) continue;
                    CharSequence text = anchor.description ? node.getContentDescription() : node.getText();
                    if ((!anchor.selected || node.isSelected()) && text != null && text.length() <= 80 && anchor.matches(value(node.getViewIdResourceName()),
                            text.toString(), value(node.getClassName()))) { found = true; break; }
                }
            } finally { for (AccessibilityNodeInfo node : nodes) if (node != null) node.recycle(); }
            if (!found) return false;
        }
        return !rule.anchors.isEmpty();
    }
}
