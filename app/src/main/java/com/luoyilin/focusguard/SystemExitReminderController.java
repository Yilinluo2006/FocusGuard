package com.luoyilin.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/** Optional, non-modal reminder. It never clicks settings, uninstalls apps or holds the user captive. */
final class SystemExitReminderController {
    private final FocusAccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String appName;
    private final Runnable returnToSystem;
    private final WindowManager windowManager;
    private final int windowType;
    interface IdentityLookup { int detect(int windowId, String pkg, String type); }
    private final IdentityLookup identityLookup;
    private View view;
    private int remindedKinds;
    private boolean closed;
    private int windowGeneration;
    private long accessibilityContextUntil;
    private final Runnable dismiss = this::hide;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || !PageMonitoringStore.read(service).enabled) {
                windowGeneration++; hide();
            }
        }
    };

    SystemExitReminderController(FocusAccessibilityService service) {
        this(service, () -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
    }
    SystemExitReminderController(FocusAccessibilityService service, Runnable returnToSystem) {
        this(service, returnToSystem, service.getSystemService(WindowManager.class),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }
    SystemExitReminderController(FocusAccessibilityService service, Runnable returnToSystem,
                                 WindowManager windowManager, int windowType) {
        this(service, returnToSystem, windowManager, windowType,
                (windowId, pkg, type) -> SystemExitIdentityReader.detect(service.getRootInActiveWindow(), pkg, type,
                        service.getString(R.string.app_name), service.getPackageName(), windowId));
    }
    SystemExitReminderController(FocusAccessibilityService service, Runnable returnToSystem,
                                 WindowManager windowManager, int windowType, IdentityLookup identityLookup) {
        this.service = service; appName = service.getString(R.string.app_name);
        this.returnToSystem = returnToSystem;
        this.windowManager = windowManager;
        this.windowType = windowType;
        this.identityLookup = identityLookup;
        IntentFilter filter = new IntentFilter(PageMonitoringStore.action(service));
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        ContextCompat.registerReceiver(service, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    void onWindow(AccessibilityEvent event) {
        if (closed) return;
        String pkg = event.getPackageName().toString();
        String type = event.getClassName() == null ? "" : event.getClassName().toString();
        if ("com.android.systemui".equals(pkg)) return;
        if (pkg.equals(service.getPackageName()) && !type.startsWith(service.getPackageName() + ".")
                && !type.equals(PageMonitoringActivity.class.getName())) return; // Our own banner is not a new page.
        if (!SystemExitPagePolicy.systemPackage(pkg)) {
            windowGeneration++; remindedKinds = 0; accessibilityContextUntil = 0; hide(); return;
        }
        if (!PageMonitoringStore.read(service).enabled) { hide(); return; }
        // OEMs emit late widget-window events from the page underneath a system activity or our banner.
        // They do not establish a different exit page; retain the reminder until an activity change or timeout.
        if (type.startsWith("android.widget.") || type.startsWith("android.view.")) return;
        final int generation = ++windowGeneration;
        android.os.PowerManager power = service.getSystemService(android.os.PowerManager.class);
        android.app.KeyguardManager lock = service.getSystemService(android.app.KeyguardManager.class);
        if (power == null || !power.isInteractive() || lock != null && lock.isKeyguardLocked()) { hide(); return; }
        // Read titles only after consent and only for the supported system packages.
        List<String> titles = new ArrayList<>();
        for (CharSequence text : event.getText()) {
            if (titles.size() >= 16) break;
            if (text != null && text.length() <= 160) titles.add(text.toString());
        }
        CharSequence description = event.getContentDescription();
        if (description != null && description.length() <= 160) titles.add(description.toString());
        if (SystemExitPagePolicy.accessibilityContext(type, titles))
            accessibilityContextUntil = SystemClock.elapsedRealtime() + 30_000;
        int kind = SystemExitPagePolicy.detect(pkg, type, titles, appName, service.getPackageName(),
                SystemClock.elapsedRealtime() < accessibilityContextUntil);
        boolean genericServicePage = kind == SystemExitPagePolicy.ACCESSIBILITY
                && !type.toLowerCase(java.util.Locale.ROOT).contains("accessibility");
        if ((kind == SystemExitPagePolicy.NONE || genericServicePage)
                && SystemExitPagePolicy.nodeLookupAllowed(pkg, type, titles,
                appName, service.getPackageName(), SystemClock.elapsedRealtime() < accessibilityContextUntil)) {
            final int windowId = event.getWindowId();
            kind = identityLookup.detect(windowId, pkg, type);
            if (kind == SystemExitPagePolicy.NONE) {
                // Some OEM window events precede population of the identity fields. Retry once, bound to
                // the same event window and visit; never retain the event, nodes or title text.
                handler.postDelayed(() -> {
                    if (closed || generation != windowGeneration || !PageMonitoringStore.read(service).enabled
                            || !power.isInteractive() || lock != null && lock.isKeyguardLocked()) return;
                    try { remind(identityLookup.detect(windowId, pkg, type)); } catch (RuntimeException ignored) { }
                }, 250);
            }
        }
        if (kind == SystemExitPagePolicy.NONE) { hide(); return; }
        remind(kind);
    }

    private void remind(int kind) {
        if (kind == SystemExitPagePolicy.NONE) return;
        if ((remindedKinds & kind) == 0) {
            hide(); show(kind);
            if (view != null) remindedKinds |= kind;
        }
    }

    private void show(int kind) {
        show(kind, null);
    }
    void recordedPage(String name) { hide(); show(SystemExitPagePolicy.NONE, name); }
    private void show(int kind, String recordedName) {
        Context ui = Appearance.overlayContext(service);
        LinearLayout body = new LinearLayout(ui); body.setOrientation(LinearLayout.VERTICAL);
        int pad = PageUi.dp(ui, 16); body.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(ui.getColor(R.color.surface_background));
        background.setCornerRadius(PageUi.dp(ui, 16));
        background.setStroke(PageUi.dp(ui, 1), ui.getColor(R.color.accent_text)); body.setBackground(background);
        body.addView(PageUi.heading(ui, "奶蛙保护提醒", 18));
        TextView explanation = new TextView(ui); explanation.setTextSize(14);
        explanation.setTextColor(ui.getColor(R.color.text_secondary));
        explanation.setPadding(0, PageUi.dp(ui, 8), 0, PageUi.dp(ui, 8));
        explanation.setText(recordedName != null
                ? "已识别你录入的系统页面：“" + recordedName + "”。\n\n若卸载奶蛙或关闭其无障碍服务，相关保护会停止。此提醒不会改变系统设置，你可以继续操作。"
                : kind == SystemExitPagePolicy.UNINSTALL
                ? "卸载后，所有本机保护会停止，本机任务与设置可能丢失，请先确认备份。\n\n这是奶蛙的可选提醒；你可以继续系统操作。"
                : "关闭无障碍后，系统退出提醒、睡眠与专注保护会停止。应用额度保护是否继续取决于壁纸守护是否可用。\n\n你可以继续关闭服务；此提醒不会改变系统设置。");
        body.addView(explanation);
        LinearLayout actions = new LinearLayout(ui);
        Button back = new Button(ui); back.setText("返回"); PageUi.secondary(back); back.setId(R.id.systemExitReminderReturn);
        back.setOnClickListener(v -> { hide(); returnToSystem.run(); });
        Button proceed = new Button(ui); proceed.setText("继续操作"); PageUi.secondary(proceed); proceed.setId(R.id.systemExitReminderContinue);
        proceed.setOnClickListener(v -> hide());
        actions.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams other = new LinearLayout.LayoutParams(0, -2, 1); other.setMarginStart(PageUi.dp(ui, 12));
        actions.addView(proceed, other); body.addView(actions);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.min(PageUi.dp(ui, 360), service.getResources().getDisplayMetrics().widthPixels - PageUi.dp(ui, 24)), -2,
                windowType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = PageUi.dp(ui, 16);
        params.setTitle("FocusGuard optional exit reminder");
        try { windowManager.addView(body, params); view = body; handler.postDelayed(dismiss, 15_000); }
        catch (RuntimeException unavailable) { hide(); } // Respect system restrictions; do not force another window type.
    }

    void hide() {
        handler.removeCallbacks(dismiss);
        if (view != null) {
            try { windowManager.removeViewImmediate(view); } catch (RuntimeException ignored) { }
            view = null;
        }
    }
    void close() {
        if (closed) return;
        closed = true; hide();
        try { service.unregisterReceiver(changes); } catch (IllegalArgumentException ignored) { }
    }
}
