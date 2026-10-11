package com.luoyilin.focusguard;

import java.time.*;
import java.util.Properties;

/** Portable clock policy; the platform wrapper supplies wall, monotonic and boot samples. */
final class ProtectionTimeLedger {
    static final class Sample {
        final long millis, day; final ZoneId zone; final boolean verified, aligned;
        Sample(long millis, ZoneId zone, boolean verified, boolean aligned, long day) {
            this.millis=millis;this.zone=zone;this.verified=verified;this.aligned=aligned;
            this.day=day;
        }
    }
    static Sample sample(Properties p,long wall,long elapsed,int boot,String zone) {
        if (!p.containsKey("anchor")) {
            put(p,"anchor",wall);put(p,"elapsed",elapsed);put(p,"boot",boot);
            put(p,"day",StreakLedger.day(wall,ZoneId.of(zone)));p.setProperty("zone",zone);
            p.setProperty("verified","false");
        } else if (n(p,"boot")!=boot || elapsed<n(p,"elapsed")) {
            put(p,"anchor",n(p,"last",n(p,"anchor")));put(p,"elapsed",elapsed);put(p,"boot",boot);
            p.setProperty("verified","false");
        }
        long now=n(p,"anchor")+Math.max(0,elapsed-n(p,"elapsed"));
        ZoneId fixed=ZoneId.of(p.getProperty("zone",zone));
        boolean verified=boot>=0&&Boolean.parseBoolean(p.getProperty("verified"));
        boolean aligned=Math.abs(now-wall)<=90_000&&fixed.getId().equals(zone);
        long day=n(p,"day");
        if(verified)day=Math.max(day,StreakLedger.day(now,fixed));
        put(p,"day",day);
        if(elapsed-n(p,"persistedElapsed",Long.MIN_VALUE/2)>=30_000||!Boolean.toString(aligned).equals(p.getProperty("aligned"))) {
            put(p,"last",now);put(p,"persistedElapsed",elapsed);p.setProperty("aligned",Boolean.toString(aligned));
        }
        return new Sample(now,fixed,verified,aligned,day);
    }
    static void verify(Properties p,long epoch,long elapsed,int boot,String currentZone) {
        // A verified anchor must not jump forward/backward because of a manual wall-clock edit.
        boolean sameBoot=n(p,"boot",-2)==boot&&elapsed>=n(p,"elapsed");
        boolean ever=Boolean.parseBoolean(p.getProperty("everVerified"));
        long projected=sameBoot?n(p,"anchor")+Math.max(0,elapsed-n(p,"elapsed")):n(p,"last",epoch);
        long accepted=ever?Math.max(projected,epoch):epoch;
        String zone=p.getProperty("zone",currentZone);
        put(p,"anchor",accepted);put(p,"elapsed",elapsed);put(p,"boot",boot);put(p,"last",accepted);
        if(!ever)put(p,"day",StreakLedger.day(accepted,ZoneId.of(zone)));
        p.setProperty("zone",zone);p.setProperty("verified","true");p.setProperty("everVerified","true");
    }
    static boolean canRelax(Sample time,int start,int end) {
        LocalTime local=Instant.ofEpochMilli(time.millis).atZone(time.zone).toLocalTime();
        return time.verified&&time.aligned&&RestrictionRules.inWindow(local.getHour()*60+local.getMinute(),start,end);
    }
    private static long n(Properties p,String key){return n(p,key,0);}
    private static long n(Properties p,String key,long fallback){return Long.parseLong(p.getProperty(key,Long.toString(fallback)));}
    private static void put(Properties p,String key,long value){p.setProperty(key,Long.toString(value));}
}
