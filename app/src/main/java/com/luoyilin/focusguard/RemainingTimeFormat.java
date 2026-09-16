package com.luoyilin.focusguard;

import java.util.Locale;

final class RemainingTimeFormat {
    private RemainingTimeFormat() { }

    static String format(long millis) {
        long seconds = millis <= 0 ? 0 : 1 + (millis - 1) / 1000;
        long hours = seconds / 3600;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
