package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;

/** Permission grants and live protection are checked separately. Nothing here grants a permission. */
final class SetupStatus {
    final boolean usage, accessibilityEnabled, accessibility, overlay, protection, battery, notifications;
    SetupStatus(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        usage = ops != null && (Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()))
                == AppOpsManager.MODE_ALLOWED;
        accessibilityEnabled = FocusAccessibilityService.isEnabled(context);
        accessibility = accessibilityEnabled && FocusAccessibilityService.isRunning(context);
        overlay = Settings.canDrawOverlays(context);
        protection = ProtectionService.isRunning(context);
        PowerManager power = context.getSystemService(PowerManager.class);
        battery = power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
    int completed() { return (usage ? 1 : 0) + (accessibility ? 1 : 0) + (overlay ? 1 : 0) + (protection ? 1 : 0); }
    int nextStep() { return !usage ? 0 : !accessibility ? 1 : !overlay ? 2 : !protection ? 3 : -1; }
    static boolean claimIntroduction(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("setup_guide", Context.MODE_PRIVATE);
        if (prefs.getBoolean("introduced", false)) return false;
        SetupStatus state = new SetupStatus(context);
        if (!prefs.edit().putBoolean("introduced", true).commit()) return false;
        return !(state.usage && state.accessibilityEnabled && state.overlay);
    }
}
