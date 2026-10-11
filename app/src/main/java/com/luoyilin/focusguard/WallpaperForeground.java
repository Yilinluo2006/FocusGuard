package com.luoyilin.focusguard;

import java.util.Objects;

/** Handles activity switches without an old activity's pause clearing the new one. */
final class WallpaperForeground {
    String packageName, className;
    void resume(String pkg, String type) { packageName = pkg; className = type; }
    void pause(String pkg, String type) {
        if (Objects.equals(pkg, packageName) && (type == null || className == null || Objects.equals(type, className))) clear();
    }
    void clear() { packageName = null; className = null; }
    static boolean blocked(long used, long limit, long sharedRemaining, long allowance, boolean focusing) {
        return (focusing || allowance <= 0) && (used >= limit || sharedRemaining <= 0);
    }
}
