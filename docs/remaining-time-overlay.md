# Remaining-time overlay

The accessibility service displays a translucent, touch-through timer for a
selected application while its daily allowance is positive. The small window
uses TYPE_ACCESSIBILITY_OVERLAY and requests no additional permissions.

- Usage events remain the authoritative source, refreshed by the existing check.
- Between checks, a monotonic clock updates the display once per second.
- The next enforcement check is capped by the remaining allowance.
- Leaving for an unrestricted app or a FocusGuard activity, reaching the limit,
  or service teardown removes the timer and cancels its display callbacks.
- Display refresh hides the timer when the screen is off or keyguard is active.
- This change does not establish compatibility with realme safety detection.

Verification on 2026-09-16:

- Debug APK assembled and installed without clearing app data.
- RemainingTimeFormatTest: all three tests passed using JUnitCore directly.
- Gradle test runner failed to load GradleWorkerMain on this machine; this is
  separate from the direct JUnit test result.
- Device visual/touch/lifecycle verification pending: phone was locked.
- Test allowance has not been changed.

The checkpoint also contains earlier uncommitted protection and synchronization
changes required by the current service. Local IDE settings, machine-specific
Gradle properties and build logs are not part of the checkpoint.
