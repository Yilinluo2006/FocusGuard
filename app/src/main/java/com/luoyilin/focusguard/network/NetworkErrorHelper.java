package com.luoyilin.focusguard.network;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class NetworkErrorHelper {

    private NetworkErrorHelper() {
    }
    // 工具类不需要创建对象，因此使用私有构造方法

    public static String getMessage(Throwable throwable) {
        return ConnectionFailure.message(NetworkEnvironment.classify(throwable));
    }
}
