package com.luoyilin.focusguard;

import android.content.Context;
import android.view.View;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Isolated probe only: never clear or synthesize the user's real app data. */
public class ComplianceAndroidTest {
    private Context c;
    @Before public void reset() throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        c.getSharedPreferences("focus_mode",0).edit().clear().commit();
        c.getSharedPreferences("device_sync",0).edit().clear().commit();
        c.getSharedPreferences("focus_guard_prefs",0).edit().clear().commit();LimitSnapshot.publish(c);
        StreakStore.access(c,p->{p.clear();ComplianceLedger.activate(p,System.currentTimeMillis(),ZoneId.systemDefault());});
    }
    @Test public void widgetShowsStatusAndFiveDaysWithoutAnHourlyTarget()throws Exception{
        Properties p=StreakStore.snapshot(c);StreakLedger.put(p,"streak",7);
        RemoteViews remote=new RemoteViews(c.getPackageName(),R.layout.widget_streak);StreakWidget.populateWeek(c,remote,p);
        View view=remote.apply(c,new LinearLayout(c));
        assertEquals(View.GONE,view.findViewById(R.id.widgetProgress).getVisibility());
        assertEquals("连续坚持 7 天",((TextView)view.findViewById(R.id.widgetDays)).getText().toString());
        assertFalse(((TextView)view.findViewById(R.id.widgetToday)).getText().toString().contains("12小时"));
        assertEquals(5,((LinearLayout)view.findViewById(R.id.widgetWeek)).getChildCount());
    }
    @Test public void cancelingAFutureSleepPlanRemovesItsPendingObligation()throws Exception{
        long now=System.currentTimeMillis();LocalTime time=Instant.ofEpochMilli(now+30*60000).atZone(ZoneId.systemDefault()).toLocalTime();
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",true).putInt("minute",time.getHour()*60+time.getMinute())
                .putInt("duration",60).putInt(FocusSchedule.WEEKDAYS_KEY,127).commit();
        ComplianceAudit.refresh(c);Properties before=StreakStore.snapshot(c);
        assertTrue(before.stringPropertyNames().stream().anyMatch(k->k.startsWith("c.sleep.")));
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",false).commit();ComplianceAudit.refresh(c);
        Properties after=StreakStore.snapshot(c);
        assertFalse(after.stringPropertyNames().stream().anyMatch(k->k.startsWith("c.sleep.")||k.startsWith("c.rule.")&&k.contains("sleep:")));
    }
    @Test public void localSleepGrantHasAtomicHistoryAndDoesNotBreakTheDay()throws Exception{
        LocalTime time=LocalTime.now().minusMinutes(1);
        c.getSharedPreferences("focus_mode",0).edit().putBoolean("daily",true).putInt("minute",time.getHour()*60+time.getMinute())
                .putInt("duration",60).putInt(FocusSchedule.WEEKDAYS_KEY,127).commit();
        android.os.Bundle state=SleepTemporaryUse.request(c);assertTrue(state.getBoolean("saved"));
        JSONArray history=new JSONArray(c.getSharedPreferences("focus_mode",0).getString("sleepTemporaryHistory","[]"));
        assertEquals(1,history.length());JSONObject grant=history.getJSONObject(0);
        assertEquals(state.getLong("start"),grant.getLong("start"));assertEquals(120000,grant.getLong("until")-grant.getLong("from"));
        long d=StreakLedger.day(System.currentTimeMillis(),ZoneId.systemDefault());assertFalse(StreakStore.snapshot(c).containsKey("broken."+d));
    }
    @Test public void newRuleResultsSurvivePortableBackupWithNoFakeProtectionHours()throws Exception{
        long day=StreakLedger.day(System.currentTimeMillis(),ZoneId.systemDefault())-1;
        StreakStore.access(c,p->{p.setProperty("result."+day,"完成");p.setProperty("ruleVersion."+day,"2");});
        JSONObject backup=CloudBackupData.validate(CloudBackupData.collect(c));
        assertEquals("2",backup.getJSONObject("streak").getString("ruleVersion."+day));
        assertEquals("完成",backup.getJSONObject("streak").getString("result."+day));
        assertFalse(backup.getJSONObject("streak").has("ms."+day));
    }
}
