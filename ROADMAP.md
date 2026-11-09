# Roadmap

Queued work. Nothing here starts until the owner says go. Items are in priority order; do them one at a time.

## 1. Move the signing key to Actions secrets (required before the repo goes public)

**Why:** the signing key (`release.p12`) is a downloadable asset of the `signing-key` release and its password is
written in `app/build.gradle.kts` and `.github/workflows/build.yml`. In a public repo anyone could download the key and
sign a fake PermLens update that installs over the real app and inherits its Shizuku access. Do not make the repo
public before this is done. Delete this item when it is finished.

**Steps**
1. Owner backs up the rules (More > Backup rules to file).
2. Generate a new keystore (alias `permlens`). Owner adds two repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`.
3. `app/build.gradle.kts`: read the password from `KEYSTORE_PASSWORD`, no default. If `KEYSTORE_PATH` is unset
   (forks, contributors) sign with the debug key instead of failing.
4. Workflow: decode the secret into the keystore file. On the main repo, fail with a clear message if the secrets are
   missing. Forks continue with debug signing. Stop creating and downloading the `signing-key` release.
5. Delete the `signing-key` release and its tag. Run the cleanup workflow so old logs are removed too.
6. Owner uninstalls the old app (the key changed, so it cannot update in place), installs the new build, restores rules.
7. Update README (remove the key note, document the secrets), then make the repo public.

## 2. Re-apply rules when an app is reinstalled or updated

**Gap:** rules are re-applied when Shizuku starts and when the app is opened, but not when an app is reinstalled while
Shizuku is already running. Android 8+ does not deliver `PACKAGE_ADDED` / `PACKAGE_REPLACED` to manifest receivers, so
a plain receiver will not work.

**Approach:** a periodic `JobScheduler` job (every few hours, persisted) that re-applies rules only when Shizuku is
running and rules exist. Optionally also register a runtime receiver while the UI is open for instant re-apply.
Log every run to the history.

## 3. Undo after a preset

Keep the previous modes of the ops a preset changed, and show a toast with an **Undo** action for a few seconds that
restores them (through `AppOpsRepo.setMode`, so rules stay in sync). Needs an action slot in `ToastPill`.

## 4. Custom presets

Save a named bundle from an app's current non-default ops, list it in the FAB menu next to the built-in presets, apply
it to any app, rename and delete. Stored as `presets.json` next to `rules.json`; include it in backup and restore.

## 5. Smaller APK with R8 shrinking (on hold)

`isMinifyEnabled` is off on purpose: the app reflects on hidden framework classes and uses Shizuku and
hiddenapibypass, which need keep rules. Only worth doing if APK size becomes a problem. If done: add keep rules,
test every screen on a device, keep an easy way to turn it back off.

## 6. Camera and mic usage alerts (on hold)

Notify when a chosen app uses the camera or microphone. Do not poll (battery). Use `IAppOpsService.startWatchingActive`
through Shizuku from a small foreground service, and only if the cost on a 2 GB device is acceptable.
