# PermLens

Lightweight per-app permission (AppOps) manager for Android, built on [Shizuku](https://shizuku.rikka.app).
See what every app can do, and change it with one tap.

## Features
- **Apps:** browse user and system apps, search by name or package.
- **Per-app operations:** see which permissions an app used and when, and set each to Allow, Ignore, Deny, Foreground or Default.
- **Presets:** a floating "+" menu on each app's screen with Lockdown, No overlays, Quiet in background and Reset app.
- **Ops tab:** pick one operation and see which apps use it.
- **Activity tab:** which apps used the camera, mic, location and clipboard in the last 24 hours.
- **Rules:** every change you make is saved and re-applied when Shizuku restarts. Backup and restore as a JSON file, with a history log.

## Requirements
- Android 11+ (minSdk 30)
- Shizuku running (Wireless Debugging, no PC needed)

## Design
Material 3 Expressive, using the official material3 1.5 components: expressive theme with spring motion, dynamic color on Android 12+ with a static fallback, large flexible app bars, connected button groups, expressive buttons, the FAB menu and the morphing loading indicator. Headings use Space Grotesk. Follows the system light/dark setting.

Design rules, file map and upgrade notes are in [DESIGN.md](DESIGN.md). Read it before changing any UI code.

## Tech stack
Kotlin, Jetpack Compose, Material 3 1.5.0-rc01, AGP 9.2, Gradle 9.4.1, Kotlin 2.3.10, compileSdk 37, Shizuku API 13.1.5.

## Build
Every push to `main` builds a signed APK in GitHub Actions and publishes it to the **Latest build** release.
The signing key is created once and reused from the `signing-key` release. Do not delete that release.

If a build fails, the workflow posts the Gradle errors to a release named **build-error**. Read it there instead of digging through the Actions log.

## Project layout
- `app/src/main/java/dev/byteghst/permlens/`
  - `Ui.kt`: theme, motion, shared components
  - `Screens.kt`, `ExtraScreens.kt`: screens and tabs
  - `MainActivity.kt`: Shizuku state, onboarding, crash screen
  - `AppOpsRepo.kt`, `RuleStore.kt`: AppOps access and saved rules
- `app/src/main/res/font/`: Space Grotesk (Bold, Medium)
- `.github/workflows/build.yml`: CI
