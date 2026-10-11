package com.luoyilin.focusguard.wxapi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.luoyilin.focusguard.BuildConfig;
import com.luoyilin.focusguard.WechatAuthActivity;
import com.luoyilin.focusguard.WechatPending;
import com.luoyilin.focusguard.WechatSdk;
import com.tencent.mm.opensdk.openapi.*;
import com.tencent.mm.opensdk.modelbase.*;
import com.tencent.mm.opensdk.modelmsg.SendAuth;

/** SDK entry only. Provider identities are verified by our backend, never accepted from Intent extras. */
public class WXEntryActivity extends Activity implements IWXAPIEventHandler {
    private IWXAPI api;
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(!WechatPending.hasPending(this)){finish();return;}api=WechatSdk.create(this);dispatch(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);dispatch(intent);}
    private void dispatch(Intent intent){try{if(api==null||!WechatPending.hasPending(this)||!api.handleIntent(intent,this))finish();}catch(Exception e){finish();}}
    public void onReq(BaseReq req){finish();}
    public void onResp(BaseResp response){
        if(response instanceof SendAuth.Resp){SendAuth.Resp auth=(SendAuth.Resp)response;
            startActivity(new Intent(this,WechatAuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("wx_state",auth.state).putExtra("wx_code",auth.code).putExtra("wx_error",auth.errCode));}
        finish();
    }
    @Override protected void onDestroy(){if(api!=null)api.detach();super.onDestroy();}
}
