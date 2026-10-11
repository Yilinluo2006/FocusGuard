package com.luoyilin.focusguard;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.TextView;

/** Display-only overlay; authoritative usage accounting stays in the service. */
final class RemainingTimeOverlay {
    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView label;
    private String iconPackage;
    private long deadline;
    private boolean offline;
    private boolean temporary;
    private boolean sleepTemporary;
    private WindowManager.LayoutParams params;
    private boolean rightEdge = true;
    private int savedY = -1;
    private float downX, downY;
    private int startX, startY;
    private boolean dragging;
    private long warningUntil;
    private final SharedPreferences warnings;
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
            if (sleepTemporary && remaining == 0) { hide(); return; }
            boolean lastMinute = remaining > 0 && remaining <= 60_000;
            boolean urgent = remaining <= 10_000;
            if (lastMinute && !offline && !temporary && iconPackage != null) {
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                if (!day.equals(warnings.getString(iconPackage, null))) {
                    warnings.edit().putString(iconPackage, day).apply();
                    warningUntil = SystemClock.elapsedRealtime() + 3000;
                }
            }
            boolean expanded = !offline && !temporary && SystemClock.elapsedRealtime() < warningUntil;
            label.setTextColor(urgent ? 0xFFFF7B7B : lastMinute ? 0xFFFFCE66 : Color.WHITE);
            label.setTypeface(Typeface.MONOSPACE, urgent ? Typeface.BOLD : Typeface.NORMAL);
            label.setSingleLine(false);
            label.setText(sleepTemporary ? "睡眠临时使用\n剩余 " + RemainingTimeFormat.format(remaining) : expanded
                    ? context.getString(R.string.last_minute_warning) + "\n"
                            + QuotaPresentation.timer(remaining,false,false,SharedQuota.enabled(context))
                    : QuotaPresentation.timer(remaining,temporary,offline,SharedQuota.enabled(context)));
            fitContent();
            handler.postDelayed(this, 1000);
        }
    };

    RemainingTimeOverlay(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
        warnings = context.getSharedPreferences("remaining_time_warnings", Context.MODE_PRIVATE);
    }

    void show(String packageName, long remainingMillis) {
        show(packageName, remainingMillis, false);
    }
    void show(String packageName, long remainingMillis, boolean offline) {
        show(packageName,remainingMillis,offline,false);
    }
    void showTemporary(String packageName,long remainingMillis){show(packageName,remainingMillis,false,true);}
    void showSleepTemporary(android.os.Bundle state) {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (!state.getBoolean("sleeping") || state.getLong("remaining") <= 0
                || power == null || !power.isInteractive() || (keyguard != null && keyguard.isKeyguardLocked())) {
            hide(); return;
        }
        // Preserve the provider's sampled expiry: redisplaying an old state cannot add time.
        long until = state.getLong("sampledElapsed", SystemClock.elapsedRealtime()) + state.getLong("remaining");
        show(context.getPackageName(), Math.max(0, until - SystemClock.elapsedRealtime()), false, true, true, until);
    }
    private void show(String packageName,long remainingMillis,boolean offline,boolean temporary){
        show(packageName, remainingMillis, offline, temporary, false, 0);
    }
    private void show(String packageName,long remainingMillis,boolean offline,boolean temporary,boolean sleepTemporary,long until){
        this.offline = offline;
        this.temporary = temporary;
        this.sleepTemporary = sleepTemporary;
        if (label != null && params.type != GuardWindow.type(context)) hide();
        if (remainingMillis <= 0) {
            hide();
            return;
        }
        deadline = sleepTemporary ? until : SystemClock.elapsedRealtime() + remainingMillis;
        handler.removeCallbacks(tick);
        try {
            if (label == null) {
                TextView view = new TextView(context);
                view.setTextColor(Color.WHITE);
                view.setTextSize(11);
                view.setSingleLine(true);
                view.setIncludeFontPadding(false);
                view.setTypeface(Typeface.MONOSPACE);
                view.setGravity(Gravity.CENTER);
                view.setPadding(dp(2), 0, dp(2), 0);
                view.setCompoundDrawablePadding(dp(2));
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                GradientDrawable background = new GradientDrawable();
                background.setColor(0x99202020);
                background.setCornerRadius(dp(8));
                view.setBackground(background);
                params = new WindowManager.LayoutParams(
                        dp(56), dp(22),
                        GuardWindow.type(context),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.y = savedY < 0 ? dp(sleepTemporary ? 72 : 120) : savedY;
                snapToEdge();
                params.setTitle(sleepTemporary ? "FocusGuard sleep temporary time" : "FocusGuard remaining time");
                view.setOnTouchListener((v, event) -> handleTouch(event));
                GuardWindow.manager(context).addView(view, params);
                label = view;
            }
            if (!packageName.equals(iconPackage)) {
                warningUntil = 0;
                Drawable icon;
                try {
                    icon = context.getPackageManager().getApplicationIcon(packageName);
                } catch (PackageManager.NameNotFoundException missing) {
                    icon = context.getPackageManager().getDefaultActivityIcon();
                }
                icon.setBounds(0, 0, dp(16), dp(16));
                label.setCompoundDrawablesRelative(icon, null, null, null);
                iconPackage = packageName;
            }
            tick.run();
        } catch (RuntimeException error) {
            Log.w("FocusRemainingTime", "Cannot show timer", error);
            hide();
        }
    }

    private boolean handleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                int slop = ViewConfiguration.get(context).getScaledTouchSlop();
                dragging |= Math.abs(dx) > slop || Math.abs(dy) > slop;
                if (dragging) {
                    DisplayMetrics metrics = metrics();
                    params.x = Math.max(0, Math.min(metrics.widthPixels - params.width,
                            startX + Math.round(dx)));
                    params.y = Math.max(0, Math.min(maxY(metrics), startY + Math.round(dy)));
                    updatePosition();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                rightEdge = params.x + params.width / 2 >= metrics().widthPixels / 2;
                savedY = params.y;
                snapToEdge();
                updatePosition();
                return true;
            default:
                return true;
        }
    }

    private void fitContent() {
        if (dragging || label == null) return;
        // Monospace digits keep the size stable until the time format changes.
        String[] lines = label.getText().toString().split("\n");
        float longest = 0;
        for (String line : lines) longest = Math.max(longest, label.getPaint().measureText(line));
        int padding = label.getCompoundPaddingLeft() + label.getCompoundPaddingRight();
        int width = Math.min(metrics().widthPixels, (int) Math.ceil(longest) + padding);
        int available = Math.max(1, width - padding);
        int lineCount = 0;
        for (String line : lines) {
            lineCount += Math.max(1, (int) Math.ceil(label.getPaint().measureText(line) / available));
        }
        int height = Math.max(dp(22), label.getPaint().getFontMetricsInt(null) * lineCount + dp(4));
        if (params.width == width && params.height == height) return;
        params.width = width;
        params.height = height;
        snapToEdge();
        updatePosition();
    }

    private DisplayMetrics metrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    private int maxY(DisplayMetrics metrics) {
        return Math.max(0, metrics.heightPixels - params.height - dp(48));
    }

    private void snapToEdge() {
        DisplayMetrics metrics = metrics();
        params.x = rightEdge ? Math.max(0, metrics.widthPixels - params.width) : 0;
        params.y = Math.max(0, Math.min(maxY(metrics), params.y));
    }

    private void updatePosition() {
        if (label == null) return;
        try {
            windows.updateViewLayout(label, params);
        } catch (RuntimeException error) {
            Log.w("FocusRemainingTime", "Cannot move timer", error);
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
            iconPackage = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
