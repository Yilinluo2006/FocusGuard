package com.luoyilin.focusguard;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class RecordedPageStoreTest {
    private Context c;
    @Before public void setup() {
        c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        PageMonitoringStore.revoke(c);
        for (RecordedPageRule r : RecordedPageStore.read(c)) assertTrue(RecordedPageStore.delete(c, r.id));
    }
    @After public void cleanup() {
        if (c == null || !c.getPackageName().endsWith(".setupprobe")) return;
        PageMonitoringStore.revoke(c);
        for (RecordedPageRule r : RecordedPageStore.read(c)) RecordedPageStore.delete(c, r.id);
    }
    private RecordedPageRule rule() {
        return new RecordedPageRule("probe-rule", "现场视频页", "probe.app", "SomeActivity", false, true,
                Arrays.asList(new RecordedPageRule.Anchor("probe.app:id/title", "视频", "android.widget.TextView", false)));
    }
    @Test public void unconsentedRecordingFailsAndNewScopeRequiresVersionThree() {
        assertEquals(4, PageMonitoringStore.VERSION);
        assertFalse(RecordedPageStore.add(c, rule())); assertTrue(RecordedPageStore.read(c).isEmpty());
    }
    @Test public void chosenFeaturesPersistToggleDeleteAndDoNotChangePlans() {
        Map<String, ?> plans = new HashMap<>(new FocusPreferences(c).getAll());
        assertTrue(PageMonitoringStore.authorize(c, true)); assertTrue(RecordedPageStore.add(c, rule()));
        RecordedPageRule read = RecordedPageStore.read(c).get(0);
        assertEquals("现场视频页", read.name); assertEquals("视频", read.anchors.get(0).text);
        assertTrue(RecordedPageStore.enable(c, read.id, false)); assertFalse(RecordedPageStore.read(c).get(0).enabled);
        assertTrue(RecordedPageStore.enable(c, read.id, true)); assertTrue(RecordedPageStore.read(c).get(0).enabled);
        assertTrue(PageMonitoringStore.revoke(c)); assertFalse(PageMonitoringStore.read(c).enabled);
        assertFalse(RecordedPageStore.add(c, rule())); assertTrue(RecordedPageStore.delete(c, read.id));
        assertTrue(RecordedPageStore.read(c).isEmpty()); assertEquals(plans, new FocusPreferences(c).getAll());
    }
    @Test public void systemAndDialerComponentsCanNeverBecomeRestrictedOrdinaryPages() {
        assertTrue(RecordedPageController.system(c, "com.android.settings"));
        assertTrue(RecordedPageController.system(c, "missing.system.component"));
        assertFalse(RecordedPageController.system(c, c.getPackageName()));
    }
    @Test public void replacingKeepsIdentityNameAndPausedStateAndCancelNeedsNoWrites() {
        assertTrue(PageMonitoringStore.authorize(c, true)); assertTrue(RecordedPageStore.add(c, rule()));
        assertTrue(RecordedPageStore.rename(c, rule().id, "我的视频页"));
        assertTrue(RecordedPageStore.enable(c, rule().id, false));
        RecordedPageRule replacement = new RecordedPageRule("replacement", "新名称", rule().pkg, "NewPage", false, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "新标题", "android.widget.TextView", false)));
        assertNull(RecordedPageStore.save(c, replacement, rule().id));
        RecordedPageRule saved = RecordedPageStore.read(c).get(0);
        assertEquals(1, RecordedPageStore.read(c).size()); assertEquals(rule().id, saved.id);
        assertEquals("我的视频页", saved.name); assertFalse(saved.enabled); assertEquals("新标题", saved.anchors.get(0).text);
        assertNotNull(RecordedPageStore.save(c, replacement, "deleted-id"));
        assertEquals(1, RecordedPageStore.read(c).size());
    }
    @Test public void corruptFileIsReportedAndCannotBeOverwrittenAsEmpty() throws Exception {
        assertTrue(PageMonitoringStore.authorize(c, true));
        java.io.File file = new java.io.File(c.getNoBackupFilesDir(), "recorded-pages.json");
        byte[] before = "broken-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(file.toPath(), before);
        try {
            assertNotNull(RecordedPageStore.readResult(c).error);
            assertNotNull(RecordedPageStore.save(c, rule(), null));
            assertArrayEquals(before, java.nio.file.Files.readAllBytes(file.toPath()));
        } finally { java.nio.file.Files.write(file.toPath(), "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    @Test public void invalidFeaturesDoNotPoisonExistingRecords() {
        assertTrue(PageMonitoringStore.authorize(c, true)); assertTrue(RecordedPageStore.add(c, rule()));
        RecordedPageRule invalid = new RecordedPageRule("bad", "bad", "probe.app", "Page", false, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "", "", false)));
        assertNotNull(RecordedPageStore.save(c, invalid, null));
        assertNotNull(RecordedPageStore.save(c, rule(), null));
        assertNull(RecordedPageStore.readResult(c).error); assertEquals(1, RecordedPageStore.read(c).size());
    }
    @Test public void classOnlyAndSystemActionPersistAndLegacyRulesKeepReminderBehavior() {
        assertTrue(PageMonitoringStore.authorize(c, true));
        RecordedPageRule page = new RecordedPageRule("class-only", "视频页", "probe.app", "probe.app.VideoActivity", false, true,
                Collections.emptyList(), true, false);
        assertNull(RecordedPageStore.save(c, page, null));
        assertTrue(RecordedPageStore.read(c).get(0).classOnly);
        assertTrue(RecordedPageStore.rename(c, page.id, "整个视频界面"));
        assertTrue(RecordedPageStore.enable(c, page.id, false));
        assertTrue(RecordedPageStore.read(c).get(0).classOnly); assertFalse(RecordedPageStore.read(c).get(0).enabled);
        RecordedPageRule system = new RecordedPageRule("system-action", "系统页", "com.android.settings", "SubSettings", true, true,
                Arrays.asList(new RecordedPageRule.Anchor("", "页面标题", "TextView", false, true, 90)), false, true);
        assertTrue(RecordedPageStore.add(c, system));
        assertTrue(RecordedPageStore.read(c).get(1).restrictSystem);
        assertTrue(RecordedPageStore.read(c).get(1).anchors.get(0).selected);
        RecordedPageRule invalid = new RecordedPageRule("bad-system", "系统页", "com.android.settings", "com.android.settings.SubSettings", true, true,
                Collections.emptyList(), true, true);
        assertNotNull(RecordedPageStore.save(c, invalid, null));
        assertTrue(RecordedPageStore.add(c, rule())); assertFalse(RecordedPageStore.read(c).get(2).restrictSystem);
    }
}
