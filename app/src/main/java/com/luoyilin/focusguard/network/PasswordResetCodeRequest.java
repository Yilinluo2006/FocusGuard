package com.luoyilin.focusguard.network;

public class PasswordResetCodeRequest {

    private final String email;
    // 发送给后端的注册邮箱

    public PasswordResetCodeRequest(String email) {
        this.email = email;
    }
    // 构造方法：把页面输入的邮箱保存到请求对象中

    public String getEmail() {
        return email;
    }
    // 返回准备发送给后端的邮箱
}
