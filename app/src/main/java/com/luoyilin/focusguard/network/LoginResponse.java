package com.luoyilin.focusguard.network;

public class LoginResponse {

    private String token;
    // 保存后端生成的 JWT

    private String tokenType;
    // 保存令牌类型，目前后端返回 Bearer

    private Long userId;
    // 保存当前登录用户的数据库 ID

    private String username;
    // 保存当前登录用户的用户名

    private String email;
    // 保存当前登录用户的邮箱

    private boolean emailVerified;
    // 保存邮箱是否已经通过验证

    public String getToken() {
        return token;
    }
    // 返回 JWT，之后需要保存到手机中

    public String getTokenType() {
        return tokenType;
    }
    // 返回令牌类型 Bearer

    public Long getUserId() {
        return userId;
    }
    // 返回用户 ID

    public String getUsername() {
        return username;
    }
    // 返回用户名

    public String getEmail() {
        return email;
    }
    // 返回邮箱

    public boolean isEmailVerified() {
        return emailVerified;
    }
    // 返回邮箱验证状态
}