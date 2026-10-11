package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import java.util.List;
import java.util.Locale;

/** Compact usage bars. Each row exposes its app name and exact duration to accessibility. */
final class AppUsageChartView extends LinearLayout {
    static final class Entry {
        final String packageName, name;
        final long millis;
        final Drawable icon;
        Entry(String packageName, String name, long millis, Drawable icon) {
            this.packageName = packageName; this.name = name; this.millis = millis; this.icon = icon;
        }
    }
    private static final int[] PALETTE = {
            0xFFEA8694, 0xFF72C7AD, 0xFFB39BE3, 0xFFE2C784,
            0xFF77A9EA, 0xFFE7AD78, 0xFF79C4D5, 0xFFD19BC2
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float timeSize, axisSize, tailWidth;
    private final double unit, maximum;

    AppUsageChartView(Context context, List<Entry> entries, int[] colors) {
        super(context);
        setOrientation(VERTICAL);
        timeSize = sp(13); axisSize = sp(11);
        paint.setTextSize(timeSize);
        float longest = 0;
        long largest = 0;
        for (Entry entry : entries) {
            largest = Math.max(largest, entry.millis);
            longest = Math.max(longest, paint.measureText(FocusStatistics.duration(entry.millis)));
        }
        // Reserve the same tail space on every row, keeping bars and the axis aligned.
        tailWidth = dp(22 + 4 + 4 + 2) + longest;
        unit = largest >= 60_000 ? 60_000 : 1000;
        // Keep the axis endpoint exact; rounding it up leaves the longest bar too short.
        maximum = Math.max(1, largest);
        addView(new Axis(context), new LayoutParams(-1, -2));
        for (int i = 0; i < entries.size(); i++) addView(new Bar(context, entries.get(i), colors[i]),
                new LayoutParams(-1, -2));
    }

    static int color(int index) {
        if (index < PALETTE.length) return PALETTE[index];
        return Color.HSVToColor(new float[]{(float) ((8 + index * 137.508) % 360), .46f, .9f});
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
    private float barWidth(int width) { return Math.max(0, width - tailWidth); }

    private final class Axis extends View {
        Axis(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            paint.setTextSize(axisSize);
            Paint.FontMetrics font = paint.getFontMetrics();
            setMeasuredDimension(resolveSize((int) dp(240), widthSpec),
                    resolveSize((int) Math.ceil(font.bottom - font.top + dp(10)), heightSpec));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setTextSize(axisSize); paint.setStyle(Paint.Style.FILL);
            paint.setColor(getContext().getColor(R.color.text_secondary));
            float baseline = -paint.getFontMetrics().top + dp(2);
            for (int i = 0; i <= 4; i++) {
                // Round intermediate labels to whole units, then place them at their
                // actual time coordinates. The final tick is the longest app's time.
                double value = i == 4 ? maximum / unit : Math.round(maximum / unit * i / 4);
                String label = value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value)
                        : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
                if (i == 4) label += unit == 60_000 ? "分" : "秒";
                paint.setTextAlign(i == 0 ? Paint.Align.LEFT : Paint.Align.CENTER);
                float x = (float) (barWidth(getWidth()) * value * unit / maximum);
                canvas.drawText(label, x, baseline, paint);
                paint.setStrokeWidth(dp(1));
                float tickTop = baseline + paint.getFontMetrics().bottom + dp(3);
                canvas.drawLine(x, tickTop, x, tickTop + dp(3), paint);
            }
        }
    }
    private final class Bar extends View {
        private final Entry entry;
        private final int color;
        Bar(Context context, Entry entry, int color) {
            super(context); this.entry = entry; this.color = color;
            setContentDescription(entry.name + "，" + FocusStatistics.duration(entry.millis));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            paint.setTextSize(timeSize);
            Paint.FontMetrics font = paint.getFontMetrics();
            int height = (int) Math.ceil(Math.max(dp(34), font.bottom - font.top + dp(12)));
            setMeasuredDimension(resolveSize((int) dp(240), widthSpec), resolveSize(height, heightSpec));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cy = getHeight() / 2f, available = barWidth(getWidth());
            float length = Math.min(available, Math.max(dp(2), (float) (entry.millis / maximum * available)));
            paint.setStyle(Paint.Style.FILL); paint.setColor(color);
            canvas.drawRoundRect(0, cy - dp(7), length, cy + dp(7), dp(7), dp(7), paint);
            float iconLeft = length + dp(4);
            int iconSize = Math.round(dp(22));
            if (entry.icon != null) {
                entry.icon.setBounds(Math.round(iconLeft), Math.round(cy - iconSize / 2f),
                        Math.round(iconLeft) + iconSize, Math.round(cy - iconSize / 2f) + iconSize);
                entry.icon.draw(canvas);
            }
            paint.setTextSize(timeSize); paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(getContext().getColor(R.color.text_primary));
            Paint.FontMetrics font = paint.getFontMetrics();
            canvas.drawText(FocusStatistics.duration(entry.millis), iconLeft + iconSize + dp(4),
                    cy - (font.ascent + font.descent) / 2f, paint);
        }
    }
}
