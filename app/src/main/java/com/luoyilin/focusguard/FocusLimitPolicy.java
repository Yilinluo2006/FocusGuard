package com.luoyilin.focusguard;

/** A focus whitelist allows entry, not exemption from a configured daily limit. */
final class FocusLimitPolicy {
    private FocusLimitPolicy() { }

    static boolean monitorDailyLimit(boolean focusActive, boolean whitelisted) {
        return !focusActive || whitelisted;
    }

    static boolean useTemporaryRelease(boolean focusActive) {
        return !focusActive;
    }

    static boolean useTemporaryRelease(boolean focusActive, boolean sleeping, boolean whitelisted) {
        return !focusActive || (sleeping && whitelisted);
    }
}
