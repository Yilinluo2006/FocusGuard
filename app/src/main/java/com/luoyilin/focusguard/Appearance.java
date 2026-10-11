package com.luoyilin.focusguard;

import android.content.*;
import android.content.res.Configuration;
import android.util.AtomicFile;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.io.*;

public final class Appearance {
    private static final String ACTION = "com.luoyilin.focusguard.APPEARANCE_CHANGED";
    private static final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private static boolean sleeping,initialized;
    private static Context application;
    private static final java.util.Set<android.app.Activity> resumed=new java.util.HashSet<>();
    private static final Runnable check=new Runnable(){public void run(){
        refreshSleep(application);if(!resumed.isEmpty())handler.postDelayed(this,1000);
    }};
    private static AtomicFile file(Context c) {
        return new AtomicFile(new File(c.getNoBackupFilesDir(), "appearance-mode"));
    }
    static int read(Context c) {
        try (FileInputStream in = file(c).openRead()) {
            int mode = in.read(); return mode >= 0 && mode <= 2 ? mode : 0;
        } catch (IOException e) { return 0; }
    }
    static void restoreMissing(Context c, int mode) throws IOException {
        AtomicFile target = file(c);
        if (target.getBaseFile().exists()) return;
        FileOutputStream out = null;
        try { out = target.startWrite(); out.write(mode); target.finishWrite(out); }
        catch (IOException error) { if (out != null) target.failWrite(out); throw error; }
        c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));
    }
    public static void initialize(Context c) {
        if(initialized)return;initialized=true;application=c.getApplicationContext();
        // Cold launch reads the disk preferences without calling a provider during Application startup.
        sleeping=sleepFrom(c,c.getSharedPreferences("focus_mode",Context.MODE_PRIVATE).getAll());
        apply(c);
        ContextCompat.registerReceiver(c, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { refreshSleep(context);apply(context); }
        }, new IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
        if(application instanceof android.app.Application)((android.app.Application)application).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){refreshSleep(a);}
            public void onActivityStarted(android.app.Activity a){}
            public void onActivityResumed(android.app.Activity a){resumed.add(a);handler.removeCallbacks(check);handler.post(check);}
            public void onActivityPaused(android.app.Activity a){resumed.remove(a);if(resumed.isEmpty())handler.removeCallbacks(check);}
            public void onActivityStopped(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){resumed.remove(a);if(resumed.isEmpty())handler.removeCallbacks(check);}
        });
        handler.post(()->refreshSleep(application));
    }
    private static boolean sleepFrom(Context c,java.util.Map<String,?> values){
        return SleepAppearancePolicy.sleeping(ProtectionClock.now(c),Boolean.TRUE.equals(values.get("daily")),
                integer(values,"minute",1350),integer(values,"duration",180),integer(values,FocusSchedule.WEEKDAYS_KEY,127),ProtectionClock.zone(c));
    }
    private static int integer(java.util.Map<String,?> values,String key,int fallback){Object v=values.get(key);return v instanceof Number?((Number)v).intValue():fallback;}
    static boolean sleepActive(Context c){
        try{return sleepFrom(c,new FocusPreferences(c).getAll());}catch(RuntimeException unavailable){return sleeping;}
    }
    static void refreshSleep(Context c){if(c==null)return;boolean active=sleepActive(c);
        if(active==sleeping)return;sleeping=active;apply(c);
        c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));
    }
    private static void apply(Context c) {
        int mode = SleepAppearancePolicy.effectiveMode(read(c),sleeping);
        AppCompatDelegate.setDefaultNightMode(mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    public static void choose(Context c) {
        if(sleepActive(c)){new AlertDialog.Builder(c).setTitle("睡眠期间自动深色")
                .setMessage("睡眠模式及临时使用期间保持深色，结束后自动恢复原来的外观设置。")
                .setPositiveButton("知道了",null).show();return;}
        new AlertDialog.Builder(c).setTitle("外观模式")
                .setSingleChoiceItems(new String[]{"跟随系统", "浅色模式", "深色模式"}, read(c), (dialog, mode) -> {
                    if (!writeMode(c, mode)) return;
                    dialog.dismiss();
                    applyAndNotify(c);
                }).setNegativeButton("取消", null).show();
    }
    static boolean setMode(Context c, int mode) {
        if(sleepActive(c)){Toast.makeText(c,"睡眠期间保持深色，结束后恢复原外观",Toast.LENGTH_SHORT).show();return false;}
        if (!writeMode(c, mode)) return false;
        applyAndNotify(c);
        return true;
    }
    private static boolean writeMode(Context c, int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Unknown appearance mode");
        AtomicFile target = file(c); FileOutputStream out = null;
        try {
            out = target.startWrite(); out.write(mode); target.finishWrite(out);
            return true;
        } catch (IOException e) {
            if (out != null) target.failWrite(out);
            Toast.makeText(c, "外观设置保存失败，请重试", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    private static void applyAndNotify(Context c) {
        apply(c);
        c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));
    }
    static boolean dark(Context c) {
        if(sleepActive(c))return true;
        int mode = read(c);
        return mode == 2 || (mode == 0 && (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }
    static Context overlayContext(Context c) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (dark(c) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new android.view.ContextThemeWrapper(c.createConfigurationContext(config),
                dark(c) ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
    }
}
