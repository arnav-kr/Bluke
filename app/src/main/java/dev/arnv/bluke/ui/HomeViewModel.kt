package dev.arnv.bluke.ui

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.HidLifecycleState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val bluetoothState: BluetoothState = BluetoothState.ReadyDisconnected,
    val statusMessage: String = "Initializing Bluetooth Controller...",
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectedDevice: BluetoothDevice? = null,
    val capsLock: Boolean = false,
    val numLock: Boolean = true,
    val scrollLock: Boolean = false,
    val hidLifecycleState: HidLifecycleState = HidLifecycleState.Idle,
)

class HomeViewModel(manager: BluetoothKeyboardManager) : ViewModel() {
    private data class DiscoveryState(
        val bluetoothState: BluetoothState,
        val statusMessage: String,
        val bondedDevices: List<BluetoothDevice>,
        val scannedDevices: List<BluetoothDevice>,
        val isScanning: Boolean,
    )

    private data class ConnectionState(
        val connectedDevice: BluetoothDevice?,
        val capsLock: Boolean,
        val numLock: Boolean,
        val scrollLock: Boolean,
        val hidLifecycleState: HidLifecycleState,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            manager.serviceState,
            manager.statusMessage,
            manager.bondedDevices,
            manager.scannedDevices,
            manager.isScanning,
            ::DiscoveryState,
        ),
        combine(
            manager.connectedDevice,
            manager.capsLockState,
            manager.numLockState,
            manager.scrollLockState,
            manager.lifecycleState,
            ::ConnectionState,
        ),
    ) { discovery, connection ->
        HomeUiState(
            bluetoothState = discovery.bluetoothState,
            statusMessage = discovery.statusMessage,
            bondedDevices = discovery.bondedDevices,
            scannedDevices = discovery.scannedDevices,
            isScanning = discovery.isScanning,
            connectedDevice = connection.connectedDevice,
            capsLock = connection.capsLock,
            numLock = connection.numLock,
            scrollLock = connection.scrollLock,
            hidLifecycleState = connection.hidLifecycleState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeUiState(
            bluetoothState = manager.serviceState.value,
            statusMessage = manager.statusMessage.value,
            bondedDevices = manager.bondedDevices.value,
            scannedDevices = manager.scannedDevices.value,
            isScanning = manager.isScanning.value,
            connectedDevice = manager.connectedDevice.value,
            capsLock = manager.capsLockState.value,
            numLock = manager.numLockState.value,
            scrollLock = manager.scrollLockState.value,
            hidLifecycleState = manager.lifecycleState.value,
        ),
    )

    companion object {
        fun factory(manager: BluetoothKeyboardManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(manager) as T
                }
            }
    }
}
