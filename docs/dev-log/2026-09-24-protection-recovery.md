# Protection recovery

## Observed on RMX3888

- Accessibility was bound. Focus overlay windows were created and drawn, but SurfaceFlinger reported the accessibility overlay's parent hierarchy as hidden. The underlying OEM trigger is not confirmed.
- Launching over-limit Valorant initially sent HOME successfully. `blockInProgress` previously stayed true until the invisible prompt was dismissed, preventing subsequent blocking.
- The 12-hour streak threshold is not an enforcement cutoff.

## Changes

- Release the block transition after 800 ms independently of prompt dismissal; recheck a launch received during the transition.
- Clear stale overlay/transition state on accessibility reconnection.
- Recover missed foreground events periodically while idle, retaining UsageSession's active-session check.
- Offer an explicit SYSTEM_ALERT_WINDOW permission entry. With permission, use application overlays and their application-context WindowManager; otherwise retain accessibility overlays.
- Apply the same window selection to focus, countdown, blocking prompts and sleep reminders.
- Wallpaper placement was left unchanged for this bug-fix pass.

## Verification

- Debug APK built and installed successfully.
- Three repeated over-limit Valorant launches returned to the launcher.
- After the user enabled overlay permission, the focus countdown and blocking prompt were visually confirmed on the phone. The short focus session completed naturally; no early termination or sleep-plan edits.
- Existing 36 JUnit tests passed via direct JUnitCore. Gradle's test executor failed to start in the local environment.
- A live under-limit remaining-time countdown and long-duration OEM stability still require verification; Valorant's current daily allowance is already exhausted.
