package com.luoyilin.focusguard;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.luoyilin.focusguard.network.EmailVerificationCodeRequest;
import com.luoyilin.focusguard.network.EmailVerificationConfirmRequest;
import com.luoyilin.focusguard.network.EmailVerificationResponse;
import com.luoyilin.focusguard.network.NetworkErrorHelper;
import com.luoyilin.focusguard.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmailVerificationActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";
    // 注册页或登录页通过这个键传入用户邮箱

    public static final String EXTRA_CODE_ALREADY_SENT =
            "extra_code_already_sent";
    // 注册接口已经发送验证码时，通过这个键直接启动倒计时

    private static final long CODE_COOLDOWN_MILLIS = 60_000L;
    // 获取验证码成功后，按钮需要等待六十秒才能再次点击

    private static final long COUNTDOWN_INTERVAL_MILLIS = 1_000L;
    // 倒计时每隔一秒更新一次按钮文字

    private static final String PREFERENCES_NAME =
            "email_verification_preferences";
    // 单独保存邮箱验证页面需要保留的本地状态

    private static final String KEY_COOLDOWN_END_TIME =
            "verification_code_cooldown_end_time";
    // 保存验证码冷却结束时对应的毫秒时间戳

    private static final String KEY_COOLDOWN_EMAIL =
            "verification_code_cooldown_email";
    // 保存倒计时所属邮箱，避免不同账号错误共用倒计时

    private EditText etVerificationEmail;
    // 注册邮箱输入框

    private EditText etVerificationCode;
    // 六位验证码输入框

    private Button btnSendVerificationCode;
    // 请求后端重新发送验证码的按钮

    private Button btnVerifyEmail;
    // 提交邮箱验证码的按钮

    private Button btnBackToLogin;
    // 返回登录页面的按钮

    private ProgressBar progressEmailVerification;
    // 网络请求期间显示的加载图标

    private CountDownTimer verificationCountDownTimer;
    // 控制重新发送按钮的六十秒倒计时

    private boolean countdownActive;
    // 记录验证码倒计时当前是否正在进行

    private boolean loading;
    // 记录页面当前是否正在等待网络响应

    private SharedPreferences verificationPreferences;
    // 保存倒计时结束时间和对应邮箱

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);
        // 加载注册邮箱验证页面的 XML 布局

        verificationPreferences = getSharedPreferences(
                PREFERENCES_NAME,
                MODE_PRIVATE
        );
        // 获取只供当前应用使用的本地键值存储

        etVerificationEmail = findViewById(
                R.id.etVerificationEmail
        );
        etVerificationCode = findViewById(
                R.id.etVerificationCode
        );
        btnSendVerificationCode = findViewById(
                R.id.btnSendVerificationCode
        );
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail);
        btnBackToLogin = findViewById(
                R.id.btnBackToLoginFromVerification
        );
        progressEmailVerification = findViewById(
                R.id.progressEmailVerification
        );
        // 根据 XML 中的 ID 获取输入框、按钮和加载图标

        String emailFromPreviousPage = getIntent()
                .getStringExtra(EXTRA_EMAIL);
        if (emailFromPreviousPage != null) {
            etVerificationEmail.setText(emailFromPreviousPage);
        }
        // 自动填写注册页或登录页传来的邮箱

        boolean countdownRestored = restoreCodeCountdown();
        // 页面重新打开时尝试恢复之前尚未结束的倒计时

        boolean codeAlreadySent = getIntent().getBooleanExtra(
                EXTRA_CODE_ALREADY_SENT,
                false
        );

        if (codeAlreadySent && !countdownRestored) {
            String email = etVerificationEmail
                    .getText()
                    .toString()
                    .trim();

            if (!email.isEmpty()) {
                startCodeCountdown(email);
            }
        }
        // 注册接口刚发送验证码时，直接显示六十秒重发倒计时

        btnSendVerificationCode.setOnClickListener(
                view -> requestVerificationCode()
        );
        // 点击获取验证码时请求后端重新发送邮件

        btnVerifyEmail.setOnClickListener(
                view -> verifyEmail()
        );
        // 点击完成验证时提交邮箱和六位验证码

        btnBackToLogin.setOnClickListener(view -> finish());
        // 关闭当前页面并返回登录页
    }

    private void requestVerificationCode() {
        String email = getValidEmail();
        if (email == null) {
            return;
        }
        // 邮箱不合法时停止发送验证码

        setLoading(true);
        // 请求期间显示加载状态并禁用页面控件

        EmailVerificationCodeRequest request =
                new EmailVerificationCodeRequest(email);
        // 把邮箱封装成后端需要的 JSON 请求对象

        RetrofitClient.getApi()
                .requestEmailVerificationCode(request)
                .enqueue(new Callback<EmailVerificationResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<EmailVerificationResponse> call,
                            @NonNull Response<EmailVerificationResponse>
                                    response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复页面控件

                        if (response.isSuccessful()) {
                            startCodeCountdown(email);
                            // 后端接受请求后启动六十秒倒计时

                            Toast.makeText(
                                    EmailVerificationActivity.this,
                                    getResponseMessage(
                                            response.body(),
                                            "如果账号尚未验证，验证码会发送到该邮箱"
                                    ),
                                    Toast.LENGTH_LONG
                            ).show();
                            // 使用统一提示，不泄露某个邮箱是否存在
                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    EmailVerificationActivity.this,
                                    "邮箱格式不正确",
                                    Toast.LENGTH_SHORT
                            ).show();
                            // 400 表示邮箱没有通过后端参数校验
                        } else {
                            showStatusCodeError(
                                    "验证码发送失败",
                                    response.code()
                            );
                            // 显示其他未单独处理的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<EmailVerificationResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复页面状态

                        Toast.makeText(
                                EmailVerificationActivity.this,
                                NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 把技术异常转换成用户容易理解的网络提示
                    }
                });
        // enqueue 异步请求后端，不会阻塞 Android 主界面
    }

    private void verifyEmail() {
        String email = getValidEmail();
        if (email == null) {
            return;
        }
        // 邮箱不合法时停止验证

        String code = etVerificationCode
                .getText()
                .toString()
                .trim();
        // 读取用户从邮件中输入的验证码

        if (!code.matches("\\d{6}")) {
            etVerificationCode.setError("请输入 6 位数字验证码");
            etVerificationCode.requestFocus();
            return;
        }
        // 验证码必须正好由六个数字组成

        setLoading(true);
        // 提交期间显示加载状态并禁用页面控件

        EmailVerificationConfirmRequest request =
                new EmailVerificationConfirmRequest(email, code);
        // 把邮箱和验证码封装成后端需要的 JSON 请求对象

        RetrofitClient.getApi()
                .confirmEmailVerification(request)
                .enqueue(new Callback<EmailVerificationResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<EmailVerificationResponse> call,
                            @NonNull Response<EmailVerificationResponse>
                                    response
                    ) {
                        setLoading(false);
                        // 收到后端响应后恢复页面控件

                        if (response.isSuccessful()) {
                            clearSavedCountdown();
                            // 邮箱验证完成后删除本地倒计时状态

                            Toast.makeText(
                                    EmailVerificationActivity.this,
                                    getResponseMessage(
                                            response.body(),
                                            "邮箱验证成功，现在可以登录"
                                    ),
                                    Toast.LENGTH_LONG
                            ).show();
                            // 告知用户已经可以正常登录

                            finish();
                            // 关闭验证页面并返回登录页面
                        } else if (response.code() == 400) {
                            Toast.makeText(
                                    EmailVerificationActivity.this,
                                    "验证码错误或已过期",
                                    Toast.LENGTH_LONG
                            ).show();
                            // 400 通常表示验证码不正确、已过期或格式错误
                        } else {
                            showStatusCodeError(
                                    "邮箱验证失败",
                                    response.code()
                            );
                            // 显示其他未单独处理的 HTTP 状态码
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<EmailVerificationResponse> call,
                            @NonNull Throwable throwable
                    ) {
                        setLoading(false);
                        // 网络请求失败后恢复页面状态

                        Toast.makeText(
                                EmailVerificationActivity.this,
                                NetworkErrorHelper.getMessage(throwable),
                                Toast.LENGTH_LONG
                        ).show();
                        // 把连接异常转换成用户容易理解的提示
                    }
                });
        // enqueue 异步发送邮箱验证请求
    }

    private void startCodeCountdown(String email) {
        long cooldownEndTime = System.currentTimeMillis()
                + CODE_COOLDOWN_MILLIS;
        // 计算六十秒冷却结束时的绝对时间

        verificationPreferences.edit()
                .putLong(KEY_COOLDOWN_END_TIME, cooldownEndTime)
                .putString(KEY_COOLDOWN_EMAIL, email)
                .apply();
        // 保存结束时间和对应邮箱，关闭页面后仍然能够恢复

        startCodeCountdown(CODE_COOLDOWN_MILLIS);
        // 使用完整的六十秒时长启动倒计时
    }

    private boolean restoreCodeCountdown() {
        long cooldownEndTime = verificationPreferences.getLong(
                KEY_COOLDOWN_END_TIME,
                0L
        );

        String cooldownEmail = verificationPreferences.getString(
                KEY_COOLDOWN_EMAIL,
                ""
        );
        // 读取上一次验证码请求保存的结束时间和邮箱

        String currentEmail = etVerificationEmail
                .getText()
                .toString()
                .trim();

        long remainingMillis = cooldownEndTime
                - System.currentTimeMillis();
        // 用结束时间减去当前时间，得到剩余等待时长

        if (remainingMillis > 0L
                && currentEmail.equalsIgnoreCase(cooldownEmail)) {
            startCodeCountdown(remainingMillis);
            return true;
        }
        // 邮箱相同且冷却尚未结束时，从剩余时间继续倒计时

        clearSavedCountdown();
        return false;
        // 过期或不属于当前邮箱的倒计时记录不再保留
    }

    private void startCodeCountdown(long durationMillis) {
        if (verificationCountDownTimer != null) {
            verificationCountDownTimer.cancel();
        }
        // 启动新计时器前取消旧计时器，避免多个倒计时同时运行

        countdownActive = true;
        btnSendVerificationCode.setEnabled(false);
        // 标记倒计时开始，并立即禁用重新发送按钮

        verificationCountDownTimer = new CountDownTimer(
                durationMillis,
                COUNTDOWN_INTERVAL_MILLIS
        ) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsRemaining =
                        (millisUntilFinished + 999L) / 1_000L;
                // 向上取整，确保按钮从六十秒开始显示

                btnSendVerificationCode.setText(
                        secondsRemaining + " 秒后重新获取"
                );
                // 每秒把剩余时间显示到按钮上
            }

            @Override
            public void onFinish() {
                countdownActive = false;
                clearSavedCountdown();
                // 倒计时完成后删除已经失效的本地记录

                btnSendVerificationCode.setText("获取验证码");
                btnSendVerificationCode.setEnabled(!loading);
                // 恢复按钮文字，并在没有网络请求时允许点击
            }
        }.start();
        // 创建并立即启动倒计时
    }

    private void clearSavedCountdown() {
        verificationPreferences.edit()
                .remove(KEY_COOLDOWN_END_TIME)
                .remove(KEY_COOLDOWN_EMAIL)
                .apply();
        // 同时删除倒计时结束时间和对应邮箱
    }

    private String getValidEmail() {
        String email = etVerificationEmail
                .getText()
                .toString()
                .trim();
        // 读取邮箱并删除首尾空格和换行

        if (email.isEmpty()) {
            etVerificationEmail.setError("请输入注册邮箱");
            etVerificationEmail.requestFocus();
            return null;
        }
        // 邮箱为空时返回 null，调用方会停止请求

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etVerificationEmail.setError("邮箱格式不正确");
            etVerificationEmail.requestFocus();
            return null;
        }
        // 使用 Android 提供的邮箱规则检查格式

        return email;
        // 邮箱合法时返回处理后的字符串
    }

    private String getResponseMessage(
            EmailVerificationResponse response,
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

        progressEmailVerification.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        // 网络请求进行时显示加载图标，请求结束后隐藏

        btnSendVerificationCode.setEnabled(
                !loading && !countdownActive
        );
        btnVerifyEmail.setEnabled(!loading);
        btnBackToLogin.setEnabled(!loading);
        etVerificationEmail.setEnabled(!loading);
        etVerificationCode.setEnabled(!loading);
        // 请求期间禁用输入和按钮，避免重复提交或内容变化
    }

    @Override
    protected void onDestroy() {
        if (verificationCountDownTimer != null) {
            verificationCountDownTimer.cancel();
            verificationCountDownTimer = null;
        }
        // 页面销毁时停止计时器，避免继续引用已经关闭的页面

        super.onDestroy();
        // 继续执行父类的页面销毁逻辑
    }
}
