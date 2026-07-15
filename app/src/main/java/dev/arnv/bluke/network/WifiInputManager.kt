package dev.arnv.bluke.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Optional WiFi (LAN) transport for Bluke.
 *
 * This module is fully additive: the Bluetooth stack stays the single producer of
 * HID report bytes, and [mirror] forwards a copy of every packed report to the
 * receiver agent running on the host PC (see the `desktop/` folder in the repo).
 *
 * Wire protocol (TCP, length-prefixed frames):
 *   frame  = u16 big-endian length, then `length` bytes: [u8 type][payload]
 *   HELLO      0x01  client->server  [u8 protocolVersion][utf8 device name]
 *   AUTH       0x02  client->server  [utf8 PIN]
 *   AUTH_OK    0x03  server->client  [utf8 receiver name (optional)]
 *   AUTH_FAIL  0x04  server->client
 *   INPUT      0x10  client->server  [u8 reportId][HID report bytes]
 *   LED        0x20  server->client  [u8 led bitmask (bit0 num, bit1 caps, bit2 scroll)]
 *   PING       0x30  client->server
 *   PONG       0x31  server->client
 */
sealed class WifiState {
    object Disconnected : WifiState()
    data class Connecting(val host: String) : WifiState()
    data class Connected(val receiverName: String) : WifiState()
    data class Error(val message: String) : WifiState()
}

data class WifiHost(val serviceName: String, val host: String, val port: Int)

class WifiInputManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WifiInputManager"
        const val SERVICE_TYPE = "_bluke._tcp."
        const val DEFAULT_PORT = 9570
        private const val PROTOCOL_VERSION = 1

        private const val TYPE_HELLO: Byte = 0x01
        private const val TYPE_AUTH: Byte = 0x02
        private const val TYPE_AUTH_OK: Byte = 0x03
        private const val TYPE_AUTH_FAIL: Byte = 0x04
        private const val TYPE_INPUT: Byte = 0x10
        private const val TYPE_LED: Byte = 0x20
        private const val TYPE_PING: Byte = 0x30

        @Volatile
        private var instance: WifiInputManager? = null

        fun getInstance(context: Context): WifiInputManager {
            return instance ?: synchronized(this) {
                instance ?: WifiInputManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Hook called from BluetoothKeyboardManager with every packed HID report.
         * No-op unless a WiFi receiver is currently connected, so the Bluetooth
         * path is unaffected when this module is unused.
         */
        fun mirror(reportId: Int, report: ByteArray) {
            val mgr = instance ?: return
            if (mgr.connectionState.value is WifiState.Connected) {
                mgr.sendInputReport(reportId, report)
            }
        }

        /**
         * Called when a Bluetooth host connects. Bluetooth takes priority, so any
         * active WiFi link is dropped to avoid sending input to two hosts at once.
         */
        fun onBluetoothConnected() {
            val mgr = instance ?: return
            val state = mgr.connectionState.value
            if (state is WifiState.Connected || state is WifiState.Connecting) {
                mgr.disconnect()
            }
        }
    }

    private val prefs = context.getSharedPreferences("wifi_remote_prefs", Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow<WifiState>(WifiState.Disconnected)
    val connectionState: StateFlow<WifiState> = _connectionState

    private val _statusMessage = MutableStateFlow("Not connected.")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _discoveredHosts = MutableStateFlow<List<WifiHost>>(emptyList())
    val discoveredHosts: StateFlow<List<WifiHost>> = _discoveredHosts

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    // LED state reported back by the receiver (mirrors the Bluetooth LED report)
    private val _capsLockState = MutableStateFlow(false)
    val capsLockState: StateFlow<Boolean> = _capsLockState
    private val _numLockState = MutableStateFlow(true)
    val numLockState: StateFlow<Boolean> = _numLockState
    private val _scrollLockState = MutableStateFlow(false)
    val scrollLockState: StateFlow<Boolean> = _scrollLockState

    private val nsdManager: NsdManager? = try {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (e: Exception) {
        null
    }

    // Single writer thread keeps report ordering, mirroring the BT report sender pattern
    private val writeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
            runnable.run()
        }, "wifi-report-sender")
    }
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "wifi-manager-scheduler")
    }

    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var readerThread: Thread? = null
    private var pingFuture: ScheduledFuture<*>? = null
    private var connectionEpoch = 0

    // ---------------------------------------------------------------------
    // Discovery (mDNS / NSD)
    // ---------------------------------------------------------------------

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile
    private var isResolving = false

    fun startDiscovery() {
        val nsd = nsdManager ?: run {
            _statusMessage.value = "Network service discovery unavailable on this device."
            return
        }
        if (_isDiscovering.value) return
        _discoveredHosts.value = emptyList()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _isDiscovering.value = true
                _statusMessage.value = "Searching for Bluke receivers on this network..."
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isDiscovering.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.add(serviceInfo)
                drainResolveQueue()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discoveredHosts.value = _discoveredHosts.value.filter {
                    it.serviceName != serviceInfo.serviceName
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
                _statusMessage.value = "Failed to start network discovery (code $errorCode)."
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
            }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery", e)
            _statusMessage.value = "Discovery error: ${e.localizedMessage}"
            discoveryListener = null
        }
    }

    fun stopDiscovery() {
        val nsd = nsdManager ?: return
        val listener = discoveryListener ?: return
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NSD discovery", e)
        }
        discoveryListener = null
        _isDiscovering.value = false
    }

    // NsdManager only supports one in-flight resolve; queue them.
    private fun drainResolveQueue() {
        if (isResolving) return
        val next = resolveQueue.poll() ?: return
        val nsd = nsdManager ?: return
        isResolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isResolving = false
                drainResolveQueue()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val hostAddress = serviceInfo.host?.hostAddress
                if (hostAddress != null) {
                    val found = WifiHost(serviceInfo.serviceName, hostAddress, serviceInfo.port)
                    val current = _discoveredHosts.value
                    if (current.none { it.host == found.host && it.port == found.port }) {
                        _discoveredHosts.value = current + found
                    }
                }
                isResolving = false
                drainResolveQueue()
            }
        })
    }

    // ---------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------

    @SuppressLint("HardwareIds")
    fun connect(host: String, port: Int, pin: String) {
        val epoch = synchronized(this) {
            teardownSocketLocked()
            ++connectionEpoch
        }
        _connectionState.value = WifiState.Connecting(host)
        _statusMessage.value = "Connecting to $host:$port..."

        Thread({
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(host, port), 5000)
                val out = DataOutputStream(sock.getOutputStream().buffered())
                val input = DataInputStream(sock.getInputStream())

                synchronized(this) {
                    if (epoch != connectionEpoch) {
                        sock.close()
                        return@Thread
                    }
                    socket = sock
                    output = out
                }

                val deviceName = try {
                    android.provider.Settings.Global.getString(
                        context.contentResolver, android.provider.Settings.Global.DEVICE_NAME
                    ) ?: android.os.Build.MODEL
                } catch (e: Exception) {
                    android.os.Build.MODEL
                }
                writeFrame(out, TYPE_HELLO, byteArrayOf(PROTOCOL_VERSION.toByte()) + deviceName.toByteArray(Charsets.UTF_8))
                writeFrame(out, TYPE_AUTH, pin.toByteArray(Charsets.UTF_8))

                readerLoop(input, epoch, host, port, pin)
            } catch (e: Exception) {
                Log.e(TAG, "Connection to $host:$port failed", e)
                if (epoch == connectionEpoch) {
                    _connectionState.value = WifiState.Error(e.localizedMessage ?: "Connection failed")
                    _statusMessage.value = "Could not reach $host:$port. Is the receiver running?"
                }
            }
        }, "wifi-connect").start()
    }

    private fun readerLoop(input: DataInputStream, epoch: Int, host: String, port: Int, pin: String) {
        readerThread = Thread.currentThread()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val length = input.readUnsignedShort()
                if (length < 1) continue
                val frame = ByteArray(length)
                input.readFully(frame)
                if (epoch != connectionEpoch) return

                when (frame[0]) {
                    TYPE_AUTH_OK -> {
                        val name = if (frame.size > 1) {
                            String(frame, 1, frame.size - 1, Charsets.UTF_8)
                        } else {
                            host
                        }
                        rememberHost(host, port, pin)
                        _connectionState.value = WifiState.Connected(name)
                        _statusMessage.value = "Link established with '$name' over WiFi! Keyboard active."
                        startPing(epoch)
                    }
                    TYPE_AUTH_FAIL -> {
                        forgetPin(host, port)
                        _connectionState.value = WifiState.Error("Wrong PIN")
                        _statusMessage.value = "Receiver rejected the PIN. Check the PIN shown on the PC."
                        disconnect()
                        return
                    }
                    TYPE_LED -> {
                        if (frame.size > 1) {
                            val ledByte = frame[1].toInt()
                            _numLockState.value = (ledByte and 0x01) != 0
                            _capsLockState.value = (ledByte and 0x02) != 0
                            _scrollLockState.value = (ledByte and 0x04) != 0
                        }
                    }
                    // PONG and anything unknown: ignore
                }
            }
        } catch (e: Exception) {
            if (epoch == connectionEpoch) {
                Log.w(TAG, "Reader loop ended: ${e.message}")
                _connectionState.value = WifiState.Disconnected
                _statusMessage.value = "WiFi link closed."
                synchronized(this) { teardownSocketLocked() }
            }
        }
    }

    private fun startPing(epoch: Int) {
        pingFuture?.cancel(false)
        pingFuture = scheduler.scheduleWithFixedDelay({
            if (epoch != connectionEpoch) return@scheduleWithFixedDelay
            val out = output ?: return@scheduleWithFixedDelay
            writeExecutor.submit {
                try {
                    writeFrame(out, TYPE_PING, ByteArray(0))
                } catch (e: Exception) {
                    handleWriteFailure(epoch, e)
                }
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    fun disconnect() {
        synchronized(this) {
            connectionEpoch++
            teardownSocketLocked()
        }
        _connectionState.value = WifiState.Disconnected
        _statusMessage.value = "Disconnected from WiFi receiver."
    }

    private fun teardownSocketLocked() {
        pingFuture?.cancel(false)
        pingFuture = null
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
        output = null
        readerThread?.interrupt()
        readerThread = null
    }

    private fun sendInputReport(reportId: Int, report: ByteArray) {
        val out = output ?: return
        val epoch = connectionEpoch
        writeExecutor.submit {
            try {
                val payload = ByteArray(report.size + 1)
                payload[0] = reportId.toByte()
                report.copyInto(payload, 1)
                writeFrame(out, TYPE_INPUT, payload)
            } catch (e: Exception) {
                handleWriteFailure(epoch, e)
            }
        }
    }

    private fun handleWriteFailure(epoch: Int, e: Exception) {
        Log.e(TAG, "Error writing to WiFi receiver", e)
        if (epoch == connectionEpoch) {
            _connectionState.value = WifiState.Disconnected
            _statusMessage.value = "WiFi link lost: ${e.localizedMessage}"
            synchronized(this) { teardownSocketLocked() }
        }
    }

    @Synchronized
    private fun writeFrame(out: DataOutputStream, type: Byte, payload: ByteArray) {
        out.writeShort(payload.size + 1)
        out.writeByte(type.toInt())
        out.write(payload)
        out.flush()
    }

    // ---------------------------------------------------------------------
    // Remembered hosts / PINs
    // ---------------------------------------------------------------------

    fun storedPin(host: String, port: Int): String? =
        prefs.getString("pin_$host:$port", null)

    fun lastHost(): Triple<String, Int, String>? {
        val host = prefs.getString("last_host", null) ?: return null
        val port = prefs.getInt("last_port", DEFAULT_PORT)
        val pin = storedPin(host, port) ?: return null
        return Triple(host, port, pin)
    }

    private fun rememberHost(host: String, port: Int, pin: String) {
        prefs.edit()
            .putString("pin_$host:$port", pin)
            .putString("last_host", host)
            .putInt("last_port", port)
            .apply()
    }

    private fun forgetPin(host: String, port: Int) {
        prefs.edit().remove("pin_$host:$port").apply()
    }
}
