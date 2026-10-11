package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;
import java.io.File;

/** Local, explicitly selected system wallpaper. Does not change accessibility grants. */
final class WallpaperGuard {
    static File image(Context c) { return new File(c.getNoBackupFilesDir(), "guard-wallpaper.jpg"); }
    static File draft(Context c) { return new File(c.getNoBackupFilesDir(), "guard-wallpaper-draft.jpg"); }
    static boolean selected(Context c) {
        try {
            android.app.WallpaperInfo info = WallpaperManager.getInstance(c).getWallpaperInfo();
            return info != null && new ComponentName(c, FocusWallpaperService.class).equals(info.getComponent());
        } catch (RuntimeException denied) { return false; }
    }
    static boolean permissions(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED
                && Settings.canDrawOverlays(c);
    }
    static boolean available(Context c) { return selected(c) && permissions(c); }
}
