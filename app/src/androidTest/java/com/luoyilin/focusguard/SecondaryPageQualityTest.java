package com.luoyilin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileOutputStream;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Visual QA runs only in the isolated probe, never on the user's account. */
public class SecondaryPageQualityTest {
    private Context context;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".setupprobe"));
        context.getSharedPreferences("focus_mode",0).edit().clear().commit();
    }
    @After public void cleanup() {
        if(context != null && context.getPackageName().endsWith(".setupprobe"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Appearance.setMode(context,0));
    }
    @Test public void lightScreensHaveDarkSystemIconsAndFixedNavigation() throws Exception { inspect(1); }
    @Test public void darkScreensHaveLightSystemIconsAndFixedNavigation() throws Exception { inspect(2); }
    private void inspect(int mode) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(Appearance.setMode(context,mode)));
        for(Class<?> type : new Class<?>[]{WallpaperGuardActivity.class, DeviceSyncActivity.class,
                ProtectionDashboardActivity.class, ProtectionRecoveryActivity.class,
                PermissionGuideActivity.class, LoginActivity.class, RegisterActivity.class,
                ForgotPasswordActivity.class, EmailVerificationActivity.class, PhoneAuthActivity.class}) {
            try(ActivityScenario<Activity> scenario = ActivityScenario.launch(new Intent(context,type))) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    View decor=activity.getWindow().getDecorView();
                    Toolbar toolbar=findToolbar(decor);
                    assertNotNull(type.getSimpleName(),toolbar);
                    assertTrue(toolbar.isShown());
                    assertTrue(toolbar.getHeight() >= PageUi.dp(activity,48));
                    androidx.core.view.WindowInsetsControllerCompat bars=androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(),decor);
                    assertEquals(type.getSimpleName(),mode==1,bars.isAppearanceLightStatusBars());
                    assertEquals(type.getSimpleName(),mode==1,bars.isAppearanceLightNavigationBars());
                });
                Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(shot);
                try(FileOutputStream out=context.openFileOutput("quality-"+mode+"-"+type.getSimpleName()+".png",0)) {
                    shot.compress(Bitmap.CompressFormat.PNG,100,out);
                }
                shot.recycle();
            }
        }
    }
    private Toolbar findToolbar(View view) {
        if(view instanceof Toolbar)return (Toolbar)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Toolbar found=findToolbar(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
}
