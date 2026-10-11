package com.luoyilin.focusguard;

import android.os.*;
import android.widget.*;
import androidx.appcompat.app.*;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import retrofit2.Call;

public class EmailBindingActivity extends AppCompatActivity {
    private SessionManager session;
    private long owner, resendAt;
    private String target;
    private String proofChallenge;
    private long proofResendAt;
    private EditText phoneProofCode;
    private Button phoneProofSend;
    private EditText email, password, code;
    private boolean busy;
    private Call<?> call;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() { render(); handler.postDelayed(this, 1000); } };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_email_binding);
        SecondaryPage.show(this, ((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0), "邮箱绑定", () -> getOnBackPressedDispatcher().onBackPressed()); 
        session = new SessionManager(this); owner = state == null ? session.getUserId() : state.getLong("bindingOwner");
        if (!ownsPage()) { finish(); return; }
        email = findViewById(R.id.bindingNewEmail); password = findViewById(R.id.bindingPassword); code = findViewById(R.id.bindingCode);
        if(!session.hasPassword()) {
            password.setHint("设置邮箱登录密码（8–64 位）");
            LinearLayout layout=(LinearLayout)password.getParent(); int index=layout.indexOfChild(password)+1;
            phoneProofSend=new Button(this);phoneProofSend.setText("验证当前手机号");phoneProofSend.setTextColor(getColor(R.color.text_primary));
            phoneProofSend.setBackgroundResource(R.drawable.button_rounded);phoneProofSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.secondary_surface)));
            layout.addView(phoneProofSend,index,new LinearLayout.LayoutParams(-1,-2));
            phoneProofCode=new EditText(this);phoneProofCode.setHint("当前手机号收到的 6 位验证码");phoneProofCode.setInputType(2);phoneProofCode.setSingleLine(true);phoneProofCode.setSaveEnabled(false);
            phoneProofCode.setTextColor(getColor(R.color.input_text));phoneProofCode.setHintTextColor(getColor(R.color.input_hint));layout.addView(phoneProofCode,index+1,new LinearLayout.LayoutParams(-1,-2));
            phoneProofSend.setOnClickListener(v->sendPhoneProof());
        }
        if (state != null) {
            target = state.getString("target");
            proofChallenge=state.getString("proofChallenge");proofResendAt=Math.min(state.getLong("proofResendAt"),SystemClock.elapsedRealtime()+60_000);
            resendAt = Math.min(state.getLong("resendAt"), SystemClock.elapsedRealtime() + 60_000);
            if (target != null) email.setText(target);
        }
        findViewById(R.id.bindingSendCode).setOnClickListener(v -> send());
        findViewById(R.id.bindingConfirm).setOnClickListener(v -> confirm());
        findViewById(R.id.bindingBack).setOnClickListener(v -> finish()); render();
    }
    @Override protected void onResume() { super.onResume(); if (email != null) handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { if (call != null) call.cancel(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("bindingOwner", owner); state.putString("target", target); state.putLong("resendAt", resendAt);
        state.putString("proofChallenge",proofChallenge);state.putLong("proofResendAt",proofResendAt);super.onSaveInstanceState(state);
    }
    private boolean ownsPage() { return session != null && session.isLoggedIn() && owner == session.getUserId(); }
    private void render() {
        ((TextView)findViewById(R.id.bindingCurrentEmail)).setText(session.getEmail()==null?"绑定邮箱 · 当前手机号 " + PhoneNumberRules.mask(session.getPhone()):"当前邮箱\n" + session.getEmail());
        if(phoneProofSend!=null){long left=Math.max(0,(proofResendAt-SystemClock.elapsedRealtime()+999)/1000);phoneProofSend.setText(left>0?left+" 秒后重发":"验证当前手机号");phoneProofSend.setEnabled(!busy&&left==0);phoneProofCode.setEnabled(!busy);}
        email.setEnabled(!busy && target == null); password.setEnabled(!busy); code.setEnabled(!busy && target != null);
        long seconds = Math.max(0, (resendAt - SystemClock.elapsedRealtime() + 999) / 1000);
        Button send = findViewById(R.id.bindingSendCode); send.setText(seconds > 0 ? seconds + " 秒后重发" : target == null ? "发送验证码" : "重新发送验证码");
        send.setEnabled(!busy && seconds == 0); findViewById(R.id.bindingConfirm).setEnabled(!busy && target != null);
        ((TextView)findViewById(R.id.bindingCodeStatus)).setText(target == null ? "验证码会发送到新邮箱" : "验证码已提交发送至 " + target + "，10 分钟内有效");
        findViewById(R.id.bindingProgress).setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    private void send() {
        if (!ownsPage()) { toast("登录状态已变化，请重新进入"); return; }
        String address = email.getText().toString().trim(), secret = password.getText().toString();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("请输入正确的新邮箱"); return; }
        if (secret.length() < 8 || secret.length() > 64) { password.setError("请输入当前账号密码"); return; }
        String auth = session.getAuthorizationHeader(); busy = true; render();
        String proof=phoneProofCode==null?null:phoneProofCode.getText().toString();
        if(!session.hasPassword()&&(proofChallenge==null||proof==null||!proof.matches("[0-9]{6}"))){busy=false;render();phoneProofCode.setError("请先验证当前手机号");return;}
        Call<java.util.Map<String,String>> request = RetrofitClient.getApi().requestEmailBinding(auth, new EmailBindingRequest(address, secret,proofChallenge,proof)); call = request;
        request.enqueue(new AccountRequestCallback<>(session, auth, (value, error) -> {
            if (isFinishing() || isDestroyed()) return; busy = false;
            if (error != null) toast(error);
            else { target = address; resendAt = SystemClock.elapsedRealtime() + 60_000; password.setText("");proofChallenge=null;if(phoneProofCode!=null)phoneProofCode.setText(""); toast("请查收新邮箱验证码"); }
            render();
        }));
    }
    private void confirm() {
        if (!ownsPage()) { toast("登录状态已变化，请重新进入"); return; }
        String digits = code.getText().toString();
        if (!digits.matches("[0-9]{6}")) { code.setError("请输入 6 位验证码"); return; }
        new AlertDialog.Builder(this).setTitle("确认更换邮箱？").setMessage(target + "\n\n账号、专注记录和备份保持不变。")
                .setNegativeButton("取消", null).setPositiveButton("确认换绑", (dialog, which) -> {
                    if (!ownsPage()) { toast("登录状态已变化，请重新进入"); return; }
                    String auth = session.getAuthorizationHeader(); busy = true; render();
                    Call<ProfileResponse> request = RetrofitClient.getApi().confirmEmailBinding(auth, new EmailBindingConfirm(target, digits)); call = request;
                    request.enqueue(new AccountRequestCallback<>(session, auth, (profile, error) -> {
                        if (isFinishing() || isDestroyed()) return; busy = false;
                        if (error != null) toast(error);
                        else if (session.updateProfile(profile, auth)) { toast("邮箱已换绑，账号记录已保留"); finish(); }
                        else toast("账号已变化，请刷新资料");
                        render();
                    }));
                }).show();
    }
    private void sendPhoneProof() {
        if(!ownsPage())return;String auth=session.getAuthorizationHeader();busy=true;render();
        Call<PhoneCodeResponse> request=RetrofitClient.getApi().requestPhoneProof(auth);call=request;
        request.enqueue(new AccountRequestCallback<>(session,auth,(response,error)->{
            if(isFinishing()||isDestroyed())return;busy=false;
            if(error!=null)toast(error);else{proofChallenge=response.challengeId;proofResendAt=SystemClock.elapsedRealtime()+response.resendAfterSeconds*1000L;toast("请查收当前手机号验证码");}render();
        }));
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
