# Building and signing

## Build and install

Use JDK 17, Android SDK platform **37.2**, build-tools **36.0.0**, and platform-tools. Compilation API and device minimum are independent. GeckoView's current stable AAR requires API 37.2. Pinned tooling: AGP 9.4.1, Gradle 9.6.0, Kotlin 2.4.20 (AGP's built-in Kotlin with a documented KGP override), GeckoView **158.0.20261007201254**, fetched from Mozilla's stable `geckoview` Maven artifact, not beta/nightly.

Install SDK packages through Android Studio or `sdkmanager 'platforms;android-37.2' 'build-tools;36.0.0' 'platform-tools'`. Set `ANDROID_HOME` or put your SDK location in an untracked `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The default APK targets **arm64-v8a** and includes the engine, so it is considerably larger than a WebView wrapper. To build for an x86_64 emulator use `./gradlew assembleDebug -PactualAbis=x86_64`; a universal supported-ABI build can use `-PactualAbis=arm64-v8a,armeabi-v7a,x86,x86_64`. Builds, unit tests and lint run locally. GitHub Actions is disabled and there are no workflow files. Fixed dependency versions improve repeatability; debug APKs from different machines may use different debug keys. This is not a claim of byte-for-byte reproducibility.


## Private release signing

Keep a long-lived signing key outside this repository. Never commit keys/passwords. Back it up securely; every update retaining local data must have the same package and signing certificate and a higher `versionCode`.

```bash
keytool -genkeypair -keystore /private/path/actual-release.jks \
  -alias actual -keyalg RSA -keysize 4096 -validity 10000
./gradlew assembleRelease
# Use your SDK's build-tools/36.0.0 executables:
zipalign -P 16 -f -v 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/actual-aligned.apk
apksigner sign --ks /private/path/actual-release.jks --ks-key-alias actual \
  --out /private/path/Actual-Budget-release.apk /tmp/actual-aligned.apk
apksigner verify --verbose /private/path/Actual-Budget-release.apk
adb install -r /private/path/Actual-Budget-release.apk
```

Signing commands prompt for passwords; do not place passwords on command lines. A private release cannot update an APK signed with another debug/release key. Export/sync before uninstalling a conflicting build: uninstall deletes budgets. Debug builds signed with another key are not an update channel for the official release.


## Signing identity for official releases

Version 1.0.1 uses versionCode 3 and starts a new permanent RSA-4096 signing identity because the original key was lost. It cannot update 1.0.0 in place; see the [migration instructions](release-notes-1.0.1.md). The new certificate SHA-256 is pinned in [SIGNING_CERTIFICATE_SHA256](SIGNING_CERTIFICATE_SHA256), and the release script rejects APKs signed by a different key. Version 1.0.0 used versionCode 2 and certificate `65cbfff2f2b2db9645d1a7056e1f30e13c6792b94f68bdace31894cce1d34244`.

All future official updates must retain the 1.0.1 key. Its PKCS12 alias is `actual`; its private key and password stay outside Git. GitHub Actions is disabled.

For the retained release keystore, supply its private path and alias and let the script prompt for passwords:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ACTUAL_KEYSTORE=/private/path/to/actual-release.p12
export ACTUAL_KEY_ALIAS=actual
./scripts/build-release.sh
```

For a separate privately built distribution, use its own key path/alias and explicitly set `ACTUAL_CERT_SHA256` to its certificate fingerprint; it will not update the official distribution. The script produces the signed APK and SHA256SUMS in ignored `release-artifacts/`. Tags/releases are created separately after checking source and certificate fingerprints; there is no CI workflow or automatic release publishing.

## Surviving laptop wipes

Before wiping or replacing a computer:

1. Save the password-protected `actual-release.p12` as an attachment in your password manager, with its password, alias `actual`, and the public certificate fingerprint in the same vault item. Use a vault that remains available independently of the laptop, and retain its recovery method.
2. Keep a second copy of the keystore on an external drive or another backed-up location. If backing up a ZIP containing the encrypted keystore and recovery instructions, retain the password separately in the password manager. A second folder on the same laptop is not a wipe-resistant backup.
3. Restore the keystore from a backup to a temporary private directory and verify it with `keytool -list -keystore /restored/path/actual-release.p12 -alias actual`. Enter the saved password and compare SHA-256 against `docs/SIGNING_CERTIFICATE_SHA256`. Verify that the entry is a `PrivateKeyEntry`.
4. On a new machine, restore this same key and use it for the next release. Never generate a replacement just because a fresh Android installation creates a new debug keystore.

The release APK and the public certificate fingerprint do not contain a recoverable signing private key. Do not upload the keystore or password as public GitHub assets or commit them to Git.
