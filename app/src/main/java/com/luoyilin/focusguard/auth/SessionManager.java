package com.luoyilin.focusguard.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyilin.focusguard.network.LoginResponse;

public class SessionManager {

    private static final String PREFERENCES_NAME = "focusguard_session";
    // SharedPreferences 文件名称

    private static final String KEY_TOKEN = "jwt_token";
    // JWT 对应的保存键名

    private static final String KEY_TOKEN_TYPE = "token_type";
    // Token 类型对应的保存键名，例如 Bearer

    private static final String KEY_USER_ID = "user_id";
    // 用户 ID 对应的保存键名

    private static final String KEY_USERNAME = "username";
    // 用户名对应的保存键名

    private static final String KEY_EMAIL = "email";
    // 邮箱对应的保存键名

    private static final String KEY_EMAIL_VERIFIED = "email_verified";
    // 邮箱验证状态对应的保存键名

    private final SharedPreferences preferences;
    // 用于在手机本地保存少量登录信息

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }
    // 获取 FocusGuard 专用的本地登录信息存储空间

    public void saveLoginSession(LoginResponse response) {
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString(KEY_TOKEN, response.getToken());
        editor.putString(KEY_TOKEN_TYPE, response.getTokenType());
        editor.putString(KEY_USERNAME, response.getUsername());
        editor.putString(KEY_EMAIL, response.getEmail());
        editor.putBoolean(
                KEY_EMAIL_VERIFIED,
                response.isEmailVerified()
        );

        if (response.getUserId() != null) {
            editor.putLong(KEY_USER_ID, response.getUserId());
        }

        editor.apply();
    }
    // 登录成功后，把后端返回的用户信息和 JWT 保存到手机本地

    public String getToken() {
        return preferences.getString(KEY_TOKEN, null);
    }
    // 读取已经保存的 JWT；没有登录时返回 null

    public String getAuthorizationHeader() {
        String token = getToken();

        if (token == null || token.isEmpty()) {
            return null;
        }

        String tokenType = preferences.getString(
                KEY_TOKEN_TYPE,
                "Bearer"
        );

        if (tokenType == null || tokenType.isEmpty()) {
            tokenType = "Bearer";
        }

        return tokenType + " " + token;
    }
    // 生成请求头需要的格式，例如：Bearer eyJhbGciOi...

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }
    // 根据本地是否存在 JWT 判断用户是否已经登录

    public long getUserId() {
        return preferences.getLong(KEY_USER_ID, -1L);
    }
    // 读取用户 ID；不存在时返回 -1

    public String getUsername() {
        return preferences.getString(KEY_USERNAME, null);
    }
    // 读取用户名

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, null);
    }
    // 读取用户邮箱

    public void clearSession() {
        preferences.edit().clear().apply();
    }
    // 退出登录时，清除手机中保存的全部登录信息
}