package com.luoyilin.focusguard;

import android.content.*;
import android.os.*;
import android.provider.Settings;

final class ProtectionRecovery {
    private static final Handler handler=new Handler(Looper.getMainLooper());
    static boolean needed(Context c){try{return !LimitSnapshot.read(c).isEmpty()||new FocusModeStore(c).prefs.getBoolean("daily",false)
            ||new FocusModeStore(c).end(ProtectionClock.now(c))>0;}catch(Exception e){return true;}}
    static ProtectionCapabilities capabilities(Context c){SetupStatus s=new SetupStatus(c);return new ProtectionCapabilities(s.usage,s.accessibilityEnabled,s.accessibility,
            s.overlay,WallpaperGuard.selected(c),WallpaperMonitorState.ready(WallpaperMonitorState.read(c)));}
    static void check(Context c,String cause){Context app=c.getApplicationContext();if(!needed(app))return;
        ProtectionService.start(app);
        ProtectionRecoveryStore.record(app,cause,capabilities(app));ProtectionWarning.refresh(app);
    }
    static void delayed(Context c,String cause){Context app=c.getApplicationContext();handler.postDelayed(()->check(app,cause),2500);}
    static String explanation(Context c){ProtectionCapabilities caps=capabilities(c);SetupStatus s=new SetupStatus(c);
        String reason=!s.accessibilityEnabled?"系统无障碍开关已关闭。":!s.accessibility?"无障碍开关已开启，但服务没有实际连接。":!s.usage?"使用记录权限已关闭。":"请检查后台保护和浮窗设置。";
        return reason+"\n\n应用额度："+(caps.daily?"仍可保护":"当前无法核实或执行限制")+"\n睡眠保护："+(caps.sleep?"可用":"已失效，需恢复无障碍")
                +"\n专注保护："+(caps.focus?"可用":"已失效，需恢复无障碍")+"\n\n已有设置和记录保留。修复完成后，会根据实际服务连接确认恢复。";}
    static void openAccessibility(android.app.Activity activity){Intent detail=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(activity,FocusAccessibilityService.class));
        try{activity.startActivity(detail);}catch(ActivityNotFoundException|SecurityException e){activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}}
}
