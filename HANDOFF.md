# FocusGuard cloud handoff

This commit preserves the current local development state for continuation in Codex Cloud.

## Repositories

- Android client: https://github.com/Yilinluo2006/FocusGuard
- Java backend: https://github.com/Yilinluo2006/FocusGuardServer
- Original local directories: `E:\FocusGuard` and `E:\FocusGuardServer\FocusGuardServer`.

Select both repositories when preparing the cloud environment if a task spans the client and backend.

## Continuation

Read the relevant files in `docs/` and `docs/dev-log/` before making changes. The local snapshot includes account/device synchronization, backups, focus and sleep features, protection recovery, recorded-page monitoring, wallpaper features, and their tests. Their presence does not establish that every feature is complete.

The user's latest reported work concerns replacing three wallpapers with high-resolution images and investigating wallpaper darkening after applying settings, while preserving existing application data. Check the source and newest development notes to establish what has already been implemented. Earlier local conversations repeatedly disconnected; do not treat their progress messages as proof that testing or installation finished.

## Environment and validation

- Determine the Android SDK, Android Gradle Plugin, and JDK versions from the build files. Supply the Android SDK in the cloud environment; SDK copies and local build outputs are excluded from Git.
- `gradle.properties` currently includes a Windows-specific `-Duser.home` JVM argument. Configure a cloud-appropriate override when building; this snapshot retains the local setting.
- Start with `sh gradlew testDebugUnitTest assembleDebug` after preparing the Android build environment. Record the actual results rather than assuming previous local results still apply.
- The backend requires Java 21. Use `sh mvnw test` from its repository after preparing dependencies; use isolated test configuration rather than production credentials.
- Real-phone ADB installation, permission/accessibility behavior, and visual wallpaper checks require local-device validation. Cloud tests do not replace those checks.
- Preserve existing application data during local installation and verification.

This upload operation did not rerun application builds or tests.
