package com.luoyilin.focusguard.network;

import android.content.Context;

import com.luoyilin.focusguard.auth.AuthInterceptor;
import com.luoyilin.focusguard.auth.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static final String BASE_URL =
            "http://192.168.10.21:8080/";
    // 后端地址，必须以 / 结尾

    private static FocusGuardApi focusGuardApi;
    // 保存已经创建的 API 对象，避免重复创建

    private RetrofitClient() {
    }
    // 私有构造方法，禁止外部创建 RetrofitClient 对象

    public static synchronized void initialize(Context context) {
        if (focusGuardApi != null) {
            return;
        }
        // 已经初始化过时直接结束，避免重复创建

        Context applicationContext =
                context.getApplicationContext();
        // 获取整个应用共用的 Context，避免内存泄漏

        SessionManager sessionManager =
                new SessionManager(applicationContext);
        // 创建登录状态管理器，用于读取 JWT

        AuthInterceptor authInterceptor =
                new AuthInterceptor(
                        applicationContext,
                        sessionManager
                );
        // 创建自动添加 JWT 的请求拦截器

        OkHttpClient okHttpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(45, TimeUnit.SECONDS)
                        .callTimeout(60, TimeUnit.SECONDS)
                        .addInterceptor(authInterceptor)
                        .build();
        // 邮件发送可能超过默认的十秒读取时间，适当延长等待时间，避免邮件已发送却误报超时
        // 创建 OkHttpClient，并安装 JWT 拦截器

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(
                        GsonConverterFactory.create()
                )
                .build();
        // 创建 Retrofit，设置后端地址、OkHttp 和 JSON 转换器

        focusGuardApi = retrofit.create(
                FocusGuardApi.class
        );
        // 根据 FocusGuardApi 自动生成接口实现对象
    }

    public static FocusGuardApi getApi() {
        if (focusGuardApi == null) {
            throw new IllegalStateException(
                    "RetrofitClient 尚未初始化"
            );
        }
        // 如果应用没有完成初始化，就明确报告错误

        return focusGuardApi;
    }
    // 返回供各个页面调用的后端接口对象
}
