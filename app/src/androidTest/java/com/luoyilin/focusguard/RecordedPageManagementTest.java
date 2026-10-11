package com.luoyilin.focusguard;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RecordedPageManagementTest {
    private Context c;
    @Before public void setup() {
        c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        for (RecordedPageRule r : RecordedPageStore.read(c)) RecordedPageStore.delete(c, r.id);
        assertTrue(PageMonitoringStore.authorize(c, true));
        assertTrue(RecordedPageStore.add(c, new RecordedPageRule("manage", "管理测试页", c.getPackageName(), "Page", false, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "固定导航", "android.widget.TextView", false)))));
    }
    @After public void cleanup() {
        if (c != null && c.getPackageName().endsWith(".setupprobe")) {
            RecordedPageStore.delete(c, "manage"); PageMonitoringStore.revoke(c);
        }
    }
    @Test public void managementEntryOpensSavedRulesAndDetailsCanRename() {
        try (ActivityScenario<PageMonitoringActivity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
            s.onActivity(a -> {
                View decor = a.getWindow().getDecorView();
                TextView entry = find(decor, "已录入页面 · 查看与管理"); assertNotNull(entry); entry.performClick();
                assertNotNull(find(decor, "管理测试页"));
                find(decor, "查看特征与编辑").performClick(); assertNotNull(find(decor, "• 固定导航"));
                EditText name = edit(decor); assertNotNull(name); name.setText("改名后的页面");
                find(decor, "保存名称").performClick();
                assertEquals("改名后的页面", RecordedPageStore.read(c).get(0).name);
            });
            s.recreate();
            s.onActivity(a -> assertNotNull(find(a.getWindow().getDecorView(), "改名后的页面")));
        }
    }
    @Test public void savedRuleLinkOpensListAndPausedRulesRemainManageable() {
        assertTrue(RecordedPageStore.enable(c, "manage", false));
        assertTrue(PageMonitoringStore.revoke(c));
        try (ActivityScenario<PageMonitoringActivity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class).putExtra("showRecords", true))) {
            s.onActivity(a -> {
                assertNotNull(find(a.getWindow().getDecorView(), "管理测试页"));
                assertNotNull(find(a.getWindow().getDecorView(), "总开关已关闭，以下规则暂不监控"));
            });
        }
    }
    private TextView find(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView)v).getText())) return (TextView)v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            TextView found = find(((ViewGroup)v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private EditText edit(View v) {
        if (v instanceof EditText) return (EditText)v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            EditText found = edit(((ViewGroup)v).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
