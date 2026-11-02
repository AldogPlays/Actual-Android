# Actual-Android 1.0.2

Private HTTPS certificate authorities installed in Android settings are now supported by native connectivity checks and the bundled GeckoView engine. Public roots remain available, and normal certificate and hostname verification stays enabled. No server URL or private CA is bundled.

This release also adds matching certificate setup guidance and consistent local release checks across Actual-Android and LubeLogger-Android. Existing launch, server recovery, authentication, and budget storage behavior is preserved.

## Updating

Version 1.0.2 uses versionCode 4 and the retained 1.0.1 signing identity. Install the signed update over 1.0.1 to keep server settings, logins, and local budgets. Do not uninstall or clear app storage. Version 1.0.0 still requires the documented [export/sync migration](release-notes-1.0.1.md) because its original signing key was lost.

Install your CA's public root certificate in the same Android profile as the app, use a hostname covered by the server certificate, and restart the app. See [HTTPS and private CAs](HTTPS.md).

## Release status

Debug and release builds, three unit tests, both lint variants, APK signature verification, the retained certificate comparison, and 16 KiB archive alignment checks passed. See the [validation record](VALIDATION.md). No Android device was connected; GrapheneOS HTTPS and data-retention checks remain unverified.
