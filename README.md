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
- Android 11 or newer (minSdk 30)
- [Shizuku](https://shizuku.rikka.app) installed and running (Wireless Debugging, no PC needed)

## Install
1. Download `PermLens.apk` from the **Latest build** release and install it (allow installs from your browser if asked).
2. Start Shizuku (Developer options > Wireless Debugging, then start it from the Shizuku app). On Android 11+ this has to be repeated after every reboot.
3. Open PermLens and grant the Shizuku permission when asked.

## Privacy
PermLens does not request the `INTERNET` permission, so nothing leaves your device. Saved rules and history are plain files in the app's private storage. A backup is a JSON file you choose where to save. It asks for `QUERY_ALL_PACKAGES` only to list installed apps.

## Disclaimer
PermLens uses hidden Android APIs through Shizuku. They can change between Android versions and vendors, so it may not work everywhere (developed and tested on Android 12). Changing operations can make apps misbehave; **Reset app** and **Reset everything I changed** restore defaults. Use at your own risk. This is an independent project, not affiliated with Google or the Shizuku project.

## Design
Material 3 Expressive, using the official material3 1.5 components: expressive theme with spring motion, dynamic color on Android 12+ with a static fallback, large flexible app bars, connected button groups, expressive buttons, the FAB menu and the morphing loading indicator. Headings and numbers use Space Grotesk, body text uses Montserrat. Follows the system light/dark setting.

Design rules, file map and upgrade notes are in [DESIGN.md](DESIGN.md). Read it before changing any UI code.

## Tech stack
Kotlin, Jetpack Compose, Material 3 1.5.0-rc01, AGP 9.2, Gradle 9.4.1, Kotlin 2.3.10, compileSdk 37, Shizuku API 13.1.5.

## Build
Every push to `main` builds an APK in GitHub Actions and publishes it to the **Latest build** release. Release builds are signed in CI with a persistent key so that updates install over earlier versions.

If a build fails, the workflow posts the Gradle errors to a release named **build-error**. Read it there instead of digging through the Actions log.

`ci/cleanup.yml` is an optional weekly workflow that removes old workflow runs and stray releases. Copy it to `.github/workflows/` to enable it.

## Project layout
- `app/src/main/java/dev/byteghst/permlens/`
  - `Ui.kt`, `AppText.kt`, `Icons.kt`: theme, typography, motion, shared components, icons
  - `Screens.kt`, `ExtraScreens.kt`: screens and tabs
  - `MainActivity.kt`: Shizuku state, onboarding, crash screen
  - `AppOpsRepo.kt`, `RuleStore.kt`, `PermLensApplication.kt`: AppOps access, saved rules, auto re-apply
- `app/src/main/res/font/`: Space Grotesk and Montserrat
- `.github/workflows/build.yml`: CI
- `ci/`: staged workflow files
- `licenses/`: license texts for bundled fonts

## Roadmap
Queued work is tracked in [ROADMAP.md](ROADMAP.md).

## License
[MIT](LICENSE). Third-party components and fonts are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). To report a security problem see [SECURITY.md](SECURITY.md).
