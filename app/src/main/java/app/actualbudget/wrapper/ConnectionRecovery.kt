package app.actualbudget.wrapper

/** Activity-scoped connection workflow. Scheduling and rendering stay with the caller. */
class ConnectionRecovery(
    private val probe: (Server) -> Health,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
    private val isBrowsing: () -> Boolean,
    private val onOutcome: (Outcome) -> Unit
) {
    sealed interface Outcome {
        data class Open(val server: Server) : Outcome
        data object BackgroundFailure : Outcome
        data object Checking : Outcome
        data class Review(val server: Server, val health: Health) : Outcome
        data class Unavailable(val reason: String) : Outcome
    }

    private var busy = false
    private var generation = 0

    /** Ignore old results without canceling queued or in-flight network requests. */
    fun invalidate() { generation++; busy = false }

    fun connect(servers: List<Server>) {
        servers.firstOrNull()?.let { onOutcome(Outcome.Open(it)) }
    }

    fun checkConnection(current: Server) {
        if (busy || !isBrowsing()) return
        busy = true
        val token = generation
        execute {
            val first = probe(current)
            val second = if (first.kind == HealthKind.UNREACHABLE) probe(current) else first
            deliver {
                if (token != generation) return@deliver
                busy = false
                if (isBrowsing() && !second.usable) onOutcome(Outcome.BackgroundFailure)
            }
        }
    }

    fun recoverAfterLoadError(current: Server, servers: List<Server>) {
        invalidate()
        val token = generation
        onOutcome(Outcome.Checking)
        busy = true
        execute {
            val health = probe(current)
            val replacement = if (!health.usable && health.kind != HealthKind.TLS_ERROR) {
                servers.filter { it != current }.map { it to probe(it) }.firstOrNull { it.second.usable }
            } else null
            deliver {
                if (token != generation) return@deliver
                busy = false
                if (replacement != null) onOutcome(Outcome.Review(replacement.first, replacement.second))
                else onOutcome(Outcome.Unavailable(if (health.usable) "Server responds, but the browser page load failed. Retry or review diagnostics." else health.detail))
            }
        }
    }

    fun attention(reason: String) {
        busy = false
        onOutcome(Outcome.Unavailable(reason))
    }
}
