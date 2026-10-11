package com.luoyilin.focusguard;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Foreground scheduling keeps settings and usage statistics synchronized. */
public class DeviceSyncService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){@Override public void run(){if(!DeviceSyncManager.enabled(DeviceSyncService.this)||!new com.luoyilin.focusguard.auth.SessionManager(DeviceSyncService.this).isLoggedIn()){stopSelf();return;}DeviceSyncManager.tick(DeviceSyncService.this);handler.postDelayed(this,15000);}};
    static void start(Context c){if(!SharedQuota.synchronizationEnabled(c)||!new com.luoyilin.focusguard.auth.SessionManager(c).isLoggedIn())return;try{ContextCompat.startForegroundService(c,new Intent(c,DeviceSyncService.class));}catch(RuntimeException ignored){}}
    static void stop(Context c){c.stopService(new Intent(c,DeviceSyncService.class));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("device_sync","多设备同步",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,2212,new Intent(this,DeviceSyncActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        boolean strict=DeviceSyncManager.strictQuota(this);
        Notification note=new NotificationCompat.Builder(this,"device_sync").setSmallIcon(R.drawable.ic_nav_statistics).setContentTitle("手机与平板自动同步")
                .setContentText(strict?"共用每日额度，断网可用已领取时间":"同步配置与用时，每台设备独立额度").setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(2212,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(2212,note);
        // A queued foreground start must be acknowledged even if sync was disabled meanwhile.
        if(!DeviceSyncManager.enabled(this)||!new com.luoyilin.focusguard.auth.SessionManager(this).isLoggedIn()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        handler.removeCallbacks(poll);handler.post(poll);return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacks(poll);super.onDestroy();}
}
