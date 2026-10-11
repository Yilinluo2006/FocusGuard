package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Shares of settled task time; textual legends in the card provide exact durations. */
final class TaskPieChartView extends View {
    private static final int[] PALETTE = {
            0xFFE2C784, 0xFF77A9EA, 0xFF72C7AD, 0xFFEA8694,
            0xFFB39BE3, 0xFFE7AD78, 0xFF79C4D5, 0xFFD19BC2
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final long[] values;
    private final int[] colors;
    private final double total;

    TaskPieChartView(Context context, long[] values, int[] colors, String[] names) {
        super(context);
        this.values = values.clone(); this.colors = colors.clone();
        double sum = 0;
        StringBuilder description = new StringBuilder("任务专注时长占比。");
        for (long value : values) sum += value;
        total = sum;
        for (int i = 0; i < values.length; i++) {
            description.append(names[i]).append("，").append(FocusStatistics.duration(values[i]));
            if (total > 0) description.append("，").append(Math.round(values[i] * 100.0 / total)).append("%；");
        }
        setContentDescription(description.toString());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    static int color(int index) {
        if (index < PALETTE.length) return PALETTE[index];
        // Extend the palette without grouping away tasks when the history grows.
        return Color.HSVToColor(new float[]{(float) ((53 + index * 137.508) % 360), .46f, .9f});
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (total <= 0 || values.length == 0) return;
        float radius = Math.max(0, Math.min(getWidth(), getHeight()) / 2f
                - 4 * getResources().getDisplayMetrics().density);
        if (radius == 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.FILL);
        double elapsed = 0;
        for (int i = 0; i < values.length; i++) {
            float start = (float) (-90 + elapsed * 360 / total);
            elapsed += values[i];
            float end = i == values.length - 1 ? 270 : (float) (-90 + elapsed * 360 / total);
            paint.setColor(colors[i]);
            canvas.drawArc(bounds, start, end - start, true, paint);
        }
        if (values.length > 1) {
            paint.setColor(getContext().getColor(R.color.surface_background));
            paint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
            elapsed = 0;
            for (long value : values) {
                double angle = Math.toRadians(-90 + elapsed * 360 / total);
                canvas.drawLine(cx, cy, cx + radius * (float) Math.cos(angle),
                        cy + radius * (float) Math.sin(angle), paint);
                elapsed += value;
            }
        }
    }
}
