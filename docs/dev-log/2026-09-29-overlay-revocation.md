# Focus overlay authorization revocation — 2026-09-29

Device: realme RMX3888, Android 16, ADB serial 470e575c.

## Evidence

- Full-display focus overlays repeatedly appeared, then accessibility authorization
  was removed about two seconds later by the OEM safety-center process.
- Switching the overlay type to TYPE_ACCESSIBILITY_OVERLAY did not prevent it.
- A configuration using default system inset fitting completed one short countdown
  with authorization retained. The user had also confirmed the OEM permission
  dialog, so that run alone did not isolate the cause.
- Restoring the full-display layout reproduced revocation even after confirmation.
- Removing FLAG_LAYOUT_IN_SCREEN alone was insufficient: the user reproduced the
  problem on the installed version that still used setFitInsetsTypes(0) and
  LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES.
- Latest captured log: enabled at 21:21:03.207, cleared by AccessibilityUtils at
  21:21:07.513; enabled again at 21:21:13.863, cleared at 21:21:18.776.
  These are authorization removals, not evidence of a new app crash.

## Current mitigation

Restore default window inset fitting for the shared focus/sleep cover. Remove
the display-cutout override, the extra content inset listener and the status-bar
gradient. Wallpaper currently ends below the status bar; extending it into the
system bars is deferred until a compatible presentation is validated.

Keep wallpaper selection in the toolbox and keep the OEM confirmation package
(com.oplus.safecenter) permitted. Its AccessibilityGuideCloseActivity must remain
visible; stale usage events must not immediately cover it again.

The OEM detection rule is still unknown. Do not claim the current mitigation is
confirmed until a device run retains authorization while the focus cover is shown.
The user requested to perform device testing; no automatic focus session is to
be started for this revision. A plain JVM unit test cannot reproduce the OEM
authorization decision.
