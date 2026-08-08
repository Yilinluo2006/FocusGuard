package com.luoyilin.focusguard;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.content.SharedPreferences;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.luoyilin.focusguard.network.NetworkErrorHelper;
import com.luoyilin.focusguard.network.PasswordResetCodeRequest;
import com.luoyilin.focusguard.network.PasswordResetConfirmRequest;
import com.luoyilin.focusguard.network.PasswordResetResponse;
import com.luoyilin.focusguard.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";
    // 登录页通过这个键把已经输入的邮箱传到当前页面

    private static final long RESET_CODE_COOLDOWN_MILLIS = 60_000L;
    // 获取验证码成功后，按钮需要等待六十秒才能再次点击

    private static final long COUNTDOWN_INTERVAL_MILLIS = 1_000L;
    // 倒计时每隔一秒更新一次按钮文字

    private static final String RESET_PREFERENCES_NAME =
            "password_reset_preferences";
    // 单独保存密码重置页面需要长期保留的本地状态

    private static final String KEY_COOLDOWN_END_TIME =
            "reset_code_cooldown_end_time";
    // 保存本次验证码冷却结束时对应的毫秒时间戳

    private EditText etResetEmail;
    // 注册邮箱输入框

    private EditText etResetCode;
    // 六位验证码输入框

    private EditText etNewPassword;
    // 新密码输入框

    private EditText etConfirmNewPassword;
    // 确认新密码输入框

    private Button btnSendResetCode;
    // 请求邮箱验证码的按钮

    private Button btnResetPassword;
    // 提交新密码的按钮

    private Button btnBackToLogin;
    // 返回登录页的按钮

    private ProgressBar progressPasswordReset;
    // 网络请求期间显示的加载图标

    private CountDownTimer resetCodeCountDownTimer;
    // 控制“获取验证码”按钮六十秒倒计时

    private boolean resetCodeCountdownActive;
    // 记录验证码倒计时当前是否正在进行

    private boolean loading;
    // 记录页面当前是否正在等待网络响应

    private SharedPreferences resetPreferences;
    // 保存倒计时结束时间，使关闭页面后仍然能够恢复倒计时

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        // 加载忘记密码页面的 XML 布局

        resetPreferences = getSharedPreferences(
                RESET_PREFERENCES_NAME,
                MODE_PRIVATE
        );
        // 获取只供当前应用使用的本地键值存储

        etResetEmail = findViewById(R.id.etResetEmail);
        etResetCode = findViewById(R.id.etResetCode);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        btnSendResetCode = findViewById(R.id.btnSendResetCode);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        progressPasswordReset = findViewById(R.id.progressPasswordReset);
        // 根据 XML 中的 ID 获取页面控件

        String emailFromLogin = getIntent().getStringExtra(EXTRA_EMAIL);
        if (emailFromLogin != null) {
            etResetEmail.setText(emailFromLogin);
        }
        // 登录页已经输入邮箱时自动带到当前页面，减少重复输入

        restoreResetCodeCountdown();
        // 页面重新打开时，根据保存的结束时间继续原来的倒计时

        btnSendResetCode.setOnClickListener(view -> requestResetCode());
        // 点击“获取验证码”时请求后端发送邮件

        btnResetPassword.setOnClickListener(view -> resetPassword());
        // 点击“重置密码”时提交验证码和新密码

        btnBackToLogin.setOnClickListener(view -> finish());
        // 关闭当前页面并返回登录页
    }

    private void requestResetCode() {
        String email = getValidEmail();
        if (email == null) {
            return;
        }
        // 邮箱不合法时停止发送验证码

        setLoading(true);
        // 请求期间显示加载状态并禁用按钮

        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest(email);
        // 把邮箱封装成后端需要的 JSON 请求对象

        RetrofitClient.getApi()
                .requestPasswordResetCode(request)
                .enqueue(new Callback<PasswordResetResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<PasswordResetResponse> call,
                            @NonNull Response<PasswordResetResponse> response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复页面按钮

                        if (response.isSuccessful()) {
                            startResetCodeCountdown();
                            // 后端接受请求后启动六十秒倒计时

                            Toast.makeText(
                                    ForgotPasswordActivity.this,
                                    getResponseMessage(
                                            response.body(),
                                            "如果邮箱已注册，验证码会发送到该邮箱"
                                    ),
                                    Toast.LENGTH_LONG
                            ).show();
                            // 使用统一提示，避免泄露某个邮箱是否已经注册
                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    ForgotPasswordActivity.this,
                                    "邮箱格式不正确",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 400 表示邮箱没有通过后端的数据校验
                        } else {
                            showStatusCodeError("验证码发送失败", response.code());
                            // 显示其他未单独处理的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<PasswordResetResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复页面状态

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 把连接异常转换成用户容易理解的提示
                    }
                });
        // enqueue 异步请求后端，不会阻塞 Android 主界面
    }

    private void resetPassword() {
        String email = getValidEmail();
        if (email == null) {
            return;
        }
        // 邮箱不合法时停止重置密码

        String code = etResetCode.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmNewPassword.getText().toString();
        // 读取验证码、新密码和确认密码

        if (!code.matches("\\d{6}")) {
            etResetCode.setError("请输入 6 位数字验证码");
            etResetCode.requestFocus();
            return;
        }
        // 验证码必须正好由六个数字组成

        if (newPassword.length() < 8 || newPassword.length() > 64) {
            etNewPassword.setError("新密码长度必须为 8 到 64 个字符");
            etNewPassword.requestFocus();
            return;
        }
        // 新密码长度必须符合后端验证规则

        if (!newPassword.equals(confirmPassword)) {
            etConfirmNewPassword.setError("两次输入的新密码不一致");
            etConfirmNewPassword.requestFocus();
            return;
        }
        // 两次密码一致时才允许提交

        setLoading(true);
        // 提交期间显示加载状态并禁用按钮

        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest(
                        email,
                        code,
                        newPassword
                );
        // 把邮箱、验证码和新密码封装成后端需要的请求对象

        RetrofitClient.getApi()
                .confirmPasswordReset(request)
                .enqueue(new Callback<PasswordResetResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<PasswordResetResponse> call,
                            @NonNull Response<PasswordResetResponse> response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复页面按钮

                        if (response.isSuccessful()) {
                            Toast.makeText(
                                    ForgotPasswordActivity.this,
                                    getResponseMessage(
                                            response.body(),
                                            "密码重置成功，请使用新密码登录"
                                    ),
                                    Toast.LENGTH_LONG
                            ).show();
                            // 提示用户密码已经修改成功

                            finish();
                            // 关闭当前页面并返回登录页
                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    ForgotPasswordActivity.this,
                                    "验证码错误、已过期或输入信息不正确",
                                    Toast.LENGTH_LONG
                            ).show();
                            // 400 通常表示验证码或请求字段没有通过校验
                        } else {
                            showStatusCodeError("密码重置失败", response.code());
                            // 显示其他未单独处理的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<PasswordResetResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复页面状态

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 把连接异常转换成用户容易理解的提示
                    }
                });
        // enqueue 异步发送确认请求
    }

    private void startResetCodeCountdown() {
        long cooldownEndTime = System.currentTimeMillis()
                + RESET_CODE_COOLDOWN_MILLIS;
        // 计算六十秒冷却结束时的绝对时间

        resetPreferences.edit()
                .putLong(KEY_COOLDOWN_END_TIME, cooldownEndTime)
                .apply();
        // 保存结束时间，关闭页面或旋转屏幕后仍然能够恢复

        startResetCodeCountdown(RESET_CODE_COOLDOWN_MILLIS);
        // 使用完整的六十秒时长启动倒计时
    }

    private void restoreResetCodeCountdown() {
        long cooldownEndTime = resetPreferences.getLong(
                KEY_COOLDOWN_END_TIME,
                0L
        );
        // 读取上一次请求验证码时保存的冷却结束时间

        long remainingMillis = cooldownEndTime
                - System.currentTimeMillis();
        // 用结束时间减去当前时间，得到还需要等待的毫秒数

        if (remainingMillis > 0L) {
            startResetCodeCountdown(remainingMillis);
            return;
        }
        // 冷却尚未结束时，从剩余时间继续倒计时

        resetPreferences.edit()
                .remove(KEY_COOLDOWN_END_TIME)
                .apply();
        // 已经过期的本地记录不再保留
    }

    private void startResetCodeCountdown(long durationMillis) {
        if (resetCodeCountDownTimer != null) {
            resetCodeCountDownTimer.cancel();
        }
        // 启动新计时器前取消旧计时器，避免出现两个倒计时

        resetCodeCountdownActive = true;
        btnSendResetCode.setEnabled(false);
        // 标记倒计时已开始，并立即禁用获取验证码按钮

        resetCodeCountDownTimer = new CountDownTimer(
                durationMillis,
                COUNTDOWN_INTERVAL_MILLIS
        ) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsRemaining =
                        (millisUntilFinished + 999L) / 1_000L;
                // 向上取整，确保按钮从六十秒开始显示

                btnSendResetCode.setText(
                        secondsRemaining + " 秒后重新获取"
                );
                // 每秒把剩余时间显示到按钮上
            }

            @Override
            public void onFinish() {
                resetCodeCountdownActive = false;
                resetPreferences.edit()
                        .remove(KEY_COOLDOWN_END_TIME)
                        .apply();
                // 倒计时完成后删除已经失效的本地结束时间

                btnSendResetCode.setText("获取验证码");
                btnSendResetCode.setEnabled(!loading);
                // 倒计时结束后恢复按钮文字，并在没有网络请求时允许点击
            }
        }.start();
        // 创建并立即启动六十秒倒计时
    }

    private String getValidEmail() {
        String email = etResetEmail.getText().toString().trim();
        // 读取邮箱并删除首尾空格和换行

        if (email.isEmpty()) {
            etResetEmail.setError("请输入注册邮箱");
            etResetEmail.requestFocus();
            return null;
        }
        // 邮箱为空时返回 null，调用方会停止请求

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etResetEmail.setError("邮箱格式不正确");
            etResetEmail.requestFocus();
            return null;
        }
        // 使用 Android 提供的邮箱规则检查格式

        return email;
        // 邮箱合法时返回处理后的字符串
    }

    private String getResponseMessage(
            PasswordResetResponse response,
            String defaultMessage
    ) {
        if (response == null
                || response.getMessage() == null
                || response.getMessage().trim().isEmpty()) {
            return defaultMessage;
        }
        // 后端没有返回有效 message 时使用本地默认提示

        return response.getMessage();
        // 返回后端提供的操作结果提示
    }

    private void showStatusCodeError(String action, int statusCode) {
        Toast.makeText(
                this,
                action + "，状态码：" + statusCode,
                Toast.LENGTH_SHORT
        ).show();
        // 把未单独处理的 HTTP 状态码显示给用户
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        // 保存当前网络加载状态，供倒计时结束时判断按钮状态

        progressPasswordReset.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        // 请求进行时显示加载图标，请求结束后隐藏

        btnSendResetCode.setEnabled(
                !loading && !resetCodeCountdownActive
        );
        // 没有网络请求且倒计时结束时，才允许再次获取验证码

        btnResetPassword.setEnabled(!loading);
        btnBackToLogin.setEnabled(!loading);
        // 网络请求期间禁用重置密码和返回按钮，避免重复操作
    }

    @Override
    protected void onDestroy() {
        if (resetCodeCountDownTimer != null) {
            resetCodeCountDownTimer.cancel();
            resetCodeCountDownTimer = null;
        }
        // 页面销毁时停止计时器，避免它继续引用已经关闭的页面

        super.onDestroy();
        // 继续执行父类的页面销毁逻辑
    }
}
