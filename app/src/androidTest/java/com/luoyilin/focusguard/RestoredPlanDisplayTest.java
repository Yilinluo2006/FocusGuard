package com.luoyilin.focusguard;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager.widget.ViewPager;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Run only in an isolated package: never stop or edit the user's protection app. */
public class RestoredPlanDisplayTest {
    @Test public void restoredPlanAppearsAndUnsavedDraftSurvivesLaterChanges() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        context.getSharedPreferences("setup_guide", 0).edit().putBoolean("introduced", true).commit();
        FocusPreferences prefs = new FocusPreferences(context);
        assertTrue(prefs.edit().clear().commit());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            // The page was created with the defaults before the restore writes configuration.
            assertTrue(prefs.edit().putBoolean("daily", false).putInt("minute", 1410)
                    .putInt("duration", 240).putInt(FocusSchedule.WEEKDAYS_KEY, 31)
                    .putStringSet("allowed", new HashSet<>(Arrays.asList("com.example.a", "com.example.b"))).commit());
            scenario.onActivity(activity -> ((ViewPager)activity.findViewById(R.id.homePager)).setCurrentItem(2, false));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.main);
                assertNotNull("Restored start time must be visible", text(root, "23:30"));
                assertNotNull("Restored allowlist must be visible", text(root, "可用应用 · 2 个"));
                EditText duration = duration(root);
                assertNotNull(duration);
                assertEquals("240", duration.getText().toString());
                duration.setText("99");
            });
            assertTrue(prefs.edit().putInt("minute", 1400).putInt("duration", 300).commit());
            scenario.onActivity(activity -> ((ViewPager)activity.findViewById(R.id.homePager)).setCurrentItem(0, false));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> ((ViewPager)activity.findViewById(R.id.homePager)).setCurrentItem(2, false));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.main);
                assertNotNull("A dirty draft must retain its own start time", text(root, "23:30"));
                assertEquals("99", duration(root).getText().toString());
            });
        }
    }
    private static TextView text(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView)view).getText())) return (TextView)view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            TextView found = text(((ViewGroup)view).getChildAt(i), expected);
            if (found != null) return found;
        }
        return null;
    }
    private static EditText duration(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            EditText found = duration(((ViewGroup)view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
