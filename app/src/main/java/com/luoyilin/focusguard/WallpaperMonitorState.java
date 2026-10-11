package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;

/** Local guard-process feedback; a previous boot must never look like live protection. */
final class WallpaperMonitorState {
    static final String RUNNING="running", STANDBY="standby", LOCKED="locked", ERROR="error", OFF="off";
    private static long lastWrite;
    private static String lastStatus;
    static synchronized void publish(Context c,String status) {
        long now=SystemClock.elapsedRealtime();
        if(status.equals(lastStatus)&&now-lastWrite<5000)return;
        AtomicFile f=new AtomicFile(new File(c.getNoBackupFilesDir(),"wallpaper-monitor-state"));FileOutputStream out=null;
        try {
            out=f.startWrite();DataOutputStream data=new DataOutputStream(out);
            data.writeInt(ProtectionClock.boot(c));data.writeLong(now);data.writeUTF(status);data.flush();f.finishWrite(out);
            lastWrite=now;lastStatus=status;
        }catch(IOException e){if(out!=null)f.failWrite(out);}
    }
    static String read(Context c) {
        try(DataInputStream in=new DataInputStream(new AtomicFile(new File(c.getNoBackupFilesDir(),"wallpaper-monitor-state")).openRead())) {
            int boot=in.readInt();long at=in.readLong();String status=in.readUTF();long age=SystemClock.elapsedRealtime()-at;
            return boot==ProtectionClock.boot(c)&&age>=0&&age<=15000?status:OFF;
        }catch(IOException e){return OFF;}
    }
    static boolean ready(String status) {return RUNNING.equals(status)||STANDBY.equals(status)||LOCKED.equals(status);}
}
