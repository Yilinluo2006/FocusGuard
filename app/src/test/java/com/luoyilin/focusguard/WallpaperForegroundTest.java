package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperForegroundTest {
    @Test public void oldActivityPauseDoesNotHideTheNewActivity() {
        WallpaperForeground state = new WallpaperForeground();
        state.resume("limited", "Old"); state.resume("limited", "New"); state.pause("limited", "Old");
        assertEquals("limited", state.packageName); state.pause("limited", "New"); assertNull(state.packageName);
    }
    @Test public void oldAppPauseDoesNotClearTheNewApp() {
        WallpaperForeground state = new WallpaperForeground();
        state.resume("limited", "Page"); state.resume("launcher", "Home"); state.pause("limited", "Page");
        assertEquals("launcher", state.packageName);
    }
    @Test public void lockScreenClearsThePreviousApp() {
        WallpaperForeground state = new WallpaperForeground(); state.resume("limited", "Page"); state.clear();
        assertNull(state.packageName); assertNull(state.className);
    }
    @Test public void exactExhaustionAndExpiredEmergencyStayBlocked() {
        assertFalse(WallpaperForeground.blocked(29999, 30000, Long.MAX_VALUE, 0, false));
        assertTrue(WallpaperForeground.blocked(30000, 30000, Long.MAX_VALUE, 0, false));
        assertFalse(WallpaperForeground.blocked(40000, 30000, Long.MAX_VALUE, 1, false));
        assertTrue(WallpaperForeground.blocked(40000, 30000, Long.MAX_VALUE, 0, false));
    }
    @Test public void dailyGrantDoesNotBypassActiveSleepOrFocus() {
        assertTrue(WallpaperForeground.blocked(40000, 30000, Long.MAX_VALUE, 120000, true));
    }
    @Test public void sharedExhaustionAlsoBlocksWhenConfigured() {
        assertTrue(WallpaperForeground.blocked(0, 30000, 0, 0, false));
    }
}
