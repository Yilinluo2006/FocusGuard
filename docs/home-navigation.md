# Four-page navigation

- Today, apps, focus and settings share a ViewPager with fixed bottom navigation.
- AppManagePage and FocusModePage reuse the original screen operations. The old
  Activity entry points remain available for notifications and existing intents.
- The activity owns system/keyboard insets. Search filters presentation only:
  saving and selected counts always use the complete application list.
- Tab changes keep page instances. Activity state includes tab, query and focus
  form drafts; restoring drafts does not save or change protection settings.
- Protection warnings remain visible above all tabs. Today progress still uses
  the existing 12-hour rule. Coverage trial and streak settlement are unchanged.

Validation: offline debug build, 30 existing pure-Java tests, real-device tab
click/swipe navigation, app search using codev (Valorant), focus and settings.
No real focus session or restriction relaxation was executed for UI testing.
