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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

sealed class BluetoothState {
    object InitializingCapabilities : BluetoothState()
    object CheckingCapabilities : BluetoothState()
    object Unsupported : BluetoothState()
    object PermissionRequired : BluetoothState()
    object BluetoothOff : BluetoothState()
    object ProfileNotSupported : BluetoothState()
    object ReadyDisconnected : BluetoothState()
    data class PairingMode(val name: String) : BluetoothState()
    data class Connected(val deviceName: String) : BluetoothState()
}

class BluetoothKeyboardManager(private val context: Context) {
    private companion object {
        const val PROXY_CALLBACK_TIMEOUT_MILLIS = 8_000L
        const val STALE_REGISTRATION_SETTLE_MILLIS = 300L
        const val PREF_DISCONNECT_AUDIO_PROFILES = "disconnect_audio_profiles"
    }

    private val reportExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
            runnable.run()
        }, "bt-report-sender")
    }

    @SuppressLint("MissingPermission")
    private fun submitReport(dev: BluetoothDevice, reportId: Int, report: ByteArray) {
        val hid = hidDeviceProfile
        val epoch = connectionEpoch.get()
        if (hid != null && !closed) {
            try {
            reportExecutor.submit {
                if (closed || epoch != connectionEpoch.get() || hid !== hidDeviceProfile ||
                    _connectedDevice.value?.address != dev.address) return@submit
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
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                // close() may shut down the executor between the guard and submission.
            }
        }
    }

    private val _serviceState = MutableStateFlow<BluetoothState>(BluetoothState.InitializingCapabilities)
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

    @Volatile private var hidDeviceProfile: BluetoothHidDevice? = null
    @Volatile private var closed = false
    private val connectionEpoch = AtomicLong()
    private val connectionSelectionLock = Any()
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

    private val managerJob = SupervisorJob()
    private val managerScope = CoroutineScope(managerJob + Dispatchers.IO)
    private val appRegistrationState = MutableStateFlow(false)
    private val isAppRegistered: Boolean get() = appRegistrationState.value
    private val _lifecycleState = MutableStateFlow<HidLifecycleState>(HidLifecycleState.Idle)
    val lifecycleState: StateFlow<HidLifecycleState> = _lifecycleState
    private val bindingMutex = Mutex()
    private val registrationMutex = Mutex()
    private val serviceStateLock = Any()
    private val capabilityCheckLock = Any()
    private val capabilityCheckGeneration = AtomicLong()
    @Volatile private var capabilityCheckJob: Job? = null
    @Volatile private var pendingProxyBinding: CompletableDeferred<BluetoothHidDevice?>? = null
    @Volatile private var incompatibleVerdictLatched = false
    @Volatile private var incompatibleVerdictMessage: String? = null
    @Volatile private var registrationCommandAccepted = false
    private val retryPolicy = RetryPolicy()
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
                            if (_connectionTargetAddress.value == device.address) {
                                _statusMessage.value = "Pairing with '$dName'... Please accept the pairing prompt."
                            }
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            updateBondedDevices()
                            if (_connectionTargetAddress.value == device.address) {
                                connectDevice(device)
                            }
                        }
                        BluetoothDevice.BOND_NONE -> {
                            updateBondedDevices()
                            synchronized(connectionSelectionLock) {
                            if (_connectionTargetAddress.value != device.address) return
                            connectRequestProcessor.clear()
                            _hasPendingConnection.value = false
                            _connectionTargetAddress.value = null
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

    // Composite Keyboard, Mouse/Trackpad, Gamepad & Consumer Control HID Descriptor definition

    private fun sdpSettings(mode: GamepadDpadOutputMode): BluetoothHidDeviceAppSdpSettings? {
        return try {
            BluetoothHidDeviceAppSdpSettings(
                "Bluke",                         // Name
                "Wireless Controller Combo",    // Description
                "Bluke",                         // Provider
                BluetoothHidDevice.SUBCLASS1_COMBO, // Subclass
                hidDescriptorForMode(mode)       // Descriptor (Android-only Z/Rz axes)
            )
        } catch (e: Throwable) {
            Log.e("BlukeBT", "Failed to create BluetoothHidDeviceAppSdpSettings", e)
            null
        }
    }

    private val sharedPrefs = context.getSharedPreferences("bluetooth_keyboard_prefs", Context.MODE_PRIVATE)
    private val appPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    @Volatile
    private var gamepadDpadOutputMode = GamepadDpadOutputMode.fromPreference(
        appPreferences.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null),
    )
    @Volatile
    private var registeredGamepadMode: GamepadDpadOutputMode? = null
    @Volatile
    private var gamepadDescriptorSwitchJob: Job? = null
    @Volatile
    private var switchingGamepadDescriptor = false
    private val behaviorPreferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == GAMEPAD_DPAD_MODE_PREFERENCE) {
                val newMode = GamepadDpadOutputMode.fromPreference(
                    preferences.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null),
                )
                if (newMode != gamepadDpadOutputMode) {
                    val descriptorChanged = requiresGamepadDescriptorRestart(gamepadDpadOutputMode, newMode)
                    _connectedDevice.value?.let { device ->
                        submitReport(
                            device,
                            3,
                            buildGamepadReport(0, 0, 0f, 0f, 0f, 0f, gamepadDpadOutputMode),
                        )
                    }
                    gamepadDpadOutputMode = newMode
                    if (descriptorChanged) {
                        switchGamepadDescriptor()
                        return@OnSharedPreferenceChangeListener
                    }
                    _connectedDevice.value?.let { device ->
                        submitReport(
                            device,
                            3,
                            buildGamepadReport(0, 0, 0f, 0f, 0f, 0f, newMode),
                        )
                    }
                }
            }
        }

    private var lastConnectedDeviceAddress: String?
        get() = sharedPrefs.getString("last_connected_device_address", null)
        set(value) {
            if (value == null) {
                sharedPrefs.edit { remove("last_connected_device_address") }
            } else {
                sharedPrefs.edit {
                    putString("last_connected_device_address", value)
                    putString("last_successful_host_address", value)
                }
            }
        }

    private data class ConnectRequest(
        val sequence: Long,
        val device: BluetoothDevice,
        val delayMillis: Long,
    )

    private val connectSequence = AtomicLong()
    private val connectRequestProcessor = LatestRequestProcessor(managerScope, ::connectWhenReady)
    private val _connectionTargetAddress = MutableStateFlow<String?>(null)
    val connectionTargetAddress: StateFlow<String?> = _connectionTargetAddress
    private val _hasPendingConnection = MutableStateFlow(false)
    val hasPendingConnection: StateFlow<Boolean> = _hasPendingConnection
    private val _appVisible = MutableStateFlow(false)
    val appVisible: StateFlow<Boolean> = _appVisible
    @Volatile private var suppressIncomingConnection = false
    private val connectionCoordinator = HidConnectionCoordinator(object : BluetoothConnectionFacade {
        @SuppressLint("MissingPermission")
        override fun state(address: String): HostLinkState = when (
            hidDeviceProfile?.getConnectionState(bluetoothAdapter?.getRemoteDevice(address))
        ) {
            BluetoothProfile.STATE_CONNECTED -> HostLinkState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> HostLinkState.CONNECTING
            BluetoothProfile.STATE_DISCONNECTING -> HostLinkState.DISCONNECTING
            else -> HostLinkState.DISCONNECTED
        }
        @SuppressLint("MissingPermission")
        override fun busyAddresses(): List<String> = hidDeviceProfile?.getDevicesMatchingConnectionStates(
            intArrayOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTING),
        )?.map { it.address }.orEmpty()
        @SuppressLint("MissingPermission")
        override fun connect(address: String): Boolean = !closed &&
            hidDeviceProfile?.connect(bluetoothAdapter?.getRemoteDevice(address)) == true
        @SuppressLint("MissingPermission")
        override fun disconnect(address: String): Boolean = !closed &&
            hidDeviceProfile?.disconnect(bluetoothAdapter?.getRemoteDevice(address)) == true
    })
    @Volatile
    private var lastRegistrationFailure: HidFailure? = null
    private var audioProfilesDisconnectedForSession = false
    @Volatile private var appInForeground = false
    @Volatile private var previouslyRegistered =
        appPreferences.getString("hid_supported_firmware", null) == Build.FINGERPRINT

    private fun recordSupportedFirmware() {
        previouslyRegistered = true
        appPreferences.edit { putString("hid_supported_firmware", Build.FINGERPRINT) }
    }

    fun setAppInForeground(foreground: Boolean) {
        if (closed || appInForeground == foreground) return
        appInForeground = foreground
        _appVisible.value = foreground
        if (foreground) {
            checkBluetoothCapabilities()
        } else if (_connectedDevice.value == null && !_hasPendingConnection.value) {
            synchronized(capabilityCheckLock) {
                capabilityCheckGeneration.incrementAndGet()
                capabilityCheckJob?.cancel()
                capabilityCheckJob = null
            }
        }
    }

    fun isAppVisible(): Boolean = appInForeground

    @SuppressLint("MissingPermission")
    fun getReconnectTarget(): BluetoothDevice? {
        // Explicit disconnect disables automatic reconnect, but should still leave
        // the last successful, still-bonded host available for a manual reconnect.
        val address = sharedPrefs.getString("last_successful_host_address", null)
            ?: lastConnectedDeviceAddress ?: return null
        return _bondedDevices.value.firstOrNull { it.address == address }
    }

    init {
        try {
            appPreferences.registerOnSharedPreferenceChangeListener(behaviorPreferenceListener)
            checkBluetoothCapabilities()
            registerBondReceiver()
        } catch (e: Throwable) {
            Log.e("BluetoothKeyboard", "Error during init: ${e.message}", e)
            publishServiceState(BluetoothState.ProfileNotSupported)
            _statusMessage.value = "Bluetooth HID profile is not supported on this device firmware."
        }
    }

    fun checkBluetoothCapabilities() {
        if (closed) return
        if (bluetoothAdapter == null) {
            registrationCommandAccepted = false
            publishServiceState(BluetoothState.Unsupported)
            _statusMessage.value = "Bluetooth is not supported on this device's hardware."
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            publishServiceState(BluetoothState.PermissionRequired)
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            gamepadDescriptorSwitchJob?.cancel()
            synchronized(capabilityCheckLock) {
                capabilityCheckGeneration.incrementAndGet()
                capabilityCheckJob?.cancel()
                capabilityCheckJob = null
            }
            connectRequestProcessor.clear()
            _hasPendingConnection.value = false
            _connectionTargetAddress.value = null
            _connectedDevice.value = null
            connectionEpoch.incrementAndGet()
            registrationCommandAccepted = false
            clearIncompatibleVerdict()
            publishServiceState(BluetoothState.BluetoothOff)
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
                publishServiceState(BluetoothState.PermissionRequired)
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
                publishServiceState(BluetoothState.PermissionRequired)
                _statusMessage.value = "Location permission is required on Android 10 and earlier to use Bluetooth."
                Log.w("BluetoothKeyboard", "Missing ACCESS_FINE_LOCATION on API ${Build.VERSION.SDK_INT} — Bluetooth HID will not work.")
                return
            }
        }

        if (incompatibleVerdictLatched) {
            publishServiceState(BluetoothState.ProfileNotSupported)
            _statusMessage.value = incompatibleVerdictMessage
                ?: "This device appears incompatible with the Bluetooth HID Device role."
            return
        }

        if (switchingGamepadDescriptor) return

        if (isCapabilityCheckRunning()) {
            return
        }

        if (!appInForeground) return

        updateBondedDevices()
        // Initialize HID Device Profile safely
        val hid = hidDeviceProfile
        if (hid == null) {
            initProfileListener()
        } else if (!isAppRegistered) {
            registerApp()
        } else {
            if (_hasPendingConnection.value) return
            publishRegisteredUiState(
                scheduleReconnect = true,
            )
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
        if (incompatibleVerdictLatched || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

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
    fun pairDevice(device: BluetoothDevice): Unit = synchronized(connectionSelectionLock) {
        if (closed || incompatibleVerdictLatched) return
        if (_hasPendingConnection.value && _connectionTargetAddress.value == device.address &&
            device.bondState == BluetoothDevice.BOND_BONDING) return
        connectRequestProcessor.clear()
        suppressIncomingConnection = false
        _connectionTargetAddress.value = device.address
        _hasPendingConnection.value = true
        stopScanning()
        val dName = device.name ?: device.address
        _statusMessage.value = "Requesting Bluetooth Pairing with '$dName'..."
        try {
            val success = device.createBond()
            if (success) {
                _statusMessage.value = "Pairing requested. Approve prompt on '$dName'."
            } else {
                _hasPendingConnection.value = false
                _statusMessage.value = "Failed to start pairing request for '$dName'."
            }
        } catch (e: Exception) {
            _hasPendingConnection.value = false
            Log.e("BluetoothKeyboard", "Error calling createBond", e)
            _statusMessage.value = "Pairing failed: ${e.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice, delayMs: Long = 0): Unit = synchronized(connectionSelectionLock) {
        if (closed || incompatibleVerdictLatched) return
        if (connectRequestProcessor.pending.value?.device?.address == device.address && _hasPendingConnection.value) return
        lastConnectedDevice = device
        stopScanning()
        val dName = device.name ?: device.address

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            _statusMessage.value = "Credentials required. Swapping to pairing mode with '$dName'..."
            pairDevice(device)
            return
        }

        suppressIncomingConnection = false
        _connectionTargetAddress.value = device.address
        _hasPendingConnection.value = true

        _statusMessage.value = if (hidDeviceProfile == null) {
            "Waiting for Bluetooth HID service... Will connect shortly."
        } else {
            "Connecting to '$dName'..."
        }
        connectRequestProcessor.submit(ConnectRequest(
            sequence = connectSequence.incrementAndGet(),
            device = device,
            delayMillis = delayMs,
        ))
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWhenReady(request: ConnectRequest) {
        val device = request.device
        val dName = device.name ?: device.address
        try {
            if (request.delayMillis > 0) delay(request.delayMillis)
            if (closed || incompatibleVerdictLatched) return
            val hid = ensureHidReady(awaitLateCallback = true) ?: return
            if (closed || incompatibleVerdictLatched || hid !== hidDeviceProfile) return
            val result = connectionCoordinator.connect(device.address) { attempt ->
                _lifecycleState.value = HidLifecycleState.Connecting(device.address)
                _statusMessage.value = "Connecting to '$dName' (attempt $attempt)..."
                DeveloperLogManager.log("BluetoothKeyboard", "connect target=${device.address} request=${request.sequence} attempt=$attempt")
            }
            if (closed || _connectionTargetAddress.value != device.address || hid !== hidDeviceProfile) return
            if (result == ConnectionResult.CONNECTED) {
                publishConnectedHost(device)
            } else {
                _lifecycleState.value = HidLifecycleState.Error(HidFailure.CONNECTION_REJECTED)
                _statusMessage.value = when (result) {
                    ConnectionResult.COMMAND_REJECTED -> "Android could not start the connection. Retry or toggle Bluetooth."
                    ConnectionResult.DISCONNECT_TIMED_OUT -> "Previous host has not disconnected. Retry connecting."
                    else -> "Connection did not complete after repeated attempts. Retry connecting."
                }
                DeveloperLogManager.log("BluetoothKeyboard", "connect target=${device.address} result=$result")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error connecting to $dName", e)
            if (closed || connectRequestProcessor.pending.value?.sequence != request.sequence) return
            _lifecycleState.value = HidLifecycleState.Error(HidFailure.CONNECTION_REJECTED)
            _statusMessage.value = "Failed to initiate link: ${e.localizedMessage}"
        } finally {
            // A completed/cancelled old request must never clear a newer selection.
            synchronized(connectionSelectionLock) {
            if (connectRequestProcessor.pending.value?.sequence == request.sequence) {
                _hasPendingConnection.value = false
                connectRequestProcessor.clear()
            }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(): Unit = synchronized(connectionSelectionLock) {
        if (closed || incompatibleVerdictLatched) return
        suppressIncomingConnection = true
        connectRequestProcessor.clear()
        _hasPendingConnection.value = false
        _connectionTargetAddress.value = null
        connectionEpoch.incrementAndGet()
        val dev = _connectedDevice.value
        val hid = hidDeviceProfile
        lastConnectedDeviceAddress = null
        if (dev != null && hid != null) {
            _statusMessage.value = "Disconnecting physical link..."
            restartHidService()
        } else {
            _connectedDevice.value = null
            lastConnectedDevice = null
            publishServiceState(
                BluetoothState.PairingMode(bluetoothAdapter?.name ?: context.getString(R.string.app_name)),
            )
            updateBondedDevices()
        }
    }

    private fun initProfileListener() {
        if (incompatibleVerdictLatched || !appInForeground) return
        _statusMessage.value = "Connecting to HID service profile proxy..."
        launchCapabilityCheck()
    }

    private fun confirmIncompatibleVerdict(
        message: String,
        failure: HidFailure = HidFailure.REGISTRATION_REJECTED,
    ) {
        // A role that registered successfully is supported. Background/stack recovery
        // failures must not turn a working phone into a permanently incompatible one.
        if (!shouldDiagnoseHidIncompatibility(
                failure = failure,
                previouslyRegistered = previouslyRegistered,
                appInForeground = appInForeground,
            )) {
            publishServiceState(BluetoothState.ReadyDisconnected)
            _statusMessage.value = "Bluetooth HID connection interrupted. Retry connecting or toggle Bluetooth."
            return
        }
        synchronized(serviceStateLock) {
            registrationCommandAccepted = false
            appRegistrationState.value = false
            incompatibleVerdictMessage = message
            incompatibleVerdictLatched = true
            _serviceState.value = BluetoothState.ProfileNotSupported
            _statusMessage.value = message
        }
        connectRequestProcessor.clear()
        _hasPendingConnection.value = false
        _connectionTargetAddress.value = null
        connectionEpoch.incrementAndGet()
        _connectedDevice.value = null
        lastConnectedDevice = null
        stopScanning()
    }

    private fun clearIncompatibleVerdict() {
        synchronized(serviceStateLock) {
            incompatibleVerdictLatched = false
            incompatibleVerdictMessage = null
        }
    }

    private fun publishServiceState(state: BluetoothState): Boolean = synchronized(serviceStateLock) {
        val blocked = shouldBlockServiceStatePublication(
            incompatibleVerdictLatched = incompatibleVerdictLatched,
            publishingIncompatibleVerdict = state is BluetoothState.ProfileNotSupported,
            capabilityCheckRunning = capabilityCheckJob?.isActive == true,
            currentStateIsCapabilityCheck =
                _serviceState.value is BluetoothState.InitializingCapabilities ||
                    _serviceState.value is BluetoothState.CheckingCapabilities,
            publishingOperationalState =
                state is BluetoothState.ReadyDisconnected ||
                    state is BluetoothState.PairingMode ||
                    state is BluetoothState.Connected,
        )
        if (incompatibleVerdictLatched && blocked) {
            _serviceState.value = BluetoothState.ProfileNotSupported
            incompatibleVerdictMessage?.let { _statusMessage.value = it }
            false
        } else if (blocked) {
            false
        } else {
            _serviceState.value = state
            true
        }
    }

    fun retryBluetoothCapabilities() {
        registrationCommandAccepted = false
        appRegistrationState.value = false
        clearIncompatibleVerdict()
        lastRegistrationFailure = null
        _lifecycleState.value = HidLifecycleState.Idle
        publishServiceState(BluetoothState.CheckingCapabilities)
        _statusMessage.value = "Rechecking Bluetooth HID Device compatibility..."
        launchCapabilityCheck(replaceRunning = true)
    }

    private fun isCapabilityCheckRunning(): Boolean = synchronized(capabilityCheckLock) {
        capabilityCheckJob?.isActive == true
    }

    private fun launchCapabilityCheck(replaceRunning: Boolean = false) {
        if (incompatibleVerdictLatched || !appInForeground) return
        val jobToStart = synchronized(capabilityCheckLock) {
            val running = capabilityCheckJob
            if (running?.isActive == true && !replaceRunning) return
            if (replaceRunning) running?.cancel()

            val generation = capabilityCheckGeneration.incrementAndGet()
            if (!previouslyRegistered && _serviceState.value !is BluetoothState.InitializingCapabilities) {
                publishServiceState(BluetoothState.CheckingCapabilities)
            }
            val newJob = managerScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val hid = ensureHidReady()
                    val isCurrent = capabilityCheckGeneration.get() == generation
                    if (isCurrent) {
                        synchronized(capabilityCheckLock) {
                            if (capabilityCheckJob === coroutineContext[Job]) {
                                capabilityCheckJob = null
                            }
                        }
                        if (hid != null && appRegistrationState.value && !incompatibleVerdictLatched) {
                            publishRegisteredUiState(scheduleReconnect = true)
                        } else if (!incompatibleVerdictLatched) {
                            publishServiceState(BluetoothState.ReadyDisconnected)
                        }
                    }
                } finally {
                    synchronized(capabilityCheckLock) {
                        if (capabilityCheckJob === coroutineContext[Job]) {
                            capabilityCheckJob = null
                        }
                    }
                }
            }
            capabilityCheckJob = newJob
            newJob
        }
        jobToStart.start()
    }

    private suspend fun ensureHidReady(awaitLateCallback: Boolean = false): BluetoothHidDevice? {
        if (incompatibleVerdictLatched) return null
        val hid = bindHidProxy() ?: return null
        if (ensureRegistered(hid)) return hid
        if (!awaitLateCallback || lastRegistrationFailure != HidFailure.REGISTRATION_TIMEOUT) {
            return null
        }

        // Some OEM stacks acknowledge registerApp() after every bounded callback window has
        // elapsed. Keep the latest request suspended instead of issuing an unbounded fourth
        // registration attempt. A newer request cancels this wait through collectLatest.
        _statusMessage.value =
            "HID registration is still pending. Waiting for the system callback..."
        appRegistrationState.first { it }
        return hidDeviceProfile ?: bindHidProxy()
    }

    @SuppressLint("MissingPermission")
    private suspend fun bindHidProxy(): BluetoothHidDevice? {
        hidDeviceProfile?.let { return it }
        var lastFailure = HidFailure.BINDING_REJECTED

        for (attempt in 1..retryPolicy.maxAttempts) {
            _lifecycleState.value = HidLifecycleState.BindingProxy(attempt)
            val binding = bindingMutex.withLock {
                hidDeviceProfile?.let { return@withLock CompletableDeferred(it) }
                pendingProxyBinding ?: CompletableDeferred<BluetoothHidDevice?>().also { deferred ->
                    pendingProxyBinding = deferred
                    val accepted = try {
                        bluetoothAdapter?.getProfileProxy(
                            context,
                            profileListener,
                            BluetoothProfile.HID_DEVICE,
                        ) == true
                    } catch (e: Throwable) {
                        Log.w("BluetoothKeyboard", "getProfileProxy attempt $attempt failed", e)
                        false
                    }
                    if (!accepted) deferred.complete(null)
                }
            }

            val proxy = withTimeoutOrNull(PROXY_CALLBACK_TIMEOUT_MILLIS) { binding.await() }
            if (proxy != null) return proxy
            lastFailure = if (binding.isCompleted) {
                HidFailure.BINDING_REJECTED
            } else {
                HidFailure.BINDING_TIMEOUT
            }
            bindingMutex.withLock {
                if (pendingProxyBinding === binding) pendingProxyBinding = null
            }
            if (attempt < retryPolicy.maxAttempts) {
                delay(retryPolicy.delayMillis(attempt, Random.nextDouble(-1.0, 1.0)))
            }
        }

        _lifecycleState.value = HidLifecycleState.Error(lastFailure)
        val failureMessage = when (lastFailure) {
            HidFailure.BINDING_TIMEOUT -> "Bluetooth HID service did not respond. Try toggling Bluetooth."
            else -> "This device appears incompatible: Android repeatedly rejected the Bluetooth HID Device profile."
        }
        if (lastFailure.indicatesLikelyDeviceIncompatibility()) {
            confirmIncompatibleVerdict(failureMessage, lastFailure)
        } else {
            _statusMessage.value = failureMessage
        }
        return null
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                if (closed || bluetoothAdapter?.isEnabled != true) {
                    bluetoothAdapter?.closeProfileProxy(profile, proxy)
                    return
                }
                val hid = proxy as BluetoothHidDevice
                hidDeviceProfile = hid
                pendingProxyBinding?.complete(hid)
                pendingProxyBinding = null
                Log.d("BluetoothKeyboard", "HID Device profile proxy obtained — firmware supports HID peripheral role")
                if (incompatibleVerdictLatched) return

                // Attempt to restore connected state from active proxy connections before we unregister
                try {
                    val connectedDevs = hid.connectedDevices
                    val activeDev = connectedDevs?.firstOrNull()
                    if (activeDev != null) {
                        publishConnectedHost(activeDev)
                        // We intentionally DO NOT call connectDevice() here.
                        // We must wait for registerApp() to complete. 
                        // onAppStatusChanged(true) will seamlessly pick up lastConnectedDeviceAddress and connect.
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothKeyboard", "Error restoring connected devices", e)
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (closed) return
            if (profile == BluetoothProfile.HID_DEVICE) {
                registrationCommandAccepted = false
                hidDeviceProfile = null
                appRegistrationState.value = false
                _lifecycleState.value = HidLifecycleState.Idle
                // Don't clear _connectedDevice here — the BT link itself may still be alive.
                // The proxy can rebind and re-report the connection. We'll get the definitive
                // STATE_DISCONNECTED via onConnectionStateChanged if the link actually drops.
                _statusMessage.value = "HID Service Proxy disconnected. Rebinding..."
                initProfileListener()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishRegisteredUiState(
        scheduleReconnect: Boolean,
    ) {
        if (closed || incompatibleVerdictLatched) return
        updateBondedDevices()
        val connectedDevs = try {
            hidDeviceProfile?.connectedDevices
        } catch (e: Exception) {
            Log.e("BluetoothKeyboard", "Error restoring connected devices", e)
            null
        }
        val activeDev = connectedDevs?.firstOrNull()
        if (activeDev != null) {
            publishConnectedHost(activeDev)
            return
        }

        if (_hasPendingConnection.value) return

        _lifecycleState.value = HidLifecycleState.Registered
        _connectedDevice.value = null
        _statusMessage.value = "Custom HID Deck is ready and advertising."
        if (!publishServiceState(
            BluetoothState.PairingMode(
                bluetoothAdapter?.name ?: context.getString(R.string.app_name),
            ),
        )) return

        if (!scheduleReconnect || connectRequestProcessor.pending.value != null) return
        val isAutoConnectEnabled = appPreferences.getBoolean("auto_connect", true)
        if (!isAutoConnectEnabled) return
        lastConnectedDeviceAddress?.let { address ->
            try {
                val lastDevice = bluetoothAdapter?.getRemoteDevice(address)
                if (lastDevice != null && lastDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(
                        "BluetoothKeyboard",
                        "Scheduling auto-reconnect to last connected device: ${lastDevice.name ?: address}",
                    )
                    // Queue the delay as part of the latest-wins request, so a manual
                    // host selection cancels it instead of being overwritten 600 ms later.
                    connectDevice(lastDevice, delayMs = 600)
                }
            } catch (e: Exception) {
                Log.e("BluetoothKeyboard", "Failed to schedule auto-reconnect to last connected device", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishConnectedHost(device: BluetoothDevice): Unit = synchronized(connectionSelectionLock) {
        if (closed || incompatibleVerdictLatched || suppressIncomingConnection ||
            !shouldAcceptConnectedHost(_connectionTargetAddress.value, device.address)) return
        val isNewLink = _connectedDevice.value?.address != device.address
        if (isNewLink) connectionEpoch.incrementAndGet()
        recordSupportedFirmware()
        _connectionTargetAddress.value = device.address
        _connectedDevice.value = device
        lastConnectedDevice = device
        lastConnectedDeviceAddress = device.address
        _lifecycleState.value = HidLifecycleState.Connected(device.address)
        if (!publishServiceState(BluetoothState.Connected(device.name ?: "Paired Host"))) return
        _statusMessage.value = "Link established with '${device.name ?: "Host"}'! Keyboard active."
        if (isNewLink) resetKeyboardState()
        updateBondedDevices()
        if (appPreferences.getBoolean(PREF_DISCONNECT_AUDIO_PROFILES, false) && !audioProfilesDisconnectedForSession) {
            audioProfilesDisconnectedForSession = true
            disconnectAudioProfiles(device)
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            if (closed) return
            if (bluetoothAdapter?.isEnabled != true) return
            
            DeveloperLogManager.log("BluetoothKeyboard", "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.address}")

            if (incompatibleVerdictLatched) {
                publishServiceState(BluetoothState.ProfileNotSupported)
                _statusMessage.value = incompatibleVerdictMessage
                    ?: "This device appears incompatible with the Bluetooth HID Device role."
                return
            }
            if (!isRegistrationCallbackActionable(registered, registrationCommandAccepted)) {
                DeveloperLogManager.log(
                    "BluetoothKeyboard",
                    "Ignoring registered callback because the current registerApp command was rejected",
                )
                return
            }
            appRegistrationState.value = registered
            if (registered) recordSupportedFirmware()
            if (!registered) registrationCommandAccepted = false
            if (registered) {
                if (switchingGamepadDescriptor) return
                if (isCapabilityCheckRunning()) return
                if (
                    _serviceState.value is BluetoothState.PairingMode ||
                    _serviceState.value is BluetoothState.Connected
                ) return
                publishRegisteredUiState(scheduleReconnect = true)
            } else {
                if (_lifecycleState.value !is HidLifecycleState.Registering) {
                    _lifecycleState.value = HidLifecycleState.Idle
                    val currentMsg = _statusMessage.value
                    if (!currentMsg.contains("Disconnecting") && !currentMsg.contains("Restarting")) {
                        _statusMessage.value = "HID profile unregistered."
                    }
                    publishServiceState(BluetoothState.ReadyDisconnected)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (closed) return
            val linkState = when (state) {
                BluetoothProfile.STATE_CONNECTED -> HostLinkState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> HostLinkState.CONNECTING
                BluetoothProfile.STATE_DISCONNECTING -> HostLinkState.DISCONNECTING
                else -> HostLinkState.DISCONNECTED
            }
            connectionCoordinator.onStateChanged(device.address, linkState)
            DeveloperLogManager.log("BluetoothKeyboard", "connection device=${device.address} state=$linkState target=${_connectionTargetAddress.value}")
            if (incompatibleVerdictLatched) return
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    publishConnectedHost(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    synchronized(connectionSelectionLock) {
                    if (!shouldClearConnectedHost(_connectedDevice.value?.address, device.address)) return
                    connectionEpoch.incrementAndGet()
                    _connectedDevice.value = null
                    audioProfilesDisconnectedForSession = false
                    resetKeyboardState()
                    if (_hasPendingConnection.value) return
                    _lifecycleState.value = HidLifecycleState.Registered
                    if (!publishServiceState(
                        BluetoothState.PairingMode(
                            bluetoothAdapter?.name ?: context.getString(R.string.app_name),
                        ),
                    )) return
                    _statusMessage.value = "Link detached. Ready for incoming / outgoing pairing."
                    resetKeyboardState()
                    updateBondedDevices()
                    }
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
        if (incompatibleVerdictLatched) return
        _statusMessage.value = "Checking Bluetooth HID Device compatibility..."
        launchCapabilityCheck()
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureRegistered(hid: BluetoothHidDevice, forceReset: Boolean = false): Boolean =
        registrationMutex.withLock {
            if (closed || incompatibleVerdictLatched || (!appInForeground && !_hasPendingConnection.value)) return@withLock false
            if (appRegistrationState.value && !forceReset && registeredGamepadMode?.let {
                    !requiresGamepadDescriptorRestart(it, gamepadDpadOutputMode)
                } == true) return@withLock true
            lastRegistrationFailure = null
            val registrationMode = gamepadDpadOutputMode
            val settings = sdpSettings(registrationMode)
            if (settings == null) {
                confirmIncompatibleVerdict(
                    "Bluetooth HID Device role is not supported on this device.",
                )
                return@withLock false
            }

            appRegistrationState.value = false
            val facade = object : BluetoothRegistrationFacade {
                override val registrationState: StateFlow<Boolean> = appRegistrationState

                override fun unregisterApp() {
                    registrationCommandAccepted = false
                    try {
                        hid.unregisterApp()
                    } catch (e: Exception) {
                        Log.w("BluetoothKeyboard", "HID registration cleanup failed", e)
                    }
                }

                override fun registerApp(): Boolean {
                    registrationCommandAccepted = false
                    registeredGamepadMode = registrationMode
                    val accepted = try {
                        hid.registerApp(settings, null, null, executor, hidCallback)
                    } catch (e: Exception) {
                        Log.w("BluetoothKeyboard", "registerApp failed", e)
                        false
                    }
                    registrationCommandAccepted = accepted
                    return accepted
                }
            }
            val result = HidRegistrationCoordinator(
                facade = facade,
                retryPolicy = retryPolicy,
                initialCleanupDelayMillis = STALE_REGISTRATION_SETTLE_MILLIS,
            ).register(forceReset = forceReset) { attempt ->
                _lifecycleState.value = HidLifecycleState.Registering(attempt)
                _statusMessage.value =
                    "Registering Bluetooth HID application profile (attempt $attempt)..."
            }

            when (result) {
                is RegistrationResult.Registered -> {
                    recordSupportedFirmware()
                    lastRegistrationFailure = null
                    _lifecycleState.value = HidLifecycleState.Registered
                    return@withLock true
                }
                is RegistrationResult.TimedOut -> {
                    lastRegistrationFailure = HidFailure.REGISTRATION_TIMEOUT
                    Log.w(
                        "BluetoothKeyboard",
                        "No onAppStatusChanged(true) within ${result.timeoutMillis}ms; capability remains inconclusive",
                    )
                }
                is RegistrationResult.Rejected -> {
                    lastRegistrationFailure = HidFailure.REGISTRATION_REJECTED
                }
            }

            val lastFailure = lastRegistrationFailure ?: HidFailure.REGISTRATION_REJECTED
            _lifecycleState.value = HidLifecycleState.Error(lastFailure)
            val failureMessage = if (lastFailure == HidFailure.REGISTRATION_TIMEOUT) {
                "HID registration callback timed out; support is inconclusive. Try toggling Bluetooth."
            } else {
                "This device appears incompatible: Android repeatedly rejected HID Device registration."
            }
            if (lastFailure.indicatesLikelyDeviceIncompatibility()) {
                confirmIncompatibleVerdict(failureMessage, lastFailure)
            } else {
                _statusMessage.value = failureMessage
            }
            false
        }

    @SuppressLint("MissingPermission")
    fun restartHidService() {
        if (closed || incompatibleVerdictLatched) return
        synchronized(connectionSelectionLock) {
            connectRequestProcessor.clear()
            _hasPendingConnection.value = false
            connectionEpoch.incrementAndGet()
        }
        _statusMessage.value = "Restarting local HID Service..."
        managerScope.launch {
            val hid = bindHidProxy() ?: return@launch
            ensureRegistered(hid, forceReset = true)
        }
    }

    /** Mode changes never navigate or run the startup compatibility screen. */
    @SuppressLint("MissingPermission")
    private fun switchGamepadDescriptor(): Unit = synchronized(connectionSelectionLock) {
        if (closed || incompatibleVerdictLatched || gamepadDescriptorSwitchJob?.isActive == true) return
        val host = _connectedDevice.value ?: connectRequestProcessor.pending.value?.device
        switchingGamepadDescriptor = true
        connectRequestProcessor.clear()
        _hasPendingConnection.value = host != null
        connectionEpoch.incrementAndGet()
        _connectedDevice.value = null
        var registeredSuccessfully = false
        val job = managerScope.launch(start = CoroutineStart.LAZY) {
            try {
                val hid = bindHidProxy() ?: return@launch
                // Coalesce rapid toggles; each completed registration must match the latest choice.
                if (!registerLatestGamepadDescriptor(
                        selectedMode = { gamepadDpadOutputMode },
                        registeredMode = { registeredGamepadMode },
                        register = { ensureRegistered(hid, forceReset = true) },
                    )) return@launch
                registeredSuccessfully = true
                if (host != null && !suppressIncomingConnection &&
                    _connectionTargetAddress.value.let { it == null || it == host.address }) {
                    _hasPendingConnection.value = false
                    connectDevice(host)
                } else {
                    if (connectRequestProcessor.pending.value == null) _hasPendingConnection.value = false
                    publishRegisteredUiState(scheduleReconnect = false)
                }
            } finally {
                switchingGamepadDescriptor = false
                if (connectRequestProcessor.pending.value == null) _hasPendingConnection.value = false
            }
        }
        gamepadDescriptorSwitchJob = job
        job.invokeOnCompletion {
            // A toggle can race the last comparison and job completion; don't lose it.
            if (registeredSuccessfully && !closed && registeredGamepadMode?.let {
                    requiresGamepadDescriptorRestart(it, gamepadDpadOutputMode)
                } == true) switchGamepadDescriptor()
        }
        job.start()
        Unit
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
        dpadMask: Int,
        leftXFloat: Float,
        leftYFloat: Float,
        rightXFloat: Float,
        rightYFloat: Float
    ) {
        // Never encode Android buttons against a desktop descriptor while switching profiles.
        if (switchingGamepadDescriptor) return
        if (registeredGamepadMode?.let {
                requiresGamepadDescriptorRestart(it, gamepadDpadOutputMode)
            } != false) return
        val dev = _connectedDevice.value
        if (dev != null) {
            val report = buildGamepadReport(
                buttonMask,
                dpadMask,
                leftXFloat,
                leftYFloat,
                rightXFloat,
                rightYFloat,
                gamepadDpadOutputMode,
            )
            submitReport(dev, 3, report) // Gamepad report ID is 3
        }
    }



    fun resetKeyboardState() {
        synchronized(activeKeys) {
            activeModifiers = 0
            activeKeys.fill(0)
        }
    }

    /** Release input without tearing down the Bluetooth session when its UI leaves foreground. */
    fun releaseAllInputs() {
        connectionEpoch.incrementAndGet() // Discard held-input reports that have not been sent yet.
        resetKeyboardState()
        val device = _connectedDevice.value ?: return
        submitReport(device, reportId, ByteArray(8))
        sendMouseReport(0, 0, 0, 0)
        sendGamepadReport(0, 0, 0f, 0f, 0f, 0f)
        sendConsumerControl(null)
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
        synchronized(connectionSelectionLock) {
            if (closed) return
            closed = true
        }
        connectionEpoch.incrementAndGet()
        connectRequestProcessor.clear()
        _hasPendingConnection.value = false
        _connectionTargetAddress.value = null
        managerJob.cancel()
        resetKeyboardState()
        appPreferences.unregisterOnSharedPreferenceChangeListener(behaviorPreferenceListener)
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
        registrationCommandAccepted = false
        appRegistrationState.value = false
        _lifecycleState.value = HidLifecycleState.Idle
        lastConnectedDevice = null
        _connectedDevice.value = null
        managerJob.cancel()
        executor.shutdownNow()
        reportExecutor.shutdownNow()
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        close()
    }

    @SuppressLint("MissingPermission")
    fun sendConsumerControl(control: ConsumerControl?) {
        val dev = _connectedDevice.value
        if (dev != null) {
            submitReport(dev, 4, buildConsumerControlReport(control))
        }
    }
}
