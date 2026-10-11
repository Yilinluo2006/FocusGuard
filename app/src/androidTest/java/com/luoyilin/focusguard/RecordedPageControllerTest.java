package com.luoyilin.focusguard;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import java.util.*;
import java.lang.reflect.Field;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class RecordedPageControllerTest {
    private Context c;
    private RecordedPageController controller;
    private SystemExitReminderController reminder;
    private static class ProbeService extends FocusAccessibilityService { ProbeService(Context c) { attachBaseContext(c); } }
    @Before public void setup() {
        c = InstrumentationRegistry.getInstrumentation().getTargetContext(); assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        PageMonitoringStore.revoke(c);
        for (RecordedPageRule r : RecordedPageStore.read(c)) RecordedPageStore.delete(c, r.id);
        assertTrue(PageMonitoringStore.authorize(c, true));
    }
    @After public void cleanup() {
        if (c == null || !c.getPackageName().endsWith(".setupprobe")) return;
        main(() -> { if (controller != null) controller.close(); });
        PageMonitoringStore.revoke(c); for (RecordedPageRule r : RecordedPageStore.read(c)) RecordedPageStore.delete(c, r.id);
    }
    private void main(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); }
    private View view(Object owner, String field) {
        try { Field f = owner.getClass().getDeclaredField(field); f.setAccessible(true); return (View) f.get(owner); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void event(String pkg, String type, int kind) {
        main(() -> {
            AccessibilityEvent e = AccessibilityEvent.obtain(kind); e.setPackageName(pkg); e.setClassName(type);
            try { controller.onEvent(e); } finally { e.recycle(); }
        });
        SystemClock.sleep(300);
    }
    private RecordedPageRule rule(String pkg, String type, boolean system) {
        RecordedPageRule r = new RecordedPageRule("controller-rule", "选定页面", pkg, type, system, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "页面标题", "android.widget.TextView", false)));
        assertTrue(RecordedPageStore.add(c, r)); return r;
    }
    private void controller(RecordedPageController.PageMatch match) {
        main(() -> {
            ProbeService service = new ProbeService(c);
            reminder = new SystemExitReminderController(service, () -> {}, PageMonitoringTest.windowHost(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            controller = new RecordedPageController(service, PageMonitoringTest.windowHost(), reminder, match);
        });
    }
    @Test public void systemReminderDoesNotRepeatWhileStayingButReturnsAfterLeaving() {
        RecordedPageRule r = rule("com.android.settings", "SubSettings", true);
        controller((pkg, type, id, candidates) -> r);
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> { assertNotNull(view(reminder, "view")); view(reminder, "view").findViewById(R.id.systemExitReminderContinue).performClick(); });
        event(r.pkg, "android.widget.TextView", AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        main(() -> assertNull(view(reminder, "view")));
        event("probe.other", "OtherActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> assertNotNull(view(reminder, "view")));
    }
    @Test public void ordinaryPageBlocksOnlyWhenChosenFeaturesMatchAndRevocationClearsIt() {
        RecordedPageRule r = rule("com.luoyilin.focusguard", "com.luoyilin.focusguard.MainActivity", false);
        final boolean[] matches = {true}; final int[] reads = {0};
        controller((pkg, type, id, candidates) -> { reads[0]++; return matches[0] ? r : null; });
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> { assertNotNull(view(controller, "overlay")); assertNull(view(reminder, "view")); });
        matches[0] = false;
        event(r.pkg, "android.widget.TextView", AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        main(() -> assertNull(view(controller, "overlay")));
        matches[0] = true;
        event(r.pkg, "android.widget.TextView", AccessibilityEvent.TYPE_VIEW_SELECTED);
        main(() -> assertNotNull(view(controller, "overlay")));
        assertTrue(PageMonitoringStore.revoke(c)); SystemClock.sleep(300);
        main(() -> assertNull(view(controller, "overlay")));
        int before = reads[0]; event("com.tencent.mm", "OtherActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED); assertEquals(before, reads[0]);
    }
    @Test public void onlyRegisteredPackageAndClassMayQueryChosenFeatures() {
        RecordedPageRule r = rule("com.android.settings", "SubSettings", false); // A stored ordinary flag cannot override system safety.
        final int[] reads = {0}; controller((pkg, type, id, candidates) -> { reads[0]++; return r; });
        event("com.tencent.mm", r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event(r.pkg, "OtherActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        assertEquals(0, reads[0]);
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> { assertNull(view(controller, "overlay")); assertNotNull(view(reminder, "view")); });
    }
    @Test public void enrollmentBubbleIsCompactDraggableAndLongPressCancels() {
        final WindowManager.LayoutParams[] params = {null}; final int[] moves = {0};
        WindowManager host = (WindowManager) java.lang.reflect.Proxy.newProxyInstance(WindowManager.class.getClassLoader(),
                new Class<?>[]{WindowManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addView") || method.getName().equals("updateViewLayout")) {
                        params[0] = (WindowManager.LayoutParams) args[1];
                        if (method.getName().equals("updateViewLayout")) moves[0]++;
                    }
                    return null;
                });
        main(() -> {
            ProbeService service = new ProbeService(c);
            reminder = new SystemExitReminderController(service, () -> {}, PageMonitoringTest.windowHost(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            controller = new RecordedPageController(service, host, reminder, (pkg, type, id, rules) -> { fail("Enrollment must not monitor content"); return null; });
            try { java.lang.reflect.Method m = RecordedPageController.class.getDeclaredMethod("startCapture", String.class); m.setAccessible(true); m.invoke(controller, "@system"); }
            catch (Exception error) { throw new AssertionError(error); }
            View bubble = view(controller, "overlay"); assertNotNull(bubble);
            assertTrue(params[0].width <= PageUi.dp(c, 100)); assertTrue(params[0].height <= PageUi.dp(c, 56));
            touch(bubble, MotionEvent.ACTION_DOWN, 0, 0); touch(bubble, MotionEvent.ACTION_MOVE, -400, 150); touch(bubble, MotionEvent.ACTION_UP, -400, 150);
            assertTrue(moves[0] >= 2); assertTrue(params[0].x >= 0); assertTrue(params[0].y >= 0);
            touch(bubble, MotionEvent.ACTION_DOWN, 0, 0);
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 30);
        main(() -> { touch(view(controller, "overlay"), MotionEvent.ACTION_UP, 0, 0); assertNull(view(controller, "overlay")); });
        assertTrue(RecordedPageStore.read(c).isEmpty());
    }
    private void touch(View view, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0);
        try { view.dispatchTouchEvent(e); } finally { e.recycle(); }
    }
    @Test public void automaticPreviewOnlySavesAfterConfirmationForBothKinds() {
        controller((pkg, type, id, rules) -> null);
        main(() -> {
            for (boolean system : new boolean[]{false, true}) {
                RecordedPageRule proposed = new RecordedPageRule("preview-" + system, "自动页面", "probe.app", "probe.app.Page", system, true,
                        Arrays.asList(new RecordedPageRule.Anchor("", "稳定标题", "android.widget.TextView", false)), false, system);
                controller.preview(proposed);
                assertEquals(system ? 1 : 0, RecordedPageStore.read(c).size());
                android.widget.Button save = findButton(view(controller, "overlay"), "确认添加");
                assertNotNull(save); save.performClick();
                List<RecordedPageRule> saved = RecordedPageStore.read(c);
                assertEquals(system ? 2 : 1, saved.size());
                assertEquals(system, saved.get(saved.size() - 1).restrictSystem);
            }
        });
    }
    @Test public void explicitlyRecordedSystemRuleBlocksAndCanBeRevoked() {
        RecordedPageRule r = new RecordedPageRule("restrict-system", "系统页面", "com.android.settings", "SubSettings", true, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "固定标题", "android.widget.TextView", false)), false, true);
        assertTrue(RecordedPageStore.add(c, r)); controller((pkg, type, id, rules) -> r);
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> { assertNotNull(view(controller, "overlay")); assertNull(view(reminder, "view")); });
        assertTrue(PageMonitoringStore.revoke(c)); SystemClock.sleep(300);
        main(() -> assertNull(view(controller, "overlay")));
    }
    @Test public void directCaptureAcceptsCurrentPackageButLegacyReplacementStillScopesToOriginalApp() {
        assertTrue(RecordedPageController.acceptsCaptureTarget("@current", "ordinary.app", false));
        assertTrue(RecordedPageController.acceptsCaptureTarget("@current", "com.android.settings", true));
        assertFalse(RecordedPageController.acceptsCaptureTarget("original.app", "other.app", false));
    }
    @Test public void classOnlyRuleMatchesWithoutRetrievingInaccessibleControlsAndAllowsAdjacentInterface() {
        RecordedPageRule r = new RecordedPageRule("class-only-controller", "视频界面", "com.luoyilin.focusguard",
                "com.luoyilin.focusguard.VideoActivity", false, true, Collections.emptyList(), true, false);
        assertTrue(RecordedPageStore.add(c, r)); controller(null); // Detached service cannot retrieve window roots.
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> assertNotNull(view(controller, "overlay")));
        event(r.pkg, "com.luoyilin.focusguard.OtherActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> assertNull(view(controller, "overlay")));
    }
    @Test public void lateBackgroundUninstallerEventCannotRemoveForegroundRestriction() {
        RecordedPageRule r = new RecordedPageRule("oem-order", "卸载页面", "com.oplus.appdetail", "UninstallPackageActivity", true, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "固定标题", "TextView", false)), false, true);
        assertTrue(RecordedPageStore.add(c, r));
        final boolean[] foregroundEvent = {true};
        main(() -> {
            ProbeService service = new ProbeService(c);
            reminder = new SystemExitReminderController(service, () -> {}, PageMonitoringTest.windowHost(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            controller = new RecordedPageController(service, PageMonitoringTest.windowHost(), reminder,
                    (pkg, type, id, rules) -> r, id -> foregroundEvent[0]);
        });
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED); main(() -> assertNotNull(view(controller, "overlay")));
        foregroundEvent[0] = false; event("com.android.packageinstaller", "UninstallerActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> assertNotNull("Delayed background activity must not clear the still-foreground restriction", view(controller, "overlay")));
        foregroundEvent[0] = true; event("probe.other", "OtherActivity", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        main(() -> assertNull("Real foreground exit must release the overlay", view(controller, "overlay")));
        event(r.pkg, r.type, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED); main(() -> assertNotNull(view(controller, "overlay")));
        assertTrue(PageMonitoringStore.revoke(c)); SystemClock.sleep(300);
        main(() -> assertNull(view(controller, "overlay")));
    }
    private android.widget.Button findButton(View v, String prefix) {
        if (v instanceof android.widget.Button && ((android.widget.Button)v).getText().toString().startsWith(prefix)) return (android.widget.Button)v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            android.widget.Button b = findButton(((ViewGroup)v).getChildAt(i), prefix); if (b != null) return b;
        }
        return null;
    }
    @Test public void realProbeControlsCanBeCapturedAndExactAnchorsDistinguishAdjacentContent() {
        assertTrue(PageMonitoringStore.revoke(c));
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.UiAutomation ui = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; ui.setServiceInfo(info);
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
            s.onActivity(a -> scroll(a.getWindow().getDecorView())); SystemClock.sleep(300);
            AccessibilityNodeInfo root = ui.getRootInActiveWindow(); assertNotNull(root);
            assertEquals(c.getPackageName(), RecordedPageNodes.value(root.getPackageName()));
            List<RecordedPageRule.Anchor> anchors = RecordedPageNodes.capture(root);
            RecordedPageRule.Anchor chosen = null;
            for (RecordedPageRule.Anchor a : anchors) if (a.text.equals("暂不开启")) chosen = a;
            assertNotNull(chosen);
            RecordedPageRule r = new RecordedPageRule("capture", "探针页面", c.getPackageName(), PageMonitoringActivity.class.getName(), false, true, Arrays.asList(chosen));
            root = ui.getRootInActiveWindow(); assertEquals(c.getPackageName(), RecordedPageNodes.value(root.getPackageName()));
            try {
                assertTrue(RecordedPageNodes.matches(root, r));
                RecordedPageRule wrong = new RecordedPageRule("other", "其他页面", r.pkg, r.type, false, true,
                        Arrays.asList(new RecordedPageRule.Anchor(chosen.viewId, "不同页面的标题", chosen.widget, chosen.description)));
                assertFalse(RecordedPageNodes.matches(root, wrong));
            } finally { root.recycle(); }
        }
    }
    @Test public void captureIncludesTextInsideClickableContainer() {
        android.app.UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
            s.onActivity(a -> {
                android.widget.LinearLayout group = new android.widget.LinearLayout(a); group.setClickable(true);
                group.setPadding(0, PageUi.dp(a, 100), 0, 0);
                android.widget.TextView label = new android.widget.TextView(a); label.setText("固定导航测试");
                group.addView(label); a.setContentView(group);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                group.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            });
            SystemClock.sleep(400);
            AccessibilityNodeInfo root = ui.getRootInActiveWindow(); assertNotNull(root);
            assertEquals(c.getPackageName(), RecordedPageNodes.value(root.getPackageName()));
            List<RecordedPageRule.Anchor> anchors = RecordedPageNodes.capture(root);
            boolean found = false; for (RecordedPageRule.Anchor a : anchors) found |= a.text.equals("固定导航测试");
            assertTrue("Text nested in a clickable navigation container must be available; candidates=" + anchors.size(), found);
        }
    }
    private void scroll(View v) {
        if (v instanceof android.widget.ScrollView) { android.widget.ScrollView s = (android.widget.ScrollView) v; s.setSmoothScrollingEnabled(false); s.fullScroll(View.FOCUS_DOWN); }
        else if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) scroll(((ViewGroup) v).getChildAt(i));
    }
}
