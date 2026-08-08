package com.luoyilin.focusguard.network;

public class EmailVerificationConfirmRequest {

    private final String email;
    // 发送给后端的注册邮箱

    private final String code;
    // 发送给后端的六位邮箱验证码

    public EmailVerificationConfirmRequest(
            String email,
            String code
    ) {
        this.email = email;
        this.code = code;
    }
    // 构造方法：保存完成邮箱验证需要的两个值

    public String getEmail() {
        return email;
    }
    // 返回注册邮箱

    public String getCode() {
        return code;
    }
    // 返回六位验证码
}
