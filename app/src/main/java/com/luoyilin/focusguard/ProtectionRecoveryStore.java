package com.luoyilin.focusguard;

import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.channels.FileLock;

/** Bounded point-in-time checks, not proof of uninterrupted overnight enforcement. */
final class ProtectionRecoveryStore {
    interface Work<T>{T run(JSONObject root)throws Exception;}
    private static synchronized <T>T access(Context c,boolean save,Work<T> work,T fallback){
        File dir=c.getNoBackupFilesDir();
        try(RandomAccessFile lock=new RandomAccessFile(new File(dir,"protection-recovery.lock"),"rw");FileLock held=lock.getChannel().lock()){
            AtomicFile file=new AtomicFile(new File(dir,"protection-recovery.json"));JSONObject root=new JSONObject();
            try(InputStream in=file.openRead()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);root=new JSONObject(bytes.toString("UTF-8"));}catch(FileNotFoundException ignored){}
            T result=work.run(root);
            if(save){FileOutputStream out=null;try{out=file.startWrite();out.write(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
            return result;
        }catch(Exception e){return fallback;}
    }
    static void lock(Context c){access(c,true,root->{
        if(!root.has("lockedElapsed")||root.optInt("lockedBoot",-1)!=ProtectionClock.boot(c)){
            root.put("lockedElapsed",SystemClock.elapsedRealtime());root.put("lockedBoot",ProtectionClock.boot(c));
        }return null;},null);}
    static void record(Context c,String cause,ProtectionCapabilities caps){access(c,true,root->{
        long now=SystemClock.elapsedRealtime();int boot=ProtectionClock.boot(c);
        long locked=root.optInt("lockedBoot",-1)==boot?Math.max(0,now-root.optLong("lockedElapsed",now)):0;
        if(cause.equals("unlock")||(cause.equals("app_resume")&&!c.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())){root.remove("lockedElapsed");root.remove("lockedBoot");}
        long update=c.getPackageManager().getPackageInfo(c.getPackageName(),0).lastUpdateTime;
        String event=cause;
        if(cause.equals("app_resume")){
            if(root.has("lastBoot")&&root.optInt("lastBoot")!=boot)event="boot";
            else if(root.has("lastUpdate")&&root.optLong("lastUpdate")!=update)event="update";
        }
        JSONArray history=root.optJSONArray("checks");if(history==null)history=new JSONArray();
        JSONObject last=history.length()>0?history.optJSONObject(history.length()-1):null;
        boolean duplicate=last!=null&&event.equals(last.optString("cause"))&&last.optInt("boot")==boot
                &&now-last.optLong("elapsed")<30000&&last.optBoolean("daily")==caps.daily&&last.optBoolean("sleep")==caps.sleep&&last.optBoolean("focus")==caps.focus&&locked==0;
        if(!duplicate){
            JSONObject check=new JSONObject().put("timeVerified",ProtectionClock.read(c).verified).put("cause",event).put("boot",boot).put("elapsed",now).put("at",ProtectionClock.now(c))
                    .put("lockedMillis",locked).put("daily",caps.daily).put("sleep",caps.sleep).put("focus",caps.focus);
            history.put(check);JSONArray bounded=new JSONArray();for(int i=Math.max(0,history.length()-24);i<history.length();i++)bounded.put(history.get(i));root.put("checks",bounded);
        }
        root.put("lastBoot",boot).put("lastUpdate",update);return null;
    },null);}
    static JSONArray checks(Context c){return access(c,false,root->{JSONArray a=root.optJSONArray("checks");return a==null?new JSONArray():a;},new JSONArray());}
    static String summary(Context c){JSONArray values=checks(c);if(values.length()==0)return "尚无检查记录。重启、更新和解锁后会记录当前保护状态。";
        StringBuilder text=new StringBuilder();
        for(int i=values.length()-1;i>=Math.max(0,values.length()-5);i--){JSONObject v=values.optJSONObject(i);if(v==null)continue;
            String cause=v.optString("cause"),name=cause.equals("boot")?"重启后":cause.equals("update")?"更新后":cause.equals("unlock")?"解锁后":cause.equals("disconnect")?"无障碍断连":cause.equals("connected")?"服务已重连":"打开应用后";
            String time=java.time.Instant.ofEpochMilli(v.optLong("at")).atZone(ProtectionClock.zone(c)).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            text.append(v.optBoolean("timeVerified",false)?time:"时间待校验").append(" · ").append(name).append("\n")
                    .append("额度 ").append(v.optBoolean("daily")?"可用":"待恢复").append(" / 睡眠 ").append(v.optBoolean("sleep")?"可用":"待恢复").append(" / 专注 ").append(v.optBoolean("focus")?"可用":"待恢复");
            if(v.optLong("lockedMillis")>0)text.append("\n本次锁屏约 ").append(Math.max(1,v.optLong("lockedMillis")/60000)).append(" 分钟；记录为解锁时检查结果");
            text.append("\n\n");
        }return text.toString().trim();}
}
