package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.HidFailure
import dev.arnv.bluke.bluetooth.HidLifecycleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusHeaderPolicyTest {
    @Test
    fun restartIsHiddenForNormalAndTransientLifecycleStates() {
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Idle))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.BindingProxy(attempt = 1)))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Registering(attempt = 1)))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Registered))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Connecting("host")))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Connected("host")))
    }

    @Test
    fun restartIsOnlyOfferedForHidServiceFailures() {
        assertTrue(shouldOfferHidRestart(HidLifecycleState.Error(HidFailure.BINDING_REJECTED)))
        assertTrue(shouldOfferHidRestart(HidLifecycleState.Error(HidFailure.BINDING_TIMEOUT)))
        assertTrue(shouldOfferHidRestart(HidLifecycleState.Error(HidFailure.REGISTRATION_REJECTED)))
        assertTrue(shouldOfferHidRestart(HidLifecycleState.Error(HidFailure.REGISTRATION_TIMEOUT)))
        assertFalse(shouldOfferHidRestart(HidLifecycleState.Error(HidFailure.CONNECTION_REJECTED)))
    }
}
