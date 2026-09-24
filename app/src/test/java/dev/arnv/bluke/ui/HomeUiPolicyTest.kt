package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.BluetoothState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiPolicyTest {
    @Test
    fun launchControlsAreHiddenForBlockingBluetoothStates() {
        assertTrue(BluetoothState.BluetoothOff.blocksInputLaunch())
        assertTrue(BluetoothState.Unsupported.blocksInputLaunch())
        assertTrue(BluetoothState.ProfileNotSupported.blocksInputLaunch())
        assertFalse(BluetoothState.ReadyDisconnected.blocksInputLaunch())
        assertFalse(BluetoothState.PairingMode("host").blocksInputLaunch())
    }

    @Test
    fun incompatibilityScreenReplacesDuplicateErrorToast() {
        assertFalse(
            shouldShowBluetoothErrorToast(
                BluetoothState.ProfileNotSupported,
                "Android repeatedly rejected HID Device registration"
            )
        )
        assertTrue(
            shouldShowBluetoothErrorToast(
                BluetoothState.ReadyDisconnected,
                "Connection refused by the host"
            )
        )
    }
}
