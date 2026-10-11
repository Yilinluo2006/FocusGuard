package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Compact bar chart with a zero baseline and an accessible textual equivalent. */
final class StatisticsChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long[] values;
    private final String[] labels;
    StatisticsChartView(Context context, long[] values, String[] labels) {
        super(context); this.values = values.clone(); this.labels = labels.clone();
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < values.length; i++) description.append(labels[i]).append("：")
                .append(FocusStatistics.duration(values[i])).append("；");
        setContentDescription(description.toString());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) return;
        long max = 60_000;
        for (long value : values) max = Math.max(max, value);
        float left = dp(6), right = getWidth() - dp(6), top = dp(28), bottom = getHeight() - dp(30);
        if (bottom <= top || right <= left) return;
        paint.setTextSize(dp(11)); paint.setColor(getContext().getColor(R.color.text_secondary));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(FocusStatistics.duration(max), left, dp(15), paint);
        paint.setColor(getContext().getColor(R.color.divider)); paint.setStrokeWidth(dp(1));
        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2;
            canvas.drawLine(left, y, right, y, paint);
        }
        float step = (right - left) / values.length, bar = Math.max(dp(2), step * .56f);
        for (int i = 0; i < values.length; i++) {
            float x = left + step * (i + .5f);
            paint.setColor(getContext().getColor(R.color.accent_text));
            if (values[i] > 0) canvas.drawRoundRect(x - bar / 2,
                    bottom - (bottom - top) * values[i] / max, x + bar / 2, bottom, dp(3), dp(3), paint);
            int stride = Math.max(1, (int) Math.ceil(values.length / 7.0));
            if (i % stride == 0 || i == values.length - 1) {
                if (i != values.length - 1 && values.length - 1 - i < stride) continue;
                paint.setColor(getContext().getColor(R.color.text_secondary));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(labels[i], x, bottom + dp(21), paint);
            }
        }
    }
}
