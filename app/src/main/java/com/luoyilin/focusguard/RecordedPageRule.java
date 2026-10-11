package com.luoyilin.focusguard;

import java.util.*;

/** The small set of page features explicitly chosen by the user, rather than a saved page tree. */
final class RecordedPageRule {
    final String id, name, pkg, type;
    final boolean system, enabled, classOnly, restrictSystem;
    final List<Anchor> anchors;
    RecordedPageRule(String id, String name, String pkg, String type, boolean system, boolean enabled, List<Anchor> anchors) {
        this(id, name, pkg, type, system, enabled, anchors, false, false);
    }
    RecordedPageRule(String id, String name, String pkg, String type, boolean system, boolean enabled, List<Anchor> anchors,
                     boolean classOnly, boolean restrictSystem) {
        this.id = id; this.name = name; this.pkg = pkg; this.type = type;
        this.system = system; this.enabled = enabled; this.classOnly = classOnly; this.restrictSystem = restrictSystem;
        this.anchors = Collections.unmodifiableList(new ArrayList<>(anchors));
    }
    static final class Anchor {
        final String viewId, text, widget;
        final boolean description, selected;
        final int priority;
        Anchor(String viewId, String text, String widget, boolean description) {
            this(viewId, text, widget, description, false, 0);
        }
        Anchor(String viewId, String text, String widget, boolean description, boolean selected, int priority) {
            this.viewId = viewId; this.text = text; this.widget = widget; this.description = description;
            this.selected = selected; this.priority = priority;
        }
        boolean matches(String id, String value, String kind) {
            return (viewId.isEmpty() || viewId.equals(id)) && text.equals(value) && widget.equals(kind);
        }
    }
    boolean page(String packageName, String windowClass) {
        return enabled && pkg.equals(packageName) && type.equals(windowClass) && (!anchors.isEmpty() || classOnly && canUseClassOnly());
    }
    boolean canUseClassOnly() {
        // Shared system containers (settings / launcher) require anchors to distinguish their pages.
        return !system && type.startsWith(pkg + ".") && !type.startsWith("android.");
    }
    RecordedPageRule renamed(String newName, boolean newEnabled) {
        return new RecordedPageRule(id, newName, pkg, type, system, newEnabled, anchors, classOnly, restrictSystem);
    }
}
