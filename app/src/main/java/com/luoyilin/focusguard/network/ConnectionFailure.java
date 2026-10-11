package com.luoyilin.focusguard.network;

/** Transport errors never imply invalid credentials; only a confirmed HTTP 401 does. */
public final class ConnectionFailure {
    public enum Kind { OFFLINE,UNREACHABLE,TIMEOUT,AUTH_EXPIRED,FORBIDDEN,SERVER_ERROR,RATE_LIMITED,REQUEST_ERROR,UNKNOWN }
    public static Kind classify(Throwable error,boolean offline,boolean localEndpoint){
        Throwable current=error;
        for(int depth=0;current!=null&&depth<20;depth++,current=current.getCause()){
            if(current instanceof HttpFailure){int code=((HttpFailure)current).code;
                return code==401?Kind.AUTH_EXPIRED:code==403?Kind.FORBIDDEN:code==429?Kind.RATE_LIMITED
                        :code==408||code==504?Kind.TIMEOUT:code>=500?Kind.SERVER_ERROR:Kind.REQUEST_ERROR;}
        }
        if(offline&&!localEndpoint)return Kind.OFFLINE;
        current=error;
        for(int depth=0;current!=null&&depth<20;depth++,current=current.getCause()){
            if(current instanceof java.net.SocketTimeoutException||current instanceof java.util.concurrent.TimeoutException)return Kind.TIMEOUT;
            if(current instanceof java.net.ConnectException||current instanceof java.net.UnknownHostException
                    ||current instanceof java.net.NoRouteToHostException||current instanceof javax.net.ssl.SSLException)return Kind.UNREACHABLE;
        }
        return Kind.UNKNOWN;
    }
    public static String message(Kind kind){switch(kind){
        case OFFLINE:return "网络已断开，请联网后重试";
        case UNREACHABLE:return "服务器暂时不可达，请稍后重试；无需重新登录";
        case TIMEOUT:return "服务器响应超时，请重试；无需重新登录";
        case AUTH_EXPIRED:return "登录已过期，请重新登录";
        case FORBIDDEN:return "当前账号无权执行此操作，请检查账号权限";
        case SERVER_ERROR:return "服务器暂时异常，请稍后重试；无需重新登录";
        case RATE_LIMITED:return "请求过于频繁，请稍后重试";
        case REQUEST_ERROR:return "请求未完成，请检查设置后重试";
        default:return "连接未完成，请稍后重试；无需重新登录";
    }}
}
