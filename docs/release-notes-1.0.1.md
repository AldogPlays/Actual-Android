# Actual Budget Android 1.0.1

Small patch release that opens Actual directly after server setup.

## Fixed

- Configured cold launches now open the saved preferred server without the extra "Server reachable" / "Open Actual" screen or a prerequisite native connectivity probe.
- Connect and Retry open the preferred server directly through the persistent browser profile.
- Failed background connection checks leave the current web UI visible, including usable cached content. A brief notice replaces the interrupting recovery screen.
- Browser load failures still provide recovery controls, and fallback server switches still require confirmation. Certificate validation and local browser data protections remain in place.

## Install and update

The release asset is `actual-budget-1.0.1-arm64.apk`, with `SHA256SUMS` for verification. Android 10+ and arm64 are required. Version code is 3.

An update from 1.0.0 must use the original signing certificate to preserve the existing installation, server settings, logins and local budgets. Do not uninstall or clear browser data to work around a signing mismatch.

## Validation

Local unit tests, lint and release build checks are recorded in [VALIDATION.md](VALIDATION.md). The launch change has not yet been verified on a physical device. Existing engine and authentication limitations are unchanged; see [USAGE.md](USAGE.md).
