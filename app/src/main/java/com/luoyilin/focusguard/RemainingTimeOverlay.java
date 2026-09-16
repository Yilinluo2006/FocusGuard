package com.luoyilin.focusguard;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Display-only overlay; authoritative usage accounting stays in the service. */
final class RemainingTimeOverlay {
    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView label;
    private long deadline;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (label == null) return;
            PowerManager power = context.getSystemService(PowerManager.class);
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (power == null || !power.isInteractive()
                    || (keyguard != null && keyguard.isKeyguardLocked())) {
                hide();
                return;
            }
            long remaining = Math.max(0, deadline - SystemClock.elapsedRealtime());
            label.setText(context.getString(R.string.remaining_time_label,
                    RemainingTimeFormat.format(remaining)));
            handler.postDelayed(this, 1000);
        }
    };

    RemainingTimeOverlay(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }

    void show(long remainingMillis) {
        if (remainingMillis <= 0) {
            hide();
            return;
        }
        deadline = SystemClock.elapsedRealtime() + remainingMillis;
        handler.removeCallbacks(tick);
        try {
            if (label == null) {
                TextView view = new TextView(context);
                view.setTextColor(Color.WHITE);
                view.setTextSize(14);
                view.setTypeface(Typeface.MONOSPACE);
                view.setGravity(Gravity.CENTER);
                view.setPadding(dp(10), dp(6), dp(10), dp(6));
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                GradientDrawable background = new GradientDrawable();
                background.setColor(0x99202020);
                background.setCornerRadius(dp(8));
                view.setBackground(background);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        dp(168), WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.END;
                params.x = dp(12);
                params.y = dp(48);
                params.setTitle("FocusGuard remaining time");
                windows.addView(view, params);
                label = view;
            }
            tick.run();
        } catch (RuntimeException error) {
            Log.w("FocusRemainingTime", "Cannot show timer", error);
            hide();
        }
    }

    void hide() {
        handler.removeCallbacks(tick);
        if (label == null) return;
        try {
            windows.removeViewImmediate(label);
        } catch (RuntimeException error) {
            Log.w("FocusRemainingTime", "Cannot remove timer", error);
        } finally {
            label = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
