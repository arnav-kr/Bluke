package dev.arnv.bluke.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.arnv.bluke.R
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

sealed class BluetoothState {
    object Unsupported : BluetoothState()
    object PermissionRequired : BluetoothState()
    object BluetoothOff : BluetoothState()
    object ProfileNotSupported : BluetoothState()
    object ReadyDisconnected : BluetoothState()
    data class PairingMode(val name: String) : BluetoothState()
    data class Connected(val deviceName: String) : BluetoothState()
}

class BluetoothKeyboardManager(private val context: Context) {

    private val _serviceState = MutableStateFlow<BluetoothState>(BluetoothState.ReadyDisconnected)
    val serviceState: StateFlow<BluetoothState> = _serviceState

    private val _statusMessage = MutableStateFlow("Initializing Bluetooth Controller...")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _targetDeviceAddress = MutableStateFlow<String?>(null)
    val targetDeviceAddress: StateFlow<String?> = _targetDeviceAddress

    // Device lists for scan / connect UI
    private val _bondedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDevice>> = _bondedDevices

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _capsLockState = MutableStateFlow(false)
    val capsLockState: StateFlow<Boolean> = _capsLockState

    private val _numLockState = MutableStateFlow(true)
    val numLockState: StateFlow<Boolean> = _numLockState

    private val _scrollLockState = MutableStateFlow(false)
    val scrollLockState: StateFlow<Boolean> = _scrollLockState

    private val bluetoothAdapter: BluetoothAdapter? = try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    } catch (e: Exception) {
        null
    }

    private var hidDeviceProfile: BluetoothHidDevice? = null
    private var isAppRegistered = false
    private var lastConnectedDevice: BluetoothDevice? = null
    private val _connectedDevice = kotlinx.coroutines.flow.MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: kotlinx.coroutines.flow.StateFlow<BluetoothDevice?> = _connectedDevice

    private val executor = Executors.newSingleThreadExecutor()
    private var isReceiverRegistered = false
    private var isBondReceiverRegistered = false

    private val bondStateReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                checkBluetoothCapabilities()
            } else if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                
                if (device != null) {
                    val dName = device.name ?: device.address
                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            _statusMessage.value = "Pairing with '$dName'... Please accept the pairing prompt."
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            _statusMessage.value = "Pairing successful! Connecting to '$dName'..."
                            updateBondedDevices()
                            connectDevice(device)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            updateBondedDevices()
                            if (prevBondState == BluetoothDevice.BOND_BONDING) {
                                _statusMessage.value = "Pairing with '$dName' refused or failed."
                            } else {
                                _statusMessage.value = "Unpaired from '$dName'."
                            }
                        }
                    }
                }
            }
        }
    }

    private fun registerBondReceiver() {
        if (!isBondReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(bondStateReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(bondStateReceiver, filter)
                }
                isBondReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error registering bond receiver: ${e.message}", e)
            }
        }
    }

    // 8-byte Keyboard HID report parameters
    private val reportId = 1
    private var activeModifiers = 0
    private val activeKeys = ByteArray(6) { 0 }

    // Discovery receiver to catch found devices and scanning events
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val currentList = _scannedDevices.value
                        if (!currentList.any { it.address == device.address }) {
                            _scannedDevices.value = currentList + device
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    // Standard Keyboard HID Descriptor definition (Wired/Bluetooth Keyboard standard)
    private val hidDescriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),         // USAGE (Keyboard)
        0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(),         //   REPORT_ID (1)
        0x05.toByte(), 0x07.toByte(),         //   USAGE_PAGE (Keyboard)
        0x19.toByte(), 0xe0.toByte(),         //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29.toByte(), 0xe7.toByte(),         //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15.toByte(), 0x00.toByte(),         //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),         //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),         //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(),         //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(),         //   INPUT (Data,Var,Abs) - Modifier byte
        0x95.toByte(), 0x01.toByte(),         //   REPORT_COUNT (1)
        0x75.toByte(), 0x08.toByte(),         //   REPORT_SIZE (8)
        0x81.toByte(), 0x03.toByte(),         //   INPUT (Cnst,Var,Abs) - Reserved byte
        0x95.toByte(), 0x05.toByte(),         //   REPORT_COUNT (5)
        0x75.toByte(), 0x01.toByte(),         //   REPORT_SIZE (1)
        0x05.toByte(), 0x08.toByte(),         //   USAGE_PAGE (LEDs)
        0x19.toByte(), 0x01.toByte(),         //   USAGE_MINIMUM (Num Lock)
        0x29.toByte(), 0x05.toByte(),         //   USAGE_MAXIMUM (Kana)
        0x91.toByte(), 0x02.toByte(),         //   OUTPUT (Data,Var,Abs) - LED report
        0x95.toByte(), 0x01.toByte(),         //   REPORT_COUNT (1)
        0x75.toByte(), 0x03.toByte(),         //   REPORT_SIZE (3)
        0x91.toByte(), 0x03.toByte(),         //   OUTPUT (Cnst,Var,Abs) - LED report padding
        0x95.toByte(), 0x06.toByte(),         //   REPORT_COUNT (6)
        0x75.toByte(), 0x08.toByte(),         //   REPORT_SIZE (8)
        0x15.toByte(), 0x00.toByte(),         //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x65.toByte(),         //   LOGICAL_MAXIMUM (101)
        0x05.toByte(), 0x07.toByte(),         //   USAGE_PAGE (Keyboard)
        0x19.toByte(), 0x00.toByte(),         //   USAGE_MINIMUM (Reserved)
        0x29.toByte(), 0x65.toByte(),         //   USAGE_MAXIMUM (Keyboard Application)
        0x81.toByte(), 0x00.toByte(),         //   INPUT (Data,Ary,Abs) - Keycodes (6 bytes)
        0xc0.toByte()                         // END_COLLECTION
    )

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "KBSim Keyboard",
        "KBSim Mechanical Keyboard Controller",
        "KBSim Inc",
        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
        hidDescriptor
    )

    init {
        checkBluetoothCapabilities()
        registerBondReceiver()
    }

    fun checkBluetoothCapabilities() {
        if (bluetoothAdapter == null) {
            _serviceState.value = BluetoothState.Unsupported
            _statusMessage.value = "Bluetooth is not supported on this device's hardware."
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _serviceState.value = BluetoothState.BluetoothOff
            _statusMessage.value = "Bluetooth is currently turned off. Please enable Bluetooth."
            hidDeviceProfile = null
            isAppRegistered = false
            return
        }

        // Check permissions on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasAdvertise = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasScan = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasConnect || !hasAdvertise || !hasScan) {
                _serviceState.value = BluetoothState.PermissionRequired
                _statusMessage.value = "Bluetooth Connect, Advertise & Scan permissions are required."
                return
            }
        }

        updateBondedDevices()
        // Initialize HID Device Profile safely
        val hid = hidDeviceProfile
        if (hid == null) {
            initProfileListener()
        } else if (!isAppRegistered) {
            registerApp()
        } else {
            // Already initialized and registered. Sync connection state.
            try {
                val connectedDevs = hid.connectedDevices
                if (!connectedDevs.isNullOrEmpty()) {
                    val activeDev = connectedDevs.first()
                    _connectedDevice.value = activeDev
                    lastConnectedDevice = activeDev
                    _serviceState.value = BluetoothState.Connected(activeDev.name ?: "Paired Host")
                    _statusMessage.value = "Link established with '${activeDev.name ?: "Host"}'! Keyboard active."
                } else {
                    _connectedDevice.value = null
                    _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter.name ?: context.getString(R.string.app_name))
                    _statusMessage.value = "Custom HID Deck is ready and advertising."
                }
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error restoring connected devices", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun updateBondedDevices() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                _bondedDevices.value = bluetoothAdapter.bondedDevices.toList()
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error listing bonded devices", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        _scannedDevices.value = emptyList()

        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(discoveryReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error registering discovery receiver: ${e.message}", e)
                _statusMessage.value = "Failed to register scanner: ${e.localizedMessage}"
            }
        }

        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            val started = bluetoothAdapter.startDiscovery()
            if (started) {
                _isScanning.value = true
                _statusMessage.value = "Scanning for other Bluetooth hosts..."
            } else {
                _statusMessage.value = "Failed to start Bluetooth discovery scanning."
            }
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error during discovery initialization", e)
            _statusMessage.value = "Scanning error: ${e.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (bluetoothAdapter == null) return
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error stopping discovery", e)
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: BluetoothDevice) {
        stopScanning()
        val dName = device.name ?: device.address
        _statusMessage.value = "Requesting Bluetooth Pairing with '$dName'..."
        try {
            val success = device.createBond()
            if (success) {
                _statusMessage.value = "Pairing requested. Approve prompt on '$dName'."
            } else {
                _statusMessage.value = "Failed to start pairing request for '$dName'."
            }
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error calling createBond", e)
            _statusMessage.value = "Pairing failed: ${e.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice, skipDisconnect: Boolean = false) {
        lastConnectedDevice = device
        val hid = hidDeviceProfile
        if (hid == null) {
            _statusMessage.value = "HID Service profile proxy is not ready."
            return
        }
        
        stopScanning()
        val dName = device.name ?: device.address

        // Automatically start credentials pairing if not already paired
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            _statusMessage.value = "Credentials required. Swapping to pairing mode with '$dName'..."
            pairDevice(device)
            return
        }

        _statusMessage.value = "Connecting to '$dName'..."

        executor.execute {
            try {
                // Only disconnect first when explicitly requested (e.g. user-initiated reconnect).
                // Skipping disconnect when restoring after proxy rebind avoids terminating a
                // still-active Bluetooth HID connection.
                if (!skipDisconnect) {
                    try {
                        hid.disconnect(device)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    try {
                        Thread.sleep(150)
                    } catch (e: InterruptedException) {
                        // Ignore
                    }
                }
                
                val success = hid.connect(device)
                if (success) {
                    _statusMessage.value = "Connecting to '$dName'..."
                } else {
                    _statusMessage.value = "Negotiation failed. Retrying connection..."
                    try {
                        Thread.sleep(250)
                    } catch (e: InterruptedException) {
                        // Ignore
                    }
                    val retrySuccess = hid.connect(device)
                    if (retrySuccess) {
                        _statusMessage.value = "Connecting to '$dName'..."
                    } else {
                        _statusMessage.value = "Host rejected link. Select again or toggle Bluetooth."
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error in connectDevice in background: ${e.localizedMessage}", e)
                _statusMessage.value = "Failed to initiate link: ${e.localizedMessage}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        val dev = _connectedDevice.value
        val hid = hidDeviceProfile
        if (dev != null && hid != null) {
            _statusMessage.value = "Sending disconnect request..."
            try {
                hid.disconnect(dev)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error in hid.disconnect", e)
            }
        }
        _connectedDevice.value = null
        lastConnectedDevice = null
        _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
        updateBondedDevices()
    }

    private fun initProfileListener() {
        _statusMessage.value = "Connecting to HID service profile proxy..."
        try {
            val success = bluetoothAdapter?.getProfileProxy(
                context,
                profileListener,
                BluetoothProfile.HID_DEVICE
            ) ?: false

            if (!success) {
                _serviceState.value = BluetoothState.ProfileNotSupported
                _statusMessage.value = "Bluetooth HID Device Profile not supported on this hardware."
            }
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error calling getProfileProxy", e)
            _serviceState.value = BluetoothState.ProfileNotSupported
            _statusMessage.value = "Failed to access Bluetooth HID service: ${e.localizedMessage}"
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                val hid = proxy as BluetoothHidDevice
                hidDeviceProfile = hid
                
                // Attempt to restore connected state from active proxy connections
                try {
                    val connectedDevs = hid.connectedDevices
                    if (!connectedDevs.isNullOrEmpty()) {
                        val activeDev = connectedDevs.first()
                        _connectedDevice.value = activeDev
                        _serviceState.value = BluetoothState.Connected(activeDev.name ?: "Paired Host")
                        _statusMessage.value = "Link established with '${activeDev.name ?: "Host"}'! Keyboard active."
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothKeyboard", "Error restoring connected devices", e)
                }
                
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDeviceProfile = null
                isAppRegistered = false
                // Don't clear _connectedDevice here — the BT link itself may still be alive.
                // The proxy can rebind and re-report the connection. We'll get the definitive
                // STATE_DISCONNECTED via onConnectionStateChanged if the link actually drops.
                _statusMessage.value = "HID Service Proxy disconnected. Rebinding..."
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            isAppRegistered = registered
            if (registered) {
                updateBondedDevices()
                val connectedDevs = hidDeviceProfile?.connectedDevices
                val activeDev = connectedDevs?.firstOrNull()
                if (activeDev != null) {
                    _connectedDevice.value = activeDev
                    lastConnectedDevice = activeDev
                    _statusMessage.value = "Link established with '${activeDev.name ?: "Host"}'! Keyboard active."
                    _serviceState.value = BluetoothState.Connected(activeDev.name ?: "Paired Host")
                } else {
                    _connectedDevice.value = null
                    _statusMessage.value = "Custom HID Deck is ready and advertising."
                    _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
                }
            } else {
                _statusMessage.value = "HID keyboard service registration failed or stopped."
                _serviceState.value = BluetoothState.ReadyDisconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevice.value = device
                    lastConnectedDevice = device
                    _serviceState.value = BluetoothState.Connected(device.name ?: "Paired Host")
                    _statusMessage.value = "Link established with '${device.name ?: "Host"}'! Keyboard active."
                    updateBondedDevices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevice.value = null
                    _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
                    _statusMessage.value = "Link detached. Ready for incoming / outgoing pairing."
                    updateBondedDevices()
                }
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            super.onSetReport(device, type, id, data)
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id == 1.toByte()) {
                parseLedReport(data)
            }
            try {
                hidDeviceProfile?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Failed to send reportError success: $e")
            }
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            super.onInterruptData(device, reportId, data)
            if (reportId == 1.toByte()) {
                parseLedReport(data)
            }
        }

        private fun parseLedReport(data: ByteArray?) {
            if (data == null || data.isEmpty()) return
            val ledByte = if (data.size > 1 && data[0] == 1.toByte()) {
                data[1].toInt()
            } else {
                data[0].toInt()
            }
            _numLockState.value = (ledByte and 0x01) != 0
            _capsLockState.value = (ledByte and 0x02) != 0
            _scrollLockState.value = (ledByte and 0x04) != 0
            Log.d("BluetoothKeyboard", "Received LED report: byte=$ledByte, caps=${_capsLockState.value}, num=${_numLockState.value}, scroll=${_scrollLockState.value}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDeviceProfile ?: return
        try {
            _statusMessage.value = "Registering Bluetooth HID application profile..."
            hid.registerApp(sdpSettings, null, null, executor, hidCallback)
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error during app registration", e)
            _statusMessage.value = "Registration crash: ${e.localizedMessage}. Entering local simulation fallback."
            _serviceState.value = BluetoothState.ProfileNotSupported
        }
    }

    @SuppressLint("MissingPermission")
    fun restartHidService() {
        val hid = hidDeviceProfile
        if (hid == null) {
            initProfileListener()
            return
        }
        try {
            _statusMessage.value = "Unregistering HID profile..."
            hid.unregisterApp()
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error during unregister", e)
        }
        // Small delay if possible or just try immediately:
        try {
            hid.registerApp(sdpSettings, null, null, executor, hidCallback)
            _statusMessage.value = "Restarted local HID Service."
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun sendKey(keyCode: Int, isPress: Boolean) {
        val dev = _connectedDevice.value
        val hid = hidDeviceProfile
        
        // Update local HID state variables (Modifiers or standard key codes)
        if (keyCode >= 0xE0 && keyCode <= 0xE7) {
            // It's a modifier key (Left Ctrl to Right GUI)
            val bitMask = 1 shl (keyCode - 0xE0)
            if (isPress) {
                activeModifiers = activeModifiers or bitMask
            } else {
                activeModifiers = activeModifiers and bitMask.inv()
            }
        } else {
            // It's a standard key
            if (isPress) {
                // Find empty slot (0x00) or check if already placed
                var placed = false
                for (j in 0 until 6) {
                    if (activeKeys[j] == keyCode.toByte()) {
                        placed = true
                        break
                    }
                }
                if (!placed) {
                    for (j in 0 until 6) {
                        if (activeKeys[j] == 0.toByte()) {
                            activeKeys[j] = keyCode.toByte()
                            break
                        }
                    }
                }
            } else {
                // Key release: remove from slots and shift left
                for (j in 0 until 6) {
                    if (activeKeys[j] == keyCode.toByte()) {
                        activeKeys[j] = 0.toByte()
                    }
                }
                // Compact active keys
                val compact = ByteArray(6) { 0 }
                var writeIdx = 0
                for (j in 0 until 6) {
                    if (activeKeys[j] != 0.toByte()) {
                        compact[writeIdx++] = activeKeys[j]
                    }
                }
                compact.copyInto(activeKeys)
            }
        }

        // Package report: 8 bytes
        // byte 0: Modifiers
        // byte 1: Reserved (0x00)
        // bytes 2-7: Scancodes
        val report = ByteArray(8)
        report[0] = activeModifiers.toByte()
        report[1] = 0x00.toByte()
        for (j in 0 until 6) {
            report[j + 2] = activeKeys[j]
        }

        // Transmit HID report
        if (dev != null && hid != null) {
            try {
                hid.sendReport(dev, reportId, report)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error transmitting HID report", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScanning()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
        if (isBondReceiverRegistered) {
            try {
                context.unregisterReceiver(bondStateReceiver)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error unregistering bond receiver", e)
            }
            isBondReceiverRegistered = false
        }
        val hid = hidDeviceProfile
        if (hid != null) {
            try {
                hid.unregisterApp()
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error during app unregistration", e)
            }
        }
        try {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error closing profile proxy", e)
        }
        hidDeviceProfile = null
        isAppRegistered = false
        lastConnectedDevice = null
        _connectedDevice.value = null
    }
}
