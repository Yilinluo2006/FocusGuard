# Coverage sampling trial

Independent `no_backup/coverage.properties` ledger, guarded by a cross-process
file lock and AtomicFile. Does not edit streaks, quotas or sleep plans.
Intervals split at local 04:00. Observation starts on first sample; no backfill.

Service health samples every ten seconds; the protection service and visible
home screen record unprotected observations when accessibility is unhealthy.
Both endpoints must agree on unlocked and protected status, the same process
session and boot, and an interval <=30 seconds with consistent wall/elapsed time.
Otherwise the interval is unknown, not protected or unprotected. Duplicate and
out-of-order samples are ignored. Locked intervals are excluded from the ratio.
All values describe observations, not proof of uninterrupted enforcement.

The trial percentage now uses system `UsageEvents` screen/keyguard transitions
to reconstruct unlocked interactive time since 04:00. The numerator remains
confirmed protected samples. Confirmed unprotected and unmatched unlocked time
remain in the denominator; long screen-off/Doze gaps are excluded. Missing
system state, missing usage access, or a significant mismatch between sources
withholds the percentage instead of reporting a misleading 100%.

The foreground protection service checks accessibility, usage access and
overlay permission after `ACTION_USER_PRESENT`. It records check outcomes for
the separate unlock-check diagnostic. The total number of sessions comes from
system events, so an unlock missed while the process was absent is not counted
as a successful check. Broadcast delivery is not guaranteed while the service
is dead; the next report reconciles events kept by the system.

The 12-hour streak rule and historical counters are unchanged. Trial coverage
does not award streak credit or establish a 95% pass rule. System events are
only retained for a few days; Android 8 lacks these screen/keyguard events.
Clock rollback observations are withheld until time catches up. Cross-timezone
travel and physical multi-day validation still need review.

Validation: unit tests cover 04:00 splitting, lock/permission state transitions,
long gaps, process restart, duplicate samples, screen-event reconstruction and
missing unlock checks. Physical two-day validation across devices is required
before changing streak rules.
