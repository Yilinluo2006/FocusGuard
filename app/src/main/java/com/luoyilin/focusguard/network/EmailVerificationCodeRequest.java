package com.luoyilin.focusguard.network;

public class EmailVerificationCodeRequest {

    private final String email;
    // 发送给后端的注册邮箱

    public EmailVerificationCodeRequest(String email) {
        this.email = email;
    }
    // 构造方法：把需要接收验证码的邮箱保存到请求对象中

    public String getEmail() {
        return email;
    }
    // 返回准备发送给后端的邮箱
}
