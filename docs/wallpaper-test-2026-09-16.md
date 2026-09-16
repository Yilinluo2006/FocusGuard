# Wallpaper experiment: 2026-09-16

Device: realme RMX3888, Android 16, serial 470e575c.
No install, IDE restart, or force-stop during this test.
FocusWallpaperService selected using the system wallpaper picker.
Guard PID before and after all three recents swipe tests: 13180.

| Time | Target | Outcome | Logged decision time (not end-to-end latency) |
| --- | --- | --- | --- |
| 23:17:00 | com.max.xiaoheihe | HOME accepted; launcher resumed | 82, 114, 94 ms |
| 23:17:32 | com.instagram.android | HOME accepted; launcher resumed | 89, 136 ms |
| 23:18:03 | com.tencent.tmgp.codev | HOME accepted; launcher resumed | 117, 216, 81 ms |

Final authorization check FAILED despite process survival:

```text
23:18:03.581 ActivityManager: Start proc 29118:com.oplus.safecenter:accessibility/1000
  for provider com.oplus.safecenter.accessibility.provider.FraudRiskProvider
23:18:03.801 OplusA11yEventHelper: [tryReportEvent] risk accessibility by com.luoyilin.focusguard
23:18:03.811 AccessibilityUtils (PID 29118): put ENABLED_ACCESSIBILITY_SERVICES,
  enabledServicesBuilder.toString=com.yunlian.awayphone/com.yunlian.awayphone.service.AService
23:18:03.832 AccessibilityManagerServiceExtImpl: removeEnableService
  diffService=[ComponentInfo{com.luoyilin.focusguard/com.luoyilin.focusguard.FocusAccessibilityService}]
```

No new process exit recorded; latest exit was FORCE STOP at 23:01:53.
At final check, guard PID was still 13180 and FocusGuard authorization was absent.
This identifies the safety-center revocation path, not its exact detection rule.
Repeated HOME calls and overlay behavior need controlled investigation; neither
is yet established as the trigger. Do not disable or bypass system safety checks.
Wallpaper retention passed these three short tests only; overall enforcement
stability and zero-latency claims are NOT validated.
