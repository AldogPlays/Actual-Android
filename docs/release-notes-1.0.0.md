# Actual Budget Android 1.0.0

First release of a dedicated Android wrapper for self-hosted Actual Budget, using Mozilla GeckoView without browser chrome.

## Included

- Native preferred/fallback server setup with encrypted configuration, custom ports and preserved URL paths.
- Persistent Gecko profile for authentication, cookies, local storage, IndexedDB and caches.
- Bounded reachability checks, network observation and native connection error screens.
- Review before changing origins, with no automatic budget clearing or migration.
- Secure-context, cross-origin isolation, SharedArrayBuffer, WebAssembly and related API diagnostics in the loaded page.
- Android back gestures, system insets, launcher settings shortcut and document-picker import/download support.
- No Google Play Services, analytics or personal server address.

## Install and update

Download `actual-budget-1.0.0-arm64.apk` and optionally its `SHA256SUMS` file. Android 10+ and arm64 are required. Connect to your own HTTPS Actual server with valid TLS and Actual's COOP/COEP headers.

The distributed APK uses the non-debuggable **release variant**. It retains the same local signing certificate as the initial working 0.1.0 debug APK, so an in-place update preserves the app's profile and configuration. The retained certificate is labelled Android Debug; its private keystore stays outside the repository. Debug APKs built elsewhere cannot be assumed to share that certificate. Builds and checks are local; GitHub Actions is disabled.

Export/sync before uninstalling or clearing browser data. Signing-key mismatch requires resolving the signing identity, not silently deleting your budgets.

## Validation and limits

The owner reported successful use of the initial APK and provided screenshots of budgets, accounts, transaction entry and reports. Linux builds, unit tests, lint, APK signatures and alignment are checked separately. No independent GrapheneOS runtime, synchronization or SharedArrayBuffer diagnostic results have been recorded. The version/signing changes for 1.0.0 do not claim new functional device validation.

Popup OAuth relying on `window.opener`, WebAuthn and native date/color prompts are not implemented. File operations may not survive process death. Different server origins retain separate local budgets and login state. The APK is approximately 200 MiB because it embeds GeckoView.

See [validation](VALIDATION.md), [usage/privacy](USAGE.md), and [engine compatibility](COMPATIBILITY.md).
