package com.luoyilin.focusguard;

final class RestrictionRules {
    static int quotaUsed(long today, long recordedDay, int count) {
        return today > recordedDay ? 0 : count;
    }

    static int consume(int used, long activeRemaining) {
        if (activeRemaining > 0) throw new IllegalStateException("已有应用临时使用正在连续计时，请结束后再申请");
        if (used >= 5) throw new IllegalStateException("应用临时次数已用完");
        return used + 1;
    }
    static boolean inWindow(int minute, int start, int end) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end
                : minute >= start || minute < end;
    }

    static boolean isRelaxation(boolean wasSelected, int oldMinutes,
                                boolean selected, int minutes) {
        return wasSelected && (!selected || minutes > oldMinutes);
    }

    static long remaining(long now, long elapsed, int boot, long deadline,
                          long elapsedDeadline, int grantedBoot) {
        // Reboot ends a short grant instead of allowing a changed wall clock to extend it.
        if (boot != grantedBoot) return 0;
        return Math.max(0, Math.min(120_000L,
                Math.min(deadline - now, elapsedDeadline - elapsed)));
    }
}
