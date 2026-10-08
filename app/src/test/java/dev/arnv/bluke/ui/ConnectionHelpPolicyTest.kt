package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.HidFailure
import dev.arnv.bluke.bluetooth.HidLifecycleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionHelpPolicyTest {
    @Test fun settingsPairingAdviceOnlyAppearsAfterAHostConnectionFailure() {
        val failed = HidLifecycleState.Error(HidFailure.CONNECTION_REJECTED)
        assertTrue(shouldOfferSettingsPairingHelp(BluetoothState.PairingMode("Bluke"), failed))
        assertTrue(shouldOfferSettingsPairingHelp(BluetoothState.ReadyDisconnected, failed))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.Connected("Host"), failed))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.BluetoothOff, failed))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.ProfileNotSupported, failed))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.PairingMode("Bluke"), HidLifecycleState.Connecting("host")))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.ReadyDisconnected, HidLifecycleState.Error(HidFailure.REGISTRATION_TIMEOUT)))
        assertFalse(shouldOfferSettingsPairingHelp(BluetoothState.PairingMode("Bluke"), HidLifecycleState.Registered))
    }

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
