package com.luoyilin.focusguard;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class SystemExitPagePolicyTest {
    private int detect(String pkg, String type, boolean parent, String... titles) {
        return SystemExitPagePolicy.detect(pkg, type, Arrays.asList(titles), "奶蛙ToDo", "com.luoyilin.focusguard", parent);
    }
    @Test public void ownUninstallConfirmationIsRecognized() {
        assertEquals(SystemExitPagePolicy.UNINSTALL, detect("com.android.packageinstaller", "UninstallerActivity", false, "要卸载奶蛙ToDo吗？"));
    }
    @Test public void otherAppUninstallIsIgnored() {
        assertEquals(0, detect("com.android.packageinstaller", "UninstallerActivity", false, "要卸载微信吗？"));
    }
    @Test public void thirdPartyTitlesCannotImpersonateSettings() {
        assertEquals(0, detect("com.tencent.mm", "UninstallerActivity", true, "卸载奶蛙ToDo"));
    }
    @Test public void accessibilityDetailsNeedOwnIdentity() {
        assertEquals(SystemExitPagePolicy.ACCESSIBILITY, detect("com.android.settings", "AccessibilityDetailsActivity", false, "奶蛙ToDo"));
        assertEquals(0, detect("com.android.settings", "AccessibilityDetailsActivity", false, "其他服务"));
    }
    @Test public void genericSubsettingsRequireRecentAccessibilityContext() {
        assertEquals(0, detect("com.android.settings", "SubSettings", false, "奶蛙ToDo"));
        assertEquals(SystemExitPagePolicy.ACCESSIBILITY, detect("com.android.settings", "SubSettings", true, "奶蛙ToDo"));
    }
    @Test public void appListAndUnrelatedOwnSettingsAreIgnored() {
        assertEquals(0, detect("com.android.settings", "ManageApplicationsActivity", false, "奶蛙ToDo", "微信"));
        assertEquals(0, detect("com.android.settings", "BatteryActivity", false, "奶蛙ToDo"));
    }
    @Test public void contextAndMissingTitlesDoNotAuthorizeDetection() {
        assertTrue(SystemExitPagePolicy.accessibilityContext("SubSettings", Arrays.asList("无障碍")));
        assertEquals(0, detect("com.android.settings", "SubSettings", true));
        assertEquals(0, detect("com.oplus.appinstaller", null, false, "奶蛙ToDo"));
    }
    @Test public void oplusUninstallTitleAloneNeedsAnIdentityLookup() {
        assertEquals(0, detect("com.oplus.appdetail", "com.oplus.appdetail.model.uninstall.UninstallPackageActivity", false, "应用详情"));
        assertTrue(SystemExitPagePolicy.nodeLookupAllowed("com.oplus.appdetail", "UninstallPackageActivity",
                Arrays.asList("应用详情"), "奶蛙ToDo", "com.luoyilin.focusguard", false));
        assertEquals(SystemExitPagePolicy.UNINSTALL, detect("com.oplus.appdetail", "UninstallPackageActivity", false, "奶蛙ToDo"));
    }
    @Test public void nodeLookupsExcludeChatsAndUnrelatedSystemPages() {
        assertFalse(SystemExitPagePolicy.nodeLookupAllowed("com.tencent.mm", "UninstallPackageActivity",
                Arrays.asList("奶蛙ToDo"), "奶蛙ToDo", "com.luoyilin.focusguard", true));
        assertFalse(SystemExitPagePolicy.nodeLookupAllowed("com.android.settings", "BatteryActivity",
                Arrays.asList("奶蛙ToDo"), "奶蛙ToDo", "com.luoyilin.focusguard", true));
        assertFalse(SystemExitPagePolicy.nodeLookupAllowed("com.android.settings", "SubSettings",
                Arrays.asList("其他服务"), "奶蛙ToDo", "com.luoyilin.focusguard", false));
        assertTrue(SystemExitPagePolicy.nodeLookupAllowed("com.android.settings", "SubSettings",
                Arrays.asList("奶蛙ToDo"), "奶蛙ToDo", "com.luoyilin.focusguard", false));
    }
}
