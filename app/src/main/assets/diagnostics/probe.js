// Read the PAGE global through Firefox's Xray waiver, not the privileged extension global.
// Never inject script, change headers or expose a native bridge to page code.
const page = window.wrappedJSObject;
function report() {
  browser.runtime.sendNativeMessage("actualDiagnostics", {
    url: location.href,
    secureContext: page.isSecureContext === true,
    crossOriginIsolated: page.crossOriginIsolated === true,
    sharedArrayBufferSupported: typeof page.SharedArrayBuffer !== "undefined",
    wasmSupported: typeof page.WebAssembly !== "undefined",
    workersSupported: typeof page.Worker !== "undefined",
    indexedDBSupported: typeof page.indexedDB !== "undefined",
    cacheSupported: typeof page.caches !== "undefined",
    serviceWorkerSupported: "serviceWorker" in page.navigator,
    engine: page.navigator.userAgent
  }).catch(() => {});
}
report();
window.addEventListener("pageshow", report);
