# PermLens design notes (for humans and AI agents)

Last UI overhaul: v0.5.0, "Material 3 Expressive" look. Read this before touching UI code.

## Stack and the key constraint
- Compose BOM 2024.09.00 -> material3 **1.3.0**, AGP 8.5.2, Kotlin 2.0.20, compileSdk 34, CI Gradle 8.9, minSdk 30.
- The *official* Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme`, `ButtonGroup`, `LoadingIndicator`,
  `MaterialShapes`, FAB menu, flexible app bars) need material3 **1.5.0-rc01** (released 2026-11-04).
  That pulls Compose 1.12, which requires **AGP 9.1+, Gradle 9.x, compileSdk 37, newer Kotlin**.
  Not done on purpose (cannot be verified without a build). So the look is built by hand on 1.3.0 APIs.
- To migrate later: bump AGP/Gradle (also `gradle-version` in `.github/workflows/build.yml`), compileSdk 37,
  Kotlin + compose plugin, material3 1.5.x, then replace the hand-made pieces below with the official ones.

## Files
- `Ui.kt`: theme, motion tokens, mode colors, all shared components. Add new shared UI here.
- `Screens.kt`: Apps list (`PermLensApp`, `AppListScreen`) and per-app ops (`OpsScreen`).
- `ExtraScreens.kt`: `PermLensRoot` (Scaffold + NavigationBar, 4 tabs), Ops tab, Activity tab, More tab, Rules view.
- `MainActivity.kt`: Shizuku state, onboarding (`StatusScreen`), `CrashScreen`.
- `res/values/themes.xml` + `values-night/themes.xml`: plain DeviceDefault light/dark, no hard-coded colors.

## Rules
1. **No hard-coded colors in screens.** Use `MaterialTheme.colorScheme`. Op mode colors come only from
   `modeColors(mode)` (accent / container / onContainer, light and dark aware).
2. Theme = dynamic color on Android 12+, static `LightScheme`/`DarkScheme` fallback on API 30. Follows system dark mode.
3. Motion = springs from `Motion` (`spatial`, `fastSpatial`, `effects`). Avoid tween except fades and counters.
4. Every screen is wrapped in `ScreenFrame(title, onBack?, actions, overlay) { body }`: collapsing `LargeTopAppBar`
   + body. Put the scrolling `LazyColumn` in the body with `Modifier.weight(1f)`; header controls go in its first `item`
   so the bar collapses on scroll. Screens do NOT add status bar padding; the root Scaffold handles the bottom bar inset.
5. Corners are large (cards 20-36dp). Clickable cards use `PressSurface` (corner morph + squish on press).
   Grouped items use `groupShape(index, count)`; filters use `ConnectedGroup` (selected button turns fully round).
6. Toasts: `rememberToast()` + `ToastPill` through `ScreenFrame(overlay = ...)`.
7. Mode picker: `ModeSheet` / `ModeOptionRow` (shared by per-app and per-op screens).
8. Icons come from `material-icons-core` only (no extended set). Add an import per icon.

## Components in Ui.kt
`PermLensTheme`, `Motion`, `modeColors`, `groupShape`, `entrance`, `ScreenFrame`, `PressSurface`, `ExpressiveButton`
(`BtnKind`), `ConnectedGroup`, `SearchField`, `SectionLabel`, `ErrorCard`, `AppIcon`, `ModeChip`, `Tag`, `PulseDot`,
`StatTile`, `SkeletonList`, `LensLogo` (rotating scalloped blobs), `ToastState`/`rememberToast`/`ToastPill`,
`ModeOptionRow`, `ModeSheet`.

## What changed in 0.5.0
- Removed the dark neon "aurora + glass + gradient" look (`Pal`, `glass`, `GradientButton`, `Segmented`, `AuroraBackground`).
- New theme, shapes, typography (heavier weights), spring motion, tonal surfaces.
- Bottom `NavigationBar` replaces the custom segmented tab bar. Large collapsing app bars replace custom headers/back pills.
- Logic untouched: `AppOpsRepo`, `RuleStore`, `CrashLog`, Shizuku handling, tab/back behavior are as before.
- Version text "PermLens 0.5.0" in `MoreMain` is hard-coded; keep it in sync with `app/build.gradle.kts`.
- Not compiled locally when written: if CI fails, read the Gradle error first (likely a small API/import slip).
