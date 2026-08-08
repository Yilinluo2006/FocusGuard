package com.luoyilin.focusguard.network;

public class PasswordResetConfirmRequest {

    private final String email;
    // 发送给后端的注册邮箱

    private final String code;
    // 发送给后端的六位验证码

    private final String newPassword;
    // 发送给后端的新密码

    public PasswordResetConfirmRequest(
            String email,
            String code,
            String newPassword
    ) {
        this.email = email;
        this.code = code;
        this.newPassword = newPassword;
    }
    // 构造方法：把重置密码需要的三个值保存到请求对象中

    public String getEmail() {
        return email;
    }
    // 返回注册邮箱

    public String getCode() {
        return code;
    }
    // 返回六位验证码

    public String getNewPassword() {
        return newPassword;
    }
    // 返回准备设置的新密码
}
