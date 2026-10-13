# GeckoView compatibility investigation — 2026-10-13

## Evidence and limits

The pinned stable artifact is `org.mozilla.geckoview:geckoview:158.0.20261007201254`; Mozilla Maven metadata identified it as latest/release when checked. Its official source JAR was inspected to implement the actual NavigationDelegate, PromptDelegate, WebExtension messaging, StorageController and runtime APIs. No WebView substitutes are used.

[Actual's documentation](https://actualbudget.org/docs/troubleshooting/shared-array-buffer/) requires HTTPS, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Embedder-Policy: require-corp`, and browser SharedArrayBuffer support. A proxy may strip headers; successful native HTTP probes do not prove browser isolation. Authentication pages often lack isolation headers and should not be rejected merely for that reason.

[Mozilla's release StaticPrefList.yaml](https://github.com/mozilla-firefox/firefox/blob/release/modules/libpref/init/StaticPrefList.yaml) has `dom.postMessage.sharedArrayBuffer.withCOOP_COEP: true` and `dom.postMessage.sharedArrayBuffer.bypassCOOP_COEP.insecure.enabled: false`. [The release all.js](https://github.com/mozilla-firefox/firefox/blob/release/modules/libpref/init/all.js) enables `javascript.options.shared_memory`. These release-branch findings support availability behind normal security gates; they are source evidence, not an Android runtime test, and branch links can change. No compatibility preferences are overridden by this application. In particular, no insecure shared-memory bypass, user-agent spoofing or certificate exceptions are installed. `COEP: credentialless` is not substituted for Actual's documented `require-corp`.

[GeckoView overview](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/) describes its embedded full web engine. Desktop Firefox behavior alone is insufficient evidence for Android GeckoView. The decisive results must come from the APK's browsing context and successful budget initialization/synchronization. These have not been measured in this environment.

## Diagnostic implementation

Mozilla documents [bundled extensions and native messaging](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html) as the preferred web-content integration. The app installs `assets/diagnostics` using `ensureBuiltIn`; its top-frame script runs at document idle and pageshow. The script's [Xray waiver](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts) reads `window.wrappedJSObject` to avoid measuring extension-world privileges:

```javascript
({
  secureContext: page.isSecureContext === true,
  crossOriginIsolated: page.crossOriginIsolated === true,
  sharedArrayBufferSupported: typeof page.SharedArrayBuffer !== 'undefined',
  wasmSupported: typeof page.WebAssembly !== 'undefined'
})
```

The native message handler checks top-level sender, active origin, session identity and reported URL. Page scripts cannot call native commands; the extension only reports fixed capability fields. Diagnostics also report Worker, IndexedDB, Cache, service-worker availability and actual page user agent. These are presence checks, **not operational storage, worker, WebAssembly module or sync tests**. The native screen explicitly marks missing/stale results as unverified and distinguishes required-check failure. The user may return to Actual, configure the server or retry. No browser errors or diagnostic failure can clear data.

## Runtime privacy configuration

`GeckoRuntimeSettings.Builder.configFilePath` is documented in [Mozilla's configuration guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/automation.html), including unconditional configuration loading. This app copies a fixed privacy configuration to private storage and explicitly passes that path. Preferences concern optional background features; they do not weaken TLS or isolation. `remoteDebuggingEnabled(false)`, `consoleOutput(false)`, `aboutConfigEnabled(false)` and `extensionsWebAPIEnabled(false)` are supported builder methods. `GeckoRuntime.notifyTelemetryPrefChanged(false)` notifies the engine telemetry observer. Internal privacy preferences require validation after future engine upgrades; a network capture remains outstanding.

## If the real-device test fails

First verify the actual rendered page is secure, response headers survive the reverse proxy, certificates validate and the diagnostics are reporting the Actual origin rather than an authentication page. Test a minimal same-origin page with those headers and the four required probes to separate deployment from engine failure. Then test a SharedArrayBuffer worker exchange, WebAssembly, IndexedDB persistence and Actual initialization. Record engine version, Android/GrapheneOS build and results without private URLs or credentials.

If GeckoView fails even the correctly isolated minimal fixture, treat it as a blocker and do not claim this client supports Actual. No such engine blocker has been demonstrated here. Switching engine is a separate implementation decision.

A possible real embedded Chromium route is a maintained [Chromium content embedder](https://www.chromium.org/developers/content-module/) built from Chromium source; Chromium's content-shell provides a reference shell, not a drop-in maintained consumer Android browser library. Shipping it requires building/packaging the native engine, secure profiles, downloads, certificates, lifecycle and frequent security updates. [Chromium build instructions](https://chromium.googlesource.com/chromium/src/+/main/docs/android_build_instructions.md) describe the substantial Linux source/toolchain requirements. Desktop-oriented CEF is not a ready Android substitute. No Chromium engine is bundled, no fallback is claimed tested, and WebView/TWA remain excluded.
