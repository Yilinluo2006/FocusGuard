package com.luoyilin.focusguard;

import java.util.*;

/** Automatically propose only stable navigation/title features for explicit confirmation. */
final class RecordedPageEnrollment {
    static List<RecordedPageRule.Anchor> suggest(List<RecordedPageRule.Anchor> candidates) {
        List<RecordedPageRule.Anchor> ranked = new ArrayList<>();
        for (RecordedPageRule.Anchor a : candidates) {
            if (a.priority >= 60 && a.text.length() >= 2 && a.text.length() <= 40
                    && !a.text.matches(".*[0-9０-９].*")) ranked.add(a);
        }
        ranked.sort(Comparator.comparingInt((RecordedPageRule.Anchor a) -> a.priority).reversed());
        return new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size())));
    }
}
