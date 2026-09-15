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
import dev.arnv.bluke.utils.DeveloperLogManager
import dev.arnv.bluke.utils.LogType
import androidx.core.content.edit
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.BufferOverflow

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

    private val reportExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
            runnable.run()
        }, "bt-report-sender")
    }

    @SuppressLint("MissingPermission")
    private fun submitReport(dev: BluetoothDevice, reportId: Int, report: ByteArray) {
        val hid = hidDeviceProfile
        if (hid != null) {
            reportExecutor.submit {
                try {
                    DeveloperLogManager.log(
                        "BluetoothKeyboard",
                        "sendReport ID=0x${reportId.toString(16)} Data=[${report.joinToString(" ") { String.format("%02X", it) }}]",
                        LogType.BLUETOOTH_PACKET
                    )
                    hid.sendReport(dev, reportId, report)
                } catch (e: Exception) {
                    Log.e("BluetoothKeyboard", "Error transmitting HID report ID $reportId", e)
                }
            }
        }
    }

    private val _serviceState = MutableStateFlow<BluetoothState>(BluetoothState.ReadyDisconnected)
    val serviceState: StateFlow<BluetoothState> = _serviceState

    private val _statusMessage = MutableStateFlow("Initializing Bluetooth Controller...")
    val statusMessage: StateFlow<String> = _statusMessage



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
    } catch (_: Exception) {
        null
    }

    private var hidDeviceProfile: BluetoothHidDevice? = null
    // removed bare isAppRegistered primitive in favor of thread-safe appRegistrationState
    private var lastConnectedDevice: BluetoothDevice? = null
    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "bt-manager-scheduler")
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())
    private val appRegistrationState = MutableStateFlow(false)
    @Volatile private var isRegisteringInProcess = false
    private val isAppRegistered: Boolean get() = appRegistrationState.value
    private val connectionStateFlow = MutableSharedFlow<Pair<BluetoothDevice, Int>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var connectionTimeoutFuture: java.util.concurrent.ScheduledFuture<*>? = null
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
                            connectDevice(device, delayMs = 1500)
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
    private val activeKeys = ByteArray(6)

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

    // Composite Keyboard, Mouse/Trackpad & Gamepad HID Descriptor definition
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
        0xc0.toByte(),                        // END_COLLECTION

        // Mouse/Trackpad (Report ID 2)
        0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),         // USAGE (Mouse)
        0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
        0x85.toByte(), 0x02.toByte(),         //   REPORT_ID (2)
        0x09.toByte(), 0x01.toByte(),         //   USAGE (Pointer)
        0xa1.toByte(), 0x00.toByte(),         //   COLLECTION (Physical)
        0x05.toByte(), 0x09.toByte(),         //     USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),         //     USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x03.toByte(),         //     USAGE_MAXIMUM (Button 3)
        0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),         //     LOGICAL_MAXIMUM (1)
        0x95.toByte(), 0x03.toByte(),         //     REPORT_COUNT (3)
        0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
        0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - L, R, M clicks
        0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
        0x75.toByte(), 0x05.toByte(),         //     REPORT_SIZE (5)
        0x81.toByte(), 0x03.toByte(),         //     INPUT (Cnst,Var,Abs) - padding
        0x05.toByte(), 0x01.toByte(),         //     USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),         //     USAGE (X)
        0x09.toByte(), 0x31.toByte(),         //     USAGE (Y)
        0x15.toByte(), 0x81.toByte(),         //     LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7f.toByte(),         //     LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),         //     REPORT_SIZE (8)
        0x95.toByte(), 0x02.toByte(),         //     REPORT_COUNT (2)
        0x81.toByte(), 0x06.toByte(),         //     INPUT (Data,Var,Rel) - delta X and Y movement
        0x09.toByte(), 0x38.toByte(),         //     USAGE (Wheel)
        0x15.toByte(), 0x81.toByte(),         //     LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7f.toByte(),         //     LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),         //     REPORT_SIZE (8)
        0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
        0x81.toByte(), 0x06.toByte(),         //     INPUT (Data,Var,Rel) - scroll wheel
        0xc0.toByte(),                        //   END_COLLECTION
        0xc0.toByte(),                        // END_COLLECTION

        // Gamepad (Report ID 3)
        0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),         // USAGE (Gamepad)
        0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
        0x85.toByte(), 0x03.toByte(),         //   REPORT_ID (3)
        0x05.toByte(), 0x09.toByte(),         //   USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),         //     USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x12.toByte(),         //     USAGE_MAXIMUM (Button 18)
        0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),         //     LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
        0x95.toByte(), 0x12.toByte(),         //     REPORT_COUNT (18)
        0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - 18 Buttons
        0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
        0x95.toByte(), 0x06.toByte(),         //     REPORT_COUNT (6)
        0x81.toByte(), 0x03.toByte(),         //     INPUT (Cnst,Var,Abs) - padding to 3 bytes
        0x05.toByte(), 0x01.toByte(),         //     USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),         //     USAGE (X) - Left Stick X
        0x09.toByte(), 0x31.toByte(),         //     USAGE (Y) - Left Stick Y
        0x09.toByte(), 0x32.toByte(),         //     USAGE (Rx) - Right Stick X
        0x09.toByte(), 0x33.toByte(),         //     USAGE (Ry) - Right Stick Y
        0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
        0x27.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (65535)
        0x75.toByte(), 0x10.toByte(),         //     REPORT_SIZE (16)
        0x95.toByte(), 0x04.toByte(),         //     REPORT_COUNT (4)
        0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - 4 16-bit Axes (X, Y, Rx, Ry)
        0xc0.toByte()                         // END_COLLECTION (Application)
    )

    private val sdpSettings: BluetoothHidDeviceAppSdpSettings? by lazy {
        try {
            BluetoothHidDeviceAppSdpSettings(
                "Bluke",                         // Name
                "Wireless Controller Combo",    // Description
                "Bluke",                         // Provider
                BluetoothHidDevice.SUBCLASS1_COMBO, // Subclass
                hidDescriptor                    // Descriptor
            )
        } catch (e: Throwable) {
            Log.e("BlukeBT", "Failed to create BluetoothHidDeviceAppSdpSettings", e)
            null
        }
    }

    private val sharedPrefs = context.getSharedPreferences("bluetooth_keyboard_prefs", Context.MODE_PRIVATE)

    private var lastConnectedDeviceAddress: String?
        get() = sharedPrefs.getString("last_connected_device_address", null)
        set(value) {
            if (value == null) {
                sharedPrefs.edit { remove("last_connected_device_address") }
            } else {
                sharedPrefs.edit { putString("last_connected_device_address", value) }
            }
        }

    private var pendingConnectAfterRestart: BluetoothDevice? = null
    private var audioProfilesDisconnectedForSession = false

    init {
        try {
            checkBluetoothCapabilities()
            registerBondReceiver()
        } catch (e: Throwable) {
            Log.e("BluetoothKeyboard", "Error during init: ${e.message}", e)
            _serviceState.value = BluetoothState.ProfileNotSupported
            _statusMessage.value = "Bluetooth HID profile is not supported on this device firmware."
        }
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
            appRegistrationState.value = false
            return
        }

        // Check permissions on API 31+ (BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasAdvertise = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasScan = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasConnect || !hasAdvertise || !hasScan) {
                _serviceState.value = BluetoothState.PermissionRequired
                _statusMessage.value = "Bluetooth Connect, Advertise & Scan permissions are required."
                return
            }
        } else {
            // On Android 9 and 10 (API 28–30), ACCESS_FINE_LOCATION is required at runtime for
            // Bluetooth device scanning and HID profile operations. Without it, getProfileProxy
            // and startDiscovery may silently do nothing with no error in logcat.
            val hasLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                              context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasLocation) {
                _serviceState.value = BluetoothState.PermissionRequired
                _statusMessage.value = "Location permission is required on Android 10 and earlier to use Bluetooth."
                Log.w("BluetoothKeyboard", "Missing ACCESS_FINE_LOCATION on API ${Build.VERSION.SDK_INT} — Bluetooth HID will not work.")
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
                    _statusMessage.value = "Bluke Bluetooth Deck is ready and advertising."
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
    private fun scheduleConnectionTimeout(device: BluetoothDevice) {
        connectionTimeoutFuture?.cancel(false)
        connectionTimeoutFuture = executor.schedule({
            if (_connectedDevice.value == null && 
                _statusMessage.value.contains("Connecting", ignoreCase = true)) {
                _statusMessage.value = "Connection timed out. Please try again."
                Log.w("BluetoothKeyboard", "Connection to ${device.name ?: device.address} timed out.")
            }
        }, 10, java.util.concurrent.TimeUnit.SECONDS)
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice, skipDisconnect: Boolean = false, delayMs: Long = 0) {
        lastConnectedDevice = device
        val hid = hidDeviceProfile
        if (hid == null) {
            // The Bluetooth HID proxy hasn't bound yet (getProfileProxy is async and can take 1-2s
            // on first launch or after BT toggle). Rather than silently dropping the user's intent,
            // queue this device and retry as soon as the proxy is ready via onAppStatusChanged.
            Log.d("BluetoothKeyboard", "HID proxy not ready — queuing connect to ${device.name ?: device.address}")
            _statusMessage.value = "Waiting for Bluetooth HID service... Will connect shortly."
            pendingConnectAfterRestart = device
            if (!isAppRegistered) {
                initProfileListener()
            }
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

        // If a different device is currently connected, disconnect it first via service restart,
        // then connect the new device after re-registration completes.
        val currentlyConnected = _connectedDevice.value
        if (currentlyConnected != null && currentlyConnected.address != device.address) {
            Log.d("BluetoothKeyboard", "Switching connection from '${currentlyConnected.name ?: currentlyConnected.address}' to '$dName'")
            _statusMessage.value = "Switching to '$dName'..."
            pendingConnectAfterRestart = device
            restartHidService()
            return
        }

        _statusMessage.value = "Connecting to '$dName'..."
        connectionTimeoutFuture?.cancel(false)

        managerScope.launch {
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }

                if (!skipDisconnect) {
                    val isCurrentlyConnected = try {
                        hid.connectedDevices?.contains(device) == true
                    } catch (e: Exception) {
                        false
                    }
                    if (isCurrentlyConnected) {
                        try {
                            val disconnectJob = async {
                                connectionStateFlow.first { it.first.address == device.address && it.second == BluetoothProfile.STATE_DISCONNECTED }
                            }
                            hid.disconnect(device)
                            // Wait securely for the native OS callback to confirm the L2CAP socket is released
                            withTimeoutOrNull(3000) {
                                disconnectJob.await()
                            }
                        } catch (e: Exception) {
                            Log.e("BluetoothKeyboard", "Error during disconnect before connect", e)
                        }
                    }
                }
                // Wait securely for the OS to finish registering the profile before attempting to connect.
                // You cannot connect to a device on a profile that is not yet registered.
                if (!appRegistrationState.value) {
                    Log.d("BluetoothKeyboard", "App is not registered yet, suspending connectDevice until onAppStatusChanged(true)...")
                    withTimeoutOrNull(3000) {
                        appRegistrationState.first { it }
                    }
                }

                Log.d("BluetoothKeyboard", "Calling hid.connect($dName), proxy=${hid}")
                val success = hid.connect(device)
                Log.d("BluetoothKeyboard", "hid.connect($dName) returned: $success")
                if (success) {
                    _statusMessage.value = "Connecting to '$dName'..."
                    scheduleConnectionTimeout(device)
                } else {
                    Log.w("BluetoothKeyboard", "hid.connect returned false for $dName, retrying after 500ms")
                    _statusMessage.value = "Negotiation failed. Retrying connection..."
                    delay(500)
                    Log.d("BluetoothKeyboard", "Retry hid.connect($dName)")
                    val retrySuccess = hid.connect(device)
                    Log.d("BluetoothKeyboard", "Retry hid.connect($dName) returned: $retrySuccess")
                    if (retrySuccess) {
                        _statusMessage.value = "Connecting to '$dName'..."
                        scheduleConnectionTimeout(device)
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
        connectionTimeoutFuture?.cancel(false)
        val dev = _connectedDevice.value
        val hid = hidDeviceProfile
        lastConnectedDeviceAddress = null
        if (dev != null && hid != null) {
            _statusMessage.value = "Disconnecting physical link..."
            restartHidService()
        } else {
            _connectedDevice.value = null
            lastConnectedDevice = null
            _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
            updateBondedDevices()
        }
    }

    private fun initProfileListener() {
        _statusMessage.value = "Connecting to HID service profile proxy..."
        _serviceState.value = BluetoothState.ReadyDisconnected

        managerScope.launch {
            val hidDeviceProfileConst = 19 // BluetoothProfile.HID_DEVICE is 19
            var success = false
            for (attempt in 1..3) {
                try {
                    success = bluetoothAdapter?.getProfileProxy(
                        context,
                        profileListener,
                        hidDeviceProfileConst
                    ) ?: false
                    if (success) {
                        Log.d("BluetoothKeyboard", "getProfileProxy succeeded on attempt $attempt")
                        break
                    }
                } catch (e: Throwable) {
                    Log.w("BluetoothKeyboard", "Attempt $attempt calling getProfileProxy failed: ${e.message}")
                }
                if (attempt < 3) {
                    kotlinx.coroutines.delay(500)
                }
            }

            if (!success) {
                Log.e("BluetoothKeyboard", "getProfileProxy returned false after 3 attempts — HID Device profile absent on this firmware")
                _serviceState.value = BluetoothState.ProfileNotSupported
                _statusMessage.value = "Bluetooth HID Device profile is not supported on this device."
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                val hid = proxy as BluetoothHidDevice
                hidDeviceProfile = hid
                Log.d("BluetoothKeyboard", "HID Device profile proxy obtained — firmware supports HID peripheral role")

                // Attempt to restore connected state from active proxy connections before we unregister
                try {
                    val connectedDevs = hid.connectedDevices
                    val activeDev = connectedDevs?.firstOrNull()
                    if (activeDev != null) {
                        lastConnectedDeviceAddress = activeDev.address
                        lastConnectedDevice = activeDev
                        _connectedDevice.value = activeDev
                        _serviceState.value = BluetoothState.Connected(activeDev.name ?: "Paired Host")
                        // We intentionally DO NOT call connectDevice() here.
                        // We must wait for registerApp() to complete. 
                        // onAppStatusChanged(true) will seamlessly pick up lastConnectedDeviceAddress and connect.
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
                appRegistrationState.value = false
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
            
            DeveloperLogManager.log("BluetoothKeyboard", "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.address}")

            appRegistrationState.value = registered
            isRegisteringInProcess = false
            if (registered) {
                spoofLocalDeviceClass(bluetoothAdapter, 0x000005C0) // Spoof Class of Device to Combo Peripheral (Keyboard/Mouse)
                updateBondedDevices()
                val connectedDevs = hidDeviceProfile?.connectedDevices
                val activeDev = connectedDevs?.firstOrNull()
                if (activeDev != null) {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val isAutoConnectEnabled = prefs.getBoolean("auto_connect", true)
                    if (isAutoConnectEnabled) {
                        _connectedDevice.value = activeDev
                        lastConnectedDevice = activeDev
                        lastConnectedDeviceAddress = activeDev.address
                        _statusMessage.value = "Restoring link with '${activeDev.name ?: "Host"}'..."
                        _serviceState.value = BluetoothState.Connected(activeDev.name ?: "Paired Host")
                        
                        // Schedule clean reconnect to refresh L2CAP channels for newly registered app process
                        managerScope.launch {
                            delay(500)
                            connectDevice(activeDev, skipDisconnect = false)
                        }
                    } else {
                        _connectedDevice.value = null
                        _statusMessage.value = "Custom HID Deck is ready and advertising."
                        _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
                    }
                } else {
                    _connectedDevice.value = null
                    _statusMessage.value = "Custom HID Deck is ready and advertising."
                    _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))

                    // Defer connection attempts out of the onAppStatusChanged callback.
                    val pendingDevice = pendingConnectAfterRestart
                    if (pendingDevice != null) {
                        pendingConnectAfterRestart = null
                        Log.d("BluetoothKeyboard", "Service restarted, scheduling connect to pending switch target: ${pendingDevice.name ?: pendingDevice.address}")
                        managerScope.launch {
                            delay(600)
                            connectDevice(pendingDevice, skipDisconnect = true)
                        }
                    } else {
                        // Otherwise, check preference before auto-reconnecting to the last known device
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val isAutoConnectEnabled = prefs.getBoolean("auto_connect", true)
                        if (isAutoConnectEnabled) {
                            lastConnectedDeviceAddress?.let { addr ->
                                try {
                                    val lastDevice = bluetoothAdapter?.getRemoteDevice(addr)
                                    if (lastDevice != null && lastDevice.bondState == BluetoothDevice.BOND_BONDED) {
                                        Log.d("BluetoothKeyboard", "Scheduling auto-reconnect to last connected device: ${lastDevice.name ?: addr}")
                                        managerScope.launch {
                                            delay(600)
                                            connectDevice(lastDevice, skipDisconnect = true)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("BluetoothKeyboard", "Failed to schedule auto-reconnect to last connected device", e)
                                }
                            }
                        }
                    }
                }
            } else {
                val currentMsg = _statusMessage.value
                if (!currentMsg.contains("Disconnecting") && !currentMsg.contains("Restarting")) {
                    _statusMessage.value = "HID profile unregistered."
                }
                _serviceState.value = BluetoothState.ReadyDisconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            connectionStateFlow.tryEmit(Pair(device, state))
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionTimeoutFuture?.cancel(false)
                    _connectedDevice.value = device
                    lastConnectedDevice = device
                    lastConnectedDeviceAddress = device.address
                    _serviceState.value = BluetoothState.Connected(device.name ?: "Paired Host")
                    _statusMessage.value = "Link established with '${device.name ?: "Host"}'! Keyboard active."
                    resetKeyboardState()
                    updateBondedDevices()
                    if (!audioProfilesDisconnectedForSession) {
                        audioProfilesDisconnectedForSession = true
                        disconnectAudioProfiles(device)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionTimeoutFuture?.cancel(false)
                    _connectedDevice.value = null
                    audioProfilesDisconnectedForSession = false
                    _serviceState.value = BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name))
                    _statusMessage.value = "Link detached. Ready for incoming / outgoing pairing."
                    resetKeyboardState()
                    updateBondedDevices()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            super.onSetReport(device, type, id, data)
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT) {
                if (id == 1.toByte()) {
                    parseLedReport(data)
                }
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
        if (isAppRegistered || isRegisteringInProcess) {
            Log.d("BluetoothKeyboard", "registerApp skipped: already registered ($isAppRegistered) or in process ($isRegisteringInProcess)")
            return
        }
        isRegisteringInProcess = true
        managerScope.launch {
            try {
                _statusMessage.value = "Registering Bluetooth HID application profile..."
                
                // Unconditionally try to unregister to clean up OS state from previous app process launches
                try {
                    hid.unregisterApp()
                    // HARDWARE DEBOUNCE: We MUST use a 300ms delay here.
                    // The Android OS Bluetooth Daemon (com.android.bluetooth) will crash (DeadObjectException)
                    // or glitch if we hammer it with an instant registerApp() immediately following unregisterApp().
                    // This is not a legacy callback wait, but a structural hardware IPC debounce.
                    delay(300)
                } catch (e: Exception) {
                    Log.e("BluetoothKeyboard", "Error during unregister", e)
                }
                
                val settings = sdpSettings
                if (settings == null) {
                    _statusMessage.value = "Bluetooth HID Device role is not supported on this device."
                    _serviceState.value = BluetoothState.ProfileNotSupported
                    return@launch
                }
                
                var registered = false
                for (attempt in 1..3) {
                    try {
                        registered = hid.registerApp(settings, null, null, executor, hidCallback)
                        if (registered) {
                            Log.d("BluetoothKeyboard", "hid.registerApp succeeded on attempt $attempt")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("BluetoothKeyboard", "Attempt $attempt calling hid.registerApp threw exception: ${e.message}")
                    }
                    if (attempt < 3) {
                        delay(400)
                    }
                }
                
                if (!registered) {
                    Log.w("BluetoothKeyboard", "hid.registerApp returned false after 3 attempts — BT stack may need a toggle")
                    _statusMessage.value = "HID registration failed. Try toggling Bluetooth off and on."
                    _serviceState.value = BluetoothState.ReadyDisconnected
                }
            } catch (e: Throwable) {
                Log.e("BluetoothKeyboard", "Error during app registration", e)
                _statusMessage.value = "Registration crash: ${e.localizedMessage}."
                _serviceState.value = BluetoothState.ProfileNotSupported
            } finally {
                isRegisteringInProcess = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun restartHidService() {
        val hid = hidDeviceProfile
        if (hid == null) {
            initProfileListener()
            return
        }
        _statusMessage.value = "Restarting local HID Service..."
        managerScope.launch {
            try {
                hid.unregisterApp()
                // HARDWARE DEBOUNCE: Protect the Android OS Daemon from IPC spam crashes
                delay(300)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error during unregister", e)
            }
            try {
                hid.registerApp(sdpSettings, null, null, executor, hidCallback)
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Error during registerApp in restart", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendKey(keyCode: Int, isPress: Boolean) {
        val dev = _connectedDevice.value
        
        val report = ByteArray(8)
        synchronized(activeKeys) {
            // Update local HID state variables (Modifiers or standard key codes)
            if (keyCode in 0xE0..0xE7) {
                // It's a modifier key (Left Ctrl to Right GUI)
                val bitMask = 1 shl (keyCode - 0xE0)
                activeModifiers = if (isPress) {
                    activeModifiers or bitMask
                } else {
                    activeModifiers and bitMask.inv()
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
                    val compact = ByteArray(6)
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
            report[0] = activeModifiers.toByte()
            report[1] = 0x00.toByte()
            for (j in 0 until 6) {
                report[j + 2] = activeKeys[j]
            }
        }

        // Transmit HID report
        if (dev != null) {
            submitReport(dev, reportId, report)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMouseReport(buttons: Byte, x: Byte, y: Byte, wheel: Byte) {
        val dev = _connectedDevice.value
        if (dev != null) {
            val report = ByteArray(4)
            report[0] = buttons
            report[1] = x
            report[2] = y
            report[3] = wheel
            submitReport(dev, 2, report) // Mouse report ID is 2
        }
    }

    @SuppressLint("MissingPermission")
    fun sendGamepadReport(
        buttonMask: Int,
        leftXFloat: Float,
        leftYFloat: Float,
        rightXFloat: Float,
        rightYFloat: Float
    ) {
        val dev = _connectedDevice.value
        if (dev != null) {
            val report = ByteArray(11)
            report[0] = (buttonMask and 0xFF).toByte()
            report[1] = ((buttonMask shr 8) and 0xFF).toByte()
            report[2] = ((buttonMask shr 16) and 0xFF).toByte()

            // Convert normalized -1.0f..1.0f to 16-bit unsigned 0..65535 (32768 center)
            val lx = ((leftXFloat.coerceIn(-1f, 1f) + 1f) * 32767.5f).toInt().coerceIn(0, 65535)
            val ly = ((leftYFloat.coerceIn(-1f, 1f) + 1f) * 32767.5f).toInt().coerceIn(0, 65535)
            val rx = ((rightXFloat.coerceIn(-1f, 1f) + 1f) * 32767.5f).toInt().coerceIn(0, 65535)
            val ry = ((rightYFloat.coerceIn(-1f, 1f) + 1f) * 32767.5f).toInt().coerceIn(0, 65535)

            // Little-endian 16-bit packing
            report[3] = (lx and 0xFF).toByte()
            report[4] = ((lx shr 8) and 0xFF).toByte()
            report[5] = (ly and 0xFF).toByte()
            report[6] = ((ly shr 8) and 0xFF).toByte()
            report[7] = (rx and 0xFF).toByte()
            report[8] = ((rx shr 8) and 0xFF).toByte()
            report[9] = (ry and 0xFF).toByte()
            report[10] = ((ry shr 8) and 0xFF).toByte()

            submitReport(dev, 3, report) // Gamepad report ID is 3
        }
    }



    fun resetKeyboardState() {
        activeModifiers = 0
        activeKeys.fill(0)
    }

    private fun spoofLocalDeviceClass(adapter: BluetoothAdapter?, classOfDevice: Int): Boolean {
        if (adapter == null) return false
        try {
            val setBluetoothClassMethod = BluetoothAdapter::class.java.getDeclaredMethod(
                "setBluetoothClass",
                Int::class.javaPrimitiveType
            )
            setBluetoothClassMethod.isAccessible = true
            val success = setBluetoothClassMethod.invoke(adapter, classOfDevice) as Boolean
            Log.d("BluetoothKeyboard", "Spoofed local device Class of Device to $classOfDevice, success=$success")
            return success
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Failed to spoof Class of Device via reflection", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectAudioProfiles(device: BluetoothDevice) {
        val adapter = bluetoothAdapter ?: return
        
        managerScope.launch {
            // Linux/Arch hosts often initiate A2DP audio connections asynchronously *after* HID connects.
            // We do 3 aggressive sweeps over 4 seconds to abort any incoming or established audio links.
            for (i in 0..2) {
                delay(if (i == 0) 500L else 1500L) // Sweeps at 0.5s, 2.0s, 3.5s
                
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            // Blindly invoke disconnect to abort even if it's currently in a 'Connecting' state
                            val disconnectMethod = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                            val success = disconnectMethod.invoke(proxy, device) as Boolean
                            Log.d("BluetoothKeyboard", "Sweep $i: Disconnected A2DP profile for host, success=$success")
                        } catch (e: Exception) {
                            Log.d("BluetoothKeyboard", "Sweep $i: No A2DP profile to disconnect or reflection failed.")
                        } finally {
                            adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        }
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, BluetoothProfile.A2DP)

                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            val disconnectMethod = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                            val success = disconnectMethod.invoke(proxy, device) as Boolean
                            Log.d("BluetoothKeyboard", "Sweep $i: Disconnected Headset profile for host, success=$success")
                        } catch (e: Exception) {
                            Log.d("BluetoothKeyboard", "Sweep $i: No Headset profile to disconnect or reflection failed.")
                        } finally {
                            adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                        }
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, BluetoothProfile.HEADSET)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        resetKeyboardState()
        connectionTimeoutFuture?.cancel(false)
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
        appRegistrationState.value = false
        lastConnectedDevice = null
        _connectedDevice.value = null
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        managerScope.cancel()
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(discoveryReceiver)
                isReceiverRegistered = false
            }
            if (isBondReceiverRegistered) {
                context.unregisterReceiver(bondStateReceiver)
                isBondReceiverRegistered = false
            }
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error during cleanup", e)
        }
    }
}
