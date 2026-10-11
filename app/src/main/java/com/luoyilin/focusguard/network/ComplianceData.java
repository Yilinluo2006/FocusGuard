package com.luoyilin.focusguard.network;
import java.util.*;
public final class ComplianceData {
    public static final class Day {
        public String day,state; public boolean participated; public long observedThrough;
        public Day(String date,boolean active,String value,long through){day=date;participated=active;state=value;observedThrough=through;}
    }
    public static final class Request { public List<Day> days = new ArrayList<>(); }
    public static final class Result { public long serverTime;public List<Day> days; }
}
