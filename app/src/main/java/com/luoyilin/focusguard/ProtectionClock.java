package com.luoyilin.focusguard;

import android.content.Context;
import android.os.*;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One persisted clock for all guard decisions. Network validation never runs on UI/guard threads. */
final class ProtectionClock {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile ProtectionTimeLedger.Sample cached;
    private static long cachedAt,lastAttempt;
    interface Edit {void run(Properties p);}
    private static synchronized ProtectionTimeLedger.Sample access(Context c,Edit edit)throws IOException {
        File dir=c.getNoBackupFilesDir();
        try(RandomAccessFile lock=new RandomAccessFile(new File(dir,"protection-clock.lock"),"rw");FileLock held=lock.getChannel().lock()) {
            AtomicFile file=new AtomicFile(new File(dir,"protection-clock.properties"));Properties p=new Properties();
            try(InputStream in=file.openRead()){p.load(in);}catch(FileNotFoundException ignored){}
            Properties before=(Properties)p.clone();if(edit!=null)edit.run(p);
            long elapsed=SystemClock.elapsedRealtime();
            ProtectionTimeLedger.Sample result=ProtectionTimeLedger.sample(p,System.currentTimeMillis(),elapsed,boot(c),ZoneId.systemDefault().getId());
            if(!before.equals(p)){FileOutputStream out=null;try{out=file.startWrite();p.store(out,null);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
            cached=result;cachedAt=elapsed;return result;
        }
    }
    static ProtectionTimeLedger.Sample read(Context c) {
        long elapsed=SystemClock.elapsedRealtime();ProtectionTimeLedger.Sample result=cached;
        if(result==null||elapsed<cachedAt||elapsed-cachedAt>=250) {
            try{result=access(c,null);}catch(Exception e){android.util.Log.e("ProtectionClock","Clock state unavailable",e);
                ZoneId zone=result==null?ZoneId.systemDefault():result.zone;
                long now=result==null?System.currentTimeMillis():result.millis;
                result=new ProtectionTimeLedger.Sample(now,zone,false,false,result==null?StreakLedger.day(now,zone):result.day);
            }
        }
        if(!result.verified||!result.aligned)verifyLater(c,false);
        return result;
    }
    static long now(Context c){return read(c).millis;}
    static ZoneId zone(Context c){return read(c).zone;}
    static long day(Context c){return read(c).day;}
    static boolean nativeReliable(Context c){ProtectionTimeLedger.Sample time=read(c);return time.aligned&&Math.abs(time.millis-System.currentTimeMillis())<=3000;}
    static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static void initialize(Context c){read(c);verifyLater(c,true);}
    static void verifyLater(Context c,boolean immediate) {
        long elapsed=SystemClock.elapsedRealtime();
        if(!immediate&&elapsed>=lastAttempt&&elapsed-lastAttempt<30_000)return;
        if(!BUSY.compareAndSet(false,true))return;
        lastAttempt=elapsed;Context app=c.getApplicationContext();
        WORK.execute(()->{try{
            Long epoch=null;
            if(Build.VERSION.SDK_INT>=33)try{epoch=SystemClock.currentNetworkTimeClock().millis();}catch(Exception ignored){}
            if(epoch==null)epoch=httpsTime();
            if(epoch!=null){long reference=epoch,at=SystemClock.elapsedRealtime();
                access(app,p->ProtectionTimeLedger.verify(p,reference,at,boot(app),ZoneId.systemDefault().getId()));
                if(nativeReliable(app))try{
                    java.util.Set<String> packages=LimitSnapshot.read(app).stringPropertyNames();
                    AppUsageReader.totals(app,now(app),packages); // Preserve existing system totals before any later clock edit.
                }catch(Exception ignored){}
            }
        }catch(Exception e){android.util.Log.w("ProtectionClock","Time verification unavailable");}finally{BUSY.set(false);}});
    }
    private static Long httpsTime()throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("https://naiwatodo.top/?timecheck="+SystemClock.elapsedRealtime()).openConnection();
        connection.setRequestMethod("HEAD");connection.setConnectTimeout(2500);connection.setReadTimeout(2500);
        connection.setUseCaches(false);connection.setRequestProperty("Cache-Control","no-cache");
        try{connection.getResponseCode();String date=connection.getHeaderField("Date"),age=connection.getHeaderField("Age");
            if(date==null||age!=null&&Long.parseLong(age)>60)return null;
            return ZonedDateTime.parse(date,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        }finally{connection.disconnect();}
    }
}
