package dev.arnv.bluke.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import dev.arnv.bluke.utils.DeveloperLogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Exercise the production manager with fake Android HID calls, including missing callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], shadows = [BluetoothManagerSessionTest.HidShadow::class])
class BluetoothManagerSessionTest {
    @Implements(BluetoothHidDevice::class)
    class HidShadow {
        companion object {
            val states = ConcurrentHashMap<BluetoothDevice, Int>()
            val unregisterCalls = AtomicInteger()
            val registerCalls = AtomicInteger()
            val disconnectCalls = AtomicInteger()
            val connectCalls = AtomicInteger()
            var disconnectAccepted = true
            var unregisterAccepted = true
            var deliverUnregisterCallback = true
            var callback: BluetoothHidDevice.Callback? = null
            var executor: Executor = Executor { it.run() }
        }
        @Implementation fun getConnectionState(device: BluetoothDevice): Int = states[device] ?: 0
        @Implementation fun getConnectedDevices(): List<BluetoothDevice> = states.filterValues { it == 2 }.keys.toList()
        @Implementation fun getDevicesMatchingConnectionStates(wanted: IntArray): List<BluetoothDevice> =
            states.filterValues { it in wanted }.keys.toList()
        @Implementation fun disconnect(device: BluetoothDevice): Boolean {
            disconnectCalls.incrementAndGet()
            if (!disconnectAccepted) return false
            states[device] = 0 // Intentionally omit DISCONNECTED callback.
            return true
        }
        @Implementation fun connect(device: BluetoothDevice): Boolean {
            connectCalls.incrementAndGet()
            states[device] = BluetoothProfile.STATE_CONNECTED
            return true
        }
        @Implementation fun sendReport(device: BluetoothDevice, id: Int, bytes: ByteArray) = true
        @Implementation fun unregisterApp(): Boolean {
            unregisterCalls.incrementAndGet()
            val oldCallback = callback
            if (unregisterAccepted && deliverUnregisterCallback) executor.execute { oldCallback?.onAppStatusChanged(null, false) }
            return unregisterAccepted
        }
        @Implementation fun registerApp(sdp: BluetoothHidDeviceAppSdpSettings,
            input: BluetoothHidDeviceAppQosSettings?, output: BluetoothHidDeviceAppQosSettings?,
            executor: Executor, callback: BluetoothHidDevice.Callback): Boolean {
            registerCalls.incrementAndGet()
            Companion.executor = executor
            Companion.callback = callback
            executor.execute { callback.onAppStatusChanged(null, true) }
            return true
        }
    }

    private lateinit var manager: BluetoothKeyboardManager
    private lateinit var adapter: BluetoothAdapter
    private lateinit var device: BluetoothDevice

    @Before fun setUp() {
        HidShadow.states.clear()
        HidShadow.unregisterCalls.set(0)
        HidShadow.registerCalls.set(0)
        HidShadow.disconnectCalls.set(0)
        HidShadow.connectCalls.set(0)
        HidShadow.disconnectAccepted = true
        HidShadow.unregisterAccepted = true
        HidShadow.deliverUnregisterCallback = true
        HidShadow.executor = Executor { it.run() }
        HidShadow.callback = null
        val app = RuntimeEnvironment.getApplication()
        adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        DeveloperLogManager.init(app)
        manager = BluetoothKeyboardManager(app)
        device = adapter.getRemoteDevice("00:11:22:33:44:55")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        ReflectionHelpers.setField(manager, "hidDeviceProfile", Shadow.newInstanceOf(BluetoothHidDevice::class.java))
        ReflectionHelpers.setField(manager, "appInForeground", true)
        ReflectionHelpers.setField(manager, "registeredGamepadMode", GamepadDpadOutputMode.NATIVE_HAT)
        ReflectionHelpers.getField<MutableStateFlow<Boolean>>(manager, "appRegistrationState").value = true
        val attempt = HidRegistrationAttempt(GamepadDpadOutputMode.NATIVE_HAT)
        attempt.commandCompleted(true)
        attempt.statusChanged(true)
        ReflectionHelpers.setField(manager, "activeRegistrationAttempt", attempt)
        HidShadow.callback = ReflectionHelpers.callInstanceMethod(manager, "createHidCallback",
            ClassParameter.from(HidRegistrationAttempt::class.java, attempt))
        HidShadow.states[device] = BluetoothProfile.STATE_CONNECTED
        HidShadow.callback!!.onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED)
    }

    @After fun tearDown() { manager.close() }

    private fun awaitIdle() = runBlocking {
        withTimeout(5_000) { while (ReflectionHelpers.getField<Any?>(manager, "sessionOperationJob") != null) delay(10) }
        synchronized(ReflectionHelpers.getField<Any>(manager, "connectionSelectionLock")) { Unit }
    }

    @Test fun disconnectClearsStaleUiWithoutUnregisteringEvenIfCallbackIsMissing() {
        assertEquals(device, manager.connectedDevice.value)
        manager.disconnectDevice()
        awaitIdle()
        assertNull(manager.connectedDevice.value)
        assertTrue(manager.serviceState.value is BluetoothState.PairingMode)
        assertEquals(1, HidShadow.disconnectCalls.get())
        assertEquals(0, HidShadow.unregisterCalls.get())
        assertEquals(0, HidShadow.registerCalls.get())
        assertFalse(manager.hasPendingConnection.value)
    }

    @Test fun settingsConnectionAfterDisconnectIsAdopted() {
        manager.disconnectDevice()
        awaitIdle()
        val other = adapter.getRemoteDevice("00:11:22:33:44:66")
        HidShadow.states[other] = BluetoothProfile.STATE_CONNECTED
        HidShadow.callback!!.onConnectionStateChanged(other, BluetoothProfile.STATE_CONNECTED)
        assertEquals(other, manager.connectedDevice.value)
        assertTrue(manager.serviceState.value is BluetoothState.Connected)
    }

    @Test fun failedSettingsHandshakeExposesHelpWithoutIssuingConnectionCommands() {
        manager.disconnectDevice()
        awaitIdle()
        val other = adapter.getRemoteDevice("00:11:22:33:44:66")
        HidShadow.states[other] = BluetoothProfile.STATE_CONNECTING
        HidShadow.callback!!.onConnectionStateChanged(other, BluetoothProfile.STATE_CONNECTING)
        assertEquals(HidLifecycleState.Connecting(other.address), manager.lifecycleState.value)
        // An unrelated old host's disconnect must not fail this handshake.
        HidShadow.callback!!.onConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(HidLifecycleState.Connecting(other.address), manager.lifecycleState.value)
        HidShadow.states[other] = BluetoothProfile.STATE_DISCONNECTED
        HidShadow.callback!!.onConnectionStateChanged(other, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(HidLifecycleState.Error(HidFailure.CONNECTION_REJECTED), manager.lifecycleState.value)
        assertEquals(0, HidShadow.connectCalls.get())
        assertEquals(1, HidShadow.disconnectCalls.get()) // Only the explicit setup disconnect.
        assertEquals(0, HidShadow.unregisterCalls.get())
    }

    @Test fun connectTapAdoptsExistingSettingsLinkWithoutRestartOrDuplicateConnect() = runBlocking {
        manager.connectDevice(device)
        withTimeout(5_000) { while (manager.hasPendingConnection.value) delay(10) }
        assertEquals(device, manager.connectedDevice.value)
        assertEquals(0, HidShadow.connectCalls.get())
        assertEquals(0, HidShadow.disconnectCalls.get())
        assertEquals(0, HidShadow.unregisterCalls.get())
    }

    @Test fun failedDisconnectKeepsActualStateAndCanBeRetried() {
        HidShadow.disconnectAccepted = false
        manager.disconnectDevice()
        awaitIdle()
        assertEquals(device, manager.connectedDevice.value)
        assertFalse(manager.hasPendingConnection.value)
        HidShadow.disconnectAccepted = true
        manager.disconnectDevice()
        awaitIdle()
        assertNull(manager.connectedDevice.value)
        assertEquals(0, HidShadow.unregisterCalls.get())
    }

    @Test fun rejectedCleanupRemainsRecoverableInsteadOfClaimingReady() {
        HidShadow.unregisterAccepted = false
        manager.restartHidService()
        awaitIdle()
        assertEquals(0, HidShadow.registerCalls.get())
        assertTrue(manager.lifecycleState.value is HidLifecycleState.Error)
        assertTrue(manager.serviceState.value is BluetoothState.ReadyDisconnected)
        assertFalse(manager.hasPendingConnection.value)
    }

    @Test fun repeatedRestartWaitsForOldCallbackAndRegistersOnlyOnce() = runBlocking {
        HidShadow.deliverUnregisterCallback = false
        val oldCallback = HidShadow.callback!!
        manager.restartHidService()
        withTimeout(5_000) { while (HidShadow.unregisterCalls.get() == 0) delay(10) }
        repeat(10) { manager.restartHidService() }
        assertEquals(1, HidShadow.unregisterCalls.get())
        assertEquals(0, HidShadow.registerCalls.get())
        oldCallback.onAppStatusChanged(null, false)
        awaitIdle()
        assertEquals(1, HidShadow.registerCalls.get())
        assertTrue(manager.serviceState.value is BluetoothState.PairingMode)
        assertNull(manager.connectedDevice.value)
        assertFalse(manager.hasPendingConnection.value)
    }

    @Test fun unexpectedRegistrationLossClearsConnectedUiAndExposesRecovery() {
        HidShadow.callback!!.onAppStatusChanged(null, false)
        assertNull(manager.connectedDevice.value)
        assertTrue(manager.serviceState.value is BluetoothState.ReadyDisconnected)
        assertEquals(HidLifecycleState.Error(HidFailure.REGISTRATION_TIMEOUT), manager.lifecycleState.value)
        assertFalse(manager.hasPendingConnection.value)
    }
}
