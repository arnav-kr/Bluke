package dev.arnv.bluke

import dev.arnv.bluke.bluetooth.BluetoothState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSessionPolicyTest {
    @Test fun backgroundPairingHandshakeKeepsServiceAliveUntilCompletion() {
        assertTrue(shouldKeepBluetoothSession(false, false, BluetoothState.PairingMode("Phone"), true))
        assertFalse(shouldKeepBluetoothSession(false, false, BluetoothState.BluetoothOff, true))
    }
    @Test
    fun backgroundingKeepsAnEstablishedConnectionAlive() {
        assertTrue(shouldKeepBluetoothSession(false, true, BluetoothState.Connected("Host")))
    }

    @Test
    fun idleBackgroundSessionStopsButVisiblePairingStaysAvailable() {
        assertFalse(shouldKeepBluetoothSession(false, false, BluetoothState.PairingMode("Phone")))
        assertTrue(shouldKeepBluetoothSession(true, false, BluetoothState.PairingMode("Phone")))
    }

    @Test
    fun LostPermissionBluetoothOffAndIncompatibilityAlwaysStopSession() {
        for (state in listOf(BluetoothState.BluetoothOff, BluetoothState.PermissionRequired,
            BluetoothState.Unsupported, BluetoothState.ProfileNotSupported)) {
            assertFalse(shouldKeepBluetoothSession(true, true, state))
            assertFalse(shouldKeepBluetoothSession(false, true, state))
        }
    }
}
