package com.luoyilin.focusguard;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import java.util.*;

/** User-initiated enrollment and matching against only the enrolled application's chosen controls. */
final class RecordedPageController {
    private final FocusAccessibilityService service;
    private final WindowManager windows;
    private final SystemExitReminderController reminder;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<RecordedPageRule> rules = Collections.emptyList();
    private String pkg = "", type = "", activeRule = "", captureTarget = "";
    private String replacingId;
    private int windowId = -1;
    private View overlay;
    private boolean closed, pending, enabled;
    interface PageMatch { RecordedPageRule find(String pkg, String type, int windowId, List<RecordedPageRule> eligible); }
    interface ForegroundWindow { boolean current(int windowId); }
    private final PageMatch matcher;
    private final ForegroundWindow foreground;
    private final Runnable check = () -> { pending = false; try { evaluate(); } catch (RuntimeException unavailable) { close(); } };
    private final Runnable cancelCapture = this::stopCapture;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            enabled = PageMonitoringStore.read(service).enabled;
            rules = RecordedPageStore.read(service); eventTypes();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || !enabled) {
                stopCapture(); leave(); handler.removeCallbacks(check); pending = false; return;
            }
            if (RecordedPageStore.captureAction(service).equals(intent.getAction())) {
                startCapture(intent.getStringExtra("target")); replacingId = intent.getStringExtra("replaceId");
            }
            else { leave(); schedule(); }
        }
    };
    RecordedPageController(FocusAccessibilityService service) {
        this(service, service.getSystemService(WindowManager.class), new SystemExitReminderController(service), null);
    }
    RecordedPageController(FocusAccessibilityService service, WindowManager windows,
                           SystemExitReminderController reminder, PageMatch matcher) {
        this(service, windows, reminder, matcher, null);
    }
    RecordedPageController(FocusAccessibilityService service, WindowManager windows,
                           SystemExitReminderController reminder, PageMatch matcher, ForegroundWindow foreground) {
        this.service = service; this.windows = windows; this.reminder = reminder;
        this.matcher = matcher == null ? this::find : matcher;
        this.foreground = foreground == null ? this::foregroundWindow : foreground; rules = RecordedPageStore.read(service);
        enabled = PageMonitoringStore.read(service).enabled;
        eventTypes();
        IntentFilter filter = new IntentFilter(PageMonitoringStore.action(service));
        filter.addAction(RecordedPageStore.captureAction(service)); filter.addAction(Intent.ACTION_SCREEN_OFF);
        ContextCompat.registerReceiver(service, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    static boolean system(Context c, String pkg) {
        if ("android".equals(pkg)) return true;
        try {
            ApplicationInfo info = c.getPackageManager().getApplicationInfo(pkg, 0);
            android.telecom.TelecomManager telecom = c.getSystemService(android.telecom.TelecomManager.class);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    || telecom != null && pkg.equals(telecom.getDefaultDialerPackage());
        } catch (Exception missing) { return true; } // An unknown component must never be held captive.
    }
    static boolean emergency(Context c, String pkg) {
        if ("android".equals(pkg) || "com.android.systemui".equals(pkg)) return true;
        android.telecom.TelecomManager telecom = c.getSystemService(android.telecom.TelecomManager.class);
        if (telecom != null && pkg.equals(telecom.getDefaultDialerPackage())) return true;
        String keyboard = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        return keyboard != null && keyboard.startsWith(pkg + "/");
    }
    private boolean foregroundWindow(int eventWindowId) {
        List<AccessibilityWindowInfo> available = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : available)
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) return window.getId() == eventWindowId;
            for (AccessibilityWindowInfo window : available)
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isActive()) return window.getId() == eventWindowId;
            return true; // No foreground metadata available; preserve existing event fallback.
        } finally { for (AccessibilityWindowInfo window : available) window.recycle(); }
    }
    private void eventTypes() {
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = service.getServiceInfo();
            if (info == null) return;
            int wanted = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            boolean any = false; for (RecordedPageRule r : rules) any |= r.enabled;
            if (enabled && any) wanted |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_SELECTED;
            if (info.eventTypes != wanted) { info.eventTypes = wanted; service.setServiceInfo(info); }
        } catch (RuntimeException unavailable) { } // Optional monitoring must not disturb the main protection service.
    }
    void onEvent(AccessibilityEvent event) {
        if (closed || !enabled || event.getPackageName() == null) return;
        String next = event.getPackageName().toString();
        if (next.equals(service.getPackageName()) && (event.getClassName() == null
                || !event.getClassName().toString().equals(PageMonitoringActivity.class.getName()))) {
            if (event.getClassName() == null || !event.getClassName().toString().startsWith(service.getPackageName() + ".")) return;
        }
        if (next.equals("com.android.systemui")) return;
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String keyboard = android.provider.Settings.Secure.getString(service.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (keyboard != null && keyboard.startsWith(next + "/")) return;
            if (!foreground.current(event.getWindowId())) return;
            String nextType = RecordedPageNodes.value(event.getClassName());
            if ((nextType.startsWith("android.widget.") || nextType.startsWith("android.view."))
                    && next.equals(pkg) && event.getWindowId() == windowId) return;
            if (!next.equals(pkg) || !nextType.equals(type) || event.getWindowId() != windowId) {
                leave();
            }
            pkg = next; type = nextType; windowId = event.getWindowId();
            if (captureTarget.isEmpty()) schedule();
        } else if ((eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
                && next.equals(pkg) && event.getWindowId() == windowId && captureTarget.isEmpty()) schedule();
    }
    private void schedule() {
        if (closed || pending || !enabled || !captureTarget.isEmpty()) return;
        boolean eligible = false;
        for (RecordedPageRule r : rules) eligible |= r.page(pkg, type);
        if (!eligible) { leave(); return; }
        pending = true; handler.postDelayed(check, 180);
    }
    private boolean awake() {
        PowerManager power = service.getSystemService(PowerManager.class);
        android.app.KeyguardManager lock = service.getSystemService(android.app.KeyguardManager.class);
        return power != null && power.isInteractive() && (lock == null || !lock.isKeyguardLocked());
    }
    private void evaluate() {
        if (closed || !captureTarget.isEmpty() || !PageMonitoringStore.read(service).enabled || !awake()) { leave(); return; }
        List<RecordedPageRule> eligible = new ArrayList<>();
        for (RecordedPageRule r : rules) if (r.page(pkg, type)) eligible.add(r);
        if (eligible.isEmpty()) { leave(); return; }
        RecordedPageRule found = matcher.find(pkg, type, windowId, eligible);
        if (found == null) { leave(); return; }
        if (found.id.equals(activeRule)) return;
        leave(); activeRule = found.id;
        boolean protectedSystem = system(service, found.pkg);
        if (emergency(service, found.pkg) || (found.system || protectedSystem) && !found.restrictSystem) reminder.recordedPage(found.name);
        else block(found);
    }
    private RecordedPageRule find(String pkg, String type, int windowId, List<RecordedPageRule> eligible) {
        for (RecordedPageRule r : eligible) if (r.classOnly && r.canUseClassOnly()) return r;
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) return null;
        try {
            if (!pkg.equals(RecordedPageNodes.value(root.getPackageName())) || root.getWindowId() != windowId) return null;
            for (RecordedPageRule r : eligible) {
                boolean match = RecordedPageNodes.matches(root, r);
                if (match) return r;
            }
            return null;
        } finally { root.recycle(); }
    }
    private void leave() { activeRule = ""; reminder.hide(); if (captureTarget.isEmpty()) removeOverlay(); }
    private void block(RecordedPageRule rule) {
        LinearLayout body = body(); body.setGravity(Gravity.CENTER);
        body.addView(PageUi.heading(body.getContext(), "此页面已限制进入", 24));
        body.addView(note(body.getContext(), rule.name + "\n\n这是你确认录入的页面规则。可随时进入管理页暂停规则或关闭页面监控。"));
        body.addView(button(body.getContext(), "返回上一页", () -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)));
        body.addView(button(body.getContext(), "返回桌面", () -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)));
        body.addView(button(body.getContext(), "管理页面规则", () -> service.startActivity(new Intent(service, PageMonitoringActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        show(body, true);
    }
    private void startCapture(String target) {
        if (target == null || target.isEmpty() || target.equals(service.getPackageName()) || !PageMonitoringStore.read(service).enabled) return;
        stopCapture(); leave(); captureTarget = target;
        showCaptureBubble(); handler.postDelayed(cancelCapture, 180_000);
        toast("拖动“录入”浮窗可移动；到目标页面后点击录入，长按取消");
    }
    private void showCaptureBubble() {
        removeOverlay(); Context c = Appearance.overlayContext(service);
        TextView bubble = new TextView(c); bubble.setText("＋ 录入"); bubble.setTextSize(14);
        bubble.setTextColor(c.getColor(R.color.text_primary)); bubble.setGravity(Gravity.CENTER);
        bubble.setContentDescription("页面录入浮窗；点击录入当前页面，拖动移动，长按取消");
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(c.getColor(R.color.surface_background)); bg.setCornerRadius(PageUi.dp(c, 24));
        bg.setStroke(PageUi.dp(c, 1), c.getColor(R.color.accent_text)); bubble.setBackground(bg);
        int width = PageUi.dp(c, 88), height = PageUi.dp(c, 48);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        p.x = Math.max(0, screenWidth - width - PageUi.dp(c, 8)); p.y = screenHeight / 3;
        p.setTitle("FocusGuard page enrollment");
        final float[] down = new float[2]; final int[] origin = new int[2];
        final long[] pressed = {0}; final boolean[] dragged = {false};
        bubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX(); down[1] = event.getRawY(); origin[0] = p.x; origin[1] = p.y;
                    pressed[0] = SystemClock.elapsedRealtime(); dragged[0] = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
                    if (Math.abs(dx) + Math.abs(dy) > PageUi.dp(c, 6)) dragged[0] = true;
                    if (dragged[0]) {
                        p.x = Math.max(0, Math.min(screenWidth - width, origin[0] + (int) dx));
                        p.y = Math.max(PageUi.dp(c, 24), Math.min(screenHeight - height - PageUi.dp(c, 40), origin[1] + (int) dy));
                        try { windows.updateViewLayout(bubble, p); } catch (RuntimeException ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragged[0]) {
                        p.x = p.x + width / 2 < screenWidth / 2 ? PageUi.dp(c, 4) : Math.max(0, screenWidth - width - PageUi.dp(c, 4));
                        try { windows.updateViewLayout(bubble, p); } catch (RuntimeException ignored) { }
                    } else if (SystemClock.elapsedRealtime() - pressed[0] >= ViewConfiguration.getLongPressTimeout()) {
                        stopCapture(); toast("已取消录入");
                    } else capture();
                    return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return false;
            }
        });
        try { windows.addView(bubble, p); overlay = bubble; }
        catch (RuntimeException unavailable) { stopCapture(); toast("系统未允许显示录入浮窗"); }
    }
    private AccessibilityNodeInfo targetRoot() {
        // Clicking our enrollment button can make its overlay active. Select only the last foreground
        // system/app window, never the overlay, and never retrieve roots of unrelated windows.
        List<AccessibilityWindowInfo> available = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : available) {
                if (window.getId() == windowId && window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
                    return window.getRoot();
            }
        } finally { for (AccessibilityWindowInfo window : available) window.recycle(); }
        return service.getRootInActiveWindow();
    }
    private AccessibilityNodeInfo enrollmentRoot() {
        // Only inspect metadata to select the focused application window. Never traverse other roots.
        List<AccessibilityWindowInfo> available = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : available)
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) return window.getRoot();
        } finally { for (AccessibilityWindowInfo window : available) window.recycle(); }
        return targetRoot();
    }
    private String pageName(String packageName, List<RecordedPageRule.Anchor> anchors) {
        String appName = packageName;
        try { appName = service.getPackageManager().getApplicationLabel(service.getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
        catch (Exception unavailable) { }
        String name = appName + (anchors.isEmpty() ? " · 当前界面" : " · " + anchors.get(0).text);
        return name.length() > 40 ? name.substring(0, 40) : name;
    }
    void preview(RecordedPageRule rule) {
        handler.removeCallbacks(cancelCapture); removeOverlay();
        LinearLayout body = body(); body.addView(PageUi.heading(body.getContext(), "确认录入当前页面", 20));
        body.addView(note(body.getContext(), "名称：" + rule.name + "\n所属应用：" + rule.pkg
                + "\n命中后：限制进入，可在奶蛙内随时暂停或撤回"));
        body.addView(note(body.getContext(), rule.classOnly
                ? "识别范围：整个界面。同一界面内的多个标签或弹层也可能一起受限；这不是整个应用的限制。"
                : "识别范围：页面标识与下列稳定特征同时出现。请确认没有敏感文字，并测试相邻页面。"));
        for (RecordedPageRule.Anchor a : rule.anchors) body.addView(note(body.getContext(), "• " + a.text + (a.selected ? "（须处于选中状态）" : "")));
        body.addView(note(body.getContext(), "页面标识：" + rule.type + "\n名称可在保存后的管理页修改。"));
        TextView feedback = note(body.getContext(), "确认摘要后添加；未确认不会保存或开启规则。");
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(feedback);
        body.addView(button(body.getContext(), replacingId == null ? "确认添加" : "确认更新", () -> saveConfirmed(rule, feedback)));
        body.addView(button(body.getContext(), "重新录入", () -> { showCaptureBubble(); handler.postDelayed(cancelCapture, 180_000); }));
        body.addView(button(body.getContext(), "取消，不保存", this::stopCapture)); show(body, false);
    }
    private void saveConfirmed(RecordedPageRule rule, TextView feedback) {
        String error = RecordedPageStore.save(service, rule, replacingId);
        if (error != null) { feedback.setText(error); return; }
        rules = RecordedPageStore.read(service);
        String savedId = replacingId == null ? rule.id : replacingId;
        captureTarget = "@saved"; replacingId = null;
        LinearLayout success = body(); success.addView(PageUi.heading(success.getContext(), "页面记录已保存", 20));
        success.addView(note(success.getContext(), "已读回确认。请分别测试目标页面与相邻页面。"));
        success.addView(button(success.getContext(), "查看已录入页面", () -> {
            stopCapture(); service.startActivity(new Intent(service, PageMonitoringActivity.class)
                    .putExtra("showRecords", true).putExtra("savedId", savedId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }));
        success.addView(button(success.getContext(), "完成，返回页面验证", () -> { stopCapture(); schedule(); })); show(success, false);
    }
    static boolean acceptsCaptureTarget(String target, String pkg, boolean system) {
        return "@current".equals(target) || ("@system".equals(target) ? system : target.equals(pkg));
    }
    private void capture() {
        if (!PageMonitoringStore.read(service).enabled || !awake()) { stopCapture(); return; }
        AccessibilityNodeInfo root = enrollmentRoot();
        if (root == null) { toast("系统未提供当前窗口，请离开后重新进入再录入"); return; }
        String targetPkg = RecordedPageNodes.value(root.getPackageName());
        boolean system = system(service, targetPkg);
        if (targetPkg.equals(service.getPackageName()) || emergency(service, targetPkg) || !targetPkg.equals(pkg) || root.getWindowId() != windowId
                || type.isEmpty() || !acceptsCaptureTarget(captureTarget, targetPkg, system)) {
            root.recycle(); toast("请先进入你选择的目标页面，再点录入"); return;
        }
        String targetType = type;
        RecordedPageNodes.CaptureResult captured = RecordedPageNodes.captureResult(root); // Consumes and releases root.
        List<RecordedPageRule.Anchor> suggested = RecordedPageEnrollment.suggest(captured.anchors);
        RecordedPageRule proposed = new RecordedPageRule(UUID.randomUUID().toString(), pageName(targetPkg, suggested),
                targetPkg, targetType, system, true, suggested, suggested.isEmpty(), system);
        if (proposed.classOnly && !proposed.canUseClassOnly()) {
            toast("这个窗口由多个页面共用，且没有稳定特征，暂不能自动区分。请尝试显示标题或重新进入。"); return;
        }
        preview(proposed);
    }
    private void stopCapture() {
        handler.removeCallbacks(cancelCapture); captureTarget = ""; replacingId = null; removeOverlay();
    }
    private LinearLayout body() {
        Context c = Appearance.overlayContext(service); LinearLayout body = new LinearLayout(c); body.setOrientation(LinearLayout.VERTICAL);
        int padding = PageUi.dp(c, 20); body.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(c.getColor(R.color.surface_background)); bg.setCornerRadius(PageUi.dp(c, 16)); body.setBackground(bg); return body;
    }
    private TextView note(Context c, String message) {
        TextView v = new TextView(c); v.setText(message); v.setTextSize(14); v.setTextColor(c.getColor(R.color.text_secondary));
        v.setPadding(0, PageUi.dp(c, 12), 0, PageUi.dp(c, 12)); return v;
    }
    private Button button(Context c, String label, Runnable click) {
        Button b = new Button(c); b.setText(label); PageUi.secondary(b); b.setOnClickListener(v -> click.run()); return b;
    }
    private void show(View body, boolean full) {
        removeOverlay();
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(full ? -1 : Math.min(PageUi.dp(service, 360),
                service.getResources().getDisplayMetrics().widthPixels - PageUi.dp(service, 24)), full ? -1 : -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = full ? Gravity.CENTER : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; p.y = full ? 0 : PageUi.dp(service, 24);
        p.setTitle(full ? "FocusGuard recorded page restriction" : "FocusGuard page enrollment");
        try { windows.addView(body, p); overlay = body; }
        catch (RuntimeException unavailable) { toast("系统未允许显示页面监控窗口"); }
    }
    private void removeOverlay() { if (overlay != null) { try { windows.removeViewImmediate(overlay); } catch (RuntimeException ignored) { } overlay = null; } }
    private void toast(String message) { Toast.makeText(service, message, Toast.LENGTH_LONG).show(); }
    void close() {
        if (closed) return; closed = true; enabled = false; eventTypes(); stopCapture(); leave(); reminder.close(); handler.removeCallbacksAndMessages(null);
        try { service.unregisterReceiver(changes); } catch (IllegalArgumentException ignored) { }
    }
}
