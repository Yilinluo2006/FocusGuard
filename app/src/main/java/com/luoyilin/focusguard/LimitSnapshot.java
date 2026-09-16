package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Properties;

/** Single writer in the UI process; guard reads without starting or binding to it. */
public final class LimitSnapshot {
    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "limit-snapshot.properties"));
    }

    public static synchronized void publish(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("focus_guard_prefs", 0);
        Properties values = new Properties();
        for (String pkg : prefs.getStringSet("selected_packages", Collections.emptySet())) {
            values.setProperty(pkg, Integer.toString(prefs.getInt("limit_minutes_" + pkg, 30)));
        }
        AtomicFile target = file(context);
        FileOutputStream out = null;
        try {
            out = target.startWrite();
            values.store(out, null);
            target.finishWrite(out);
        } catch (Exception error) {
            if (out != null) target.failWrite(out);
            Log.e("FocusAccessibility", "Cannot publish limits", error);
        }
    }

    public static Properties read(Context context) throws java.io.IOException {
        Properties values = new Properties();
        try (FileInputStream in = file(context).openRead()) { values.load(in); }
        return values;
    }
}
