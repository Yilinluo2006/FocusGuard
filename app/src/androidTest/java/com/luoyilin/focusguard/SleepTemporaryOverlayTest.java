package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real overlay windows and controller decisions, only in the isolated probe. */
public class SleepTemporaryOverlayTest {
    private Context context;
    private AtomicBoolean busy;
    private boolean frozen;
    private ActivityScenario<android.app.Activity> screen;
    private FocusModeController controller;
    private final ArrayList<RemainingTimeOverlay> timers = new ArrayList<>();

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        assertTrue("Grant overlay permission only to the isolated probe", Settings.canDrawOverlays(context));
        Field field = ProtectionClock.class.getDeclaredField("BUSY");
        field.setAccessible(true);
        busy = (AtomicBoolean) field.get(null);
        for (int i = 0; i < 30 && busy.get(); i++) Thread.sleep(200);
        assertTrue(busy.compareAndSet(false, true)); frozen = true;
        context.getSharedPreferences("device_sync", 0).edit().clear().commit();
        Properties clock = new Properties();
        ProtectionTimeLedger.verify(clock, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                ProtectionClock.boot(context), ZoneId.systemDefault().getId());
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), "protection-clock.properties"));
        FileOutputStream out = file.startWrite(); clock.store(out, null); file.finishWrite(out);
        field = ProtectionClock.class.getDeclaredField("cached"); field.setAccessible(true); field.set(null, null);
        LocalTime start = Instant.ofEpochMilli(ProtectionClock.now(context) - 60_000).atZone(ProtectionClock.zone(context)).toLocalTime();
        assertTrue(context.getSharedPreferences("focus_mode", 0).edit().clear()
                .putBoolean("daily", true).putInt("minute", start.getHour() * 60 + start.getMinute())
                .putInt("duration", 30).putInt(FocusSchedule.WEEKDAYS_KEY, 127)
                .putStringSet("allowed", Collections.emptySet()).commit());
        Bundle grant = SleepTemporaryUse.request(context);
        assertTrue(grant.getString("message"), grant.getBoolean("saved"));
        assertEquals(4, grant.getInt("left"));
        screen = ActivityScenario.launch(new Intent().setClassName(context.getPackageName(),
                "com.luoyilin.focusguard.SleepTemporaryTestActivity"));
    }

    @After public void cleanup() {
        if (!frozen) return;
        try {
            main(() -> {
                if (controller != null) controller.close();
                for (RemainingTimeOverlay timer : timers) timer.hide();
            });
            if (screen != null) screen.close();
            context.getSharedPreferences("focus_mode", 0).edit().clear().commit();
            main(() -> Appearance.refreshSleep(context));
        } finally { busy.set(false); frozen = false; }
    }

    /** No accessibility is enabled for the probe; the real controller uses its application overlay. */
    private static class ProbeService extends FocusAccessibilityService {
        ProbeService(Context context) { attachBaseContext(context); }
        @Override void onFocusModeChanged(boolean active, String packageName) { }
    }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private Object field(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private TextView label(RemainingTimeOverlay timer) { return (TextView) field(timer, "label"); }
    private RemainingTimeOverlay sleepTimer() { return (RemainingTimeOverlay) field(controller, "sleepTemporaryOverlay"); }
    private String launcher() {
        ResolveInfo home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        assertNotNull(home); return home.activityInfo.packageName;
    }
    private void window(String pkg) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName(pkg); event.setClassName("android.app.Activity");
        try { controller.onWindow(event); } finally { event.recycle(); }
    }
    private void startController() {
        main(() -> { controller = new FocusModeController(new ProbeService(context)); window(launcher()); });
    }

    @Test public void controllerShowsOnLauncherAndUnrestrictedAppsWithoutNewGrants() throws Exception {
        startController();
        main(() -> {
            assertNotNull(label(sleepTimer()));
            assertTrue(label(sleepTimer()).getText().toString().startsWith("睡眠临时使用\n剩余 "));
            assertFalse(controller.active());
            window("probe.unrestricted.app");
            assertNotNull(label(sleepTimer()));
        });
        assertEquals(4, SleepTemporaryUse.state(context).getInt("left"));
        assertEquals(1, new org.json.JSONArray(context.getSharedPreferences("focus_mode", 0)
                .getString("sleepTemporaryHistory", "[]")).length());
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        android.view.accessibility.AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root != null && context.getPackageName().contentEquals(root.getPackageName())) {
            android.graphics.Bitmap screenshot = automation.takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream out = context.openFileOutput("sleep-temporary-overlay.png", 0)) {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                } finally { screenshot.recycle(); }
            }
        }
        if (root != null) root.recycle();
    }

    @Test public void delayedAndRepeatedDisplayKeepsTheOriginalSampledDeadline() throws Exception {
        Bundle sampled = SleepTemporaryUse.state(context);
        long until = sampled.getLong("sampledElapsed") + sampled.getLong("remaining");
        long storedUntil = new FocusPreferences(context).getLong("sleepTemporaryElapsedUntil", 0);
        RemainingTimeOverlay timer = new RemainingTimeOverlay(context); timers.add(timer);
        Thread.sleep(100);
        main(() -> { timer.showSleepTemporary(sampled); assertEquals(until, ((Long) field(timer, "deadline")).longValue()); });
        Thread.sleep(100);
        main(() -> { timer.showSleepTemporary(sampled); assertEquals(until, ((Long) field(timer, "deadline")).longValue()); });
        assertTrue(until <= storedUntil);
        assertEquals(storedUntil, new FocusPreferences(context).getLong("sleepTemporaryElapsedUntil", 0));
        assertEquals(4, SleepTemporaryUse.state(context).getInt("left"));
    }

    @Test public void countdownUpdatesWithoutResamplingAndExpiredStateCannotReappear() throws Exception {
        RemainingTimeOverlay timer = new RemainingTimeOverlay(context); timers.add(timer);
        Bundle sampled = SleepTemporaryUse.state(context);
        final String[] first = new String[1];
        main(() -> { timer.showSleepTemporary(sampled); first[0] = label(timer).getText().toString(); });
        Thread.sleep(1200);
        main(() -> assertNotEquals(first[0], label(timer).getText().toString()));
        Bundle expired = new Bundle(sampled);
        expired.putLong("sampledElapsed", SystemClock.elapsedRealtime() - 1500);
        expired.putLong("remaining", 1000);
        main(() -> { timer.showSleepTemporary(expired); assertNull(label(timer)); });
    }

    @Test public void grantExpiryRemovesTheTimerAndRestoresSleepControl() {
        startController();
        assertTrue(new FocusPreferences(context).edit().putLong("sleepTemporaryElapsedUntil", 0).commit());
        main(() -> { window(launcher()); assertNull(label(sleepTimer())); assertTrue(controller.active()); });
        assertEquals(4, SleepTemporaryUse.state(context).getInt("left"));
    }

    @Test public void sleepAndAppTemporaryTimersRemainSeparate() {
        startController();
        RemainingTimeOverlay app = new RemainingTimeOverlay(context); timers.add(app);
        main(() -> {
            app.showTemporary(context.getPackageName(), 60_000);
            WindowManager.LayoutParams sleepParams = (WindowManager.LayoutParams) field(sleepTimer(), "params");
            WindowManager.LayoutParams appParams = (WindowManager.LayoutParams) field(app, "params");
            assertTrue(sleepParams.y + sleepParams.height <= appParams.y);
            assertTrue(label(app).getText().toString().startsWith("应用临时使用"));
            sleepTimer().hide();
            assertNotNull(label(app));
        });
    }

    @Test public void controllerShutdownRemovesTimerAndCancelsRedisplay() throws Exception {
        startController();
        main(() -> { assertNotNull(label(sleepTimer())); controller.close(); assertNull(label(sleepTimer())); });
        Thread.sleep(1100);
        main(() -> assertNull(label(sleepTimer())));
        assertTrue(SleepTemporaryUse.state(context).getLong("remaining") > 0);
    }
}
