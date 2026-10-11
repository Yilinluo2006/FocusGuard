package com.luoyilin.focusguard;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
public class ClockSourceTest {
    @Test public void onlineTimeSourceCanVerifyWithoutAnAccountLogin()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("focusguard_session",0).edit().clear().commit();
        new android.util.AtomicFile(new java.io.File(c.getNoBackupFilesDir(),"protection-clock.properties")).delete();
        java.lang.reflect.Field field=ProtectionClock.class.getDeclaredField("cached");field.setAccessible(true);field.set(null,null);
        ProtectionClock.initialize(c);
        for(int i=0;i<60&&!ProtectionClock.read(c).verified;i++)Thread.sleep(200);
        assertTrue("Online time source did not provide a usable anchor",ProtectionClock.read(c).verified);
    }
}
