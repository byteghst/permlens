# PermLens design notes (for humans and AI agents)

Last UI overhaul: v0.6.0, official Material 3 Expressive (material3 1.5.0-rc01). Read this before touching UI code.

## Stack
- Compose BOM 2026.09.00 with **material3 1.5.0-rc01**, AGP **9.2.0** (built-in Kotlin: no `kotlin-android` plugin,
  no `kotlinOptions`), Kotlin compose plugin 2.3.10, Gradle **9.4.1** (set in `.github/workflows/build.yml`),
  compileSdk **37**, minSdk 30, JDK 17.
- Still marked experimental in rc01 and opted in per file: `LoadingIndicator`, `MaterialShapes` / `toShape()`.
  Everything else expressive used here is stable. Files that use them carry
  `@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)`.
- When bumping versions: AGP, Gradle (`gradle-version` in the workflow) and compileSdk move together.
  Compose 1.12+ needs AGP 9.2+ and compileSdk 37.
- CI: on failure the workflow publishes a pre-release named `build-error` whose body holds the Gradle errors.
  On success it deletes that release. Read it instead of asking for logs.
- The workflow lives in `.github/workflows/build.yml`; `ci/build.yml` is a staged copy (tools that cannot write to
  the workflows folder commit there, a human copies it over).

## Files
- `Ui.kt`: theme (`MaterialExpressiveTheme` + `MotionScheme.expressive()`), motion tokens, mode colors, shared components.
- `Icons.kt`: `AppIcons` (Apps, Tune, History, MoreHoriz, Block, Snooze) as tiny `ImageVector`s with a safe fallback.
  Other icons come from `material-icons-core` (no extended set).
- `Screens.kt`: Apps list, per-app ops (`OpsScreen`), presets (`PRESETS`, `PresetsOverlay` = `FloatingActionButtonMenu`).
- `ExtraScreens.kt`: `PermLensRoot` (Scaffold + `ShortNavigationBar`, 4 tabs), Ops tab, Activity tab, More tab, Rules view.
- `AppOpsRepo.kt` / `RuleStore.kt` / `PermLensApplication.kt`: logic. `setMode` records a rule automatically;
  rules are re-applied when Shizuku starts.
- `MainActivity.kt`: Shizuku state, onboarding (`StatusScreen`), `CrashScreen`.

## Rules
1. No hard-coded colors in screens. Use `MaterialTheme.colorScheme`; op mode colors only via `modeColors(mode)`.
2. Theme = dynamic color on Android 12+, static `LightScheme`/`DarkScheme` fallback on API 30.
3. Motion = the theme's `motionScheme` for official components. Custom animations use `Motion` tokens, which equal the
   expressive springs (spatial 0.8/380, fast spatial 0.6/800, effects 1.0/1600).
4. Every screen is wrapped in `ScreenFrame(title, subtitle?, onBack?, actions, overlay) { body }` (a
   `LargeFlexibleTopAppBar` with collapse). Put the scrolling `LazyColumn` in the body with `Modifier.weight(1f)`.
5. Buttons: `ExpressiveButton` (official `Button`/`FilledTonalButton` with `ButtonDefaults.shapes()`, medium height).
   Filters: `ConnectedGroup` (official `ToggleButton`s with connected shapes). Cards: `PressSurface` / `groupShape`.
6. Loading: `SkeletonList()` now shows the official `LoadingIndicator`.
7. Toasts: `rememberToast()` + `ToastPill` via `ScreenFrame(overlay = ...)`. Mode picker: `ModeSheet`.
8. Version text "PermLens 0.6.0" in `MoreMain` is hard-coded; keep it in sync with `app/build.gradle.kts`.

## Fonts
- Font files live in `app/src/main/res/font/` (`space_grotesk_bold.ttf`, `space_grotesk_medium.ttf`,
  `montserrat_variable.ttf`). Do not rename or delete them.
- `HeadingFont` = Space Grotesk (Medium + Bold), internal. Used for `display*`, `headline*`, `titleLarge` and every number.
- `BodyFont` = Montserrat, built from the single variable file with one `Font` entry per weight (400, 500, 600, 700)
  via `FontVariation.weight(...)`. Used for `titleMedium`, `titleSmall`, `body*`, `label*`.
- All 15 styles in `AppTypography` are set explicitly through the private helpers `TextStyle.body()` and
  `TextStyle.heading()` in `Ui.kt`. Do not change this.
- `AppText.kt` defines a top-level `@Composable fun Text(text: String, ...)` in package `dev.byteghst.permlens`.
  Same-package declarations win over the star import `androidx.compose.material3.*`, so every `Text("...")` call in
  the package goes through it. It draws each digit run (regex `\d+(?:[.,:]\d+)*`) in `HeadingFont` and the rest in the
  theme style, and calls `androidx.compose.material3.Text` by its fully qualified name, so it does not recurse.
  Do not delete or rename this file, and do not add `import androidx.compose.material3.Text` to other files.
  (`MainActivity.kt` imports it explicitly and so bypasses the wrapper on purpose.)
- Compose rule: `RoundedPolygon.toShape()` is `@Composable` in material3 1.5.x. Never wrap it in `remember { }`; call
  it directly, as in `MaterialShapes.Cookie12Sided.toShape()` in `LensLogo`.

## Presets (per-app screen, FAB menu)
Lockdown (Ignore camera, mic, locations, clipboard read, contacts, call log, SMS), No overlays (Deny
`system_alert_window`, `picture_in_picture`), Quiet in background (Ignore `run_in_background`, `run_any_in_background`),
Reset app (changed ops back to Default). Edit the `PRESETS` list in `Screens.kt` to add more.
