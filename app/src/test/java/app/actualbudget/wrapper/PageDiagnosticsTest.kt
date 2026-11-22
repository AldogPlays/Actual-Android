package app.actualbudget.wrapper

import org.junit.Assert.*
import org.junit.Test

class PageDiagnosticsTest {
    private val session = Any()
    private val extension = Any()

    private class Messaging : PageDiagnostics.Adapter<Any, Any> {
        val installations = mutableListOf<(Any?) -> Unit>()
        val failures = mutableListOf<() -> Unit>()
        var current = true
        var origin: String? = "https://actual.example:443"
        var receive: ((PageDiagnostics.Message<Any>) -> Unit)? = null
        override fun install(onInstalled: (Any?) -> Unit, onFailure: () -> Unit) { installations.add(onInstalled); failures.add(onFailure) }
        override fun isCurrent(session: Any) = current
        override fun activeOrigin() = origin
        override fun bind(extension: Any, session: Any, onMessage: (PageDiagnostics.Message<Any>) -> Unit) { receive = onMessage }
        override fun unbind(extension: Any, session: Any) { receive = null }
    }

    @Test fun navigationReadinessWaitsForInstallationWithoutWaitingForBinding() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        val binding = diagnostics.binding(session, messaging)
        val events = mutableListOf<String>()

        binding.beforeNavigation(isCurrent = { true }, onReady = {
            binding.attach()
            events.add("navigate")
        }, onFailure = { events.add("failure") })

        assertTrue(events.isEmpty())
        messaging.installations.removeAt(0)(extension)
        assertEquals(listOf("navigate"), events)
        assertNull(messaging.receive)
        messaging.installations.removeAt(0)(extension)
        assertNotNull(messaging.receive)
    }

    @Test fun onlyMatchingTopFrameReportsFromTheActiveSessionAndOriginAreAccepted() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        diagnostics.binding(session, messaging).attach()
        messaging.installations.removeAt(0)(extension)
        val url = "https://actual.example/budget"
        val report = PageDiagnostics.Report(url)
        val message = PageDiagnostics.Message(session, true, url, report)

        listOf(
            message.copy(session = Any()),
            message.copy(topLevel = false),
            message.copy(senderUrl = "https://identity.example/login"),
            message.copy(report = PageDiagnostics.Report("https://actual.example/other")),
            message.copy(report = null)
        ).forEach {
            messaging.receive!!(it)
            assertNull(diagnostics.snapshot(url))
        }
        messaging.receive!!(message)
        assertEquals(report, diagnostics.snapshot(url))
    }

    @Test fun resultsSurviveRebindingButNotNavigationAndMissingChecksStayUnknown() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        diagnostics.binding(session, messaging).attach()
        messaging.installations.removeAt(0)(extension)
        val url = "https://actual.example/budget"
        val report = PageDiagnostics.Report(url, mapOf(PageDiagnostics.Capability.SECURE_CONTEXT to true))
        messaging.receive!!(PageDiagnostics.Message(session, true, url, report))

        assertFalse(diagnostics.snapshot(url)!!.requiredPresent)
        assertNull(diagnostics.snapshot(url)!!.checks[PageDiagnostics.Capability.WASM])
        assertNull(diagnostics.snapshot("https://actual.example/other"))
        diagnostics.binding(session, messaging).attach()
        assertEquals(report, diagnostics.snapshot(url))
        diagnostics.clear()
        assertNull(diagnostics.snapshot(url))
    }

    @Test fun requiredPresenceDoesNotDependOnOptionalChecks() {
        val checks = mapOf(
            PageDiagnostics.Capability.SECURE_CONTEXT to true,
            PageDiagnostics.Capability.CROSS_ORIGIN_ISOLATED to true,
            PageDiagnostics.Capability.SHARED_ARRAY_BUFFER to true,
            PageDiagnostics.Capability.WASM to true
        )
        val report = PageDiagnostics.Report("https://actual.example/", checks)
        assertTrue(report.requiredPresent)
        for (capability in checks.keys) {
            assertFalse(report.copy(checks = checks - capability).requiredPresent)
            assertFalse(report.copy(checks = checks + (capability to false)).requiredPresent)
        }
    }

    @Test fun staleNavigationReadinessDoesNotNavigateOrReportAFailure() {
        for (fail in listOf(false, true)) {
            val diagnostics = PageDiagnostics<Any, Any>()
            val messaging = Messaging()
            var current = true
            val events = mutableListOf<String>()
            diagnostics.binding(session, messaging).beforeNavigation(
                isCurrent = { current }, onReady = { events.add("ready") }, onFailure = { events.add("failed") }
            )
            current = false
            if (fail) messaging.failures.removeAt(0)() else messaging.installations.removeAt(0)(extension)
            assertTrue(events.isEmpty())
        }
    }

    @Test fun navigationInstallationFailureIsReportedOnlyForTheCurrentSession() {
        for (current in listOf(false, true)) {
            val diagnostics = PageDiagnostics<Any, Any>()
            val messaging = Messaging().apply { this.current = current }
            val events = mutableListOf<String>()
            diagnostics.binding(session, messaging).beforeNavigation(
                isCurrent = { true }, onReady = { events.add("ready") }, onFailure = { events.add("failed") }
            )
            messaging.failures.removeAt(0)()
            assertEquals(if (current) listOf("failed") else emptyList<String>(), events)
        }
    }

    @Test fun nullExtensionIsUnverifiedAndStaleSessionDoesNotBind() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        diagnostics.binding(session, messaging).attach()
        messaging.installations.removeAt(0)(null)
        assertEquals("Diagnostic extension unavailable.", diagnostics.snapshot("")!!.error)
        assertFalse(diagnostics.snapshot("")!!.requiredPresent)
        assertNull(messaging.receive)
        diagnostics.binding(session, messaging).attach()
        messaging.current = false
        messaging.installations.removeAt(0)(extension)
        assertNull(messaging.receive)
    }

    @Test fun detachingBindingKeepsTheAcceptedResultForTheNextActivity() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        val binding = diagnostics.binding(session, messaging)
        binding.attach()
        messaging.installations.removeAt(0)(extension)
        val url = "https://actual.example/budget"
        val report = PageDiagnostics.Report(url)
        messaging.receive!!(PageDiagnostics.Message(session, true, url, report))
        binding.detach()
        assertNull(messaging.receive)
        assertEquals(report, diagnostics.snapshot(url))
    }

    @Test fun failedBindingRetainsAnErrorWithoutPresentingItAsAPageResult() {
        val diagnostics = PageDiagnostics<Any, Any>()
        val messaging = Messaging()
        diagnostics.binding(session, messaging).attach()
        messaging.failures.removeAt(0)()

        assertEquals("Diagnostic extension could not initialize.", diagnostics.snapshot("")!!.error)
        assertNull(diagnostics.snapshot("https://actual.example/budget"))
    }
}
