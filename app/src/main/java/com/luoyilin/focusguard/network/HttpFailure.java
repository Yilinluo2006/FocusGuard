package com.luoyilin.focusguard.network;
public final class HttpFailure extends java.io.IOException {
    public final int code;
    public HttpFailure(int code){super("HTTP "+code);this.code=code;}
}
