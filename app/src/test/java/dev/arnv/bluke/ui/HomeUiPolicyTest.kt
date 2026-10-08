package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.BluetoothState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiPolicyTest {
    @org.junit.Test fun discoveryReconcilesByAddressNotDeviceName() {
        org.junit.Assert.assertFalse(shouldShowDiscoveredHost("A", setOf("A"), null))
        org.junit.Assert.assertFalse(shouldShowDiscoveredHost("B", emptySet(), "B"))
        org.junit.Assert.assertTrue(shouldShowDiscoveredHost("B", setOf("A"), null))
    }
    @Test
    fun gamepadEntryRequiresAcknowledgementRegardlessOfEntryPoint() {
        assertTrue(shouldShowGamepadGuide(InputMode.GAMEPAD.id, guideSeen = false))
        assertFalse(shouldShowGamepadGuide(InputMode.GAMEPAD.id, guideSeen = true))
        assertFalse(shouldShowGamepadGuide(InputMode.KEYBOARD.id, guideSeen = false))
        assertFalse(shouldShowGamepadGuide(InputMode.TOUCHPAD.id, guideSeen = false))
    }

    @Test
    fun launchControlsAreHiddenForBlockingBluetoothStates() {
        assertTrue(BluetoothState.InitializingCapabilities.blocksInputLaunch())
        assertTrue(BluetoothState.CheckingCapabilities.blocksInputLaunch())
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
                BluetoothState.InitializingCapabilities,
                "Previous registration failed"
            )
        )
        assertFalse(
            shouldShowBluetoothErrorToast(
                BluetoothState.CheckingCapabilities,
                "Previous registration failed"
            )
        )
        assertFalse(
            shouldShowBluetoothErrorToast(
                BluetoothState.ProfileNotSupported,
                "Android repeatedly rejected HID Device registration"
            )
        )
        assertFalse(
            shouldShowBluetoothErrorToast(
                BluetoothState.ReadyDisconnected,
                "This device appears incompatible: Android repeatedly rejected HID Device registration."
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
