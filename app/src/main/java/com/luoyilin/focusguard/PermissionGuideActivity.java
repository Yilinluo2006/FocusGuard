package com.luoyilin.focusguard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** First-use checklist and permanent repair entry share the same live status checks. */
public class PermissionGuideActivity extends AppCompatActivity {
    static final String EXTRA_FIRST_SETUP = "first_setup";
    private long resumedAt;
    private SetupStatus status;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000L); }
    };
    private final ActivityResultLauncher<String> requestNotifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refresh());

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permission_guide);
        android.view.View guide = findViewById(R.id.permissionGuidePage);
        ((android.view.ViewGroup) guide.getParent()).removeView(guide);
        boolean first = getIntent().getBooleanExtra(EXTRA_FIRST_SETUP, false);
        guide.findViewById(R.id.setupGuideTitle).setVisibility(android.view.View.GONE);
        guide.findViewById(R.id.btnSkipSetup).setVisibility(android.view.View.GONE);
        SecondaryPage.show(this,guide,first ? "开启专注保护" : "必要设置",this::finish);
        int[] actions = {R.id.btnOpenUsageSettings, R.id.btnOpenAccessibilitySettings,
                R.id.btnOpenOverlaySettings, R.id.btnStartProtectionService};
        for (int i = 0; i < actions.length; i++) {
            final int step = i;
            Button button = findViewById(actions[i]);
            PageUi.secondary(button);
            button.setOnClickListener(v -> openStep(step));
        }
        Button battery = findViewById(R.id.btnOpenBatterySettings);
        PageUi.secondary(battery);
        battery.setOnClickListener(v -> openBatterySettings());
        Button notifications = findViewById(R.id.btnOpenNotificationSettings);
        PageUi.secondary(notifications);
        notifications.setOnClickListener(v -> openNotificationSettings());
        findViewById(R.id.btnSetupNext).setOnClickListener(v -> {
            status = new SetupStatus(this);
            int next = status.nextStep();
            if (next < 0) finish(); else openStep(next);
        });
        LinearProgressIndicator progress = findViewById(R.id.setupProgress);
        progress.setIndicatorColor(getColor(R.color.primary_button));
        progress.setTrackColor(getColor(R.color.secondary_surface));
    }
    @Override protected void onResume() {
        super.onResume();
        resumedAt = android.os.SystemClock.uptimeMillis();
        ProtectionService.start(this);
        handler.removeCallbacks(check);
        handler.post(check);
    }
    @Override protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
    private void refresh() {
        if (isFinishing() || isDestroyed()) return;
        status = new SetupStatus(this);
        row(R.id.tvPermissionStatus, R.id.btnOpenUsageSettings, status.usage, "已开启", "待开启", "开启使用记录");
        row(R.id.tvOverlayStatus, R.id.btnOpenOverlaySettings, status.overlay, "已开启", "待开启", "开启浮窗");
        row(R.id.tvProtectionStatus, R.id.btnStartProtectionService, status.protection, "运行中", "未运行", "启动后台保护");
        row(R.id.tvBatteryStatus, R.id.btnOpenBatterySettings, status.battery, "已解除限制", "建议开启", "设置后台运行");
        row(R.id.tvNotificationStatus, R.id.btnOpenNotificationSettings, status.notifications, "已开启", "建议开启", "开启通知");
        boolean connecting = status.accessibilityEnabled && !status.accessibility
                && android.os.SystemClock.uptimeMillis() - resumedAt < 2000L;
        row(R.id.tvAccessibilityStatus, R.id.btnOpenAccessibilitySettings, status.accessibility,
                "运行中", status.accessibilityEnabled ? (connecting ? "连接中" : "已断开") : "待开启",
                status.accessibilityEnabled ? "重新连接无障碍" : "开启无障碍");
        ((TextView) findViewById(R.id.setupAccessibilityDescription)).setText(
                status.accessibilityEnabled && !status.accessibility && !connecting
                        ? "请在系统设置中关闭再开启奶蛙ToDo 无障碍服务。"
                        : "识别正在使用的应用，在超时或专注时执行限制。");
        int completed = status.completed();
        ((TextView) findViewById(R.id.setupProgressText)).setText(completed == 4
                ? "必要设置已就绪" : "已完成 " + completed + " / 4");
        ((LinearProgressIndicator) findViewById(R.id.setupProgress)).setProgressCompat(completed, false);
        String[] next = {"开启使用记录", "开启无障碍", "开启浮窗", "启动后台保护"};
        Button action = findViewById(R.id.btnSetupNext);
        action.setText(status.nextStep() < 0 ? "完成设置" : status.nextStep() == 1 && status.accessibilityEnabled
                ? (connecting ? "等待服务连接…" : "重新连接无障碍") : next[status.nextStep()]);
        action.setEnabled(!connecting);
    }
    private void row(int statusId, int actionId, boolean ready, String readyText, String missingText, String action) {
        TextView label = findViewById(statusId);
        label.setText(ready ? readyText : missingText);
        label.setTextColor(getColor(ready ? R.color.accent_text : R.color.text_secondary));
        ((Button) findViewById(actionId)).setText(ready ? "查看设置" : action);
        if (actionId == R.id.btnStartProtectionService && ready)
            ((Button) findViewById(actionId)).setText("重新启动");
    }
    private void openStep(int step) {
        if (step == 0) openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        else if (step == 1) {
            Intent detail = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(this, FocusAccessibilityService.class));
            try { startActivity(detail); }
            catch (android.content.ActivityNotFoundException | SecurityException error) {
                openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        } else if (step == 2) openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        else {
            if (!FocusAccessibilityService.isEnabled(this) && !WallpaperGuard.available(this)) { openStep(1); return; }
            ProtectionService.start(this);
            handler.removeCallbacks(check);
            handler.postDelayed(check, 800L);
        }
    }
    private void openBatterySettings() {
        status = new SetupStatus(this);
        if (!status.battery) openSettings(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName())));
        else openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !new SetupStatus(this).notifications
                && androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            boolean asked = getPreferences(MODE_PRIVATE).getBoolean("notificationsAsked", false);
            if (!asked) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notificationsAsked", true).apply();
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException error) {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                android.widget.Toast.makeText(this, "请到系统设置中查找奶蛙ToDo", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
