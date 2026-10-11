package com.luoyilin.focusguard.network;

public class RegisterRequest {

    private final String email;
    // 发送给后端的注册邮箱

    private final String password;
    // 发送给后端的明文密码，后端会使用 BCrypt 加密

    public RegisterRequest(
            String email,
            String password
    ) {
        this.email = email;
        this.password = password;
    }
    // 构造方法：把注册页面输入的三个值保存到请求对象中

    public String getEmail() {
        return email;
    }
    // 返回邮箱

    public String getPassword() {
        return password;
    }
    // 返回密码
}
