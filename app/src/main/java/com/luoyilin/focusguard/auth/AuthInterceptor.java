package com.luoyilin.focusguard.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.luoyilin.focusguard.LoginActivity;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final Context applicationContext;
    // 保存应用级 Context，用于登录失效后跳转登录页面

    private final SessionManager sessionManager;
    // 用于读取和清除手机本地保存的登录信息

    public AuthInterceptor(
            Context context,
            SessionManager sessionManager
    ) {
        this.applicationContext = context.getApplicationContext();
        this.sessionManager = sessionManager;
    }
    // 创建拦截器时传入应用 Context 和登录状态管理器

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        // 获取 Retrofit 准备发送的原始网络请求

        String requestPath = originalRequest.url().encodedPath();
        // 获取请求路径，例如 /api/limits 或 /api/auth/login

        if (requestPath.startsWith("/api/auth/")) {
            return chain.proceed(originalRequest);
        }
        // 注册和登录接口不需要 JWT，也不处理它们返回的 401

        String authorizationHeader =
                sessionManager.getAuthorizationHeader();
        // 从 SessionManager 中读取 Bearer JWT

        Request requestToSend = originalRequest;

        if (authorizationHeader != null
                && originalRequest.header("Authorization") == null) {
            requestToSend = originalRequest
                    .newBuilder()
                    .header("Authorization", authorizationHeader )
                    .build();
        }
        // 存在 JWT 且请求未自行设置请求头时，自动添加 Authorization

        Response response = chain.proceed(requestToSend);
        // 把请求发送给后端，并取得 HTTP 响应

        if (response.code() == 401) {
            handleUnauthorized();
        }
        // 收到 401 表示当前登录凭证已经失效

        return response;
    }

    private synchronized void handleUnauthorized() {
        if (!sessionManager.isLoggedIn()) {
            return;
        }
        // 多个请求同时返回 401 时，只处理第一次

        sessionManager.clearSession();
        // 删除手机本地已经失效的 JWT 和用户信息

        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(
                    applicationContext,
                    LoginActivity.class
            );
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            applicationContext.startActivity(intent);
        });
        // 网络请求运行在后台线程，因此切换到主线程打开登录页面
    }
}
