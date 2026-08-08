package com.luoyilin.focusguard.network;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class NetworkErrorHelper {

    private NetworkErrorHelper() {
    }
    // 工具类不需要创建对象，因此使用私有构造方法

    public static String getMessage(Throwable throwable) {
        Throwable currentThrowable = throwable;
        // 从 Retrofit 返回的最外层异常开始检查

        while (currentThrowable != null) {
            if (currentThrowable instanceof UnknownHostException) {
                return "请检查网络连接";
            }
            // 无法解析服务器地址，通常表示设备断网

            if (currentThrowable instanceof ConnectException) {
                return "暂时无法连接服务器";
            }
            // 已找到服务器地址，但后端没有运行或端口无法访问

            if (currentThrowable instanceof SocketTimeoutException) {
                return "连接超时，请稍后重试";
            }
            // 网络请求在规定时间内没有完成

            currentThrowable = currentThrowable.getCause();
            // 某些异常会包装真实原因，因此继续检查内部异常
        }

        return "网络请求失败，请稍后重试";
        // 无法进一步识别时返回统一提示，不暴露技术错误文本
    }
}
