# Statistics dashboard — first version

The main navigation now contains Today, Apps, Plan, Statistics and Settings.
StatisticsPage reads a single focus preferences snapshot in a background worker;
viewing statistics does not start, finish or modify a focus session.

Included: accumulated sessions, duration and daily average; a day picker; seven-day,
monthly and annual duration charts; hourly distribution; task totals; monthly
interruption reasons; settled session details; and the selected day's top eight
launchable apps by foreground usage time. Existing appearance colors and rounded
cards are used throughout.

Days follow the existing local 04:00 boundary. Session counts belong to the day
the session began. Work time is apportioned across day/hour boundaries. Ordinary
sessions preserve recorded elapsed duration even if wall time differs. Strict
pomodoro sessions contribute only their 25-minute work segments, excluding rests.
Interrupted sessions contribute work already recorded; user interruption and sleep
takeover remain distinct. Current sessions are shown only after settlement.
The daily average spans the first recorded session day through today, including
days with no sessions. A statistics explanation is available from the page header.

Application history availability depends on the Android usage-events retention;
missing permission, read failures, unavailable history and empty data have explicit
states. AppUsageReader's existing current-day API delegates to the new range API.
No synthetic sleep/wake data is shown: actual sleep/wake times are not yet recorded.
Custom date ranges, export/share and actual sleep/wake tracking are not included.

Validation: assembleDebug and unit-test compilation succeeded; direct JUnitCore
passed 66 tests, including seven statistics tests for the 04:00 boundary, cross-day
allocation, pomodoro rest exclusion, interruptions, empty ranges, wall-time changes
and DST. Device UI testing is reserved for the user as requested.
