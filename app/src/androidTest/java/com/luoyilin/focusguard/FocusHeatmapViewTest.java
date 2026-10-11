package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.Collections;
import static org.junit.Assert.*;

/** Checks the first render, before scrolling into view or tapping the heatmap. */
@RunWith(AndroidJUnit4.class)
public class FocusHeatmapViewTest {
    @Test public void firstRecordingContainsCellsEvenBelowViewport() {
        checkFirstDraw(true);
    }
    @Test public void visibleHeatmapAlsoDrawsWithoutTap() {
        checkFirstDraw(false);
    }
    private void checkFirstDraw(boolean belowViewport) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_FocusGuard);
            ScrollView page = new ScrollView(context);
            LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
            page.addView(body);
            if (belowViewport) body.addView(new View(context), new LinearLayout.LayoutParams(480, 1000));
            LocalDate date = LocalDate.of(2026, 10, 2);
            FocusHeatmapView grid = new FocusHeatmapView(context,
                    Collections.singletonMap(date, 60_000L), date, (day, millis) -> { });
            grid.setViewportWidth(480);
            HorizontalScrollView weeks = new HorizontalScrollView(context);
            weeks.addView(grid);
            body.addView(weeks, new LinearLayout.LayoutParams(-1, -2));
            page.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
            page.layout(0, 0, 480, 600);
            assertEquals("Fixture visibility", !belowViewport, grid.getLocalVisibleRect(new Rect()));
            Bitmap bitmap = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                grid.draw(new Canvas(bitmap));
                int painted = 0;
                for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                    if ((bitmap.getPixel(x, y) >>> 24) != 0) painted++;
                }
                assertTrue("Initial draw must contain cells without any tap; painted pixels=" + painted, painted > 100);
            } finally { bitmap.recycle(); }
        });
    }
}
