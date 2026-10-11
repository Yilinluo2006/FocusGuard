package com.luoyilin.focusguard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A session-driven progress dial; only the center indicator breathes independently. */
final class FocusProgressDialView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF(-88, -88, 88, 88);
    private final int gold;
    private final int track;
    private float progress;
    private float breath;
    private boolean countUp;
    private ValueAnimator animator;

    FocusProgressDialView(Context context) {
        super(context);
        gold = context.getColor(R.color.accent_text);
        track = context.getColor(R.color.outline);
    }

    void setProgress(float value, boolean countUp) {
        this.progress = Math.max(0f, Math.min(1f, value));
        this.countUp = countUp;
        setContentDescription(countUp ? "正计时，本分钟已过 " + Math.round(progress * 60) + " 秒"
                : "当前阶段已完成 " + Math.round(progress * 100) + "%");
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(value -> {
            breath = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        int saved = canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(size / 220f, size / 220f);

        paint.setColor(track);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            int tick = canvas.save();
            canvas.rotate(i * 6f);
            paint.setStrokeWidth(i % 5 == 0 ? 2f : 1f);
            paint.setAlpha(i % 5 == 0 ? 170 : 95);
            canvas.drawLine(0, -100, 0, i % 5 == 0 ? -106 : -103, paint);
            canvas.restoreToCount(tick);
        }
        paint.setStrokeWidth(7f);
        paint.setAlpha(105);
        canvas.drawArc(ring, -90, 360, false, paint);
        paint.setColor(gold);
        paint.setAlpha(255);
        if (progress > 0f) canvas.drawArc(ring, -90, progress * 360f, false, paint);

        double angle = Math.toRadians(-90f + progress * 360f);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle((float) (88 * Math.cos(angle)), (float) (88 * Math.sin(angle)), 5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setAlpha(115);
        canvas.drawCircle(0, 0, 37, paint);
        paint.setAlpha(70);
        canvas.drawCircle(0, 0, 48, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(200 + Math.round(breath * 55));
        canvas.drawCircle(0, 0, 8f + breath * 2f, paint);
        canvas.restoreToCount(saved);
    }
}
