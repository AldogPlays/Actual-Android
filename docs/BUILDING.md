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


## Signing identity for the official first release

Version 1.0.0 uses versionCode 2 and retains the local certificate used by the owner's working initial APK. The certificate is labelled Android Debug, matching the retained-certificate approach used by Goveon. This does not make the release variant debuggable. The original private key must be backed up securely and retained for every future update. There is no signing configuration, keystore or password in Git. GitHub Actions is disabled.

For that retained local keystore, supply its private path and alias and let the script prompt for passwords:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ACTUAL_KEYSTORE=/private/path/to/retained.keystore
export ACTUAL_KEY_ALIAS=androiddebugkey
./scripts/build-release.sh
```

For a new privately built distribution, use its own key path/alias; it will not update the official retained-certificate distribution. The script produces the signed APK and SHA256SUMS in ignored `release-artifacts/`. Tags/releases are created separately after checking source and certificate fingerprints; there is no CI workflow or automatic release publishing.
