package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Never changes the real application's consent, services, settings or uninstall state. */
public class PageMonitoringTest {
    private Context c;
    private SystemExitReminderController controller;
    @Before public void setup() {
        c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        assertTrue(PageMonitoringStore.revoke(c));
    }
    @After public void cleanup() {
        if (c == null || !c.getPackageName().endsWith(".setupprobe")) return;
        main(() -> { if (controller != null) controller.close(); });
        PageMonitoringStore.revoke(c);
    }
    private void main(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); }
    private SystemExitReminderController reminder(ProbeService service) {
        return reminder(service, () -> service.backs++);
    }
    private SystemExitReminderController reminder(ProbeService service, Runnable back) {
        // Exercise routing/lifecycle against a recording window host. Actual trusted-overlay visibility is
        // separately verified with the formally bound service on the OEM settings and uninstall pages.
        return new SystemExitReminderController(service, back, windowHost(), android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }
    static android.view.WindowManager windowHost() {
        final View[] mounted = {null};
        return (android.view.WindowManager) java.lang.reflect.Proxy.newProxyInstance(
                android.view.WindowManager.class.getClassLoader(), new Class<?>[]{android.view.WindowManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addView")) {
                        assertNull(mounted[0]);
                        android.view.WindowManager.LayoutParams params = (android.view.WindowManager.LayoutParams) args[1];
                        assertEquals(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type);
                        assertTrue((params.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
                        assertTrue((params.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
                        mounted[0] = (View) args[0];
                    } else if (method.getName().equals("removeViewImmediate")) {
                        assertSame(mounted[0], args[0]); mounted[0] = null;
                    }
                    return null;
                });
    }
    private static class ProbeService extends FocusAccessibilityService {
        int backs;
        ProbeService(Context c) { attachBaseContext(c); }
    }
    private View banner() {
        try { Field f = SystemExitReminderController.class.getDeclaredField("view"); f.setAccessible(true); return (View) f.get(controller); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void awaitHidden() {
        // sendBroadcast is asynchronous; an idle main queue alone does not fence delivery from system_server.
        java.util.concurrent.atomic.AtomicBoolean hidden = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 1500;
        do {
            main(() -> hidden.set(banner() == null));
            if (hidden.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        main(() -> assertNull(banner()));
    }
    private void event(String pkg, String type, String title) {
        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg); e.setClassName(type); if (title != null) e.getText().add(title);
        try { controller.onWindow(e); } finally { e.recycle(); }
    }
    @Test public void defaultsAndUnacknowledgedEnableFailClosed() {
        assertFalse(PageMonitoringStore.read(c).authorized); assertFalse(PageMonitoringStore.read(c).enabled);
        assertFalse(PageMonitoringStore.authorize(c, false)); assertFalse(PageMonitoringStore.enable(c, true));
    }
    @Test public void legacyConsentCannotReadNodesAndCurrentConsentOnlyQueriesEligiblePages() throws Exception {
        java.util.Properties old = new java.util.Properties();
        old.setProperty("consentVersion", "1"); old.setProperty("enabled", "true");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getNoBackupFilesDir(), "page-monitoring.properties"))) {
            old.store(out, null);
        }
        assertFalse(PageMonitoringStore.read(c).authorized);
        assertFalse(PageMonitoringStore.read(c).enabled);
        assertFalse(PageMonitoringStore.enable(c, true));
        old.setProperty("consentVersion", "2");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getNoBackupFilesDir(), "page-monitoring.properties"))) { old.store(out, null); }
        assertFalse(PageMonitoringStore.read(c).authorized); assertFalse(PageMonitoringStore.read(c).enabled);
        old.setProperty("consentVersion", "3");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getNoBackupFilesDir(), "page-monitoring.properties"))) { old.store(out, null); }
        assertFalse(PageMonitoringStore.read(c).authorized); assertFalse(PageMonitoringStore.read(c).enabled);
        final int[] reads = {0};
        ProbeService service = new ProbeService(c);
        main(() -> {
            controller = new SystemExitReminderController(service, () -> service.backs++,
                    windowHost(), android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, (e, pkg, type) -> {
                reads[0]++; return SystemExitPagePolicy.UNINSTALL;
            });
            event("com.oplus.appdetail", "UninstallPackageActivity", "应用详情");
            assertEquals(0, reads[0]); assertNull(banner());
        });
        assertTrue(PageMonitoringStore.authorize(c, true));
        main(() -> {
            event("com.tencent.mm", "UninstallPackageActivity", c.getString(R.string.app_name));
            event("com.android.settings", "BatteryActivity", c.getString(R.string.app_name));
            assertEquals(0, reads[0]);
            event("com.oplus.appdetail", "UninstallPackageActivity", "应用详情");
            assertEquals(1, reads[0]); assertNotNull(banner());
        });
        assertTrue(PageMonitoringStore.enable(c, false));
        main(() -> {
            event("com.oplus.appdetail", "UninstallPackageActivity", "应用详情");
            assertEquals(1, reads[0]); assertNull(banner()); assertEquals(0, service.backs);
        });
    }
    @Test public void separateConsentPersistsUntilRevokedWithoutChangingSleepPlan() {
        HashMap<String, ?> before = new HashMap<>(new FocusPreferences(c).getAll());
        assertTrue(PageMonitoringStore.authorize(c, true)); assertTrue(PageMonitoringStore.read(c).enabled);
        assertTrue(PageMonitoringStore.enable(c, false)); assertTrue(PageMonitoringStore.read(c).authorized);
        assertTrue(PageMonitoringStore.enable(c, true)); assertTrue(PageMonitoringStore.revoke(c));
        assertFalse(PageMonitoringStore.read(c).authorized); assertFalse(PageMonitoringStore.enable(c, true));
        assertEquals(before, new FocusPreferences(c).getAll());
    }
    @Test public void authorizeButtonRequiresTheUncheckedAcknowledgment() {
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
            s.onActivity(a -> {
                CheckBox box = a.findViewById(R.id.pageMonitoringConsent);
                Button accept = a.findViewById(R.id.pageMonitoringAuthorize);
                assertFalse(box.isChecked()); assertFalse(accept.isEnabled());
                box.setChecked(true); assertTrue(accept.isEnabled()); accept.performClick();
                assertTrue(PageMonitoringStore.read(c).enabled);
                a.findViewById(R.id.pageMonitoringRevoke).performClick();
                assertFalse(PageMonitoringStore.read(c).authorized);
                assertFalse(((CheckBox) a.findViewById(R.id.pageMonitoringConsent)).isChecked());
            });
        }
    }
    @Test public void continueDoesNotActOnTheSystemAndDoesNotRepeatInTheSameVisit() throws Exception {
        assertTrue(PageMonitoringStore.authorize(c, true));
        ProbeService service = new ProbeService(c);
        main(() -> {
            controller = reminder(service);
            event("com.android.packageinstaller", "UninstallerActivity", "卸载" + c.getString(R.string.app_name));
            assertNotNull(banner()); banner().findViewById(R.id.systemExitReminderContinue).performClick();
            assertNull(banner()); assertEquals(0, service.backs);
            event("com.android.packageinstaller", "UninstallerActivity", "卸载" + c.getString(R.string.app_name));
            assertNull(banner());
            event("probe.other", "Activity", "");
            event("com.android.packageinstaller", "UninstallerActivity", "卸载" + c.getString(R.string.app_name));
            assertNotNull(banner()); banner().findViewById(R.id.systemExitReminderReturn).performClick();
            assertEquals(1, service.backs); assertNull(banner());
        });
    }
    @Test public void revokedConsentImmediatelyRemovesAnOpenReminder() throws Exception {
        assertTrue(PageMonitoringStore.authorize(c, true));
        main(() -> {
            controller = reminder(new ProbeService(c));
            event("com.android.packageinstaller", "UninstallerActivity", "卸载" + c.getString(R.string.app_name));
            assertNotNull(banner());
        });
        assertTrue(PageMonitoringStore.revoke(c));
        awaitHidden();
    }

    @Test public void differentExitKindsAreRemindedAndLateWidgetsCannotHideTheBanner() {
        assertTrue(PageMonitoringStore.authorize(c, true));
        main(() -> {
            controller = reminder(new ProbeService(c));
            event("com.android.settings", "AccessibilityDetailsActivity", c.getString(R.string.app_name));
            assertNotNull(banner()); banner().findViewById(R.id.systemExitReminderContinue).performClick();
            event("com.oplus.appdetail", "UninstallPackageActivity", c.getString(R.string.app_name));
            View uninstall = banner(); assertNotNull(uninstall);
            event("com.android.settings", "android.widget.FrameLayout", c.getString(R.string.app_name));
            assertSame(uninstall, banner());
            banner().findViewById(R.id.systemExitReminderContinue).performClick();
            event("com.oplus.appdetail", "UninstallPackageActivity", c.getString(R.string.app_name));
            assertNull(banner());
        });
    }
    @Test public void delayedIdentityCanShowButCannotReviveAfterLeavingThePage() {
        assertTrue(PageMonitoringStore.authorize(c, true));
        final int[] reads = {0};
        main(() -> {
            controller = new SystemExitReminderController(new ProbeService(c), () -> fail("Unexpected system action"),
                    windowHost(), android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    (id, pkg, type) -> ++reads[0] == 1 ? SystemExitPagePolicy.NONE : SystemExitPagePolicy.UNINSTALL);
            event("com.oplus.appdetail", "UninstallPackageActivity", "应用详情");
            assertNull(banner());
        });
        SystemClock.sleep(400);
        main(() -> {
            assertNotNull(banner()); assertEquals(2, reads[0]);
            controller.close();
            reads[0] = 0;
            controller = new SystemExitReminderController(new ProbeService(c), () -> fail("Unexpected system action"),
                    windowHost(), android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    (id, pkg, type) -> ++reads[0] == 1 ? SystemExitPagePolicy.NONE : SystemExitPagePolicy.UNINSTALL);
            event("com.oplus.appdetail", "UninstallPackageActivity", "应用详情");
            event("probe.other", "Activity", "");
        });
        SystemClock.sleep(400);
        main(() -> { assertNull(banner()); assertEquals(1, reads[0]); });
    }

    @Test public void timeoutAndCloseRemoveReminderWithoutSystemAction() {
        assertTrue(PageMonitoringStore.authorize(c, true));
        ProbeService service = new ProbeService(c);
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
        main(() -> {
            controller = reminder(service);
            event("com.android.settings", "AccessibilityDetailsActivity", c.getString(R.string.app_name));
            assertNotNull(banner());
        });
        SystemClock.sleep(10_000);
        main(() -> assertNotNull(banner()));
        SystemClock.sleep(5_500);
        main(() -> {
            assertNull(banner()); assertEquals(0, service.backs);
            event("probe.other", "Activity", "");
            event("com.android.settings", "AccessibilityDetailsActivity", c.getString(R.string.app_name));
            assertNotNull(banner()); controller.close(); controller.close(); assertNull(banner());
            event("com.android.settings", "AccessibilityDetailsActivity", c.getString(R.string.app_name));
            assertNull(banner()); assertEquals(0, service.backs);
        });
        }
    }

    @Test public void disablingReminderKeepsConsentAndRemovesWindow() {
        assertTrue(PageMonitoringStore.authorize(c, true));
        main(() -> {
            controller = reminder(new ProbeService(c));
            event("com.android.packageinstaller", "UninstallerActivity", "卸载" + c.getString(R.string.app_name));
            assertNotNull(banner());
        });
        assertTrue(PageMonitoringStore.enable(c, false));
        awaitHidden();
        assertTrue(PageMonitoringStore.read(c).authorized);
        assertFalse(PageMonitoringStore.read(c).enabled);
    }

    @Test public void disclosureIsReadableInBothThemes() {
        int original = Appearance.read(c);
        try {
            for (int mode : new int[]{1, 2}) {
                final String theme = mode == 1 ? "light" : "dark";
                main(() -> assertTrue(Appearance.setMode(c, theme.equals("light") ? 1 : 2)));
                try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    s.onActivity(a -> capture(a, theme + "-top"));
                    s.onActivity(a -> scrollToConsent(a.getWindow().getDecorView()));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    s.onActivity(a -> {
                        CheckBox box = a.findViewById(R.id.pageMonitoringConsent);
                        assertFalse(box.isChecked()); assertTrue(box.isShown());
                        assertFalse(a.findViewById(R.id.pageMonitoringAuthorize).isEnabled());
                        capture(a, theme + "-bottom");
                    });
                }
            }
        } finally { main(() -> Appearance.setMode(c, original)); }
    }
    private void scrollToConsent(View view) {
        if (view instanceof android.widget.ScrollView) {
            android.widget.ScrollView scroll = (android.widget.ScrollView) view;
            scroll.setSmoothScrollingEnabled(false);
            scroll.fullScroll(View.FOCUS_DOWN);
        }
        else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scrollToConsent(group.getChildAt(i));
        }
    }
    private void capture(android.app.Activity a, String name) {
        View decor = a.getWindow().getDecorView();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        decor.draw(new android.graphics.Canvas(bitmap));
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getFilesDir(), "page-monitoring-" + name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        finally { bitmap.recycle(); }
    }
}
