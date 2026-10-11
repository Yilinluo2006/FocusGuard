# Widget pin investigation

Device: realme RMX3888, Android 16, launcher com.android.launcher.

Verified:
- AppWidgetManager reports support and returns true from requestPinAppWidget.
- A populated EXTRA_APPWIDGET_PREVIEW and explicit success PendingIntent did not
  produce a system confirmation or success callback on this device.
- Activity starter records CONFIRM_PIN_APPWIDGET targeting
  com.android.launcher3.dragndrop.AddItemActivity with a successful start result.
- Read-only inspection of the installed OplusLauncher APK shows isAllowedAddWidget:
  vendor/system apps or WidgetControlHelper whitelist entries are accepted;
  other providers cause finish(), with an optional vendor-controlled toast.
- FeatureOption.isSupportWhiteListControl returns !isExp.
  This is a strong explanation for this device's silent behavior, not a runtime
  trace proving the exact branch; layout locks/other launcher checks also exist.

Changes:
- In-app preview and visible accepted/rejected/timeout states.
- Explicit successful-pin callback refreshes the widget and confirms success.
- Standard system request is still made automatically on opening the add dialog.
- A user-controlled Home shortcut provides a manual-placement fallback.
- No private launcher intents, permission grants, whitelist modifications, root,
  or automatic taps are used to bypass launcher restrictions.

Build and device request flow tested. Successful pinning on an unrestricted
launcher has not been tested; one-tap placement on this realme remains unavailable.
Diagnostic APK/smali/dependency artifacts are only under ignored app/build.
