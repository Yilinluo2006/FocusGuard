package com.luoyilin.focusguard.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FocusGuardApi {

    @GET("api/limits")
    Call<List<AppLimitResponse>> getAppLimits();
    // GET：查询所有限制记录

    @POST("api/limits")
    Call<AppLimitResponse> createAppLimit(
            @Body AppLimitRequest request
    );
    // POST：创建新的限制记录

    @PUT("api/limits/{id}")
    Call<AppLimitResponse> updateAppLimit(
            @Path("id") Long id,
            @Body AppLimitRequest request
    );
    // PUT：根据 ID 修改已有记录

    @DELETE("api/limits/{id}")
    Call<Void> deleteAppLimit(
            @Path("id") Long id
    );
    // DELETE：根据 ID 从后端和 MySQL 中删除记录
}