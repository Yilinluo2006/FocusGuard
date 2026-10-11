package com.luoyilin.focusguard;
final class SharedQuotaPolicy {
    static final String PER_DEVICE = "per_device";
    static final String STRICT_SHARED = "strict_shared";
    static boolean strictMode(String mode){return STRICT_SHARED.equals(mode);}
    static long perDeviceRemaining(long dailyLimit,long localUsed){
        return Math.max(0,Math.max(0,dailyLimit)-Math.max(0,localUsed));
    }
    static long displayRemaining(long dailyLimit,long reported,long local,long totalUsed,long allocatedRemaining,boolean online){
        if(allocatedRemaining<=0)return 0;
        if(!online)return allocatedRemaining;
        long otherUsed=Math.max(0,totalUsed-reported);
        return Math.max(0,dailyLimit-otherUsed-Math.max(reported,local));
    }
    static long remaining(long ceiling,long reported,long local,long elapsed,long expires,int savedBoot,int boot){
        if(boot<0||savedBoot!=boot||elapsed>=expires||local+2000<reported)return 0;
        return Math.max(0,ceiling-Math.max(reported,local));
    }
}
