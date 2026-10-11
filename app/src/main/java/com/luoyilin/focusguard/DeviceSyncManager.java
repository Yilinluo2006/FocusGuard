package com.luoyilin.focusguard;

import android.content.*;
import android.os.*;
import android.app.AppOpsManager;
import com.google.gson.Gson;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import retrofit2.*;

/** Network work stays in the main process; guard reads only its local quota file. */
final class DeviceSyncManager {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final Gson JSON=new Gson();
    private static volatile boolean applying;
    private static SharedPreferences.OnSharedPreferenceChangeListener changes;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("device_sync",0);}
    static boolean enabled(Context c){SessionManager s=new SessionManager(c);SharedPreferences p=prefs(c);return p.getBoolean("enabled",false)&&s.getUserId()>0&&p.getLong("owner",-1)==s.getUserId()&&BuildConfig.API_BASE_URL.equals(p.getString("server",""));}
    // No public mode selector yet. Existing installations and new accounts use local daily quotas.
    // The strict path remains available for a future account-wide option after deployment.
    static boolean strictQuota(Context c){return enabled(c)&&SharedQuotaPolicy.strictMode(prefs(c).getString("quotaMode",SharedQuotaPolicy.PER_DEVICE));}
    private static String localUse(Context c){return strictQuota(c)?"已领取的时间仍可使用":"本机额度正常可用";}
    static void initialize(Context c){
        Context app=c.getApplicationContext();
        changes=(p,key)->{
            if("jwt_token".equals(key)||"user_id".equals(key)){
                if(!enabled(app)||!new SessionManager(app).isLoggedIn()){try{SharedQuota.publish(app,null,Collections.emptyMap());}catch(Exception ignored){}DeviceSyncService.stop(app);}
                else DeviceSyncService.start(app);
                complianceAccount(app);
            }else if(!applying&&(key==null||key.equals("tasks")||key.equals("selected_packages")||key.startsWith("limit_minutes_")))request(app);
        };
        for(String name:new String[]{"focus_tasks","focus_guard_prefs","focusguard_session"})app.getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(changes);
        if(enabled(app)!=SharedQuota.synchronizationEnabled(app)||strictQuota(app)!=SharedQuota.enabled(app)){
            try{SharedQuota.publish(app,null,Collections.emptyMap());}catch(Exception ignored){}
        }
        complianceAccount(app);
    }
    static void setEnabled(Context c,boolean value)throws Exception{
        SessionManager s=new SessionManager(c);if(value&&!s.isLoggedIn())throw new IllegalStateException("请先登录");
        SharedPreferences p=prefs(c);boolean same=p.getLong("owner",-1)==s.getUserId()&&BuildConfig.API_BASE_URL.equals(p.getString("server",""));
        SharedPreferences.Editor e=p.edit();if(!same)e.clear();
        if(!e.putBoolean("enabled",value).putLong("owner",s.getUserId()).putString("server",BuildConfig.API_BASE_URL)
                .putString("statusKind",value?"WAITING":"CLOSED")
                .putString("status",value?"等待同步配置与使用记录":"多设备同步已关闭").commit())throw new IllegalStateException("保存失败");
        SharedQuota.publish(c,null,Collections.emptyMap());
        complianceAccount(c);
        if(value)DeviceSyncService.start(c);else DeviceSyncService.stop(c);
    }
    static String status(Context c){
        SessionManager s=new SessionManager(c);SharedPreferences p=prefs(c);
        if(!p.getBoolean("enabled",false)||!BuildConfig.API_BASE_URL.equals(p.getString("server","")))return "多设备同步已关闭";
        if(s.isSessionExpired())return "登录已过期 · 同步已暂停 · "+localUse(c);
        if(!s.isLoggedIn())return "尚未登录 · 登录后可同步 · "+localUse(c);
        if(s.getUserId()!=p.getLong("owner",-1))return "当前账号的设备同步未开启";
        if(NetworkEnvironment.offline(c)&&!NetworkEnvironment.localEndpoint())return "网络已断开 · 联网后自动重试 · "+localUse(c);
        String kind=p.getString("statusKind","");
        if(kind.equals("SUCCESS"))return "同步已完成 · "+localUse(c);
        if(kind.equals("OFFLINE"))return "等待恢复连接后重试 · "+localUse(c);
        String value=p.getString("status","");
        if(kind.isEmpty()&&(value.isEmpty()||value.startsWith("已同步")||value.startsWith("请重新登录")))return "等待同步 · "+localUse(c);
        return value.isEmpty()?"等待同步":value;
    }
    static String lastSuccess(Context c){
        SessionManager s=new SessionManager(c);SharedPreferences p=prefs(c);
        if(s.getUserId()!=p.getLong("owner",-1)||!BuildConfig.API_BASE_URL.equals(p.getString("server","")))return "最后成功同步：尚无记录";
        long at=p.contains("lastSuccessSync")?p.getLong("lastSuccessSync",0):p.getBoolean("conflict",false)?0:p.getLong("lastSync",0);
        return at<=0?"最后成功同步：尚无记录":"最后成功同步："+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(at));
    }
    static void request(Context c){if(enabled(c))DeviceSyncService.start(c);}
    static void tick(Context c){
        Context app=c.getApplicationContext();if(!enabled(app)||!BUSY.compareAndSet(false,true))return;
        if(!new SessionManager(app).isLoggedIn()){BUSY.set(false);return;}
        prefs(app).edit().putString("statusKind","SYNCING").putString("status","正在同步配置与使用记录").apply();
        WORK.execute(()->{
            SessionManager account=new SessionManager(app);long owner=account.getUserId();String auth=account.getAuthorizationHeader();
            try{
                boolean conflict=false;
                try{settings(app,owner,auth);}catch(Conflict e){conflict=true;prefs(app).edit().putBoolean("conflict",true).commit();}
                check(app,owner,auth);
                usage(app,owner,auth);
                compliance(app,owner,auth);
                check(app,owner,auth);
                SharedPreferences.Editor saved=prefs(app).edit().putString("statusKind",conflict?"CONFLICT":"SUCCESS")
                        .putString("status",conflict?"设置有冲突 · 请在设备同步中选择":"同步已完成");
                if(!conflict)saved.putLong("lastSuccessSync",ProtectionClock.now(app)).putLong("lastSync",ProtectionClock.now(app));
                saved.commit();
            }catch(Exception e){if(current(app,owner,auth)||expiredCurrent(app,owner)){
                try{SharedQuota.markOffline(app);}catch(java.io.IOException ignored){}
                saveFailure(app,e);
            }}
            finally{BUSY.set(false);}
        });
    }
    private static boolean current(Context c,long user,String auth){SessionManager now=new SessionManager(c);return enabled(c)&&now.getUserId()==user&&Objects.equals(auth,now.getAuthorizationHeader());}
    private static void check(Context c,long user,String auth){if(!current(c,user,auth))throw new IllegalStateException("登录账号已变化");}
    private static boolean expiredCurrent(Context c,long owner){SessionManager s=new SessionManager(c);return enabled(c)&&s.isSessionExpired()&&s.getUserId()==owner;}
    private static void saveFailure(Context c,Exception error){
        ConnectionFailure.Kind kind=new SessionManager(c).isSessionExpired()?ConnectionFailure.Kind.AUTH_EXPIRED:NetworkEnvironment.classify(error);
        String message=error instanceof IllegalStateException&&!new SessionManager(c).isSessionExpired()?error.getMessage():ConnectionFailure.message(kind);
        prefs(c).edit().putString("statusKind",kind.name()).putString("status",message+" · "+localUse(c)).commit();
    }
    private static <T>T execute(Call<T> call)throws Exception{call.timeout().timeout(8,TimeUnit.SECONDS);Response<T> r=call.execute();
        if(!r.isSuccessful()){if(r.errorBody()!=null)r.errorBody().close();throw new HttpFailure(r.code());}
        if(r.body()==null)throw new java.io.IOException("服务器返回数据为空");return r.body();}
    private static <T>T onMain(Callable<T> task)throws Exception{FutureTask<T> pending=new FutureTask<>(task);UI.post(pending);try{return pending.get(10,TimeUnit.SECONDS);}catch(TimeoutException timeout){pending.cancel(false);throw timeout;}}
    private static final class Conflict extends Exception { }
    private static void settings(Context c,long owner,String auth)throws Exception{
        if(prefs(c).getBoolean("conflict",false))throw new Conflict();
        onMain(()->{
            check(c,owner,auth);
            Bundle replay=c.getContentResolver().call(LimitConfigProvider.URI,"replayPendingSleep","focus_mode",null);
            if(replay==null||!replay.getBoolean("saved"))
                throw new IllegalStateException(replay==null?"睡前计划同步状态保存失败":replay.getString("message","睡前计划同步状态保存失败"));
            return true;
        });
        for(int attempt=0;attempt<3;attempt++){
            DeviceSyncData.Settings remote=execute(RetrofitClient.getApi().getSharedSettings(auth));check(c,owner,auth);
            String local=onMain(()->SharedSettingsData.collect(c));
            String proposed=local;
            if(remote.payload!=null){
                try{
                    String base=prefs(c).getString("base",null);
                    if(base==null)base=onMain(()->SharedSettingsData.initialBase(c));
                    proposed=SharedSettingsData.merge(base,local,remote.payload);
                }
                catch(IllegalStateException conflict){prefs(c).edit().putString("remote",remote.payload).commit();throw new Conflict();}
                if(!proposed.equals(SharedSettingsData.canonical(new JSONObject(remote.payload)))){
                    Call<DeviceSyncData.Settings> upload=RetrofitClient.getApi().putSharedSettings(auth,new DeviceSyncData.SettingsRequest(remote.revision,proposed));
                    upload.timeout().timeout(8,TimeUnit.SECONDS);
                    Response<DeviceSyncData.Settings> response=upload.execute();
                    if(response.code()==409)continue;
                    if(!response.isSuccessful()){if(response.errorBody()!=null)response.errorBody().close();throw new HttpFailure(response.code());}
                    if(response.body()==null)throw new java.io.IOException("设置上传返回为空");remote=response.body();
                }
            }else remote=execute(RetrofitClient.getApi().putSharedSettings(auth,new DeviceSyncData.SettingsRequest(0,local)));
            check(c,owner,auth);String apply=remote.payload;long revision=remote.revision;
            boolean saved=onMain(()->{
                check(c,owner,auth);applying=true;
                try{
                    if(!SharedSettingsData.apply(c,local,apply))return false;
                    if(!prefs(c).edit().putString("base",SharedSettingsData.canonical(new JSONObject(apply))).putLong("revision",revision).commit())throw new IllegalStateException("同步状态保存失败");
                    return true;
                }finally{applying=false;}
            });
            if(saved)return;
        }
        throw new IllegalStateException("本机设置正在变化，稍后重试");
    }
    static void resolve(Context c,boolean useLocal){
        if(!new SessionManager(c).isLoggedIn())return;
        WORK.execute(()->{
            SessionManager s=new SessionManager(c);long owner=s.getUserId();String auth=s.getAuthorizationHeader();
            try{
                DeviceSyncData.Settings remote=execute(RetrofitClient.getApi().getSharedSettings(auth));check(c,owner,auth);
                onMain(()->{
                    check(c,owner,auth);
                    if(!useLocal){applying=true;try{String local=SharedSettingsData.collect(c);if(!SharedSettingsData.apply(c,local,remote.payload))throw new IllegalStateException("设置变化，请重试");}finally{applying=false;}}
                    prefs(c).edit().putString("base",SharedSettingsData.canonical(new JSONObject(remote.payload))).remove("conflict").remove("remote").commit();return true;
                });
                tick(c);
            }catch(Exception e){if(current(c,owner,auth)||expiredCurrent(c,owner))saveFailure(c,e);}
        });
    }
    static String device(Context c)throws Exception{return CloudBackupManager.device(c);}
    private static void complianceAccount(Context c){
        SessionManager s=new SessionManager(c);boolean active=enabled(c)&&s.isLoggedIn();
        String account=active?s.getUserId()+"@"+BuildConfig.API_BASE_URL:"";
        try{StreakStore.access(c,p->{
            ComplianceLedger.activate(p,ProtectionClock.now(c),ProtectionClock.zone(c));
            if(!account.equals(p.getProperty("c.account",""))) {
                for(String key:new HashSet<>(p.stringPropertyNames()))if(key.startsWith("c.remote." )||key.startsWith("c.remoteAt."))p.remove(key);
            }
            p.setProperty("c.account",account);p.setProperty("c.sync",Boolean.toString(active));
            if(ProtectionClock.read(c).verified)ComplianceLedger.settle(p,ProtectionClock.now(c),ProtectionClock.zone(c));
        });}catch(Exception error){android.util.Log.w("Compliance","Account state not saved",error);}
    }
    private static void compliance(Context c,long owner,String auth)throws Exception{
        check(c,owner,auth);ComplianceAudit.refresh(c);check(c,owner,auth);
        Properties p=StreakStore.snapshot(c);long now=ProtectionClock.now(c);ZoneId zone=ProtectionClock.zone(c);
        long today=StreakLedger.day(now,zone),first=StreakLedger.n(p,"complianceFrom");
        ComplianceData.Request request=new ComplianceData.Request();
        for(long d=Math.max(first,today-13);d<=today;d++){
            String local=d==today?ComplianceLedger.local(p,d):p.getProperty("c.local."+d,"待确认");
            String state="未完成".equals(local)?"failed":"完成".equals(local)&&d<today?"complete":"无记录".equals(local)&&d<today?"inactive":"pending";
            long through=Math.max(StreakLedger.boundary(d,zone),StreakLedger.n(p,"c.through."+d));
            request.days.add(new ComplianceData.Day(LocalDate.ofEpochDay(d).toString(),Boolean.parseBoolean(p.getProperty("c.active."+d)),state,through));
        }
        ComplianceData.Result result=execute(RetrofitClient.getApi().postCompliance(auth,device(c),request));check(c,owner,auth);
        StreakStore.access(c,live->{
            check(c,owner,auth);
            if(!(owner+"@"+BuildConfig.API_BASE_URL).equals(live.getProperty("c.account")))return;
            if(result.days!=null)for(ComplianceData.Day value:result.days){
                long d=LocalDate.parse(value.day).toEpochDay();if(d<first||d>today)continue;
                String outcome="complete".equals(value.state)?"完成":"failed".equals(value.state)?"未完成":"inactive".equals(value.state)?"无记录":"待确认";
                live.setProperty("c.remote."+d,outcome);StreakLedger.put(live,"c.remoteAt."+d,result.serverTime);
            }
            ComplianceLedger.settle(live,ProtectionClock.now(c),zone);
        });
        StreakWidget.updateAll(c);
    }
    static String type(Context c){return c.getResources().getConfiguration().smallestScreenWidthDp>=600?"tablet":"phone";}
    private static void usage(Context c,long owner,String auth)throws Exception{
        ProtectionTimeLedger.Sample time = ProtectionClock.read(c);
        if (!time.verified || !ProtectionClock.nativeReliable(c)) throw new IllegalStateException("时间待校验 · 本机剩余额度继续计时，暂缓上传统计");
        AppOpsManager ops=c.getSystemService(AppOpsManager.class);
        if(ops==null||ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())!=AppOpsManager.MODE_ALLOWED)
            throw new IllegalStateException(strictQuota(c)?"设置已同步 · 请开启使用记录权限领取时间":"设置已同步 · 请开启使用记录权限统计用时");
        String id=device(c);DeviceSyncData.Usage state=execute(RetrofitClient.getApi().getDeviceUsage(auth,id,null));check(c,owner,auth);
        String seed=prefs(c).getString("seedDay","");int days=state.day.equals(seed)?1:7;
        // Import a week of real system usage once per quota day; never synthesize another device's data.
        for(int offset=days-1;offset>=0;offset--){
            LocalDate day=LocalDate.parse(state.day).minusDays(offset);ZoneId zone=ZoneId.of("Asia/Shanghai");
            long from=day.atTime(4,0).atZone(zone).toInstant().toEpochMilli(),to=Math.min(System.currentTimeMillis(),day.plusDays(1).atTime(4,0).atZone(zone).toInstant().toEpochMilli());
            AppUsageTimeline timeline=AppUsageReader.readRange(c,from,to,null);if(timeline==null)throw new IllegalStateException(strictQuota(c)?"使用记录暂不可用，尚未领取新时间":"使用记录暂不可用，稍后重试同步");
            Map<String,Long> totals=timeline.totalsAt(to);DeviceSyncData.Request request=new DeviceSyncData.Request();
            request.day=day.toString();request.deviceName=(Build.MANUFACTURER+" "+Build.MODEL);request.deviceType=type(c);
            Map<String,Long> reported=new TreeMap<>();
            for(var item:totals.entrySet()){
                if(item.getValue()<=0||c.getPackageManager().getLaunchIntentForPackage(item.getKey())==null)continue;
                String label=item.getKey();try{label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(item.getKey(),0)).toString();}catch(Exception ignored){}
                if(label.length()>120)label=label.substring(0,120);
                long ms=Math.min(93_600_000L,item.getValue());request.usage.add(new DeviceSyncData.Item(item.getKey(),label,ms));reported.put(item.getKey(),ms);
            }
            if(offset==0&&strictQuota(c)){
                Properties limits=LimitSnapshot.read(c);
                for(String pkg:limits.stringPropertyNames()){
                    if(c.getPackageManager().getLaunchIntentForPackage(pkg)==null)continue;
                    long used=totals.getOrDefault(pkg,0L);request.desiredCeilings.put(pkg,Math.min(93_600_000L,used+120_000L));
                }
            }
            long sent=SystemClock.elapsedRealtime();
            DeviceSyncData.Usage result=execute(RetrofitClient.getApi().postDeviceUsage(auth,id,request));result.requestStarted=sent;check(c,owner,auth);
            prefs(c).edit().putString("usage."+day,JSON.toJson(result)).commit();
            if(offset==0)onMain(()->{check(c,owner,auth);SharedQuota.publish(c,result,reported);return true;});
        }
        check(c,owner,auth);prefs(c).edit().putString("seedDay",state.day).commit();
    }
    static DeviceSyncData.Usage loadUsage(Context c,LocalDate day){
        if(!enabled(c))return null;
        String cached=prefs(c).getString("usage."+day,null);
        try{
            SessionManager s=new SessionManager(c);String auth=s.getAuthorizationHeader();long owner=s.getUserId();
            if(!s.isLoggedIn())throw new IllegalStateException("登录后可刷新统计");
            DeviceSyncData.Usage value=execute(RetrofitClient.getApi().getDeviceUsage(auth,device(c),day.toString()));check(c,owner,auth);
            prefs(c).edit().putString("usage."+day,JSON.toJson(value)).commit();return value;
        }catch(Exception error){
            if(cached==null)return null;
            try{DeviceSyncData.Usage fallback=JSON.fromJson(cached,DeviceSyncData.Usage.class);fallback.fromCache=true;return fallback;}catch(RuntimeException corrupt){return null;}
        }
    }
    static Map<String,Long> combinedToday(Context c,Map<String,Long> local){
        Map<String,Long> result=new TreeMap<>(local);if(!enabled(c))return result;
        LocalDate day=Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("Asia/Shanghai")).minusHours(4).toLocalDate();
        try{
            String raw=prefs(c).getString("usage."+day,null);if(raw==null)return result;
            DeviceSyncData.Usage usage=JSON.fromJson(raw,DeviceSyncData.Usage.class);String own=device(c);
            if(usage.devices!=null)for(DeviceSyncData.Device peer:usage.devices){if(own.equals(peer.deviceId))continue;if(peer.usage!=null)for(DeviceSyncData.Item item:peer.usage)result.merge(item.packageName,item.millis,Long::sum);}
        }catch(Exception ignored){}
        return result;
    }
}
