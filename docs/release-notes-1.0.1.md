# Actual Budget Android 1.0.1

Small patch release that opens Actual directly after server setup.

## Fixed

- Configured cold launches now open the saved preferred server without the extra "Server reachable" / "Open Actual" screen or a prerequisite native connectivity probe.
- Connect and Retry open the preferred server directly through the persistent browser profile.
- Failed background connection checks leave the current web UI visible, including usable cached content. A brief notice replaces the interrupting recovery screen.
- Browser load failures still provide recovery controls, and fallback server switches still require confirmation. Certificate validation and local browser data protections remain in place.

## Install and update

The release asset is `actual-budget-1.0.1-arm64.apk`, with `SHA256SUMS` for verification. Android 10+ and arm64 are required. Version code is 3.

**One-time reinstall required for 1.0.0 users.** The original signing key was lost, so 1.0.1 starts a new signing identity and cannot install over 1.0.0. Before uninstalling, synchronize and export your budgets from the old app, and verify that your export or synchronized budget is available. Uninstalling deletes local budgets, unsynced changes, logins and server settings. Then install 1.0.1, configure your server and reopen or import your budgets.

Future releases will retain the new certificate for in-place updates. Its SHA-256 fingerprint is `a35d2bf5749c646485ff273dc10c3864235f0567216d8059da5b620c1c7d7d4a`.

## Validation

Local unit tests, lint and release build checks are recorded in [VALIDATION.md](VALIDATION.md). The launch change has not yet been verified on a physical device. Existing engine and authentication limitations are unchanged; see [USAGE.md](USAGE.md).
