package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.LoginRequest;
import com.luoyilin.focusguard.network.LoginResponse;
import com.luoyilin.focusguard.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    // 邮箱输入框

    private EditText etPassword;
    // 密码输入框

    private Button btnLogin;
    // 登录按钮

    private Button btnGoRegister;
// 打开注册页面的按钮

    private ProgressBar progressLogin;
    // 登录请求进行时显示的加载图标

    private SessionManager sessionManager;
    // 统一负责保存和读取登录状态

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // 加载登录页面的 XML 布局

        sessionManager = new SessionManager(this);
        // 创建登录状态管理对象

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);
        progressLogin = findViewById(R.id.progressLogin);
        // 根据 XML 中的 ID 获取登录按钮、注册入口和加载图标

        btnLogin.setOnClickListener(view -> login());
        // 点击登录按钮时执行 login 方法

        btnGoRegister.setOnClickListener(view -> {
            Intent intent = new Intent(
                    LoginActivity.this,
                    RegisterActivity.class
            );
            startActivity(intent);
        });
        // 点击“立即注册”后打开 RegisterActivity
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        // 获取邮箱并删除首尾空格

        String password = etPassword.getText().toString();
        // 获取用户输入的密码

        if (email.isEmpty()) {
            etEmail.setError("请输入邮箱");
            etEmail.requestFocus();
            return;
        }
        // 邮箱为空时停止登录

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("邮箱格式不正确");
            etEmail.requestFocus();
            return;
        }
        // 使用 Android 提供的规则检查邮箱格式

        if (password.length() < 8 || password.length() > 64) {
            etPassword.setError("密码长度必须为 8 到 64 个字符");
            etPassword.requestFocus();
            return;
        }
        // 检查密码长度是否符合后端要求

        setLoading(true);
        // 禁用登录按钮并显示加载图标

        LoginRequest request = new LoginRequest(email, password);
        // 把邮箱和密码封装成后端需要的请求对象

        RetrofitClient.getApi()
                .login(request)
                .enqueue(new Callback<LoginResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<LoginResponse> call,
                            @NonNull Response<LoginResponse> response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复登录按钮

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getToken() != null) {

                            sessionManager.saveLoginSession(response.body());
                            // 使用 SessionManager 保存 JWT 和用户信息

                            Toast.makeText(
                                    LoginActivity.this,
                                    "登录成功",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 提示用户登录成功

                            Intent intent = new Intent(
                                    LoginActivity.this,
                                    MainActivity.class
                            );
                            startActivity(intent);
                            finish();
                            // 打开首页并关闭登录页面

                        } else if (response.code() == 401) {
                            Toast.makeText(
                                    LoginActivity.this,
                                    "邮箱或密码错误",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 401 表示身份验证失败

                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    LoginActivity.this,
                                    "登录信息格式不正确",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 400 表示提交的数据格式不符合要求

                        } else {
                            Toast.makeText(
                                    LoginActivity.this,
                                    "登录失败，状态码：" + response.code(),
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 显示其他未知的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<LoginResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复按钮状态

                        Toast.makeText(
                                LoginActivity.this,
                                "无法连接后端：" + throwable.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        // 显示断网、后端未运行等连接错误
                    }
                });
        // enqueue 表示异步发送请求，不会阻塞页面
    }

    private void setLoading(boolean loading) {
        progressLogin.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        // loading 为 true 时显示加载图标，否则隐藏

        btnLogin.setEnabled(!loading);
        // 请求期间禁用按钮，避免用户连续点击

        btnGoRegister.setEnabled(!loading);
        // 登录请求进行时暂时禁止跳转注册页面
    }
}