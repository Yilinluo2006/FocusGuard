package com.luoyilin.focusguard.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface FocusGuardApi {

    @GET("api/limits")
    Call<List<AppLimitResponse>> getAppLimits();
    // 向后端发送 GET /api/limits 请求，并接收应用限制列表
}