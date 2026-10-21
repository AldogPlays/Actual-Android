# Actual-Android contributor and agent guide

Actual-Android is an independent Kotlin/GeckoView wrapper for a self-hosted Actual Budget server. Read [README.md](README.md), [usage and privacy](docs/USAGE.md), and [engine compatibility](docs/COMPATIBILITY.md) before changing browser or connection behavior.

## Working safely

- Inspect Git status, the current branch, and recent history before editing. Preserve unrelated work; do not reset, stash, or overwrite it automatically.
- Keep changes focused on the requested behavior. Preserve the existing Kotlin/GeckoView design and pinned dependencies unless an upgrade is requested or necessary for the task.
- Keep builds and validation local. Do not add GitHub Actions or enable hosted automation without explicit authorization.
- Device installation, launch, emulator use, and destructive data operations need explicit authorization. Never uninstall or clear app data to resolve a build or signing conflict; budgets and browser profiles may contain unsynced data.
- Do not commit private URLs, credentials, budget data, signing keys, machine-specific SDK paths, APKs, or build output.

## Browser and data boundaries

- Preserve the persistent Gecko profile, separate storage by origin, origin-change review, and non-destructive connection recovery.
- A failed background probe must not replace a working page or clear data. TLS certificate validation must remain enabled.
- API-presence diagnostics do not establish working synchronization, storage, or device compatibility. Do not mark a device check as passed from a build, screenshot, or source inspection alone.

## Build and validation

Follow [BUILDING.md](docs/BUILDING.md) for JDK, Android SDK, ABI, and signing setup. From the repository root:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
git diff --check
```

For release-related changes, also build `assembleRelease` and follow the existing signing instructions. Documentation-only edits need link, formatting, and factual checks rather than an Android build.

Report the commands actually run, their results, and any remaining device checks. Keep historical results in [VALIDATION.md](docs/VALIDATION.md) distinct from validation of the current change. Record device/build versions when manual results are available; retain incomplete or reported-only status where appropriate.

## Signing and publication

- Official updates must retain the 1.0.1 signing identity and use a higher version code. The certificate is pinned in `docs/SIGNING_CERTIFICATE_SHA256`; never generate a replacement key or override the pin to make an official release build pass.
- Preserve the 1.0.0-to-1.0.1 reinstall and budget-export warning. Follow the backup and restore steps in `docs/BUILDING.md`; a local archive is not an off-device backup.
- Follow the user's current Git publication instructions. Permission to edit documentation or code is not permission to create version tags, publish app releases, upload APKs, or change signing credentials.

## Documentation

- Write public guides for app users and contributors. Keep assistant execution details and workflow instructions here instead of narrating sessions in release notes or validation records.
- Lead README with purpose, requirements, and download links; keep implementation details in linked guides.
- Preserve independent-project attribution, license and third-party notices, and security limitations.
- Screenshot tables use two columns, 260-pixel previews, descriptive alt text, and links to the full images. The existing screenshots were supplied by the maintainer for publication without editing; do not alter or replace them without a request to do so.
