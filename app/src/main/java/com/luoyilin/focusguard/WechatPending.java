package com.luoyilin.focusguard;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.WechatData;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** Opaque state only. Authorization codes, provider tokens and AppSecret are never persisted here. */
public final class WechatPending {
    static final class Request {final String state,authorization;final long owner;final boolean binding,backup;Request(String state,boolean binding,boolean backup,long owner,String authorization){this.state=state;this.binding=binding;this.backup=backup;this.owner=owner;this.authorization=authorization;} }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("wechat_pending",0);}
    private static String session(Context c){String auth=new SessionManager(c).getAuthorizationHeader();try{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((auth==null?"":auth).getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b));return out.toString();
    }catch(Exception e){throw new IllegalStateException(e);}}
    static synchronized void save(Context c,WechatData.Start start,boolean binding,boolean backup,long requestedAt){
        if(!BuildConfig.WECHAT_APP_ID.equals(start.appId)||start.state==null||!start.state.matches("[A-Za-z0-9_-]{43}"))throw new IllegalStateException("微信登录暂不可用");
        long ttl=Math.min(300000,Math.max(0,start.expiresAt-start.serverTime-(SystemClock.elapsedRealtime()-requestedAt)));
        if(ttl<=0)throw new IllegalStateException("微信授权请求已过期，请重新尝试");
        if(!prefs(c).edit().clear().putString("state",start.state).putBoolean("binding",binding).putBoolean("backup",backup)
                .putLong("owner",new SessionManager(c).getUserId()).putString("session",session(c)).putString("server",BuildConfig.API_BASE_URL)
                .putLong("until",SystemClock.elapsedRealtime()+ttl).putInt("boot",Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)).commit())throw new IllegalStateException("授权状态保存失败");
    }
    static synchronized Request take(Context c,String state){
        SharedPreferences p=prefs(c);String saved=p.getString("state","");
        if(state==null||saved.isEmpty()||!MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8),state.getBytes(StandardCharsets.UTF_8)))return null;
        boolean valid=SystemClock.elapsedRealtime()<p.getLong("until",0)
                &&Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)==p.getInt("boot",-2)
                &&new SessionManager(c).getUserId()==p.getLong("owner",-2)&&session(c).equals(p.getString("session",""))
                &&BuildConfig.API_BASE_URL.equals(p.getString("server",""));
        Request request=valid?new Request(saved,p.getBoolean("binding",false),p.getBoolean("backup",false),p.getLong("owner",-2),new SessionManager(c).getAuthorizationHeader()):null;
        if(!p.edit().clear().commit())return null;return request;
    }
    static synchronized void clear(Context c){prefs(c).edit().clear().commit();}
    public static synchronized boolean hasPending(Context c){SharedPreferences p=prefs(c);
        return !p.getString("state","").isEmpty()&&SystemClock.elapsedRealtime()<p.getLong("until",0)
                &&Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)==p.getInt("boot",-2)
                &&BuildConfig.API_BASE_URL.equals(p.getString("server",""))
                &&new SessionManager(c).getUserId()==p.getLong("owner",-2)&&session(c).equals(p.getString("session",""));}
}
