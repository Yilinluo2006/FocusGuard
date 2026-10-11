package com.luoyilin.focusguard.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FocusGuardApi {
    @POST("api/auth/wechat/start") Call<WechatData.Start> startWechat();
    @POST("api/auth/wechat/login") Call<LoginResponse> loginWechat(@Body WechatData.Code request);
    @POST("api/account/bindings/wechat/start") Call<WechatData.Start> startWechatBinding(@Header("Authorization") String auth);
    @POST("api/account/bindings/wechat/confirm") Call<ProfileResponse> bindWechat(@Header("Authorization") String auth,@Body WechatData.Code request);
    @GET("api/sync/sleep/temporary/{device}") Call<SleepTemporaryData.State> getSleepTemporary(@Header("Authorization") String auth,@Path("device") String device);
    @POST("api/sync/sleep/temporary/{device}") Call<SleepTemporaryData.State> requestSleepTemporary(@Header("Authorization") String auth,@Path("device") String device,@Body SleepTemporaryData.Request request);
    @GET("api/sync/settings") Call<DeviceSyncData.Settings> getSharedSettings(@Header("Authorization") String authorization);
    @PUT("api/sync/settings") Call<DeviceSyncData.Settings> putSharedSettings(@Header("Authorization") String authorization,@Body DeviceSyncData.SettingsRequest request);
    @GET("api/sync/usage/{device}") Call<DeviceSyncData.Usage> getDeviceUsage(@Header("Authorization") String authorization,@Path("device") String device,@Query("day") String day);
    @POST("api/sync/usage/{device}") Call<DeviceSyncData.Usage> postDeviceUsage(@Header("Authorization") String authorization,@Path("device") String device,@Body DeviceSyncData.Request request);
    @POST("api/sync/compliance/{device}") Call<ComplianceData.Result> postCompliance(@Header("Authorization") String authorization,@Path("device") String device,@Body ComplianceData.Request request);
    @POST("api/auth/phone/request") Call<PhoneCodeResponse> requestPhoneLogin(@Body PhoneCodeRequest request);
    @POST("api/auth/phone/confirm") Call<LoginResponse> confirmPhoneLogin(@Body PhoneCodeConfirm request);
    @POST("api/account/bindings/phone/proof/request") Call<PhoneCodeResponse> requestPhoneProof(@Header("Authorization") String auth);
    @POST("api/account/bindings/phone/request") Call<PhoneCodeResponse> requestPhoneBinding(@Header("Authorization") String auth,@Body PhoneBindingRequest request);
    @POST("api/account/bindings/phone/confirm") Call<ProfileResponse> confirmPhoneBinding(@Header("Authorization") String auth,@Body PhoneCodeConfirm request);
    @GET("api/account/profile") Call<ProfileResponse> getProfile(@Header("Authorization") String authorization);
    @PUT("api/account/profile") Call<ProfileResponse> updateProfile(@Header("Authorization") String authorization, @Body ProfileRequest request);
    @POST("api/account/bindings/email/request") Call<java.util.Map<String,String>> requestEmailBinding(@Header("Authorization") String authorization, @Body EmailBindingRequest request);
    @POST("api/account/bindings/email/confirm") Call<ProfileResponse> confirmEmailBinding(@Header("Authorization") String authorization, @Body EmailBindingConfirm request);
    @GET("api/backups")
    Call<List<BackupResponse>> getBackups(@Header("Authorization") String authorization);
    @GET("api/backups/{device}")
    Call<BackupResponse> getBackup(@Path("device") String device, @Header("Authorization") String authorization);
    @PUT("api/backups/{device}")
    Call<BackupResponse> putBackup(@Path("device") String device, @Header("Authorization") String authorization, @Body BackupRequest request);

    @POST("api/auth/register")
    Call<RegisterResponse> register(
            @Body RegisterRequest request
    );
    // POST：把用户名、邮箱和密码发送给后端注册接口

    @POST("api/auth/email-verification/request")
    Call<EmailVerificationResponse> requestEmailVerificationCode(
            @Body EmailVerificationCodeRequest request
    );
    // POST：请求后端向尚未验证的注册邮箱发送六位验证码

    @POST("api/auth/email-verification/confirm")
    Call<EmailVerificationResponse> confirmEmailVerification(
            @Body EmailVerificationConfirmRequest request
    );
    // POST：提交注册邮箱和验证码，完成邮箱验证

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
