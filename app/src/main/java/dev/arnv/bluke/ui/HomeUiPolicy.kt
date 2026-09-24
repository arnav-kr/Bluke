package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.BluetoothState

internal fun BluetoothState.blocksInputLaunch(): Boolean =
    this is BluetoothState.BluetoothOff ||
        this is BluetoothState.Unsupported ||
        this is BluetoothState.ProfileNotSupported

internal fun shouldShowBluetoothErrorToast(
    bluetoothState: BluetoothState,
    message: String
): Boolean {
    if (bluetoothState is BluetoothState.ProfileNotSupported) return false

    val normalizedMessage = message.lowercase()
    return normalizedMessage.contains("timed out") ||
        normalizedMessage.contains("rejected") ||
        normalizedMessage.contains("failed") ||
        normalizedMessage.contains("refused") ||
        normalizedMessage.contains("error")
}
