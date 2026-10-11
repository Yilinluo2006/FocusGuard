package com.luoyilin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WidgetPreviewTest {
    @Test public void populatedPreviewWorksInsideAppCompatActivity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        // The isolated host installs AppCompat's inflater factory just like MainActivity.
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(new Intent()
                .setClassName(context.getPackageName(), "com.luoyilin.focusguard.WidgetPreviewTestActivity"))) {
            scenario.onActivity(activity -> {
                RemoteViews preview = new RemoteViews(activity.getPackageName(), R.layout.widget_streak);
                StreakWidget.populateWeek(activity, preview, new Properties());
                preview.setTextViewText(R.id.widgetDays, "preview-test");
                android.view.View view = WidgetPin.inflatePreview(activity, preview, new LinearLayout(activity));
                assertEquals("preview-test", ((TextView) view.findViewById(R.id.widgetDays)).getText().toString());
                assertNotNull(((ImageView) view.findViewById(R.id.widgetMascot)).getDrawable());
                assertEquals(5, ((LinearLayout) view.findViewById(R.id.widgetWeek)).getChildCount());
            });
        }
    }
}
