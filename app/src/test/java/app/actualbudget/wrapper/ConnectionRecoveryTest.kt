package app.actualbudget.wrapper

import org.junit.Assert.*
import org.junit.Test

class ConnectionRecoveryTest {
    private val preferred = Server.parse("https://preferred.example/actual/")
    private val fallback = Server.parse("https://fallback.example/actual/")

    private class Scenario {
        val outcomes = mutableListOf<ConnectionRecovery.Outcome>()
        val work = ArrayDeque<() -> Unit>()
        val deliveries = ArrayDeque<() -> Unit>()
        val checks = mutableListOf<Server>()
        val replies = ArrayDeque<Health>()
        var browsing = true
        val recovery = ConnectionRecovery(
            probe = { checks.add(it); replies.removeFirst() },
            execute = { work.addLast(it) },
            deliver = { deliveries.addLast(it) },
            isBrowsing = { browsing },
            onOutcome = { outcomes.add(it) }
        )
        fun finish() { work.removeFirst()(); deliveries.removeFirst()() }
    }

    @Test fun backgroundFailureRechecksUnreachableAndOnlyReportsANotice() {
        val s = Scenario()
        s.replies.addAll(listOf(Health(HealthKind.UNREACHABLE, "offline"), Health(HealthKind.UNREACHABLE, "still offline")))

        s.recovery.checkConnection(preferred)
        s.recovery.checkConnection(preferred) // A check in progress prevents another one.
        s.finish()

        assertEquals(listOf(preferred, preferred), s.checks)
        assertEquals(listOf(ConnectionRecovery.Outcome.BackgroundFailure), s.outcomes)
        assertTrue(s.work.isEmpty())
    }

    @Test fun browserFailureOffersFallbackForReviewWithoutOpeningIt() {
        val s = Scenario()
        val reachable = Health(HealthKind.AUTH, "Sign in")
        s.replies.addAll(listOf(Health(HealthKind.UNREACHABLE, "offline"), reachable))

        s.recovery.recoverAfterLoadError(preferred, listOf(preferred, fallback))
        assertEquals(listOf(ConnectionRecovery.Outcome.Checking), s.outcomes)
        s.finish()

        assertEquals(listOf(preferred, fallback), s.checks)
        assertEquals(listOf(ConnectionRecovery.Outcome.Checking, ConnectionRecovery.Outcome.Review(fallback, reachable)), s.outcomes)
    }

    @Test fun invalidatedChecksCannotDeliverOrUnlockANewerCheck() {
        val s = Scenario()
        s.replies.addAll(listOf(Health(HealthKind.HTTP_ERROR, "old"), Health(HealthKind.REACHABLE, "new")))
        s.recovery.checkConnection(preferred)
        s.work.removeFirst()()
        s.recovery.invalidate()
        s.recovery.checkConnection(fallback)
        s.deliveries.removeFirst()()
        s.recovery.checkConnection(preferred)
        assertEquals(1, s.work.size)
        s.finish()
        assertTrue(s.outcomes.isEmpty())
        assertEquals(listOf(preferred, fallback), s.checks)
    }

    @Test fun tlsFailureRequiresAttentionWithoutProbingFallback() {
        val s = Scenario()
        s.replies.addLast(Health(HealthKind.TLS_ERROR, "certificate failed"))
        s.recovery.recoverAfterLoadError(preferred, listOf(preferred, fallback))
        s.finish()
        assertEquals(listOf(preferred), s.checks)
        assertEquals(ConnectionRecovery.Outcome.Unavailable("certificate failed"), s.outcomes.last())
    }

    @Test fun respondingActiveServerDoesNotOfferFallbackAfterBrowserFailure() {
        for (kind in listOf(HealthKind.REACHABLE, HealthKind.AUTH, HealthKind.REDIRECT)) {
            val s = Scenario()
            s.replies.addLast(Health(kind, "responding"))
            s.recovery.recoverAfterLoadError(preferred, listOf(preferred, fallback))
            s.finish()
            assertEquals(listOf(preferred), s.checks)
            assertEquals(ConnectionRecovery.Outcome.Unavailable("Server responds, but the browser page load failed. Retry or review diagnostics."), s.outcomes.last())
        }
    }

    @Test fun invalidatedRecoveryKeepsAlreadyQueuedProbesButDiscardsTheirResult() {
        val s = Scenario()
        s.replies.addAll(listOf(Health(HealthKind.UNREACHABLE, "offline"), Health(HealthKind.REACHABLE, "fallback")))
        s.recovery.recoverAfterLoadError(preferred, listOf(preferred, fallback))
        s.recovery.invalidate() // Settings, an explicit load, or Activity destruction.
        s.finish()
        assertEquals(listOf(preferred, fallback), s.checks)
        assertEquals(listOf(ConnectionRecovery.Outcome.Checking), s.outcomes)
    }

    @Test fun backgroundChecksStayQuietOutsideBrowserAndAfterReturningToSettings() {
        val s = Scenario()
        s.browsing = false
        s.recovery.checkConnection(preferred)
        assertTrue(s.work.isEmpty())
        s.browsing = true
        s.replies.addLast(Health(HealthKind.TLS_ERROR, "certificate failed"))
        s.recovery.checkConnection(preferred)
        s.browsing = false
        s.finish()
        assertEquals(listOf(preferred), s.checks)
        assertTrue(s.outcomes.isEmpty())
    }

    @Test fun startupAndRetryOpenPreferredWithoutProbing() {
        val outcomes = mutableListOf<ConnectionRecovery.Outcome>()
        val recovery = ConnectionRecovery(
            probe = { error("Startup must not probe") },
            execute = { error("Startup must not queue work") },
            deliver = { it() },
            isBrowsing = { true },
            onOutcome = { outcomes.add(it) }
        )

        recovery.connect(listOf(preferred, fallback))
        recovery.connect(listOf(preferred, fallback))

        assertEquals(listOf(ConnectionRecovery.Outcome.Open(preferred), ConnectionRecovery.Outcome.Open(preferred)), outcomes)
    }
}
