package com.luoyilin.focusguard.network;
public final class SleepTemporaryData {
    public static final class Request {
        public String requestId;public long sleepStart,plannedEnd;
        public Request(String id,long start,long end){requestId=id;sleepStart=start;plannedEnd=end;}
    }
    public static final class State {public long sleepStart,plannedEnd,serverTime,validUntil;public int left;public String requestId;}
}
