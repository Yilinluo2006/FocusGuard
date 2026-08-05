package com.luoyilin.focusguard.auth;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final SessionManager sessionManager;
    // 用于读取手机本地保存的 JWT

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    // 创建拦截器时，传入登录状态管理对象

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        // 获取 Retrofit 原本准备发送的网络请求

        String requestPath = originalRequest.url().encodedPath();
        // 获取请求路径，例如 /api/limits 或 /api/auth/login

        if (requestPath.startsWith("/api/auth/")) {
            return chain.proceed(originalRequest);
        }
        // 注册和登录接口不需要 JWT，直接发送原请求

        String authorizationHeader =
                sessionManager.getAuthorizationHeader();
        // 从 SessionManager 中读取 Bearer JWT

        if (authorizationHeader == null) {
            return chain.proceed(originalRequest);
        }
        // 用户没有登录时，不添加 Authorization 请求头

        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest);
        }
        // 如果请求原本已经带有 Authorization，就不重复添加

        Request authorizedRequest = originalRequest
                .newBuilder()
                .header("Authorization", authorizationHeader)
                .build();
        // 在原请求基础上加入 Authorization 请求头

        return chain.proceed(authorizedRequest);
        // 把添加 JWT 后的新请求继续发送给后端
    }
}