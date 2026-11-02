# HTTPS and private certificate authorities

Actual-Android is a native Kotlin app with a bundled GeckoView engine. It is not a PWA or Trusted Web Activity, and an external browser does not control its HTTPS trust.

Native connectivity checks use Android's standard [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config), with both `system` and `user` trust anchors in the base configuration. This applies to all configured hosts in debug and release builds.

GeckoView uses its own certificate verifier. The app enables the supported [`enterpriseRootsEnabled(true)` runtime setting](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoRuntimeSettings.Builder.html#enterpriseRootsEnabled(boolean)) before creating the runtime. Gecko keeps Mozilla's built-in public roots and imports Android's user-installed CA roots. It does not replace its public root store with Android's system CA store, so the browser and native probes do not have identical public root lists. Android's XML by itself does not configure Gecko's certificate store. See [Mozilla's certificate architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html).

## Using a private CA

1. Install the CA's **public root certificate** as a CA certificate in Android settings, in the same user or work profile as this app. Never install or distribute the CA's private key. For Caddy's internal issuer, use its public root certificate, not the server's leaf certificate.
2. Configure an HTTPS server URL whose hostname is covered by the certificate's Subject Alternative Name. A wildcard covers one label: `*.home.arpa` can cover `budget.home.arpa`, but not `home.arpa`, a deeper subdomain, or an IP address. Enter a concrete hostname in the app, not the wildcard.
3. Ensure DNS resolves that hostname from the device and the server sends its required intermediate certificate chain. A trusted root does not repair DNS, an expired certificate, an incorrect hostname, or a missing intermediate.
4. Fully close and reopen the app after changing Android's CA store so Gecko can refresh its imported roots. Do not clear app storage or reinstall.

No hostname, CA certificate, credential, or private key is bundled with the app. Public HTTPS servers and other private CAs are supported without rebuilding. Trusting the user CA store means trusting CAs deliberately installed in that Android profile, not arbitrary certificates presented by a server.

## Verification and data

Certificate-chain, validity, and hostname checks remain enabled. There is no accept-all trust manager, custom permissive hostname verifier, certificate exception, or TLS-error continuation. HTTP support retains its existing warning and does not downgrade an HTTPS connection.

The update keeps the application ID, Android Keystore aliases, encrypted server settings, and persistent Gecko profile. Install a correctly signed update over the existing app to retain data. A replacement signing key cannot provide an ordinary in-place update; do not uninstall or clear storage to resolve a signature conflict.

## Release acceptance checks

Use a disposable test server/budget. Record Android/GrapheneOS version, app version, and GeckoView version. Check both **Test connectivity** and the loaded Gecko page; successful native probing alone does not establish browser trust.

| Certificate presented by HTTPS server | Expected result in both paths |
|---|---|
| Valid public CA chain, matching hostname | Accepted |
| Valid private CA chain installed in this Android profile, matching hostname | Accepted |
| Private CA absent from this Android profile | Rejected |
| Untrusted self-signed leaf | Rejected |
| Expired certificate issued by the installed CA | Rejected |
| Valid certificate issued by the installed CA, wrong hostname | Rejected |

Also verify that server settings, logins, and a synchronized test budget survive an update and cold restart, and that Actual still initializes and synchronizes. Keep the same origin when checking persistence. Runtime checks remain pending until recorded in [VALIDATION.md](VALIDATION.md).
