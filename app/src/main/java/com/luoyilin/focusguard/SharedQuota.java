package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;
import com.luoyilin.focusguard.network.DeviceSyncData;
import java.io.*;
import java.util.*;

/** UI-process writer and guard-process reader, with atomic replacement and boot-bound expiry. */
final class SharedQuota {
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"shared-quota.properties"));}
    private static File marker(Context c){return new File(c.getNoBackupFilesDir(),"shared-quota-enabled");}
    private static File strictMarker(Context c){return new File(c.getNoBackupFilesDir(),"shared-quota-strict");}
    static Properties read(Context c){Properties p=new Properties();try(InputStream in=file(c).openRead()){p.load(in);}catch(Exception ignored){}return p;}
    static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static boolean synchronizationEnabled(Context c){return marker(c).exists()||Boolean.parseBoolean(read(c).getProperty("enabled","false"));}
    static boolean enabled(Context c){
        if(strictMarker(c).exists())return true;
        Properties p=read(c);
        // Legacy snapshots have no mode. Their two-minute grants must not cap the new default.
        return Boolean.parseBoolean(p.getProperty("enabled","false"))&&SharedQuotaPolicy.strictMode(p.getProperty("quotaMode",SharedQuotaPolicy.PER_DEVICE));
    }
    static synchronized void publish(Context c,DeviceSyncData.Usage result,Map<String,Long> reported)throws IOException{
        Properties p=new Properties();p.setProperty("enabled",Boolean.toString(DeviceSyncManager.enabled(c)));
        boolean strict=DeviceSyncManager.strictQuota(c);
        p.setProperty("quotaMode",strict?SharedQuotaPolicy.STRICT_SHARED:SharedQuotaPolicy.PER_DEVICE);
        if(strict){if(!strictMarker(c).exists()&&!strictMarker(c).createNewFile())throw new IOException("无法保存共享额度模式");}
        else if(strictMarker(c).exists()&&!strictMarker(c).delete())throw new IOException("无法切换本机额度模式");
        if(DeviceSyncManager.enabled(c)){if(!marker(c).exists()&&!marker(c).createNewFile())throw new IOException("无法保存共享时间状态");}
        else if(marker(c).exists()&&!marker(c).delete())throw new IOException("无法关闭共享时间状态");
        if(result!=null){
            long now=SystemClock.elapsedRealtime(),transit=result.requestStarted>0?Math.max(0,now-result.requestStarted):8000;
            p.setProperty("online","true");p.setProperty("confirmedAt",Long.toString(now));
            p.setProperty("hasTotals",Boolean.toString(result.totalMillis!=null));
            p.setProperty("boot",Integer.toString(boot(c)));p.setProperty("expires",Long.toString(now+Math.max(0,result.validUntil-result.serverTime-transit)));
            p.setProperty("day",result.day);
            if(result.ceilings!=null)result.ceilings.forEach((pkg,ms)->p.setProperty("ceiling."+pkg,Long.toString(ms)));
            reported.forEach((pkg,ms)->p.setProperty("reported."+pkg,Long.toString(ms)));
            if(result.totalMillis!=null)result.totalMillis.forEach((pkg,ms)->p.setProperty("total."+pkg,Long.toString(ms)));
        }
        write(c,p);
    }
    private static void write(Context c,Properties p)throws IOException{
        AtomicFile target=file(c);FileOutputStream out=null;
        try{out=target.startWrite();p.store(out,null);target.finishWrite(out);}catch(IOException e){if(out!=null)target.failWrite(out);throw e;}
    }
    static synchronized void markOffline(Context c)throws IOException{
        if(!DeviceSyncManager.enabled(c)||!enabled(c))return;
        Properties p=read(c);p.setProperty("online","false");write(c,p);
    }
    static final class Timer {
        final long millis;final boolean offline;
        Timer(long millis,boolean offline){this.millis=millis;this.offline=offline;}
    }
    static Timer timer(Context c,String pkg,long local,long dailyLimit,long allocatedRemaining){
        if(!enabled(c))return new Timer(SharedQuotaPolicy.perDeviceRemaining(dailyLimit,local),false);
        Properties p=read(c);
        try{
            long age=SystemClock.elapsedRealtime()-Long.parseLong(p.getProperty("confirmedAt","0"));
            boolean online=Boolean.parseBoolean(p.getProperty("online","false"))&&age>=0&&age<45_000
                    &&Integer.parseInt(p.getProperty("boot","-2"))==boot(c)&&Boolean.parseBoolean(p.getProperty("hasTotals","false"));
            long reported=Long.parseLong(p.getProperty("reported."+pkg,"0"));
            long total=Long.parseLong(p.getProperty("total."+pkg,"0"));
            return new Timer(SharedQuotaPolicy.displayRemaining(dailyLimit,reported,local,total,allocatedRemaining,online),!online);
        }catch(RuntimeException error){return new Timer(Math.max(0,allocatedRemaining),true);}
    }
    static long remaining(Context c,String pkg,long local){
        if(!enabled(c))return Long.MAX_VALUE;
        Properties p=read(c);
        if(!Boolean.parseBoolean(p.getProperty("enabled","false")))return 0;
        try{return SharedQuotaPolicy.remaining(Long.parseLong(p.getProperty("ceiling."+pkg,"0")),Long.parseLong(p.getProperty("reported."+pkg,"0")),local,
                SystemClock.elapsedRealtime(),Long.parseLong(p.getProperty("expires","0")),Integer.parseInt(p.getProperty("boot","-2")),boot(c));}
        catch(RuntimeException error){return 0;}
    }
    static String blockedMessage(Context c,String name){return enabled(c)?name+" 当前设备可用时间已用完，联网后领取剩余时间":name+" 本机每日额度已用尽，04:00 恢复";}
}
