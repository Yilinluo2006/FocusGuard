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

    @POST("api/auth/register")
    Call<RegisterResponse> register(
            @Body RegisterRequest request
    );
    // POST：把用户名、邮箱和密码发送给后端注册接口

    @POST("api/auth/login")
    Call<LoginResponse> login(
            @Body LoginRequest request
    );
    // POST：把邮箱和密码发送给后端登录接口，并接收 JWT

    @POST("api/auth/password-reset/request")
    Call<PasswordResetResponse> requestPasswordResetCode(
            @Body PasswordResetCodeRequest request
    );
    // POST：请求后端向注册邮箱发送六位密码重置验证码

    @POST("api/auth/password-reset/confirm")
    Call<PasswordResetResponse> confirmPasswordReset(
            @Body PasswordResetConfirmRequest request
    );
    // POST：提交邮箱、验证码和新密码，完成密码重置

    @GET("api/limits")
    Call<List<AppLimitResponse>> getAppLimits();
    // GET：查询当前登录用户的全部应用限制

    @POST("api/limits")
    Call<AppLimitResponse> createAppLimit(
            @Body AppLimitRequest request
    );
    // POST：创建一条新的应用限制

    @PUT("api/limits/{id}")
    Call<AppLimitResponse> updateAppLimit(
            @Path("id") Long id,
            @Body AppLimitRequest request
    );
    // PUT：根据 ID 修改已有的应用限制

    @DELETE("api/limits/{id}")
    Call<Void> deleteAppLimit(
            @Path("id") Long id
    );
    // DELETE：根据 ID 删除应用限制
}
