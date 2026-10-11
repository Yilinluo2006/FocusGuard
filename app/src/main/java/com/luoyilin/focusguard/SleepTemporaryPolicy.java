package com.luoyilin.focusguard;

/** A temporary interval belongs to its original sleep plan and never ends that plan. */
final class SleepTemporaryPolicy {
    static boolean permitsApp(boolean sleeping, boolean launcher, boolean whitelisted, boolean essential) {
        return essential || whitelisted && !(sleeping && launcher);
    }
    static boolean controlling(long sessionEnd,long temporaryRemaining){return sessionEnd>0&&temporaryRemaining<=0;}
    static long remaining(long sleepStart,long plannedEnd,long grantStart,long now,long elapsed,
            int boot,long until,long elapsedUntil,int grantBoot) {
        if(sleepStart<=0||sleepStart!=grantStart||now>=plannedEnd||boot<0)return 0;
        return Math.min(Math.max(0,plannedEnd-now),RestrictionRules.remaining(now,elapsed,boot,until,elapsedUntil,grantBoot));
    }
    static int left(long day,long savedDay,int used){return Math.max(0,5-Math.max(0,RestrictionRules.quotaUsed(day,savedDay,used)));}
}
