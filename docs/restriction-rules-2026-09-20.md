# Restriction editing and emergency access

- The default relaxation window is 06:00 inclusive to 07:00 exclusive.
- Adding restrictions or reducing time remains immediate.
- Raising time and disabling restrictions are checked at the interaction and persistence boundaries.
- Window changes take effect on the next local calendar day. Overnight windows are supported.
- All apps share five emergency grants per local 04:00-to-04:00 quota period. Each grant lasts 120 continuous seconds.
- Grants do not change the daily limit. A second grant cannot start while one is active.
- After five grants, a further request reveals the separate confirmation to exempt only that app until the next 04:00.
- The emergency path does not override focus or sleep sessions.
- The blocking overlay now remains until dismissed or an emergency grant is confirmed.

State is stored in a no-backup AtomicFile protected by a cross-process file lock.
Process restarts preserve the quota and expiry. Device reboot ends an active short grant
conservatively but does not refund its use. Time is bounded by both wall and elapsed clocks
within the same boot. App usage and emergency quota reset at local 04:00; relaxation-window
changes still take effect on the next calendar day. This is not a trusted-time system
and does not claim resistance to deliberate date/timezone changes, data clearing or uninstall.

Validation:
- Debug build and Java unit-test compilation passed.
- 15 pure-Java tests passed, including window boundaries, overnight windows, relaxation
  classification, five-grant quota, next-day reset and short-grant expiration.
- Real device: disabling a restricted app outside the window was rejected.
- Real device: blocking prompt and two-step emergency confirmation displayed;
  one grant persisted with used=1 and wall/elapsed deadlines.
- Full real-device expiry, fifth-grant/day-release, reboot and 04:00 rollover workflows
  still require uninterrupted regression testing.

No user limits, whitelist or sleep schedule were intentionally changed by these tests.
