package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.util.Properties;

/** Consent stays on this installation and is never restored or synced to another device. */
final class PageMonitoringStore {
    static final int VERSION = 4;
    static String action(Context context) { return context.getPackageName() + ".PAGE_MONITORING_CHANGED"; }
    static final class State {
        final boolean authorized, enabled;
        State(Properties p) {
            authorized = Integer.toString(VERSION).equals(p.getProperty("consentVersion"));
            enabled = authorized && Boolean.parseBoolean(p.getProperty("enabled", "false"));
        }
    }
    private interface Edit { void apply(Properties p); }
    private static synchronized State access(Context c, Edit edit) throws IOException {
        File dir = c.getNoBackupFilesDir();
        try (RandomAccessFile lock = new RandomAccessFile(new File(dir, "page-monitoring.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(dir, "page-monitoring.properties"));
            Properties p = new Properties();
            try (InputStream in = file.openRead()) { p.load(in); } catch (FileNotFoundException ignored) { }
            if (edit != null) {
                edit.apply(p);
                FileOutputStream out = null;
                try { out = file.startWrite(); p.store(out, null); file.finishWrite(out); }
                catch (IOException error) { if (out != null) file.failWrite(out); throw error; }
            }
            return new State(p);
        }
    }
    static State read(Context c) {
        try { return access(c, null); } catch (IOException | RuntimeException error) { return new State(new Properties()); }
    }
    static boolean authorize(Context c, boolean understood) {
        if (!understood) return false;
        return write(c, p -> { p.setProperty("consentVersion", Integer.toString(VERSION));
            p.setProperty("acceptedAt", Long.toString(System.currentTimeMillis())); p.setProperty("enabled", "true"); });
    }
    static boolean enable(Context c, boolean enabled) {
        if (enabled && !read(c).authorized) return false;
        return write(c, p -> {
            if (enabled && !new State(p).authorized) throw new IllegalStateException("需要先独立授权");
            p.setProperty("enabled", Boolean.toString(enabled));
        });
    }
    static boolean revoke(Context c) { return write(c, Properties::clear); }
    private static boolean write(Context c, Edit edit) {
        try { access(c, edit); c.sendBroadcast(new Intent(action(c)).setPackage(c.getPackageName())); return true; }
        catch (IOException | RuntimeException error) { return false; }
    }
}
