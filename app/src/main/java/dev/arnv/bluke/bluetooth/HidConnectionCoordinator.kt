package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal enum class HostLinkState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }
internal enum class ConnectionResult { CONNECTED, COMMAND_REJECTED, TIMED_OUT, DISCONNECT_TIMED_OUT }

internal interface BluetoothConnectionFacade {
    fun state(address: String): HostLinkState
    fun busyAddresses(): List<String>
    fun connect(address: String): Boolean
    fun disconnect(address: String): Boolean
}

/** Serializes physical transactions; cancellation cannot undo an already-issued Bluetooth command. */
internal class HidConnectionCoordinator(
    private val facade: BluetoothConnectionFacade,
    private val connectionTimeoutMillis: Long = 10_000,
    private val disconnectTimeoutMillis: Long = 3_000,
    private val retryDelayMillis: Long = 500,
    private val maxAttempts: Int = 3,
) {
    private data class Event(val revision: Long, val state: HostLinkState)
    private val events = MutableStateFlow<Map<String, Event>>(emptyMap())
    private val eventLock = Any()
    private val transactionMutex = Mutex()
    private var revision = 0L

    fun onStateChanged(address: String, state: HostLinkState) = synchronized(eventLock) {
        events.value = events.value + (address to Event(++revision, state))
    }

    private suspend fun awaitEvent(address: String, after: Long, timeout: Long, states: Set<HostLinkState>): HostLinkState? =
        withTimeoutOrNull(timeout) {
            events.first { map -> map[address]?.let { it.revision > after && it.state in states } == true }
                .getValue(address).state
        }

    /** Descriptor changes must finish physical teardown before touching registration. */
    suspend fun disconnectAll(): Boolean = transactionMutex.withLock { disconnectAllLocked() }

    private suspend fun disconnectAllLocked(): Boolean {
        for (address in facade.busyAddresses()) {
            val before = events.value[address]?.revision ?: 0
            if (facade.state(address) == HostLinkState.DISCONNECTED) continue
            if (facade.state(address) != HostLinkState.DISCONNECTING && !facade.disconnect(address)) {
                if (facade.state(address) != HostLinkState.DISCONNECTED) return false
            }
            if (facade.state(address) != HostLinkState.DISCONNECTED &&
                awaitEvent(address, before, disconnectTimeoutMillis, setOf(HostLinkState.DISCONNECTED)) == null
            ) return false
            if (facade.state(address) != HostLinkState.DISCONNECTED) return false
        }
        return facade.busyAddresses().isEmpty()
    }

    suspend fun connect(address: String, requireFreshConnection: Boolean = false, onAttempt: (Int) -> Unit = {}): ConnectionResult = transactionMutex.withLock {
        // A host can auto-reconnect while registration is being replaced. Mode switches
        // require a new physical connect command after the new registration is confirmed.
        if (requireFreshConnection && !disconnectAllLocked()) return@withLock ConnectionResult.DISCONNECT_TIMED_OUT
        var result = ConnectionResult.COMMAND_REJECTED
        for (attempt in 1..maxAttempts) {
            if (facade.state(address) == HostLinkState.CONNECTED) return@withLock ConnectionResult.CONNECTED
            onAttempt(attempt)
            // A cancelled older request may still be connecting in the Bluetooth stack.
            for (other in facade.busyAddresses().filter { it != address }) {
                val before = events.value[other]?.revision ?: 0
                if (facade.state(other) != HostLinkState.DISCONNECTED) {
                    if (facade.state(other) != HostLinkState.DISCONNECTING) facade.disconnect(other)
                    if (facade.state(other) != HostLinkState.DISCONNECTED &&
                        awaitEvent(other, before, disconnectTimeoutMillis, setOf(HostLinkState.DISCONNECTED)) == null
                    ) return@withLock ConnectionResult.DISCONNECT_TIMED_OUT
                }
            }
            if (facade.state(address) == HostLinkState.DISCONNECTING) {
                val before = events.value[address]?.revision ?: 0
                if (facade.state(address) != HostLinkState.DISCONNECTED &&
                    awaitEvent(address, before, disconnectTimeoutMillis, setOf(HostLinkState.DISCONNECTED)) == null
                ) return@withLock ConnectionResult.DISCONNECT_TIMED_OUT
            }

            val before = events.value[address]?.revision ?: 0
            val accepted = when (facade.state(address)) {
                HostLinkState.CONNECTED -> return@withLock ConnectionResult.CONNECTED
                HostLinkState.CONNECTING -> true // Join the existing handshake, do not issue another connect.
                else -> facade.connect(address)
            }
            if (facade.state(address) == HostLinkState.CONNECTED) return@withLock ConnectionResult.CONNECTED
            if (accepted) {
                val state = awaitEvent(address, before, connectionTimeoutMillis,
                    setOf(HostLinkState.CONNECTED, HostLinkState.DISCONNECTED))
                if (state == HostLinkState.CONNECTED) return@withLock ConnectionResult.CONNECTED
                result = ConnectionResult.TIMED_OUT
            } else {
                result = ConnectionResult.COMMAND_REJECTED
            }
            if (attempt < maxAttempts) delay(retryDelayMillis * attempt)
        }
        result
    }
}

internal fun shouldAcceptConnectedHost(desired: String?, address: String): Boolean = desired == null || desired == address
internal fun shouldClearConnectedHost(active: String?, address: String): Boolean = active == address
