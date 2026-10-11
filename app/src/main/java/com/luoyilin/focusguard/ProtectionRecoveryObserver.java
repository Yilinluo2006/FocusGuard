package com.luoyilin.focusguard;

import android.content.*;
import android.os.*;
import androidx.core.content.ContextCompat;
import java.util.HashSet;
import java.util.Set;

/** Shared by wallpaper and foreground protection; no wake lock or permission regrant. */
final class ProtectionRecoveryObserver {
    private static ProtectionRecoveryObserver instance;
    private final Set<Object> owners=new HashSet<>();private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable unlock;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))ProtectionRecoveryStore.lock(c);
        else if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){handler.removeCallbacks(unlock);handler.postDelayed(unlock,2000);}
    }};
    private ProtectionRecoveryObserver(Context c){context=c.getApplicationContext();unlock=()->UnlockHealthCheck.run(context);IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(context,receiver,f,ContextCompat.RECEIVER_NOT_EXPORTED);}
    static synchronized void acquire(Context c,Object owner){if(instance==null)instance=new ProtectionRecoveryObserver(c);instance.owners.add(owner);}
    static synchronized void release(Object owner){if(instance==null)return;instance.owners.remove(owner);if(instance.owners.isEmpty()){
        instance.handler.removeCallbacksAndMessages(null);instance.context.unregisterReceiver(instance.receiver);instance=null;
    }}
}
