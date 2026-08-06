package com.luoyilin.focusguard.network;

public class RegisterResponse {

    private Long id;
    // 后端创建的新用户数据库 ID

    private String username;
    // 后端返回的用户名

    private String email;
    // 后端返回的注册邮箱

    private boolean emailVerified;
    // 表示邮箱目前是否已经验证

    private String createdAt;
    // 账号创建时间，先使用 String 接收后端时间文本

    public Long getId() {
        return id;
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

    public String getCreatedAt() {
        return createdAt;
    }
    // 返回账号创建时间
}