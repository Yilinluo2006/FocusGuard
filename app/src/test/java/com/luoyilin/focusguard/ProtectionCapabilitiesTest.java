package com.luoyilin.focusguard;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionCapabilitiesTest {
    @Test public void wallpaperOnlyProtectsDailyLimits(){
        ProtectionCapabilities p=new ProtectionCapabilities(true,false,false,true,true,true);
        assertTrue(p.daily);assertFalse(p.sleep);assertFalse(p.focus);assertEquals("仅应用额度保护可用",p.summary());
    }
    @Test public void accessibilityToggleWithoutConnectionIsNotProtection(){
        ProtectionCapabilities p=new ProtectionCapabilities(true,true,false,true,false,false);
        assertFalse(p.daily);assertFalse(p.sleep);assertFalse(p.focus);
    }
    @Test public void wallpaperSelectionWithoutMonitorCannotLookHealthy(){
        assertFalse(new ProtectionCapabilities(true,false,false,true,true,false).daily);
        assertFalse(new ProtectionCapabilities(true,false,false,true,false,true).daily);
    }
    @Test public void revokedUsageOrOverlayDisablesWallpaperFallback(){
        assertFalse(new ProtectionCapabilities(false,false,false,true,true,true).daily);
        assertFalse(new ProtectionCapabilities(true,false,false,false,true,true).daily);
    }
    @Test public void accessibilityCanUseItsOwnWindowWithoutFloatingPermission(){
        ProtectionCapabilities p=new ProtectionCapabilities(true,true,true,false,false,false);
        assertTrue(p.daily);assertTrue(p.sleep);assertTrue(p.focus);
    }
    @Test public void absentUsageAccessOnlyAffectsDailyAccounting(){
        ProtectionCapabilities p=new ProtectionCapabilities(false,true,true,true,true,true);
        assertFalse(p.daily);assertTrue(p.sleep);assertTrue(p.focus);assertEquals("应用额度保护需修复",p.summary());
    }
    @Test public void staleRunningFlagCannotOverrideAUserDisabledAccessibilityToggle(){
        ProtectionCapabilities p=new ProtectionCapabilities(true,false,true,false,false,false);
        assertFalse(p.sleep);assertFalse(p.daily);
    }
}
