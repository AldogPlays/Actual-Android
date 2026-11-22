package app.actualbudget.wrapper

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/** Converts Gecko messaging into page reports without retaining an Activity in session state. */
class GeckoDiagnosticsAdapter(
    private val runtime: GeckoRuntime,
    private val currentSession: (GeckoSession) -> Boolean,
    private val currentOrigin: () -> String?
) : PageDiagnostics.Adapter<GeckoSession, WebExtension> {
    override fun install(onInstalled: (WebExtension?) -> Unit, onFailure: () -> Unit) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/diagnostics/", "diagnostics@actualbudget.wrapper"
        ).accept({ onInstalled(it) }, { onFailure() })
    }

    override fun isCurrent(session: GeckoSession) = currentSession(session)
    override fun activeOrigin() = currentOrigin()

    override fun bind(extension: WebExtension, session: GeckoSession, onMessage: (PageDiagnostics.Message<GeckoSession>) -> Unit) {
        session.webExtensionController.setMessageDelegate(extension, object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                val data = message as? JSONObject
                val report = data?.let {
                    PageDiagnostics.Report(
                        url = it.optString("url"),
                        checks = PageDiagnostics.Capability.entries.filter { capability -> data.has(capability.field) }
                            .associateWith { capability -> data.optBoolean(capability.field) },
                        engine = it.optString("engine", "unknown"),
                        error = it.optString("error")
                    )
                }
                onMessage(PageDiagnostics.Message(sender.session, sender.isTopLevel, sender.url, report))
                return null
            }
        }, "actualDiagnostics")
    }

    override fun unbind(extension: WebExtension, session: GeckoSession) {
        session.webExtensionController.setMessageDelegate(extension, null, "actualDiagnostics")
    }
}
