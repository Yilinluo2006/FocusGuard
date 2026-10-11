package com.luoyilin.focusguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.PixelCopy;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.lang.ref.WeakReference;

/** Reveals the newly themed Activity through the old frame retained across recreation. */
final class ThemeReveal {
    private static final long DURATION = 620;
    private static final double MAX_PIXELS = 4_000_000d;

    public static final class State extends ViewModel {
        Bitmap snapshot;
        int width, height;
        float x, y;
        boolean targetDark, busy;
        int generation;
        WeakReference<View> source = new WeakReference<>(null);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable expiry = this::clear;
        public State() { }

        void clear() {
            generation++;
            handler.removeCallbacks(expiry);
            if (snapshot != null) { snapshot.recycle(); snapshot = null; }
            View button = source.get();
            if (button != null) button.setEnabled(true);
            source.clear();
            busy = false;
        }
        @Override protected void onCleared() { clear(); }
    }

    private static State state(AppCompatActivity activity) {
        return new ViewModelProvider(activity).get(State.class);
    }

    static void toggle(AppCompatActivity activity, View source) {
        if(Appearance.sleepActive(activity)){android.widget.Toast.makeText(activity,"睡眠期间保持深色，结束后恢复原外观",android.widget.Toast.LENGTH_SHORT).show();return;}
        State state = state(activity);
        if (state.busy || activity.isFinishing() || activity.isDestroyed()) return;
        boolean targetDark = !Appearance.dark(activity);
        FrameLayout host = activity.findViewById(android.R.id.content);
        if (!ValueAnimator.areAnimatorsEnabled() || host.getWidth() == 0 || host.getHeight() == 0) {
            Appearance.setMode(activity, targetDark ? 2 : 1);
            return;
        }
        Bitmap pending = null;
        try {
            state.width = host.getWidth();
            state.height = host.getHeight();
            double scale = Math.min(1d, Math.sqrt(MAX_PIXELS / ((double) state.width * state.height)));
            int width = Math.max(1, (int) Math.round(state.width * scale));
            int height = Math.max(1, (int) Math.round(state.height * scale));
            pending = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] hostLocation = new int[2], sourceLocation = new int[2];
            host.getLocationInWindow(hostLocation);
            source.getLocationInWindow(sourceLocation);
            state.x = sourceLocation[0] - hostLocation[0] + source.getWidth() / 2f;
            state.y = sourceLocation[1] - hostLocation[1] + source.getHeight() / 2f;
            state.targetDark = targetDark;
            state.busy = true;
            state.source = new WeakReference<>(source);
            source.setEnabled(false);
            // If recreation never arrives, release the frame and allow another attempt.
            state.handler.postDelayed(state.expiry, 3000);
            Bitmap frame = pending;
            int request = ++state.generation;
            Rect bounds = new Rect(hostLocation[0], hostLocation[1],
                    hostLocation[0] + state.width, hostLocation[1] + state.height);
            // Copy the already rendered window instead of redrawing the entire View tree in software.
            PixelCopy.request(activity.getWindow(), bounds, frame, result -> {
                if (request != state.generation || activity.isFinishing() || activity.isDestroyed()
                        || !activity.hasWindowFocus()) {
                    frame.recycle();
                    if (request == state.generation) state.clear();
                    return;
                }
                if (result == PixelCopy.SUCCESS) state.snapshot = frame;
                else { frame.recycle(); state.clear(); }
                if (!Appearance.setMode(activity, targetDark ? 2 : 1)) state.clear();
            }, state.handler);
            return;
        } catch (OutOfMemoryError | RuntimeException error) {
            if (pending != null) pending.recycle();
            state.clear();
            android.util.Log.w("ThemeReveal", "Using immediate theme switch", error);
        }
        if (!Appearance.setMode(activity, targetDark ? 2 : 1)) state.clear();
    }

    static Cover attach(AppCompatActivity activity) {
        State state = state(activity);
        if (state.snapshot == null) return null;
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark != state.targetDark) { state.clear(); return null; }
        state.handler.removeCallbacks(state.expiry);
        FrameLayout host = activity.findViewById(android.R.id.content);
        Cover cover = new Cover(activity, host, state);
        host.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        cover.getViewTreeObserver().addOnPreDrawListener(cover);
        return cover;
    }

    static final class Cover extends View implements ViewTreeObserver.OnPreDrawListener {
        private final FrameLayout host;
        private final State state;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path opening = new Path();
        private final Rect destination = new Rect();
        private Bitmap snapshot;
        private ValueAnimator animator;
        private float radius;
        private boolean finished;

        Cover(AppCompatActivity activity, FrameLayout host, State state) {
            super(activity);
            this.host = host;
            this.state = state;
            snapshot = state.snapshot;
            state.snapshot = null;
            setClickable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            if (getWidth() != state.width || getHeight() != state.height
                    || !ValueAnimator.areAnimatorsEnabled()) {
                finish();
                return true;
            }
            destination.set(0, 0, getWidth(), getHeight());
            float dx = Math.max(state.x, getWidth() - state.x);
            float dy = Math.max(state.y, getHeight() - state.y);
            float end = (float) Math.hypot(dx, dy) + 2;
            animator = ValueAnimator.ofFloat(0f, end);
            animator.setDuration(DURATION);
            animator.setInterpolator(new PathInterpolator(.22f, 0f, .2f, 1f));
            animator.addUpdateListener(value -> {
                radius = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { finish(); }
                @Override public void onAnimationCancel(Animator animation) { finish(); }
            });
            animator.start();
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (snapshot == null || finished) return;
            // The complete old image covers the first frame; the new view tree is already laid out below.
            destination.set(0, 0, getWidth(), getHeight());
            int save = canvas.save();
            if (radius > 0) {
                opening.reset();
                opening.addCircle(state.x, state.y, radius, Path.Direction.CW);
                canvas.clipOutPath(opening);
            }
            canvas.drawBitmap(snapshot, null, destination, paint);
            canvas.restoreToCount(save);
        }

        void finish() {
            if (finished) return;
            finished = true;
            if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(this);
            if (animator != null) {
                animator.removeAllListeners();
                animator.removeAllUpdateListeners();
                animator.cancel();
            }
            host.removeView(this);
            // Drop the reference; RenderThread may still own the previous display list for a frame.
            snapshot = null;
            state.clear();
        }

        @Override protected void onDetachedFromWindow() {
            finish();
            super.onDetachedFromWindow();
        }
    }
}
