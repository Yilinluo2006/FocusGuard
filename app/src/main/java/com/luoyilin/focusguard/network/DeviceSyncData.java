package com.luoyilin.focusguard.network;
import java.util.*;
public final class DeviceSyncData {
    public static final class Settings { public long revision; public String payload,updatedAt,timezone; }
    public static final class SettingsRequest {
        public long expectedRevision;public String payload;
        public SettingsRequest(long revision,String payload){expectedRevision=revision;this.payload=payload;}
    }
    public static final class Item {
        public String packageName,name;public long millis;
        public Item(String pkg,String name,long ms){packageName=pkg;this.name=name;millis=ms;}
    }
    public static final class Request {
        public String deviceName,deviceType,day;
        public List<Item> usage=new ArrayList<>();public Map<String,Long> desiredCeilings=new TreeMap<>();
    }
    public static final class Device {
        public String deviceId,deviceName,deviceType,updatedAt;public List<Item> usage;
    }
    public static final class Usage {
        public transient long requestStarted;
        public transient boolean fromCache;
        public String day;public long serverTime,validUntil;
        public Map<String,Long> ceilings,totalMillis;public List<Device> devices;
    }
}
