package com.luoyilin.focusguard;
import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.util.*;

final class UsageBudgetStore {
    private static synchronized Properties access(Context c,String active,Map<String,Long> nativeTotals,boolean tick)throws IOException {
        ProtectionTimeLedger.Sample time=ProtectionClock.read(c);File dir=c.getNoBackupFilesDir();
        try(RandomAccessFile lock=new RandomAccessFile(new File(dir,"usage-budget.lock"),"rw");FileLock held=lock.getChannel().lock()) {
            AtomicFile file=new AtomicFile(new File(dir,"usage-budget.properties"));Properties p=new Properties();
            try(InputStream in=file.openRead()){p.load(in);}catch(FileNotFoundException ignored){}
            p.setProperty("zone",time.zone.getId());
            if(tick)UsageBudgetLedger.sample(p,time.day,time.millis,SystemClock.elapsedRealtime(),ProtectionClock.boot(c),active,
                    ProtectionClock.nativeReliable(c)?nativeTotals:Collections.emptyMap());
            else {
                if(time.day>Long.parseLong(p.getProperty("day",Long.toString(time.day))))
                    UsageBudgetLedger.sample(p,time.day,time.millis,SystemClock.elapsedRealtime(),ProtectionClock.boot(c),null,Collections.emptyMap());
                if(ProtectionClock.nativeReliable(c))for(Map.Entry<String,Long> v:nativeTotals.entrySet())p.setProperty("used."+v.getKey(),Long.toString(Math.max(UsageBudgetLedger.used(p,v.getKey()),v.getValue())));
                p.putIfAbsent("day",Long.toString(time.day));
                if(time.day<Long.parseLong(p.getProperty("day")))p.setProperty("day",Long.toString(time.day));
            }
            FileOutputStream out=null;try{out=file.startWrite();p.store(out,null);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
            return p;
        }
    }
    static long tick(Context c,String pkg,long nativeUsed)throws IOException {
        return UsageBudgetLedger.used(access(c,pkg,Collections.singletonMap(pkg,nativeUsed),true),pkg);
    }
    static void pause(Context c){try{access(c,null,Collections.emptyMap(),true);}catch(IOException e){android.util.Log.e("UsageBudget","Counter not saved",e);}}
    static Map<String,Long> totals(Context c,Set<String> packages,Map<String,Long> nativeTotals)throws IOException {
        Properties p=access(c,null,nativeTotals==null?Collections.emptyMap():nativeTotals,false);Map<String,Long> out=new HashMap<>();
        for(String key:p.stringPropertyNames())if(key.startsWith("used.")){String pkg=key.substring(5);if(packages==null||packages.contains(pkg))out.put(pkg,UsageBudgetLedger.used(p,pkg));}
        return out;
    }
}
