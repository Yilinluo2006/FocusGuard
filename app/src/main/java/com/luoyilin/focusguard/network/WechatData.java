package com.luoyilin.focusguard.network;
public final class WechatData {
    public static final class Start { public String appId,state;public long serverTime,expiresAt; }
    public static final class Code { public final String code,state;public Code(String code,String state){this.code=code;this.state=state;} }
}
