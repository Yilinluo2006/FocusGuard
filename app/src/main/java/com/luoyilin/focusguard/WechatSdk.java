package com.luoyilin.focusguard;
import android.content.Context;
import com.tencent.mm.opensdk.openapi.*;
import com.tencent.mm.opensdk.utils.ILog;
public final class WechatSdk {
    private WechatSdk(){}
    public static IWXAPI create(Context c){
        // Do not let the SDK log authorization codes/state or provider payloads.
        com.tencent.mm.opensdk.utils.Log.setLogImpl(new ILog(){
            public void v(String tag,String message){}public void d(String tag,String message){}
            public void i(String tag,String message){}public void w(String tag,String message){}public void e(String tag,String message){}
        });
        return WXAPIFactory.createWXAPI(c,BuildConfig.WECHAT_APP_ID,true);
    }
}
