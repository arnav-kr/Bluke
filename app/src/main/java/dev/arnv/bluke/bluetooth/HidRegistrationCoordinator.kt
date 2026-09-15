package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Small framework boundary so registration policy can be tested without Android Bluetooth. */
internal interface BluetoothRegistrationFacade {
    val registrationState: StateFlow<Boolean>

    fun unregisterApp()

    fun registerApp(): Boolean
}

internal sealed interface RegistrationResult {
    data class Registered(val attempts: Int) : RegistrationResult
    data class Rejected(val attempts: Int) : RegistrationResult
    data class TimedOut(val attempts: Int, val timeoutMillis: Long) : RegistrationResult
}

internal class HidRegistrationCoordinator(
    private val facade: BluetoothRegistrationFacade,
    private val retryPolicy: RetryPolicy,
    private val callbackTimeoutMillis: Long =
        BluetoothCapabilityRepository.REGISTRATION_CALLBACK_TIMEOUT_MILLIS,
    private val initialCleanupDelayMillis: Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Double = { Random.nextDouble(-1.0, 1.0) },
) {
    suspend fun register(
        forceReset: Boolean,
        onAttempt: (Int) -> Unit = {},
    ): RegistrationResult {
        if (facade.registrationState.value && !forceReset) {
            return RegistrationResult.Registered(attempts = 0)
        }

        facade.unregisterApp()
        sleep(initialCleanupDelayMillis)

        var lastResult: RegistrationResult = RegistrationResult.Rejected(attempts = 0)
        for (attempt in 1..retryPolicy.maxAttempts) {
            if (facade.registrationState.value) {
                return RegistrationResult.Registered(attempts = attempt - 1)
            }

            onAttempt(attempt)
            if (!facade.registerApp()) {
                lastResult = RegistrationResult.Rejected(attempt)
            } else {
                val registered = withTimeoutOrNull(callbackTimeoutMillis) {
                    facade.registrationState.first { it }
                }
                if (registered == true) {
                    return RegistrationResult.Registered(attempt)
                }
                lastResult = RegistrationResult.TimedOut(attempt, callbackTimeoutMillis)
            }

            if (attempt < retryPolicy.maxAttempts) {
                facade.unregisterApp()
                sleep(retryPolicy.delayMillis(attempt, jitter()))
            }
        }
        return lastResult
    }

    suspend fun awaitLateRegistration() {
        facade.registrationState.first { it }
    }
}
