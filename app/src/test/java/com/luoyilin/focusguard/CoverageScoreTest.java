package com.luoyilin.focusguard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class CoverageScoreTest {
    @Test public void unknownUnlockedTimeStaysInDenominator() {
        ScreenUsageLedger.Result usage = new ScreenUsageLedger.Result(
                10 * 60_000L, 0, Collections.emptyList());
        CoverageScore score = CoverageScore.calculate(usage, 9 * 60_000L, 0, Collections.emptyList());
        assertTrue(score.hasRate());
        assertEquals(90.0, score.percent(), 0.001);
        assertEquals(60_000L, score.unconfirmedMillis);
    }

    @Test public void missedUnlockIsNotCreditedAsHealthy() {
        ScreenUsageLedger.Result usage = new ScreenUsageLedger.Result(
                10_000, 0, Arrays.asList(1000L, 5000L));
        CoverageScore score = CoverageScore.calculate(usage, 10_000, 0,
                Arrays.asList(new CoverageScore.Check(2500, true)));
        assertEquals(2, score.unlocks);
        assertEquals(1, score.checksReceived);
        assertEquals(1, score.checksHealthy);
    }

    @Test public void missingSystemStateAndSourceDisagreementHideRate() {
        ScreenUsageLedger.Result uncertain = new ScreenUsageLedger.Result(10_000, 1000,
                Collections.emptyList());
        assertFalse(CoverageScore.calculate(uncertain, 10_000, 0, Collections.emptyList()).hasRate());
        ScreenUsageLedger.Result certain = new ScreenUsageLedger.Result(10_000, 0,
                Collections.emptyList());
        assertFalse(CoverageScore.calculate(certain, 80_000, 0, Collections.emptyList()).hasRate());
        assertFalse(CoverageScore.calculate(new ScreenUsageLedger.Result(0, 0,
                Collections.emptyList()), 0, 0, Collections.emptyList()).hasRate());
    }
}
