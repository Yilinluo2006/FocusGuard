package com.luoyilin.focusguard;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class ProtectionService extends Service {

    private static final String TAG = "ProtectionService";
    private static final long HEALTH_INTERVAL_MILLIS = 10_000L;

    public static boolean isRunning(Context context) {
        return ProtectionStateStore.isProtectionHealthy(context);
    }

    private static final String ACTION_RESTART =
            "com.luoyilin.focusguard.action.RESTART_PROTECTION";
    private static final String CHANNEL_ID = "focus_guard_protection";
    private static final int NOTIFICATION_ID = 1001;
    private static final int RESTART_REQUEST_CODE = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable healthTask = new Runnable() {
        @Override
        public void run() {
            ProtectionStateStore.touchProtection(ProtectionService.this);
            handler.postDelayed(this, HEALTH_INTERVAL_MILLIS);
        }
    };

    public static void start(Context context) {
        if (!FocusAccessibilityService.isEnabled(context)) {
            return;
        }

        Intent serviceIntent = new Intent(context, ProtectionService.class);
        serviceIntent.setAction(ACTION_RESTART);

        try {
            ContextCompat.startForegroundService(context, serviceIntent);
            // 以前台服务形式启动，提高进程在后台的存活优先级
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to start foreground protection", exception);
            // Android 12 以上可能拒绝后台拉起；前台页面和无障碍连接仍会再次启动。
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        ProtectionStateStore.markProtectionRunning(this, true, "protection_created");
        handler.post(healthTask);
        // 前台服务必须尽快显示通知，否则 Android 会主动停止服务
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ProtectionStateStore.markProtectionRunning(this, true, "protection_started");
        handler.removeCallbacks(healthTask);
        handler.post(healthTask);
        // 系统重连无障碍服务时，设置状态可能短暂读为关闭，不能因此主动结束保护服务。
        return START_STICKY;
        // 进程被系统回收后，请求 Android 在条件允许时重新创建服务
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        ProtectionStateStore.markProtectionRunning(this, true, "task_removed");
        if (FocusAccessibilityService.isEnabled(this)) {
            scheduleRestart();
        }
        // 用户划掉最近任务时，安排一次显式恢复，避免保护随界面任务一起消失
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(healthTask);
        ProtectionStateStore.markProtectionRunning(this, false, "protection_destroyed");
        super.onDestroy();
        // 不在 onDestroy 中反复拉起服务，避免形成“拉起-杀死-再拉起”的循环。
        // 用户划掉最近任务时，onTaskRemoved() 已经安排了一次恢复。
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "应用限制保护",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("保持 FocusGuard 的应用限制功能在后台运行");
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("FocusGuard 正在保护")
                .setContentText("受限应用的使用时间会在后台继续检查")
                .setContentIntent(openAppPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void scheduleRestart() {
        Intent restartIntent = new Intent(this, ProtectionRestartReceiver.class);
        restartIntent.setAction(ACTION_RESTART);

        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(
                this,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 2000L,
                    restartPendingIntent
            );
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
