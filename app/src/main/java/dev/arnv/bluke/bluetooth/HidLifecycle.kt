package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

sealed interface HidLifecycleState {
    data object Idle : HidLifecycleState
    data class BindingProxy(val attempt: Int) : HidLifecycleState
    data class Registering(val attempt: Int) : HidLifecycleState
    data object Registered : HidLifecycleState
    data class Connecting(val deviceAddress: String) : HidLifecycleState
    data class Connected(val deviceAddress: String) : HidLifecycleState
    data class Error(val failure: HidFailure) : HidLifecycleState
}

enum class HidFailure {
    BINDING_REJECTED,
    BINDING_TIMEOUT,
    REGISTRATION_REJECTED,
    REGISTRATION_TIMEOUT,
    CONNECTION_REJECTED,
}

/**
 * A synchronous rejection that persists through the bounded retry policy is the best signal
 * Android exposes for firmware that does not provide a usable HID Device role. Callback timeouts
 * remain inconclusive because some OEM stacks acknowledge a successful command late or not at all.
 */
internal fun HidFailure.indicatesLikelyDeviceIncompatibility(): Boolean = when (this) {
    HidFailure.BINDING_REJECTED,
    HidFailure.REGISTRATION_REJECTED -> true
    HidFailure.BINDING_TIMEOUT,
    HidFailure.REGISTRATION_TIMEOUT,
    HidFailure.CONNECTION_REJECTED -> false
}

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 300,
    val maxDelayMillis: Long = 2_000,
    val jitterRatio: Double = 0.25,
) {
    init {
        require(maxAttempts > 0)
        require(initialDelayMillis >= 0)
        require(maxDelayMillis >= initialDelayMillis)
        require(jitterRatio in 0.0..1.0)
    }

    fun delayMillis(attempt: Int, jitter: Double): Long {
        require(attempt > 0)
        require(jitter in -1.0..1.0)
        val multiplier = 1L shl min(attempt - 1, 30)
        val exponential = min(maxDelayMillis, initialDelayMillis * multiplier)
        return (exponential * (1.0 + jitterRatio * jitter)).toLong().coerceAtLeast(0)
    }
}

sealed interface BluetoothCapability {
    data object Checking : BluetoothCapability
    data object Available : BluetoothCapability
    data object RegistrationRejected : BluetoothCapability
    data class Inconclusive(val timeoutMillis: Long) : BluetoothCapability
}

class BluetoothCapabilityRepository(
    private val registrationState: StateFlow<Boolean>,
    private val requestRegistration: suspend () -> Boolean,
    private val callbackTimeoutMillis: Long = REGISTRATION_CALLBACK_TIMEOUT_MILLIS,
) {
    fun capability(): Flow<BluetoothCapability> = flow {
        emit(BluetoothCapability.Checking)
        if (registrationState.value) {
            emit(BluetoothCapability.Available)
            return@flow
        }

        if (!requestRegistration()) {
            emit(BluetoothCapability.RegistrationRejected)
            return@flow
        }

        val registered = withTimeoutOrNull(callbackTimeoutMillis) {
            registrationState.first { it }
        }
        emit(
            if (registered == true) BluetoothCapability.Available
            else BluetoothCapability.Inconclusive(callbackTimeoutMillis)
        )
    }

    companion object {
        // Field reports only establish this as a weak baseline, not proof of unsupported firmware.
        const val REGISTRATION_CALLBACK_TIMEOUT_MILLIS = 8_000L
    }
}
