package com.luoyilin.focusguard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordedPageRuleTest {
    private RecordedPageRule rule(boolean enabled) {
        return new RecordedPageRule("1", "视频页", "app.target", "MainActivity", false, enabled,
                Arrays.asList(new RecordedPageRule.Anchor("app.target:id/tab", "视频", "android.widget.TextView", false)));
    }
    @Test public void unrelatedPackagesAndWindowClassesCannotMatch() {
        assertTrue(rule(true).page("app.target", "MainActivity"));
        assertFalse(rule(true).page("app.other", "MainActivity"));
        assertFalse(rule(true).page("app.target", "SettingsActivity"));
    }
    @Test public void disabledAndEmptyRulesCannotMatch() {
        assertFalse(rule(false).page("app.target", "MainActivity"));
        assertFalse(new RecordedPageRule("1", "空", "app.target", "MainActivity", false, true,
                Collections.emptyList()).page("app.target", "MainActivity"));
    }
    @Test public void chosenTextAndControlTypeAreExact() {
        RecordedPageRule.Anchor a = rule(true).anchors.get(0);
        assertTrue(a.matches(a.viewId, "视频", a.widget));
        assertFalse(a.matches("app.target:id/other", "视频", a.widget));
        assertFalse(a.matches(a.viewId, "视频 12", a.widget));
        assertFalse(a.matches(a.viewId, "视频", "android.widget.Button"));
    }
    @Test public void idlessFeatureStillRequiresExactTextAndWidget() {
        RecordedPageRule.Anchor a = new RecordedPageRule.Anchor("", "要卸载奶蛙ToDo吗？", "android.widget.TextView", false);
        assertTrue(a.matches("", a.text, a.widget));
        assertFalse(a.matches("", "要卸载其他应用吗？", a.widget));
    }
    @Test public void classOnlyCannotSilentlyCoverOtherInterfacesOrSharedSystemContainers() {
        RecordedPageRule r = new RecordedPageRule("class", "页面", "app.target", "app.target.VideoActivity", false, true,
                Collections.emptyList(), true, false);
        assertTrue(r.page("app.target", "app.target.VideoActivity"));
        assertFalse(r.page("app.target", "app.target.MainActivity"));
        assertFalse(r.page("app.other", "app.target.VideoActivity"));
        RecordedPageRule system = new RecordedPageRule("system", "页面", "com.android.settings", "com.android.settings.SubSettings", true, true,
                Collections.emptyList(), true, true);
        assertFalse(system.canUseClassOnly()); assertFalse(system.page(system.pkg, system.type));
    }
    @Test public void automaticSuggestionExcludesDynamicNumbersAndLowConfidenceContent() {
        RecordedPageRule.Anchor selected = new RecordedPageRule.Anchor("", "视频", "TextView", false, true, 90);
        RecordedPageRule.Anchor heading = new RecordedPageRule.Anchor("", "聊天", "TextView", false, false, 70);
        List<RecordedPageRule.Anchor> proposed = RecordedPageEnrollment.suggest(Arrays.asList(
                new RecordedPageRule.Anchor("", "聊天218", "TextView", false, false, 70),
                new RecordedPageRule.Anchor("", "个人消息", "TextView", false, false, 0), heading, selected));
        assertEquals(Arrays.asList(selected, heading), proposed);
    }
}
