package com.luoyilin.focusguard.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static final String BASE_URL =
            "http://192.168.10.21:8080/";
    // 后端基础地址，必须以 / 结尾

    private static FocusGuardApi focusGuardApi;
    // 保存创建好的 API 对象，避免重复创建

    private RetrofitClient() {
    }
    // 私有构造方法，防止外部创建 RetrofitClient 对象

    public static FocusGuardApi getApi() {
        if (focusGuardApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();
            // 创建 Retrofit，并配置服务器地址和 JSON 转换器

            focusGuardApi = retrofit.create(
                    FocusGuardApi.class
            );
            // 根据 FocusGuardApi 接口自动生成网络请求对象
        }

        return focusGuardApi;
        // 返回可以调用后端接口的对象
    }
}