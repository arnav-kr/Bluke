package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HidConnectionCoordinatorTest {
    @Test fun automaticReconnectNeverDisconnectsSystemConnectedHost() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("settings-host", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.HOST_ALREADY_CONNECTED,
            coordinator.connect("remembered-host", allowHostSwitch = false))
        assertTrue(fake.commands.isEmpty())
    }

    @Test fun automaticRetryYieldsWhenAnotherHostConnectsBetweenAttempts() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.onConnect = {
            fake.emit(it, HostLinkState.DISCONNECTED)
            fake.emit("settings-host", HostLinkState.CONNECTED)
            true
        }
        assertEquals(ConnectionResult.HOST_ALREADY_CONNECTED,
            coordinator.connect("remembered-host", allowHostSwitch = false))
        assertEquals(listOf("connect:remembered-host"), fake.commands)
    }

    @Test fun proxyStateCanConfirmDisconnectWithoutCallback() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { address ->
            launch { kotlinx.coroutines.delay(50); fake.states[address] = HostLinkState.DISCONNECTED }
            true
        }
        assertTrue(coordinator.disconnectAll())
    }

    @Test fun proxyStateCanConfirmConnectWithoutCallback() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.onConnect = { address ->
            launch { kotlinx.coroutines.delay(25); fake.states[address] = HostLinkState.CONNECTED }
            true
        }
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("A"))
        assertEquals(listOf("connect:A"), fake.commands)
    }
    private class FakeBluetooth : BluetoothConnectionFacade {
        val states = mutableMapOf<String, HostLinkState>()
        val commands = mutableListOf<String>()
        lateinit var coordinator: HidConnectionCoordinator
        var onConnect: (String) -> Boolean = { emit(it, HostLinkState.CONNECTED); true }
        var onDisconnect: (String) -> Boolean = { emit(it, HostLinkState.DISCONNECTED); true }
        override fun state(address: String) = states[address] ?: HostLinkState.DISCONNECTED
        override fun busyAddresses() = states.filterValues { it != HostLinkState.DISCONNECTED }.keys.toList()
        override fun connect(address: String): Boolean { commands += "connect:$address"; return onConnect(address) }
        override fun disconnect(address: String): Boolean { commands += "disconnect:$address"; return onDisconnect(address) }
        fun emit(address: String, state: HostLinkState) {
            states[address] = state
            coordinator.onStateChanged(address, state)
        }
        fun prepare() = HidConnectionCoordinator(this, 100, 100, 10).also { coordinator = it }
    }

    @Test fun alreadyConnectedHostIsNotDisconnectedOnPairingCompletion() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("A"))
        assertTrue(fake.commands.isEmpty())
    }

    @Test fun descriptorSwitchDisconnectsBeforeSameHostCanReconnect() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        assertTrue(coordinator.disconnectAll())
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("A"))
        assertEquals(listOf("disconnect:A", "connect:A"), fake.commands)
    }

    @Test fun freshConnectCannotReuseHostAutoReconnectDuringRegistration() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("A", requireFreshConnection = true))
        assertEquals(listOf("disconnect:A", "connect:A"), fake.commands)
    }

    @Test fun freshConnectFailsIfOldLinkCannotBeRemoved() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { false }
        assertEquals(ConnectionResult.DISCONNECT_TIMED_OUT, coordinator.connect("A", requireFreshConnection = true))
        assertEquals(listOf("disconnect:A"), fake.commands)
    }

    @Test fun descriptorTeardownWaitsForDelayedPhysicalDisconnect() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { fake.emit(it, HostLinkState.DISCONNECTING); true }
        val teardown = async { coordinator.disconnectAll() }
        runCurrent()
        assertFalse(teardown.isCompleted)
        fake.emit("A", HostLinkState.DISCONNECTED)
        assertTrue(teardown.await())
    }

    @Test fun rejectedOrMissingDisconnectDoesNotPretendTeardownSucceeded() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { false }
        assertFalse(coordinator.disconnectAll())
        fake.onDisconnect = { true }
        assertFalse(coordinator.disconnectAll())
        assertFalse(fake.commands.any { it.startsWith("connect:") })
    }

    @Test fun cancellationReleasesTeardownLockWithoutIssuingConnect() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { true }
        val teardown = async { coordinator.disconnectAll() }
        runCurrent()
        teardown.cancel()
        teardown.join()
        fake.emit("A", HostLinkState.DISCONNECTED)
        assertTrue(coordinator.disconnectAll())
        assertEquals(listOf("disconnect:A"), fake.commands)
    }

    @Test fun synchronousConnectCallbackIsNotLostBeforeWaitStarts() = runTest {
        val fake = FakeBluetooth()
        assertEquals(ConnectionResult.CONNECTED, fake.prepare().connect("A"))
        assertEquals(listOf("connect:A"), fake.commands)
    }

    @Test fun synchronousDisconnectBeforeWaitStillAllowsHostSwitch() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("B"))
        assertEquals(listOf("disconnect:A", "connect:B"), fake.commands)
    }

    @Test fun joinsExistingHostHandshakeInsteadOfIssuingDuplicateConnect() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTING)
        val result = async { coordinator.connect("A") }
        runCurrent()
        fake.emit("A", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.CONNECTED, result.await())
        assertTrue(fake.commands.isEmpty())
    }

    @Test fun failedAsynchronousHandshakeRetriesThreeTimes() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.onConnect = { fake.emit(it, HostLinkState.DISCONNECTED); true }
        assertEquals(ConnectionResult.TIMED_OUT, coordinator.connect("A"))
        assertEquals(3, fake.commands.size)
    }

    @Test fun rejectedCommandsHaveHardAttemptCeiling() = runTest {
        val fake = FakeBluetooth()
        fake.onConnect = { false }
        assertEquals(ConnectionResult.COMMAND_REJECTED, fake.prepare().connect("A"))
        assertEquals(3, fake.commands.size)
    }

    @Test fun callbackTimeoutDoesNotIssueFourthCommand() = runTest {
        val fake = FakeBluetooth()
        fake.onConnect = { true }
        assertEquals(ConnectionResult.TIMED_OUT, fake.prepare().connect("A"))
        assertEquals(3, fake.commands.size)
    }

    @Test fun oldPhysicalHandshakeIsDisconnectedAfterCoroutineCancellation() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.onConnect = { address ->
            fake.emit(address, if (address == "A") HostLinkState.CONNECTING else HostLinkState.CONNECTED)
            true
        }
        val old = launch { coordinator.connect("A") }
        runCurrent()
        old.cancel()
        old.join()
        assertEquals(ConnectionResult.CONNECTED, coordinator.connect("B"))
        assertEquals(listOf("connect:A", "disconnect:A", "connect:B"), fake.commands)
    }

    @Test fun failureToDisconnectPreviousHostDoesNotConnectAnotherHost() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.emit("A", HostLinkState.CONNECTED)
        fake.onDisconnect = { false }
        assertEquals(ConnectionResult.DISCONNECT_TIMED_OUT, coordinator.connect("B"))
        assertEquals(listOf("disconnect:A"), fake.commands)
    }

    @Test fun oldCallbackCannotReplaceOrClearLatestHost() {
        assertFalse(shouldAcceptConnectedHost("B", "A"))
        assertFalse(shouldClearConnectedHost("B", "A"))
        assertTrue(shouldAcceptConnectedHost("B", "B"))
        assertTrue(shouldClearConnectedHost("B", "B"))
    }

    @Test fun concurrentRequestsForSameHostIssueOnlyOneConnect() = runTest {
        val fake = FakeBluetooth()
        val coordinator = fake.prepare()
        fake.onConnect = { fake.emit(it, HostLinkState.CONNECTING); true }
        val first = async { coordinator.connect("A") }
        val second = async { coordinator.connect("A") }
        runCurrent()
        fake.emit("A", HostLinkState.CONNECTED)
        assertEquals(ConnectionResult.CONNECTED, first.await())
        assertEquals(ConnectionResult.CONNECTED, second.await())
        assertEquals(listOf("connect:A"), fake.commands)
    }
}
