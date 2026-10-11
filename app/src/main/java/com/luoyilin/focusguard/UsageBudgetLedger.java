package com.luoyilin.focusguard;
import java.util.*;

/** Monotonic high-water totals survive wall-clock edits and process/device restarts. */
final class UsageBudgetLedger {
    static void sample(Properties p,long day,long now,long elapsed,int boot,String active,Map<String,Long> nativeTotals) {
        long saved=n(p,"day",day),priorElapsed=n(p,"elapsed",elapsed);String prior=p.getProperty("active","");
        long delta=boot==n(p,"boot",-2)&&elapsed>=priorElapsed?elapsed-priorElapsed:0;
        if(delta>30_000)delta=0; // Missing observations are filled by native records when trustworthy.
        if(day>saved){for(String key:new HashSet<>(p.stringPropertyNames()))if(key.startsWith("used."))p.remove(key);
            long boundary=nativeBoundary(day,p.getProperty("zone","Asia/Shanghai"));delta=Math.min(delta,Math.max(0,now-boundary));saved=day;}
        if(day<saved)saved=day; // Correct an unverified initial date without erasing consumed time.
        if(!prior.isEmpty())p.setProperty("used."+prior,Long.toString(n(p,"used."+prior,0)+delta));
        for(Map.Entry<String,Long> v:nativeTotals.entrySet())p.setProperty("used."+v.getKey(),Long.toString(Math.max(n(p,"used."+v.getKey(),0),v.getValue())));
        p.setProperty("day",Long.toString(saved));p.setProperty("elapsed",Long.toString(elapsed));p.setProperty("boot",Integer.toString(boot));
        p.setProperty("active",active==null?"":active);
    }
    private static long nativeBoundary(long day,String zone){return StreakLedger.boundary(day,java.time.ZoneId.of(zone));}
    static long used(Properties p,String pkg){return n(p,"used."+pkg,0);}
    private static long n(Properties p,String key,long fallback){return Long.parseLong(p.getProperty(key,Long.toString(fallback)));}
}
