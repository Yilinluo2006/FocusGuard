package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Seven weekday rows with scrollable week columns and accessible, tappable day cells. */
final class FocusHeatmapView extends View {
    static final int[] COLORS = {R.color.focus_heatmap_0, R.color.focus_heatmap_1,
            R.color.focus_heatmap_2, R.color.focus_heatmap_3, R.color.focus_heatmap_4};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<LocalDate, Long> values;
    private final LocalDate start, end;
    private final int columns;
    private final float gap, gridLeft, gridTop, textSize;
    private final int[] colors = new int[5];
    private final BiConsumer<LocalDate, Long> selected;
    private final DayAccessibility accessibility;
    private final int touchSlop;
    private float step, cellSize, downX, downY;
    private int viewportWidth;
    private LocalDate highlighted;

    FocusHeatmapView(Context context, Map<LocalDate, Long> totals, LocalDate end,
                     BiConsumer<LocalDate, Long> selected) {
        super(context);
        this.values = new HashMap<>(totals); this.end = end; this.selected = selected;
        LocalDate endWeek = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = endWeek.minusWeeks(11);
        for (LocalDate day : totals.keySet()) if (day.isBefore(first)) first = day;
        start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        columns = (int) (ChronoUnit.DAYS.between(start, endWeek) / 7) + 1;
        gap = dp(3); gridLeft = dp(24);
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics());
        paint.setTextSize(textSize);
        Paint.FontMetrics font = paint.getFontMetrics();
        gridTop = font.bottom - font.top + dp(10);
        for (int i = 0; i < colors.length; i++) colors[i] = context.getColor(COLORS[i]);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        highlighted = end;
        accessibility = new DayAccessibility();
        ViewCompat.setAccessibilityDelegate(this, accessibility);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
        setViewportWidth(Math.round(getResources().getDisplayMetrics().widthPixels - dp(72)));
    }
    void setViewportWidth(int width) {
        if (width <= 0 || width == viewportWidth) return;
        viewportWidth = width;
        step = Math.max(dp(15), (width - gridLeft + gap) / 12);
        cellSize = step - gap;
        requestLayout(); invalidate(); accessibility.invalidateRoot();
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private LocalDate day(int id) { return start.plusDays(id); }
    private long duration(int id) { return values.getOrDefault(day(id), 0L); }
    private String description(int id) {
        long millis = duration(id);
        return day(id).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) + "，专注 "
                + (millis > 0 && millis < 1000 ? "不足1秒" : FocusStatistics.duration(millis));
    }
    private int cellAt(float x, float y) {
        if (x < gridLeft || y < gridTop) return ExploreByTouchHelper.INVALID_ID;
        int column = (int) ((x - gridLeft) / step), row = (int) ((y - gridTop) / step);
        if (column >= columns || row >= 7) return ExploreByTouchHelper.INVALID_ID;
        int id = column * 7 + row;
        return day(id).isAfter(end) ? ExploreByTouchHelper.INVALID_ID : id;
    }
    private void select(int id) {
        highlighted = day(id); invalidate(); accessibility.invalidateRoot();
        selected.accept(highlighted, duration(id));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int) Math.ceil(gridLeft + columns * step - gap), widthSpec),
                resolveSize((int) Math.ceil(gridTop + 7 * step - gap + dp(4)), heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect visible = new Rect();
        // A hardware display list may be recorded while this child is below the
        // ScrollView viewport. Window visibility would cache an empty first frame;
        // use the recording canvas clip so scrolling can reveal the recorded cells.
        if (!canvas.getClipBounds(visible)) return;
        int firstColumn = Math.max(0, (int) ((visible.left - gridLeft) / step));
        int lastColumn = Math.min(columns - 1, (int) ((visible.right - gridLeft) / step));
        paint.setTextSize(textSize); paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL); paint.setColor(getContext().getColor(R.color.text_secondary));
        Paint.FontMetrics font = paint.getFontMetrics();
        String[] weekdays = {"一", "", "三", "", "五", "", "日"};
        for (int row = 0; row < 7; row++) canvas.drawText(weekdays[row], 0,
                gridTop + row * step + cellSize / 2 - (font.ascent + font.descent) / 2, paint);
        float previousLabelEnd = -1;
        for (int column = firstColumn; column <= lastColumn; column++) {
            LocalDate week = start.plusWeeks(column);
            if (column == 0 || week.getMonthValue() != week.minusWeeks(1).getMonthValue()) {
                String label = week.getMonthValue() == 1 ? week.getYear() + "/1" : week.getMonthValue() + "月";
                float x = Math.min(gridLeft + column * step, getWidth() - paint.measureText(label));
                if (x >= previousLabelEnd + dp(4)) {
                    paint.setColor(getContext().getColor(R.color.text_secondary));
                    canvas.drawText(label, x, -font.top, paint);
                    previousLabelEnd = x + paint.measureText(label);
                }
            }
            for (int row = 0; row < 7; row++) {
                int id = column * 7 + row;
                LocalDate day = day(id);
                if (day.isAfter(end)) continue;
                float x = gridLeft + column * step, y = gridTop + row * step;
                paint.setColor(colors[FocusHeatmapData.level(duration(id))]);
                canvas.drawRoundRect(x, y, x + cellSize, y + cellSize, dp(3), dp(3), paint);
                if (day.equals(highlighted)) {
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.2f));
                    paint.setColor(getContext().getColor(R.color.accent_text));
                    float inset = dp(.6f);
                    canvas.drawRoundRect(x + inset, y + inset, x + cellSize - inset,
                            y + cellSize - inset, dp(3), dp(3), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (Math.abs(event.getX() - downX) <= touchSlop && Math.abs(event.getY() - downY) <= touchSlop) {
                int id = cellAt(event.getX(), event.getY());
                if (id != ExploreByTouchHelper.INVALID_ID) { select(id); performClick(); }
            }
            return true;
        }
        return event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }
    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }
    private final class DayAccessibility extends ExploreByTouchHelper {
        DayAccessibility() { super(FocusHeatmapView.this); }
        @Override protected int getVirtualViewAt(float x, float y) { return cellAt(x, y); }
        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            Rect visible = new Rect();
            if (!getLocalVisibleRect(visible)) return;
            int first = Math.max(0, (int) ((visible.left - gridLeft) / step));
            int last = Math.min(columns - 1, (int) ((visible.right - gridLeft) / step));
            for (int c = first; c <= last; c++) for (int r = 0; r < 7; r++) {
                int id = c * 7 + r;
                if (!day(id).isAfter(end)) ids.add(id);
            }
        }
        @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
            node.setContentDescription(description(id)); node.setClassName("android.widget.Button");
            node.setClickable(true); node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setSelected(day(id).equals(highlighted));
            float x = gridLeft + id / 7 * step, y = gridTop + id % 7 * step;
            node.setBoundsInParent(new Rect((int) x, (int) y, (int) (x + cellSize), (int) (y + cellSize)));
        }
        @Override protected boolean onPerformActionForVirtualView(int id, int action, Bundle args) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            select(id); return true;
        }
    }
}
