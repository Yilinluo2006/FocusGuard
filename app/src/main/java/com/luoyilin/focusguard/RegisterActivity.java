package com.luoyilin.focusguard;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.luoyilin.focusguard.network.NetworkErrorHelper;
import com.luoyilin.focusguard.network.RegisterRequest;
import com.luoyilin.focusguard.network.RegisterResponse;
import com.luoyilin.focusguard.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etRegisterUsername;
    // 用户名输入框

    private EditText etRegisterEmail;
    // 邮箱输入框

    private EditText etRegisterPassword;
    // 密码输入框

    private EditText etRegisterConfirmPassword;
    // 确认密码输入框

    private Button btnRegister;
    // 提交注册请求的按钮

    private Button btnBackToLogin;
    // 返回登录页面的按钮

    private ProgressBar progressRegister;
    // 网络请求期间显示的加载图标

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        // 加载注册页面 XML 布局

        etRegisterUsername = findViewById(R.id.etRegisterUsername);
        etRegisterEmail = findViewById(R.id.etRegisterEmail);
        etRegisterPassword = findViewById(R.id.etRegisterPassword);
        etRegisterConfirmPassword =
                findViewById(R.id.etRegisterConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        progressRegister = findViewById(R.id.progressRegister);
        // 根据 XML 中的 ID 获取所有控件

        btnRegister.setOnClickListener(view -> register());
        // 点击注册按钮时执行 register 方法

        btnBackToLogin.setOnClickListener(view -> finish());
        // 关闭注册页面，返回之前的登录页面
    }

    private void register() {
        String username = etRegisterUsername
                .getText()
                .toString()
                .trim();
        // 读取用户名，并删除首尾空格

        String email = etRegisterEmail
                .getText()
                .toString()
                .trim();
        // 读取邮箱，并删除首尾空格

        String password = etRegisterPassword
                .getText()
                .toString();
        // 读取密码，不主动删除空格

        String confirmPassword = etRegisterConfirmPassword
                .getText()
                .toString();
        // 读取确认密码，只在 Android 本地进行比较

        if (username.length() < 3 || username.length() > 50) {
            etRegisterUsername.setError("用户名长度必须为 3 到 50 个字符");
            etRegisterUsername.requestFocus();
            return;
        }
        // 检查用户名是否符合后端长度要求

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etRegisterEmail.setError("请输入正确的邮箱地址");
            etRegisterEmail.requestFocus();
            return;
        }
        // 使用 Android 提供的规则检查邮箱格式

        if (password.length() < 8 || password.length() > 64) {
            etRegisterPassword.setError("密码长度必须为 8 到 64 个字符");
            etRegisterPassword.requestFocus();
            return;
        }
        // 检查密码是否符合后端长度要求

        if (!password.equals(confirmPassword)) {
            etRegisterConfirmPassword.setError("两次输入的密码不一致");
            etRegisterConfirmPassword.requestFocus();
            return;
        }
        // 比较密码和确认密码，避免用户输错

        setLoading(true);
        // 禁用按钮并显示加载图标

        RegisterRequest request =
                new RegisterRequest(username, email, password);
        // 把用户名、邮箱和密码封装成注册请求对象

        RetrofitClient.getApi()
                .register(request)
                .enqueue(new Callback<RegisterResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<RegisterResponse> call,
                            @NonNull Response<RegisterResponse> response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复按钮状态

                        if (response.isSuccessful()
                                && response.body() != null) {

                            Toast.makeText(
                                    RegisterActivity.this,
                                    "注册成功，请登录",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 提示账号已经成功创建

                            finish();
                            // 关闭注册页面，返回登录页面

                        } else if (response.code() == 409) {
                            Toast.makeText(
                                    RegisterActivity.this,
                                    "用户名或邮箱已经被注册",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 409 表示用户名或邮箱与已有账号冲突

                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    RegisterActivity.this,
                                    "注册信息格式不正确",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 400 表示提交的数据不符合后端验证规则

                        } else {
                            Toast.makeText(
                                    RegisterActivity.this,
                                    "注册失败，状态码：" + response.code(),
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 显示其他未单独处理的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<RegisterResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复页面状态

                        Toast.makeText(
                                RegisterActivity.this,
                                NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 把技术异常转换成用户容易理解的网络提示
                    }
                });
        // enqueue 异步发送请求，不会阻塞 Android 主界面
    }

    private void setLoading(boolean loading) {
        progressRegister.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        // 请求进行时显示 ProgressBar，请求结束后隐藏

        btnRegister.setEnabled(!loading);
        btnBackToLogin.setEnabled(!loading);
        // 请求期间禁用按钮，防止重复注册
    }
}
