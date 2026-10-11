package com.luoyilin.focusguard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

final class ProtectionWarning {
    private static Boolean lastHealthy;
    static boolean healthy(Context c) {
        return FocusAccessibilityService.isEnabled(c) && FocusAccessibilityService.isRunning(c);
    }
    static boolean dailyBackupRunning(Context c) {
        return WallpaperGuard.available(c) && WallpaperMonitorState.ready(WallpaperMonitorState.read(c));
    }

    static void refresh(Context c) {
        ComplianceAudit.request(c, false);
        SleepControlSession.observe(c);
        ProtectionCapabilities capabilities = ProtectionRecovery.capabilities(c);
        boolean active = capabilities.daily && capabilities.sleep && capabilities.focus;
        if (!ProtectionRecovery.needed(c)) { c.getSystemService(NotificationManager.class).cancel(53); return; }
        if (lastHealthy == null || lastHealthy != active) {
            lastHealthy = active;
            StreakWidget.updateAll(c);
        }
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (active) { manager.cancel(53); return; }
        manager.createNotificationChannel(new NotificationChannel("protection_paused",
                "保护中断提醒", NotificationManager.IMPORTANCE_HIGH));
        if (!manager.areNotificationsEnabled()) return;
        PendingIntent open = PendingIntent.getActivity(c, 53,
                new Intent(c, ProtectionRecoveryActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.notify(53, new NotificationCompat.Builder(c, "protection_paused")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(capabilities.sleep && capabilities.focus ? "应用额度保护需要恢复"
                            : capabilities.daily ? "睡眠与专注保护待恢复" : "保护已暂停，请检查设置")
                    .setContentText("点击查看受影响功能并恢复保护。本机设置与记录保留。")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(
                            ProtectionRecovery.explanation(c)))
                    .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(false).build());
        } catch (SecurityException ignored) { }
    }
}
