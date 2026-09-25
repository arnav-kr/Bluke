package dev.arnv.bluke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionHelpPolicyTest {
    @Test
    fun offersHelpAfterRepeatedConnectionRequests() {
        assertFalse(shouldOfferConnectionHelp(connectionAttempts = 2))
        assertTrue(shouldOfferConnectionHelp(connectionAttempts = 3))
    }

    @Test
    fun offersHelpWhenAConnectionAttemptStalls() {
        assertFalse(shouldOfferConnectionHelp(stalledForMillis = 11_999L))
        assertTrue(shouldOfferConnectionHelp(stalledForMillis = 12_000L))
    }

    @Test
    fun offersHelpForFrequentStateChurnOnlyInsideTheWindow() {
        val now = 500_000L
        assertTrue(
            shouldOfferConnectionHelp(
                transitionTimestamps = listOf(now - 90_000L, now - 60_000L, now - 30_000L, now),
                nowMillis = now,
            ),
        )
        assertFalse(
            shouldOfferConnectionHelp(
                transitionTimestamps = listOf(now - 130_000L, now - 60_000L, now - 30_000L, now),
                nowMillis = now,
            ),
        )
    }
}
