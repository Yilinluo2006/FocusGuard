package com.luoyilin.focusguard;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import retrofit2.Call;
public class PhoneAuthActivity extends AppCompatActivity {
    private SessionManager session;
    private long owner,resendAt,proofResendAt;
    private String auth,challenge,proofChallenge,target;
    private boolean binding,replacing,busy;
    private EditText phone,password,code,proofCode;
    private Button send,confirm,proofSend;
    private TextView status;
    private Call<?> call;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); session=new SessionManager(this);binding=getIntent().getBooleanExtra("binding",false);
        owner=state==null?session.getUserId():state.getLong("owner");auth=state==null?session.getAuthorizationHeader():state.getString("auth");
        if(binding && !owns()) { finish();return; }
        replacing=binding&&session.getPhone()!=null;
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(getColor(R.color.page_background));
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(24),dp(24),dp(24),dp(24));scroll.addView(layout);SecondaryPage.show(this,scroll,binding?(replacing?"更换手机号":"绑定手机号"):"手机号登录",this::finish);
        if(binding) { TextView current=new TextView(this);current.setText("当前手机号 · "+PhoneNumberRules.mask(session.getPhone()));current.setTextColor(getColor(R.color.text_secondary));layout.addView(current); }
        phone=input(layout,"手机号",android.text.InputType.TYPE_CLASS_PHONE);
        password=input(layout,"当前账号密码",129);password.setVisibility(replacing&&session.hasPassword()?View.VISIBLE:View.GONE);
        proofSend=button(layout,"验证当前手机号");proofCode=input(layout,"当前手机号收到的 6 位验证码",2);
        boolean proof=replacing&&!session.hasPassword();proofSend.setVisibility(proof?View.VISIBLE:View.GONE);proofCode.setVisibility(proof?View.VISIBLE:View.GONE);
        send=button(layout,"发送验证码");code=input(layout,"6 位短信验证码",2);confirm=button(layout,binding?(replacing?"确认更换":"确认绑定"):"登录 / 注册");
        status=new TextView(this);status.setTextColor(getColor(R.color.text_secondary));status.setTextSize(14);layout.addView(status);
        if(!binding) {TextView note=new TextView(this);note.setText("已有邮箱账号？请先通过邮箱登录，在个人资料中绑定手机号，继续使用原有记录。");note.setTextColor(getColor(R.color.text_secondary));layout.addView(note);}
        send.setOnClickListener(v->send());confirm.setOnClickListener(v->confirm());proofSend.setOnClickListener(v->proof());
        if(state!=null){challenge=state.getString("challenge");proofChallenge=state.getString("proof");target=state.getString("target");resendAt=Math.min(state.getLong("resend"),SystemClock.elapsedRealtime()+60000);proofResendAt=Math.min(state.getLong("proofResend"),SystemClock.elapsedRealtime()+60000);if(target!=null)phone.setText(target);}
        render();
    }
    private boolean owns(){return owner==session.getUserId()&&java.util.Objects.equals(auth,session.getAuthorizationHeader())&&(!binding||session.isLoggedIn());}
    private EditText input(LinearLayout layout,String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(type);e.setSaveEnabled(false);e.setTextColor(getColor(R.color.input_text));e.setHintTextColor(getColor(R.color.input_hint));layout.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));return e;}
    private Button button(LinearLayout layout,String text){Button b=new Button(this);b.setText(text);b.setTextColor(getColor(R.color.text_primary));b.setBackgroundResource(R.drawable.button_rounded);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.secondary_surface)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(12);layout.addView(b,p);return b;}
    private void render(){if(send==null)return;long s=Math.max(0,(resendAt-SystemClock.elapsedRealtime()+999)/1000);long p=Math.max(0,(proofResendAt-SystemClock.elapsedRealtime()+999)/1000);send.setText(s>0?s+" 秒后重发":"发送验证码");send.setEnabled(!busy&&s==0);proofSend.setText(p>0?p+" 秒后重发":"验证当前手机号");proofSend.setEnabled(!busy&&p==0);confirm.setEnabled(!busy&&challenge!=null);phone.setEnabled(!busy&&target==null);password.setEnabled(!busy);code.setEnabled(!busy&&challenge!=null);proofCode.setEnabled(!busy);status.setText(busy?"正在处理…":challenge==null?"验证码 5 分钟内有效":"已发送至 "+PhoneNumberRules.mask(target));}
    private <T> void enqueue(Call<T> request,java.util.function.Consumer<T> success){busy=true;render();call=request;request.enqueue(new AccountRequestCallback<>(session,auth,(value,error)->{if(isFinishing()||isDestroyed())return;busy=false;if(!owns()){toast("账号已变化，请重新进入");finish();return;}if(error!=null)toast(error);else success.accept(value);render();}));}
    private void proof(){if(!owns())return;enqueue(RetrofitClient.getApi().requestPhoneProof(auth),r->{proofChallenge=r.challengeId;proofResendAt=SystemClock.elapsedRealtime()+r.resendAfterSeconds*1000L;toast("请查收当前手机号验证码");});}
    private void send(){if(!owns()){finish();return;}String number=phone.getText().toString().trim();if(!PhoneNumberRules.valid(number)){phone.setError("请输入中国大陆手机号");return;}
        Call<PhoneCodeResponse> request;
        if(binding){String secret=password.getText().toString();String proof=proofCode.getText().toString();if(replacing&&session.hasPassword()&&(secret.length()<8||secret.length()>64)){password.setError("请输入当前账号密码");return;}if(replacing&&!session.hasPassword()&&(proofChallenge==null||!proof.matches("[0-9]{6}"))){proofCode.setError("请先验证当前手机号");return;}request=RetrofitClient.getApi().requestPhoneBinding(auth,new PhoneBindingRequest(number,replacing&&session.hasPassword()?secret:null,replacing?proofChallenge:null,replacing&&!session.hasPassword()?proof:null));}
        else request=RetrofitClient.getApi().requestPhoneLogin(new PhoneCodeRequest(number));
        enqueue(request,r->{target=number;challenge=r.challengeId;resendAt=SystemClock.elapsedRealtime()+r.resendAfterSeconds*1000L;password.setText("");proofCode.setText("");proofChallenge=null;code.setText("");toast("验证码已发送");});
    }
    private void confirm(){if(!owns()){finish();return;}String digits=code.getText().toString();if(!digits.matches("[0-9]{6}")){code.setError("请输入 6 位验证码");return;}PhoneCodeConfirm request=new PhoneCodeConfirm(challenge,digits);
        if(binding)enqueue(RetrofitClient.getApi().confirmPhoneBinding(auth,request),r->{if(session.updateProfile(r,auth)){toast("手机号已绑定，账号记录已保留");finish();}});
        else enqueue(RetrofitClient.getApi().confirmPhoneLogin(request),r->{if(r.getToken()==null||r.getUserId()==null){toast("登录响应无效，请重试");return;}session.saveLoginSession(r);setResult(RESULT_OK);if(!getIntent().getBooleanExtra("returnToBackup",false))startActivity(new Intent(this,MainActivity.class));finish();});
    }
    @Override protected void onResume(){super.onResume();if(send!=null)handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(call!=null)call.cancel();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("owner",owner);state.putString("auth",auth);state.putString("challenge",challenge);state.putString("proof",proofChallenge);state.putString("target",target);state.putLong("resend",resendAt);state.putLong("proofResend",proofResendAt);super.onSaveInstanceState(state);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String v){Toast.makeText(this,v,Toast.LENGTH_LONG).show();}
}
