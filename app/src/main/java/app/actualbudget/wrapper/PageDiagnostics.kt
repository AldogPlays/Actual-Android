package app.actualbudget.wrapper

/** Session-scoped results with Activity-scoped messaging bindings. */
class PageDiagnostics<S : Any, E : Any> {
    interface Adapter<S, E> {
        fun install(onInstalled: (E?) -> Unit, onFailure: () -> Unit)
        fun isCurrent(session: S): Boolean
        fun activeOrigin(): String?
        fun bind(extension: E, session: S, onMessage: (Message<S>) -> Unit)
        fun unbind(extension: E, session: S)
    }

    data class Message<S>(val session: S?, val topLevel: Boolean, val senderUrl: String, val report: Report?)
    enum class Capability(val field: String) {
        SECURE_CONTEXT("secureContext"), CROSS_ORIGIN_ISOLATED("crossOriginIsolated"),
        SHARED_ARRAY_BUFFER("sharedArrayBufferSupported"), WASM("wasmSupported"),
        WORKERS("workersSupported"), INDEXED_DB("indexedDBSupported"),
        CACHE("cacheSupported"), SERVICE_WORKER("serviceWorkerSupported")
    }

    data class Report(
        val url: String,
        val checks: Map<Capability, Boolean> = emptyMap(),
        val engine: String = "unknown",
        val error: String = ""
    ) {
        val requiredPresent: Boolean get() = listOf(
            Capability.SECURE_CONTEXT, Capability.CROSS_ORIGIN_ISOLATED,
            Capability.SHARED_ARRAY_BUFFER, Capability.WASM
        ).all { checks[it] == true }
    }
    private var extension: E? = null
    private var report: Report? = null

    fun snapshot(location: String?): Report? = report?.takeIf { it.url == location }
    fun clear() { report = null }

    fun binding(session: S, adapter: Adapter<S, E>): Binding = Binding(session, adapter)

    inner class Binding internal constructor(private val session: S, private val adapter: Adapter<S, E>) {
        fun beforeNavigation(isCurrent: () -> Boolean, onReady: () -> Unit, onFailure: () -> Unit) {
            adapter.install({ installed ->
                if (isCurrent() && adapter.isCurrent(session)) {
                    extension = installed
                    onReady()
                }
            }, { if (isCurrent() && adapter.isCurrent(session)) onFailure() })
        }

        fun attach() {
            adapter.install({ installed ->
                if (installed == null) {
                    report = Report("", error = "Diagnostic extension unavailable.")
                    return@install
                }
                extension = installed
                if (adapter.isCurrent(session)) adapter.bind(installed, session) { message ->
                    if (message.session !== session || !message.topLevel || Server.origin(message.senderUrl) != adapter.activeOrigin()) return@bind
                    val received = message.report ?: return@bind
                    if (received.url == message.senderUrl) report = received
                }
            }, { report = Report("", error = "Diagnostic extension could not initialize.") })
        }

        fun detach() { extension?.let { adapter.unbind(it, session) } }
    }
}
