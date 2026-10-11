package com.luoyilin.focusguard;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

final class SleepReminderOverlay {
    private final Context context;
    private final FocusModeStore store;
    private final WindowManager windows;
    private View view;
    private TextView countdown;
    private ObjectAnimator pulse;
    private long start;
    private int stage;
    private long shownAt;
    private long retryAfter;

    SleepReminderOverlay(Context context, FocusModeStore store) {
        this.context = context;
        this.store = store;
        windows = context.getSystemService(WindowManager.class);
    }

    void refresh(long now, long upcomingStart, int upcomingStage) {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        AudioManager audio = context.getSystemService(AudioManager.class);
        boolean unavailable = !power.isInteractive() || keyguard.isKeyguardLocked()
                || (audio != null && audio.getMode() != AudioManager.MODE_NORMAL);
        if (upcomingStage == 0 || unavailable) { hide(); return; }
        if (view != null && (start != upcomingStart || stage != upcomingStage)) hide();
        if (store.prefs.getLong("sleepOverlayAcknowledgedStart", 0) == upcomingStart
                && store.prefs.getInt("sleepOverlayAcknowledgedStage", 0) >= upcomingStage) return;
        if (view == null) {
            if (SystemClock.elapsedRealtime() < retryAfter) return;
            start = upcomingStart;
            stage = upcomingStage;
            show();
        }
        if (view == null) return;
        long seconds = Math.max(0, (upcomingStart - now + 999) / 1000);
        countdown.setText(String.format(Locale.getDefault(), "睡眠模式将在 %d:%02d 后开始",
                seconds / 60, seconds % 60));
        if (stage == 1 && SystemClock.elapsedRealtime() - shownAt >= 20_000) acknowledge();
    }

    private void show() {
        Context ui = Appearance.overlayContext(context);
        LinearLayout body = new LinearLayout(ui);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(ui.getColor(R.color.surface_background));
        background.setCornerRadius(dp(8));
        background.setStroke(dp(2), ui.getColor(R.color.success_text));
        body.setBackground(background);
        body.setElevation(dp(8));
        countdown = new TextView(ui);
        countdown.setTextSize(19);
        countdown.setTypeface(null, Typeface.BOLD);
        countdown.setTextColor(ui.getColor(R.color.text_primary));
        body.addView(countdown);
        TextView note = new TextView(ui);
        note.setText(stage == 2 ? "还剩最后一分钟，请结束手头操作，准备休息。"
                : "请安排好接下来的事，准备休息。开始后仅可使用白名单应用。");
        note.setTextSize(15);
        note.setTextColor(ui.getColor(R.color.text_secondary));
        note.setPadding(0, dp(6), 0, dp(4));
        body.addView(note);
        Button dismiss = new Button(ui);
        dismiss.setText(stage == 1 ? "知道了，最后一分钟再提醒" : "知道了");
        dismiss.setAllCaps(false);
        dismiss.setTextSize(14);
        dismiss.setTextColor(ui.getColor(R.color.text_primary));
        dismiss.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.getColor(R.color.secondary_surface)));
        dismiss.setOnClickListener(v -> acknowledge());
        body.addView(dismiss, new LinearLayout.LayoutParams(-1, dp(48)));
        int width = Math.min(dp(360), context.getResources().getDisplayMetrics().widthPixels - dp(24));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, -2,
                GuardWindow.type(context),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(16);
        try {
            GuardWindow.manager(context).addView(body, params);
            view = body;
            shownAt = SystemClock.elapsedRealtime();
            // Pulse only the title, keeping the text readable and the controls stationary.
            if (stage == 2 && ValueAnimator.areAnimatorsEnabled()) {
                pulse = ObjectAnimator.ofFloat(countdown, "alpha", 1f, 0.65f);
                pulse.setDuration(1200);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.start();
            }
        } catch (RuntimeException error) {
            retryAfter = SystemClock.elapsedRealtime() + 30_000;
            android.util.Log.w("SleepReminder", "Cannot display reminder", error);
        }
    }

    private void acknowledge() {
        store.prefs.edit().putLong("sleepOverlayAcknowledgedStart", start)
                .putInt("sleepOverlayAcknowledgedStage", stage).apply();
        hide();
    }

    void hide() {
        if (pulse != null) { pulse.cancel(); pulse = null; }
        if (view != null) {
            try { windows.removeViewImmediate(view); }
            catch (RuntimeException ignored) { }
        }
        view = null;
        countdown = null;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
