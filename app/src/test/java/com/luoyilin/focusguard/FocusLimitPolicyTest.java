package com.luoyilin.focusguard;

import org.junit.Test;

import static org.junit.Assert.*;

public class FocusLimitPolicyTest {
    @Test public void sleepingWhitelistCanUseItsRemainingDailyTemporaryTime() {
        assertTrue(FocusLimitPolicy.useTemporaryRelease(true, true, true));
        assertFalse(FocusLimitPolicy.useTemporaryRelease(true, true, false));
        assertFalse(FocusLimitPolicy.useTemporaryRelease(true, false, true));
    }
    @Test public void whitelistedAppStillUsesItsDailyLimitInFocus() {
        assertTrue(FocusLimitPolicy.monitorDailyLimit(true, true));
        assertFalse(FocusLimitPolicy.useTemporaryRelease(true));
    }

    @Test public void focusDoesNotMonitorAppsItBlocksEntirely() {
        assertFalse(FocusLimitPolicy.monitorDailyLimit(true, false));
    }

    @Test public void normalModeKeepsDailyLimitsAndTemporaryRelease() {
        assertTrue(FocusLimitPolicy.monitorDailyLimit(false, false));
        assertTrue(FocusLimitPolicy.useTemporaryRelease(false));
    }
}
