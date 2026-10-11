package com.luoyilin.focusguard;
import android.content.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import com.tencent.mm.opensdk.openapi.*;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import retrofit2.*;
import java.util.Objects;

public class WechatAuthActivity extends AppCompatActivity {
    private TextView status;private Button retry;private ProgressBar spinner;private Call<?> call;
    private boolean binding,backup,busy;private long owner;private String auth;private IWXAPI api;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=getIntent().getBooleanExtra("binding",false);backup=getIntent().getBooleanExtra("returnToBackup",false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER);body.setPadding(PageUi.dp(this,20),PageUi.dp(this,20),PageUi.dp(this,20),PageUi.dp(this,20));body.setBackgroundColor(getColor(R.color.page_background));
        status=new TextView(this);status.setTextSize(18);status.setTextColor(getColor(R.color.text_primary));status.setGravity(Gravity.CENTER);body.addView(status,new LinearLayout.LayoutParams(-1,-2));
        spinner=new ProgressBar(this);body.addView(spinner);retry=new Button(this);retry.setText("重新微信授权");retry.setOnClickListener(v->start());
        LinearLayout.LayoutParams retryParams=new LinearLayout.LayoutParams(-1,-2);
        retryParams.topMargin=PageUi.dp(this,20);body.addView(retry,retryParams);
        SecondaryPage.show(this,body,"微信授权",this::back);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){back();}});
        if(getIntent().hasExtra("wx_state"))complete(getIntent());else start();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);complete(intent);}
    @Override protected void onDestroy(){if(call!=null)call.cancel();if(api!=null)api.detach();super.onDestroy();}
    private void loading(String text){busy=true;status.setText(text);spinner.setVisibility(android.view.View.VISIBLE);retry.setEnabled(false);}
    private void error(String message){busy=false;status.setText(message);spinner.setVisibility(android.view.View.GONE);retry.setEnabled(true);retry.setText("重新微信授权");}
    private void connectionError(Throwable t){error(NetworkErrorHelper.getMessage(t));retry.setText("重试连接");}
    private String accountChangedMessage(){return new SessionManager(this).isSessionExpired()?"登录已过期，请返回原账号重新登录":"账号已变化，请返回后重新操作";}
    private boolean current(){SessionManager s=new SessionManager(this);return s.getUserId()==owner&&Objects.equals(s.getAuthorizationHeader(),auth);}
    private void start(){
        if(busy)return;SessionManager session=new SessionManager(this);owner=session.getUserId();auth=session.getAuthorizationHeader();
        if(binding&&!session.isLoggedIn()){error("请先登录原账号，再绑定微信");return;}
        try{getPackageManager().getPackageInfo("com.tencent.mm",0);}catch(Exception e){error("请先安装微信，或返回使用邮箱、手机号登录");return;}
        WechatPending.clear(this);loading("正在准备微信授权…");long sent=SystemClock.elapsedRealtime();
        Call<WechatData.Start> request=binding?RetrofitClient.getApi().startWechatBinding(auth):RetrofitClient.getApi().startWechat();call=request;
        request.enqueue(new Callback<>() {
            public void onResponse(Call<WechatData.Start> c,Response<WechatData.Start> r){if(isFinishing()||isDestroyed())return;
                if(!current()){error(accountChangedMessage());return;}
                if(!r.isSuccessful()||r.body()==null){error(failure(r));return;}
                try{WechatPending.save(WechatAuthActivity.this,r.body(),binding,backup,sent);
                    api=WechatSdk.create(WechatAuthActivity.this);api.registerApp(BuildConfig.WECHAT_APP_ID);
                    SendAuth.Req req=new SendAuth.Req();req.scope="snsapi_userinfo";req.state=r.body().state;
                    if(!api.isWXAppInstalled()||!api.sendReq(req)){WechatPending.clear(WechatAuthActivity.this);error("无法打开微信，请稍后重试或使用其他登录方式");}
                    else {busy=false;spinner.setVisibility(android.view.View.GONE);status.setText("请在微信中完成授权，返回后也可以重新尝试");retry.setEnabled(true);}
                }catch(Exception e){WechatPending.clear(WechatAuthActivity.this);error("微信授权暂不可用，请使用其他登录方式");}
            }
            public void onFailure(Call<WechatData.Start> c,Throwable t){if(!isFinishing()&&!isDestroyed())connectionError(t);}
        });
    }
    private void complete(Intent intent){
        WechatPending.Request pending=WechatPending.take(this,intent.getStringExtra("wx_state"));
        if(pending==null){error("微信授权请求已过期或账号已变化，请返回原页面重新发起");retry.setEnabled(false);return;}
        binding=pending.binding;backup=pending.backup;SessionManager session=new SessionManager(this);owner=pending.owner;auth=pending.authorization;
        if(!current()){error(accountChangedMessage());return;}
        int result=intent.getIntExtra("wx_error",-1);String code=intent.getStringExtra("wx_code");
        if(result!=0||code==null||code.isEmpty()){error(result==-2?"已取消微信授权":result==-4?"微信授权未获允许，可使用其他登录方式":"微信授权未完成，请重新尝试");return;}
        loading(binding?"正在绑定到当前账号…":"正在完成微信登录…");WechatData.Code proof=new WechatData.Code(code,pending.state);
        if(binding){Call<ProfileResponse> request=RetrofitClient.getApi().bindWechat(auth,proof);call=request;
            request.enqueue(new Callback<>(){public void onResponse(Call<ProfileResponse> c,Response<ProfileResponse> r){if(isFinishing()||isDestroyed())return;
                if(r.isSuccessful()&&r.body()!=null&&current()&&session.updateProfile(r.body(),auth)){Toast.makeText(WechatAuthActivity.this,"微信已绑定到当前账号",Toast.LENGTH_LONG).show();setResult(RESULT_OK);if(isTaskRoot())startActivity(new Intent(WechatAuthActivity.this,ProfileActivity.class));finish();}
                else error(current()?failure(r):"账号已变化，绑定结果未写入本机");}
                public void onFailure(Call<ProfileResponse> c,Throwable t){if(!isFinishing()&&!isDestroyed())connectionError(t);}});
        }else{Call<LoginResponse> request=RetrofitClient.getApi().loginWechat(proof);call=request;
            request.enqueue(new Callback<>(){public void onResponse(Call<LoginResponse> c,Response<LoginResponse> r){if(isFinishing()||isDestroyed())return;
                if(!current()){error("账号已变化，登录结果未写入本机");return;}
                if(r.isSuccessful()&&r.body()!=null&&r.body().getToken()!=null){session.saveLoginSession(r.body());Toast.makeText(WechatAuthActivity.this,"微信登录成功",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);
                    if(!backup)startActivity(new Intent(WechatAuthActivity.this,MainActivity.class));else if(isTaskRoot())startActivity(new Intent(WechatAuthActivity.this,AccountBackupActivity.class));finish();}
                else error(failure(r));}
                public void onFailure(Call<LoginResponse> c,Throwable t){if(!isFinishing()&&!isDestroyed())connectionError(t);}});
        }
    }
    private void back(){WechatPending.clear(this);if(call!=null)call.cancel();
        if(isTaskRoot())startActivity(new Intent(this,binding&&new SessionManager(this).isLoggedIn()?ProfileActivity.class:LoginActivity.class).putExtra("returnToBackup",backup));finish();}
    private String failure(Response<?> response){
        String fallback=response.code()>=500?"服务器暂时异常，请稍后重试；无需重新登录":response.code()==401?(binding?"登录已过期，请重新登录原账号":"微信授权已失效，请重新授权")
                :response.code()==409?"微信绑定状态存在冲突，请使用原账号登录":response.code()==400?"微信授权已失效，请重新授权"
                :response.code()==429?"请求过于频繁，请稍后重试":"微信登录未完成，请重新尝试";
        if(response.errorBody()!=null)try(okhttp3.ResponseBody body=response.errorBody()){
            String raw=body.string();if(raw.length()<=4096){org.json.JSONObject json=new org.json.JSONObject(raw);String message=json.optString("message",json.optString("detail",fallback));if(!message.isEmpty()&&message.length()<=120)return message;}
        }catch(Exception ignored){}return fallback;
    }
}
