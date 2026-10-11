package com.luoyilin.focusguard;

import android.content.*;
import org.json.*;
import java.util.*;

/** Portable account configuration only; no sessions, permissions, appearance or credentials. */
final class SharedSettingsData {
    static String collect(Context c) throws Exception {
        JSONArray tasks=new JSONArray(c.getSharedPreferences("focus_tasks",0).getString("tasks","[]"));
        SharedPreferences restrictions=c.getSharedPreferences("focus_guard_prefs",0);JSONArray limits=new JSONArray();
        for(String pkg:new TreeSet<>(restrictions.getStringSet("selected_packages",Collections.emptySet())))limits.put(new JSONObject().put("packageName",pkg).put("minutes",restrictions.getInt("limit_minutes_"+pkg,30)));
        Map<String,?> prefs=FocusPreferences.read(c,"focus_mode");
        JSONObject sleep=new JSONObject();
        sleep.put("daily",prefs.containsKey("daily")?prefs.get("daily"):false);
        sleep.put("minute",prefs.containsKey("minute")?prefs.get("minute"):1350);
        sleep.put("duration",prefs.containsKey("duration")?prefs.get("duration"):180);
        sleep.put(FocusSchedule.WEEKDAYS_KEY,prefs.containsKey(FocusSchedule.WEEKDAYS_KEY)?prefs.get(FocusSchedule.WEEKDAYS_KEY):127);
        sleep.put("allowed",prefs.containsKey("allowed")?new JSONArray(new TreeSet<>((Set<String>)prefs.get("allowed"))):new JSONArray());
        String pending=DeviceSyncManager.prefs(c).getString("pendingSleep",null);
        if(pending!=null)sleep=new JSONObject(pending);
        return canonical(new JSONObject().put("tasks",sorted(tasks,"id")).put("limits",sorted(limits,"packageName")).put("sleep",sleep));
    }
    private static JSONArray sorted(JSONArray values,String key)throws Exception{Map<String,Object> indexed=index(values,key);JSONArray result=new JSONArray();for(Object value:indexed.values())result.put(value);return result;}
    static String empty() throws Exception {
        return canonical(new JSONObject().put("tasks",new JSONArray()).put("limits",new JSONArray()).put("sleep",JSONObject.NULL));
    }
    static String initialBase(Context c) throws Exception {
        JSONObject base=new JSONObject(empty());
        Map<String,?> saved=FocusPreferences.read(c,"focus_mode");
        boolean configured=saved.containsKey("daily")||saved.containsKey("minute")||saved.containsKey("duration")
                ||saved.containsKey(FocusSchedule.WEEKDAYS_KEY)||saved.containsKey("allowed");
        // A new installation has no local sleep-plan edit to conflict with the account's plan.
        if(!configured)base.put("sleep",new JSONObject(collect(c)).get("sleep"));
        return canonical(base);
    }
    static String merge(String base,String local,String remote) throws Exception {
        JSONObject a=new JSONObject(base),b=new JSONObject(local),c=new JSONObject(remote),merged=new JSONObject();
        merged.put("tasks",mergeItems(a.getJSONArray("tasks"),b.getJSONArray("tasks"),c.getJSONArray("tasks"),"id"));
        merged.put("limits",mergeItems(a.getJSONArray("limits"),b.getJSONArray("limits"),c.getJSONArray("limits"),"packageName"));
        merged.put("sleep",choose(a.opt("sleep"),b.opt("sleep"),c.opt("sleep")));
        return canonical(merged);
    }
    private static Object choose(Object base,Object local,Object remote) throws Exception {
        String a=canonical(base),b=canonical(local),c=canonical(remote);
        if(b.equals(c)||a.equals(c))return local;
        if(a.equals(b))return remote;
        throw new IllegalStateException("两台设备同时修改了同一项，请选择保留哪一端");
    }
    private static JSONArray mergeItems(JSONArray a,JSONArray b,JSONArray c,String key) throws Exception {
        Map<String,Object> base=index(a,key),local=index(b,key),remote=index(c,key);Set<String> ids=new TreeSet<>();
        ids.addAll(base.keySet());ids.addAll(local.keySet());ids.addAll(remote.keySet());JSONArray result=new JSONArray();
        for(String id:ids){Object value=choose(base.getOrDefault(id,JSONObject.NULL),local.getOrDefault(id,JSONObject.NULL),remote.getOrDefault(id,JSONObject.NULL));if(value!=JSONObject.NULL)result.put(value);}
        return result;
    }
    private static Map<String,Object> index(JSONArray a,String key)throws Exception{
        Map<String,Object> result=new TreeMap<>();for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(result.put(item.getString(key),item)!=null)throw new IllegalArgumentException("重复设置");}return result;
    }
    static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();object.keys().forEachRemaining(keys::add);Collections.sort(keys);List<String> values=new ArrayList<>();for(String key:keys)values.add(JSONObject.quote(key)+":"+canonical(object.get(key)));return "{"+String.join(",",values)+"}";}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;List<String> values=new ArrayList<>();for(int i=0;i<array.length();i++)values.add(canonical(array.get(i)));return "["+String.join(",",values)+"]";}
        return value instanceof String?JSONObject.quote((String)value):value.toString();
    }
    static boolean apply(Context c,String expected,String payload) throws Exception {
        android.os.Bundle sleepSnapshot=sleepSyncSnapshot(c);
        if(!collect(c).equals(expected))return false;
        if(expected.equals(canonical(new JSONObject(payload))))return true;
        JSONObject data=new JSONObject(payload);
        // Reuse backup format validation before writing any portable values.
        JSONObject validation=new JSONObject("{\"schemaVersion\":1,\"tasks\":[],\"limits\":[],\"focus\":{\"focusHistory\":[],\"pomodoroHistory\":[],\"sleepControlHistory\":[]},\"appearance\":2,\"wallpaper\":0,\"restriction\":{},\"streak\":{}}");
        validation.put("tasks",data.getJSONArray("tasks")).put("limits",data.getJSONArray("limits"));
        JSONObject focus=validation.getJSONObject("focus"),sleep=data.getJSONObject("sleep");
        for(Iterator<String> it=sleep.keys();it.hasNext();){String key=it.next();focus.put(key,sleep.get(key));}
        CloudBackupData.validate(validation.toString());
        // Preserve the user's existing card order; append newly received tasks.
        Map<String,Object> incoming=index(data.getJSONArray("tasks"),"id");JSONArray ordered=new JSONArray();
        JSONArray existing=new JSONArray(c.getSharedPreferences("focus_tasks",0).getString("tasks","[]"));
        for(int i=0;i<existing.length();i++){Object task=incoming.remove(existing.getJSONObject(i).getString("id"));if(task!=null)ordered.put(task);}
        for(Object task:incoming.values())ordered.put(task);
        if(!c.getSharedPreferences("focus_tasks",0).edit().putString("tasks",ordered.toString()).commit())throw new IllegalStateException("专注任务保存失败");
        SharedPreferences limits=c.getSharedPreferences("focus_guard_prefs",0);Set<String> selected=new HashSet<>();SharedPreferences.Editor editor=limits.edit();
        JSONArray rules=data.getJSONArray("limits");for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);String pkg=rule.getString("packageName");selected.add(pkg);editor.putInt("limit_minutes_"+pkg,rule.getInt("minutes"));}
        if(!editor.putStringSet("selected_packages",selected).commit()||!LimitSnapshot.publish(c))throw new IllegalStateException("限制设置保存失败");
        android.os.Bundle applied=applySleep(c,sleep,sleepSnapshot,true);
        return !applied.getBoolean("stale");
    }
    /** A denied remote change stays pending; receiving it never weakens local protection. */
    static boolean applySleep(Context c,JSONObject sleep)throws Exception{
        return applySleep(c,sleep,sleepSyncSnapshot(c),false).getBoolean("applied");
    }
    static android.os.Bundle sleepChanges(JSONObject sleep)throws Exception{
        Set<String> allowed=new HashSet<>();JSONArray apps=sleep.getJSONArray("allowed");for(int i=0;i<apps.length();i++)allowed.add(apps.getString(i));
        android.os.Bundle changes=new android.os.Bundle();
        changes.putBoolean("daily",sleep.getBoolean("daily"));changes.putInt("minute",sleep.getInt("minute"));
        changes.putInt("duration",sleep.getInt("duration"));changes.putInt(FocusSchedule.WEEKDAYS_KEY,sleep.getInt(FocusSchedule.WEEKDAYS_KEY));
        changes.putStringArrayList("allowed",new ArrayList<>(allowed));
        return changes;
    }
    private static android.os.Bundle sleepSyncSnapshot(Context c){
        android.os.Bundle result=c.getContentResolver().call(LimitConfigProvider.URI,"sleepSyncSnapshot","focus_mode",null);
        if(result==null)throw new IllegalStateException("睡前计划同步状态无法读取");
        return result;
    }
    private static android.os.Bundle applySleep(Context c,JSONObject sleep,android.os.Bundle snapshot,boolean queueIfDenied)throws Exception{
        android.os.Bundle args=new android.os.Bundle();
        args.putSerializable("expectedPlan",snapshot.getSerializable("plan"));
        args.putString("expectedPending",snapshot.getString("pending"));
        args.putString("sleep",canonical(sleep));args.putBoolean("queueIfDenied",queueIfDenied);
        android.os.Bundle result=c.getContentResolver().call(LimitConfigProvider.URI,"applySyncedSleep","focus_mode",args);
        if(result==null)throw new IllegalStateException("睡前计划保存失败");
        if(!result.getBoolean("saved")&&!result.getBoolean("stale")
                &&!(result.getBoolean("denied")&&!queueIfDenied))
            throw new IllegalStateException(result.getString("message","睡前计划保存失败"));
        return result;
    }
}
