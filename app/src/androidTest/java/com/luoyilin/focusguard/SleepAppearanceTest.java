package com.luoyilin.focusguard;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.time.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class SleepAppearanceTest {
    private Context c;
    @Before public void setup(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertTrue(Appearance.setMode(c,1)));}
    @After public void cleanup(){if(c!=null&&c.getPackageName().endsWith(".setupprobe")){c.getSharedPreferences("focus_mode",0).edit().clear().commit();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->Appearance.setMode(c,0));}}
    private void sleep(){LocalTime time=Instant.ofEpochMilli(ProtectionClock.now(c)).atZone(ProtectionClock.zone(c)).toLocalTime().minusMinutes(1);
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",true).putInt("minute",time.getHour()*60+time.getMinute()).putInt("duration",30).putInt(FocusSchedule.WEEKDAYS_KEY,127).commit();}
    @Test public void sleepMakesOverlaysDarkWithoutReplacingLightPreference(){sleep();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Appearance.refreshSleep(c);assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode());});
        assertTrue(Appearance.dark(c));assertEquals(1,Appearance.read(c));
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,Appearance.overlayContext(c).getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Appearance.refreshSleep(c);assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode());});
        assertFalse(Appearance.dark(c));assertEquals(1,Appearance.read(c));}
    @Test public void globalTemporaryUseRemainsDarkAndManualLightCannotOverride(){sleep();assertTrue(SleepTemporaryUse.request(c).getBoolean("saved"));
        assertTrue(Appearance.dark(c));InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertFalse(Appearance.setMode(c,1)));assertEquals(1,Appearance.read(c));}
}
