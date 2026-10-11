package com.luoyilin.focusguard;

final class SleepAppearancePolicy {
    static int effectiveMode(int preference,boolean sleeping){return sleeping?2:preference;}
    static boolean sleeping(long now,boolean enabled,int minute,int duration,int weekdays,java.time.ZoneId zone){
        return enabled&&FocusSchedule.activeStart(now,minute,duration,weekdays,zone)>0;
    }
}
