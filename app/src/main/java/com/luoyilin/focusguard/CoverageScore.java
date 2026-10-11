package com.luoyilin.focusguard;

import java.util.List;

/** Trial figures only; never changes streak settlement. */
final class CoverageScore {
    static final class Check {
        final long at;
        final boolean healthy;

        Check(long at, boolean healthy) {
            this.at = at;
            this.healthy = healthy;
        }
    }

    final long screenMillis;
    final long protectedMillis;
    final long unprotectedMillis;
    final long unconfirmedMillis;
    final long unknownScreenStateMillis;
    final boolean inconsistent;
    final int unlocks;
    final int checksReceived;
    final int checksHealthy;

    private CoverageScore(long screenMillis, long protectedMillis, long unprotectedMillis,
                          long unconfirmedMillis, long unknownScreenStateMillis, boolean inconsistent,
                          int unlocks, int checksReceived, int checksHealthy) {
        this.screenMillis = screenMillis;
        this.protectedMillis = protectedMillis;
        this.unprotectedMillis = unprotectedMillis;
        this.unconfirmedMillis = unconfirmedMillis;
        this.unknownScreenStateMillis = unknownScreenStateMillis;
        this.inconsistent = inconsistent;
        this.unlocks = unlocks;
        this.checksReceived = checksReceived;
        this.checksHealthy = checksHealthy;
    }

    static CoverageScore calculate(ScreenUsageLedger.Result usage, long protectedMillis,
                                   long unprotectedMillis, List<Check> checks) {
        long total = usage.unlockedMillis;
        boolean inconsistent = protectedMillis + unprotectedMillis > total + 60_000L;
        long yes = Math.min(total, Math.max(0, protectedMillis));
        long no = Math.min(total - yes, Math.max(0, unprotectedMillis));
        int received = 0, healthy = 0;
        boolean[] used = new boolean[checks.size()];
        for (long unlockAt : usage.sessionStarts) {
            for (int i = 0; i < checks.size(); i++) {
                Check check = checks.get(i);
                if (!used[i] && check.at >= unlockAt - 5_000L
                        && check.at <= unlockAt + 30_000L) {
                    used[i] = true;
                    received++;
                    if (check.healthy) healthy++;
                    break;
                }
            }
        }
        return new CoverageScore(total, yes, no, total - yes - no,
                usage.uncertainStateMillis, inconsistent, usage.sessionStarts.size(), received, healthy);
    }

    boolean hasRate() {
        return screenMillis > 0 && unknownScreenStateMillis == 0 && !inconsistent;
    }

    double percent() {
        return screenMillis == 0 ? 0 : protectedMillis * 100.0 / screenMillis;
    }
}
