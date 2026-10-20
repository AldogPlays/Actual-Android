# Validation record — October 13, 2026

## Direct-launch change — October 20, 2026

- Configured cold launches, Connect and Retry now open the preferred URL through Gecko without a prerequisite native probe or connection review. Existing sessions still resume without reloading.
- Background probe failures show a brief notice and preserve the visible page. Actual browser network load failures retain native recovery and fallback review; TLS failures do not silently switch origins.
- `testDebugUnitTest lintDebug assembleDebug` passed on Linux: all three existing JVM tests passed, and a debug APK was produced. These tests cover URL parsing and native probes, not Android launch behavior.
- Validation used the available JDK 21 with Java/Kotlin target 17, a temporary Android SDK 37.2/build-tools 36.0.0 installation, and a command-line AAPT2 override for NixOS. No machine-specific paths or build configuration changes were committed.
- Cold launch, cached offline use, fallback recovery and TLS rejection still require on-device acceptance testing using the checklist below. No device test or new signed release is claimed.

## Verified on Linux

- Examined LubeLogger-Android reference commit `0ea4e69235b2ea4a6df3460980930dd9a13e6b1a`, including its native Activity, probes, WebView configuration and Gradle files.
- Official stable Mozilla Maven metadata and GeckoView source JAR inspected. GeckoView pinned to `158.0.20261007201254`; API signatures were compiled against that AAR.
- JDK 17, AGP 9.4.1, Kotlin 2.4.20 and Gradle 9.6.0 compiled with Android SDK 37.2 and build-tools 36.0.0. The initially available SDK 35 was insufficient; SDK 37.2 was installed in `/tmp/actual-sdk` for this run. The local SDK path is untracked and must be changed on another machine. The source project does not depend on `/tmp` paths.
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` completed successfully.
- **3 unit tests, 0 failures:** ports/paths/IPv6/overlay hostname normalization and origin separation; rejected schemes/credentials/query/fragment/bad ports; local HTTP fixture with arbitrary successful response, 401, 503, same-origin redirect, external auth redirect and bounded redirect loop. These are pure JVM/network tests, not Android UI tests.
- Android lint completed with **0 errors, 16 warnings**. Warnings cover intentionally older target SDK, supported-but-pinned Gradle, optional HTTP policy, arm64-only default (ChromeOS ABI), newer manifest back-callback attribute, English text/localization and the supplemental two-finger gesture accessibility lint. Settings remain available through the native launcher shortcut. The current target SDK is 35; increasing it requires testing newer local-network permission behavior and navigation changes.
- APK manifest inspected with `aapt`: package `app.actualbudget.wrapper`, version `0.1.0` / code 1, min SDK 29, target SDK 35, launcher Activity present, arm64 native libraries. Only the launcher Activity is exported; the dependency profiling receiver is removed (a targeted MissingClass lint annotation applies solely to its removal directive). Only INTERNET and ACCESS_NETWORK_STATE system permissions; one package-scoped AndroidX signature permission. No inherited wake-lock/audio-settings permissions remain.
- Debug APK verified by `apksigner verify --verbose`: one signer, APK signature scheme v2 verified. Debug signing is automatic and is not a production signing identity.
- APK checked with `zipalign -c -P 16 4`: successful archive/native-library offset alignment check. This does not verify native ELF runtime behavior or replace a device test.
- Unsigned release APK produced, ready for private alignment/signing as documented in README.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (approximately 200 MiB; arm64-v8a).

SHA-256 of this build:

```text
22c5b42503b3f699f220a3cb2e9fd885581132549c075d04f3df6711ad9d0150
```

Release output: `app/build/outputs/apk/release/app-release-unsigned.apk`.

Tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint: `app/build/reports/lint-results-debug.html`.

## User-reported app testing — October 13, 2026

After installing the initial debug build, the user reported that the app “works great” and supplied four screenshots showing the Actual budget, new-transaction, accounts and reports screens without browser chrome. This is user-reported app use, not an independently observed device test. It establishes successful opening and rendering of Actual on the user's device; the device/OS version, diagnostic boolean results and synchronization behavior were not separately recorded.

The screenshots are included at the user's request for repository presentation. No claim is made that all acceptance criteria below have been exercised. Release 1.0.0 retains the same app behavior and, at the owner’s request, the signing certificate used by the working initial APK.

## Not independently verified

No Android device was connected (`adb devices` returned an empty list). There was no configured AVD, no emulator system image and no `/dev/kvm`. No APK was installed or launched by the build agent. The subsequent user report and screenshots are recorded above. There was no supplied Actual instance, login or test budget. The build agent has not independently verified GrapheneOS compatibility or synchronization; the user reports successful app use. Source-level availability of Gecko features is not a passing runtime result.

## Physical-device acceptance checklist

Use a disposable budget and an arm64 GrapheneOS device with networking permitted for the app. Record app/engine, Android and GrapheneOS versions with each result; do not publish private hostnames, cookies, tokens or budget data.

| Test | Expected result | Status |
|---|---|---|
| Install debug APK | Android accepts package and launcher icon appears | User-reported app use; exact device not recorded |
| First launch | Native URL setup; no personal address prefilled | Not tested |
| Connect, cold relaunch, Retry | Preferred URL opens without connection review or prerequisite probe; saved browser data retained | Not tested |
| HTTPS custom port + path | Configured path/port used without truncation | Not tested |
| Tailscale/Headscale, IPv4, IPv6 | Reachable while appropriate network is connected | Not tested |
| HTTP warning | Explicit warning; isolation failure accurately diagnosed | Not tested |
| Invalid TLS certificate | Native failure; no certificate exception or silent bypass | Not tested |
| Authentication / proxy redirects | Identification warning; new origin approval; login works | Not tested |
| Main content | No address bar, tab bar, menu or permanent toolbar | Shown in user screenshots |
| Status/navigation/keyboard/cutout | Correct insets in portrait, landscape and gesture navigation | Not tested |
| Launcher settings shortcut and two-finger hold | Native settings opens; returning retains current page | Not tested |
| Back gesture | Gecko history first, then exits | Not tested |
| Required diagnostics on Actual page | secureContext, crossOriginIsolated, SAB, WASM all true | Not tested |
| Worker + shared memory | Real SharedArrayBuffer exchange and Atomics work in a worker | Not tested |
| IndexedDB/local storage/cookies/cache | Persist across rotation, background and cold restart | Not tested |
| Actual initialize/sync | Disposable budget initializes, edits and syncs with server | Not tested |
| Offline/cache behavior | Existing data retained; no destructive recovery | Not tested |
| Preferred/fallback | Bounded probes; review before cross-origin switch; independent data retained | Not tested |
| Brief network fluctuations / offline cached page | Background probe failure leaves current content visible; no recovery screen or switch | Not tested |
| Browser network load failure | Recovery controls appear; reachable fallback requires review before opening | Not tested |
| Rotation/process death | No rotation reload; state restoration where available; profile remains | Not tested |
| External navigation | Unapproved origin blocked pending choice; approved auth can return | Not tested |
| File import/export | Document picker works; exported budget readable; cancellation safe | Not tested |
| Clear browser data | Destructive warning; cancels safely; confirms deletion for all origins | Not tested |
| No Play Services | Functions on stock GrapheneOS without installing Google components | Not tested |
| Network privacy capture | Optional engine services disabled; residual certificate/page traffic understood | Not tested |
| Signed update | Same private key and higher version retain local budget/profile | Not tested |

## Known limits

- Runtime diagnostics check API presence and isolation only. They do not attest to database persistence or successful synchronization.
- No insecure engine preferences or Chromium replacement are installed. If correctly isolated page probes fail on-device, pause adoption and investigate using the procedure in COMPATIBILITY.md.
- Native UI is English, platform Material without permanent chrome; supplemental gesture is not the accessibility entry point. Launcher shortcut availability depends on the launcher.
- The default APK is arm64-only; README documents other-ABI builds. Gecko increases APK size substantially.
- Auth origins must be approved again after Activity recreation. Popup OAuth relying on `window.opener`, WebAuthn and native date/color prompts are not implemented.
- An active file picker/download can be canceled by Activity destruction or process death; exports have a generic suggested name.
- Saved session-state payloads above 256 KiB are omitted to avoid Android transaction limits. In that case the configured server reopens with the persistent profile; unsaved in-page form state may not survive process death.
- Native probe and Gecko certificate stores differ; a native reachability pass does not guarantee Gecko trusts the certificate. System/private-CA scenarios need device testing.
- Privacy preference support must be rechecked after engine updates. No traffic capture was performed.

## Release 1.0.0 build validation

- Version 1.0.0, versionCode 2, arm64-v8a, release variant with `application-debuggable` absent from APK badging.
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` passed; three unit tests and lint with 0 errors / 16 warnings. No functional engine changes were made for the release.
- APK signed locally using the retained working-APK certificate, then signature and 16 KiB archive alignment verified. Both the new debug and release APKs have the same certificate fingerprint.
- Release APK: `release-artifacts/actual-budget-1.0.0-arm64.apk`, 205,568,539 bytes. APKs, aligned intermediates and checksums are release assets, not Git source files.
- APK SHA-256: `961a5ee7bc3261be8d4e990ecb23ce59145127fa28db968f38e079fc3f075cd1`.
- Signing certificate SHA-256: `65cbfff2f2b2db9645d1a7056e1f30e13c6792b94f68bdace31894cce1d34244`. This public fingerprint is not a private key.
- `scripts/build-release.sh` syntax checked. The equivalent local build/align/sign/verify steps were run without recording passwords in source control. The interactive script was not exercised end-to-end with terminal password entry.
- The 1.0.0 release APK itself has not yet been installed by the build agent. User-reported screenshots apply to the initial working build.
