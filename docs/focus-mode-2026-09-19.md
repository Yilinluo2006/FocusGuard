# Focus mode preview

## Implementation

- Home entry: focus and sleep plan. Manual duration: 1-720 minutes.
- Daily local-time start with 1-720 minute duration; disabled by default.
- FocusModeActivity runs in :guard alongside accessibility. FocusModeStore is
  local-only and does not depend on backend login/synchronization.
- Accessibility overlay covers non-allowed apps and the launcher. User-selected
  apps, dialer/call UI, settings, and permission prompts remain accessible.
- Focus whitelist permits opening an app but does not bypass its daily usage limit.
  The remaining-time check continues during manual focus and scheduled sleep;
  temporary emergency releases are not honored until focus ends.
- App usage quotas and emergency grants use the local 04:00-to-04:00 period;
  crossing midnight does not refresh a restricted app's allowance.
- Early finish needs two taps, clears manual focus, and skips the current daily
  occurrence without disabling tomorrow's plan.
- End timestamps survive process recreation. Authorization still must be present.
- Only last completion/interruption timestamps are stored; these are not a streak
  ledger or proof of uninterrupted enforcement. Current device timestamps include QA.

## Verified

- assembleDebug succeeded and APK installed without clearing app data.
- JUnitCore: 9 tests passed (schedule, usage sessions, timer formatting).
- Realme: manual start, visible countdown, Chrome whitelist launch through OEM
  one-time confirmation, overlay returning on Home, early finish, one-minute expiry.
- Accessibility remained bound; test focus ended and temporary whitelist cleared.
- Daily schedule left disabled on device.

## Limits / follow-up tests

- The whitelist-limit and 04:00 changes build and pass 46 Java tests. This build
  has not been installed for an uninterrupted on-device 04:00 rollover test.

- This is a reversible focus aid, not device-owner kiosk mode or uninstall protection.
- Settings and emergency exit intentionally remain available.
- Timer uses wall time; manual clock changes can change the effective duration.
- Handler checks while the service runs; no exact alarm or wake lock. Doze may delay
  execution until wake-up. No guarantee under force-stop or revoked accessibility.
- Overnight, reboot, incoming calls, OEM floating windows, and multiple devices still
  require acceptance testing. Cross-midnight boundaries have unit coverage only.
- Lint could not run offline: lint-gradle:32.2.1 is not cached.
