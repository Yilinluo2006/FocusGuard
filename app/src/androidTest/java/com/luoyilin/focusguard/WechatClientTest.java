package com.luoyilin.focusguard;
import android.content.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import org.junit.*;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Isolated app only: no real WeChat account or user session is accessed. */
public class WechatClientTest {
    private Context c;private final String state="s".repeat(43);
    @Before public void setup(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        new SessionManager(c).clearSession();WechatPending.clear(c);}
    @After public void cleanup(){if(c!=null&&c.getPackageName().endsWith(".setupprobe")){new SessionManager(c).clearSession();WechatPending.clear(c);}}
    private void begin(boolean binding){WechatData.Start value=new WechatData.Start();value.appId=BuildConfig.WECHAT_APP_ID;value.state=state;value.serverTime=1000;value.expiresAt=301000;
        WechatPending.save(c,value,binding,true,SystemClock.elapsedRealtime());}
    private void session(int owner,String token){new SessionManager(c).saveLoginSession(new com.google.gson.Gson().fromJson("{\"token\":\""+token+"\",\"userId\":"+owner+",\"username\":\"测试账号\",\"wechatVerified\":true}",LoginResponse.class));}
    @Test public void oneStateCanBeConsumedOnlyOnce(){begin(false);assertTrue(WechatPending.hasPending(c));assertNotNull(WechatPending.take(c,state));assertNull(WechatPending.take(c,state));}
    @Test public void foreignCallbackDoesNotConsumeTheRealRequest(){begin(false);assertNull(WechatPending.take(c,"x".repeat(43)));assertTrue(WechatPending.hasPending(c));assertNotNull(WechatPending.take(c,state));}
    @Test public void changedAccountCannotAcceptTheOldWechatCallback(){session(10,"fixture-original");begin(true);session(11,"fixture-other");assertFalse(WechatPending.hasPending(c));assertNull(WechatPending.take(c,state));assertEquals(11,new SessionManager(c).getUserId());}
    @Test public void expiryAndRebootInvalidatePendingAuthorization(){begin(false);c.getSharedPreferences("wechat_pending",0).edit().putLong("until",0).commit();assertNull(WechatPending.take(c,state));
        begin(false);c.getSharedPreferences("wechat_pending",0).edit().putInt("boot",-2).commit();assertFalse(WechatPending.hasPending(c));assertNull(WechatPending.take(c,state));}
    @Test public void successfulWechatSessionHasAUsableLoginLabel(){session(10,"fixture-original");assertTrue(new SessionManager(c).isWechatLinked());assertEquals("微信账号",new SessionManager(c).getLoginIdentifier());}
    @Test public void loginKeepsEmailPhoneAndWechatChoices(){try(ActivityScenario<android.app.Activity> scenario=ActivityScenario.launch(new Intent().setClassName(c.getPackageName(),"com.luoyilin.focusguard.LoginActivity"))){
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));onView(withText("手机号登录 / 注册")).check(matches(isDisplayed()));onView(withText("微信登录")).check(matches(isDisplayed()));}}
}
