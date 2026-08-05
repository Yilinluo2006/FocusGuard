package com.luoyilin.focusguard.network;

public class LoginRequest {

    private final String email;
    // 保存用户输入的登录邮箱

    private final String password;
    // 保存用户输入的明文密码

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
    // 构造方法：创建准备发送给后端的登录请求对象

    public String getEmail() {
        return email;
    }
    // 返回登录邮箱

    public String getPassword() {
        return password;
    }
    // 返回登录密码
}