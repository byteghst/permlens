# Roadmap

Queued work. Nothing here starts until the owner says go. Items are in priority order; do them one at a time.

## 1. Re-apply rules when an app is reinstalled or updated

**Gap:** rules are re-applied when Shizuku starts and when the app is opened, but not when an app is reinstalled while
Shizuku is already running. Android 8+ does not deliver `PACKAGE_ADDED` / `PACKAGE_REPLACED` to manifest receivers, so
a plain receiver will not work.

**Approach:** a periodic `JobScheduler` job (every few hours, persisted) that re-applies rules only when Shizuku is
running and rules exist. Optionally also register a runtime receiver while the UI is open for instant re-apply.
Log every run to the history.

## 2. Undo after a preset

Keep the previous modes of the ops a preset changed, and show a toast with an **Undo** action for a few seconds that
restores them (through `AppOpsRepo.setMode`, so rules stay in sync). Needs an action slot in `ToastPill`.

## 3. Custom presets

Save a named bundle from an app's current non-default ops, list it in the FAB menu next to the built-in presets, apply
it to any app, rename and delete. Stored as `presets.json` next to `rules.json`; include it in backup and restore.

## 4. Smaller APK with R8 shrinking (on hold)

`isMinifyEnabled` is off on purpose: the app reflects on hidden framework classes and uses Shizuku and
hiddenapibypass, which need keep rules. Only worth doing if APK size becomes a problem. If done: add keep rules,
test every screen on a device, keep an easy way to turn it back off.

## 5. Camera and mic usage alerts (on hold)

Notify when a chosen app uses the camera or microphone. Do not poll (battery). Use `IAppOpsService.startWatchingActive`
through Shizuku from a small foreground service, and only if the cost on a 2 GB device is acceptable.
