package com.luoyilin.focusguard;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepPlanRulesTest {
    private static final int MONDAY = 1;
    private static final int TUESDAY = 1 << 1;
    private static final int SATURDAY = 1 << 5;
    private static final int SUNDAY = 1 << 6;
    private static final int ALL_DAYS = 127;

    private static Map<String, Object> plan(boolean enabled, int minute, int duration,
                                            int weekdays, String... allowed) {
        Map<String, Object> values = new HashMap<>();
        values.put("daily", enabled);
        values.put("minute", minute);
        values.put("duration", duration);
        values.put("sleepWeekdays", weekdays);
        values.put("allowed", new HashSet<>(Arrays.asList(allowed)));
        return values;
    }

    @Test public void unchangedPlanIsNotRelaxation() {
        Map<String, Object> before = plan(true, 1410, 240, ALL_DAYS, "phone");
        assertFalse(SleepPlanRules.isRelaxation(before, new HashMap<>(before)));
    }

    @Test public void shorterDurationLosesControlledMinutes() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1410, 239, ALL_DAYS)));
    }

    @Test public void longerDurationKeepsAllControlledMinutes() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1410, 241, ALL_DAYS)));
    }

    @Test public void disablingAnExistingPlanIsRelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(false, 1410, 240, ALL_DAYS)));
    }

    @Test public void enablingPreviouslyDisabledPlanIsStrengthening() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(false, 1410, 240, ALL_DAYS),
                plan(true, 1410, 240, ALL_DAYS)));
    }

    @Test public void removingAWeekdayIsRelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, MONDAY | TUESDAY),
                plan(true, 1410, 240, MONDAY)));
    }

    @Test public void addingAWeekdayIsStrengthening() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, MONDAY),
                plan(true, 1410, 240, MONDAY | TUESDAY)));
    }

    @Test public void exchangingWeekdaysIsRelaxationEvenWithEqualDuration() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, MONDAY),
                plan(true, 1410, 240, TUESDAY)));
    }

    @Test public void earlierStartWithMatchingExtensionCoversTheOldPlan() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1380, 270, ALL_DAYS)));
    }

    @Test public void earlierStartWithSameDurationLosesTheOldEnd() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1380, 240, ALL_DAYS)));
    }

    @Test public void laterStartLosesTheOldBeginningDespiteLongerDuration() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1425, 300, ALL_DAYS)));
    }

    @Test public void oneMinuteMissingAtTheEndIsStillRelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, SUNDAY),
                plan(true, 1409, 240, SUNDAY)));
    }

    @Test public void exactCoverageFromAnEarlierStartIsAllowedAcrossMidnight() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, MONDAY),
                plan(true, 1380, 270, MONDAY)));
    }

    @Test public void sundayOvernightCoverageWrapsToMonday() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, SUNDAY),
                plan(true, 1380, 270, SUNDAY)));
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, SUNDAY),
                plan(true, 0, 240, MONDAY)));
    }

    @Test public void previousDayOvernightPlanCanCoverAnEarlyMorningPlan() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 30, 60, TUESDAY),
                plan(true, 1410, 120, MONDAY)));
    }

    @Test public void previousSundayOvernightPlanCanCoverMondayMorning() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 30, 60, MONDAY),
                plan(true, 1410, 120, SUNDAY)));
    }

    @Test public void previousSaturdayOvernightPlanCanCoverSundayMorning() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 30, 60, SUNDAY),
                plan(true, 1410, 120, SATURDAY)));
    }

    @Test public void exchangingSundayAndMondayOvernightPlansLosesCoverage() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, SUNDAY),
                plan(true, 1410, 240, MONDAY)));
    }

    @Test public void addingWhitelistAppIsRelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS, "phone"),
                plan(true, 1410, 240, ALL_DAYS, "phone", "browser")));
    }

    @Test public void removingWhitelistAppIsStrengthening() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS, "phone", "browser"),
                plan(true, 1410, 240, ALL_DAYS, "phone")));
    }

    @Test public void exchangingWhitelistAppsStillIntroducesARelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS, "phone"),
                plan(true, 1410, 240, ALL_DAYS, "browser")));
    }

    @Test public void strongerScheduleDoesNotCancelWhitelistRelaxation() {
        assertTrue(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, MONDAY),
                plan(true, 1380, 300, ALL_DAYS, "browser")));
    }

    @Test public void disabledOldPlanAllowsAnyScheduleAndWhitelistChanges() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(false, 1410, 240, ALL_DAYS, "phone"),
                plan(false, 60, 1, MONDAY, "browser")));
        assertFalse(SleepPlanRules.isRelaxation(
                plan(false, 1410, 240, ALL_DAYS),
                plan(true, 60, 1, MONDAY, "browser")));
    }

    @Test public void oldPlanWithoutAnyWeekdayHasNoCoverageToRelax() {
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, 0),
                plan(false, 60, 1, MONDAY)));
    }

    @Test public void legacyEnabledPlanDefaultsToAllDaysAndOriginalTime() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("daily", true);
        assertFalse(SleepPlanRules.isRelaxation(
                legacy, plan(true, 1350, 180, ALL_DAYS)));
        assertTrue(SleepPlanRules.isRelaxation(
                legacy, plan(true, 1350, 180, MONDAY)));
        assertTrue(SleepPlanRules.isRelaxation(
                legacy, plan(true, 1350, 179, ALL_DAYS)));
    }

    @Test public void missingDailyDefaultsToDisabled() {
        assertFalse(SleepPlanRules.isRelaxation(
                Collections.emptyMap(), plan(true, 1410, 240, ALL_DAYS, "browser")));
    }

    @Test public void missingWhitelistDefaultsToEmpty() {
        Map<String, Object> before = plan(true, 1410, 240, ALL_DAYS);
        before.remove("allowed");
        assertFalse(SleepPlanRules.isRelaxation(
                before, plan(true, 1410, 240, ALL_DAYS)));
        assertTrue(SleepPlanRules.isRelaxation(
                before, plan(true, 1410, 240, ALL_DAYS, "browser")));
    }

    @Test public void autumnClockRollbackMakesNominalExtensionLoseRealCoverage() {
        ZoneId zone = ZoneId.of("America/New_York");
        long now = LocalDate.of(2026, 10, 9).atStartOfDay(zone).toInstant().toEpochMilli();
        Map<String, Object> before = plan(true, 180, 180, SUNDAY);
        Map<String, Object> after = plan(true, 60, 300, SUNDAY);
        assertFalse("The nominal weekly minutes alone appear to preserve 03:00-06:00",
                SleepPlanRules.isRelaxation(before, after));
        assertTrue("The repeated hour makes 300 elapsed minutes end before the old plan on DST rollback",
                SleepPlanRules.isRelaxation(before, after, zone, now));
    }

    @Test public void extendingEarlierInShanghaiPreservesRealOvernightCoverage() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long now = LocalDate.of(2026, 10, 9).atStartOfDay(zone).toInstant().toEpochMilli();
        assertFalse(SleepPlanRules.isRelaxation(
                plan(true, 1410, 240, ALL_DAYS),
                plan(true, 1380, 270, ALL_DAYS), zone, now));
    }
}
