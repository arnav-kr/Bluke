package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.BluetoothState

internal fun shouldShowDiscoveredHost(address: String, bondedAddresses: Set<String>, activeAddress: String?): Boolean =
    address !in bondedAddresses && address != activeAddress

internal fun shouldShowGamepadGuide(mode: Int, guideSeen: Boolean): Boolean =
    mode == InputMode.GAMEPAD.id && !guideSeen

internal fun BluetoothState.blocksInputLaunch(): Boolean =
    this is BluetoothState.InitializingCapabilities ||
        this is BluetoothState.CheckingCapabilities ||
        this is BluetoothState.BluetoothOff ||
        this is BluetoothState.Unsupported ||
        this is BluetoothState.ProfileNotSupported

internal fun shouldShowBluetoothErrorToast(
    bluetoothState: BluetoothState,
    message: String
): Boolean {
    if (bluetoothState is BluetoothState.InitializingCapabilities ||
        bluetoothState is BluetoothState.CheckingCapabilities ||
        bluetoothState is BluetoothState.ProfileNotSupported
    ) return false

    val normalizedMessage = message.lowercase()
    if (normalizedMessage.contains("appears incompatible") ||
        normalizedMessage.contains("repeatedly rejected hid")
    ) return false

    return normalizedMessage.contains("timed out") ||
        normalizedMessage.contains("rejected") ||
        normalizedMessage.contains("failed") ||
        normalizedMessage.contains("refused") ||
        normalizedMessage.contains("error")
}
