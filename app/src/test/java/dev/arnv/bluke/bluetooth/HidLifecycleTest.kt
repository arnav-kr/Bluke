package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HidLifecycleTest {
    @Test
    fun onlySynchronousProfileRejectionsIndicateLikelyIncompatibility() {
        assertEquals(true, HidFailure.BINDING_REJECTED.indicatesLikelyDeviceIncompatibility())
        assertEquals(true, HidFailure.REGISTRATION_REJECTED.indicatesLikelyDeviceIncompatibility())
        assertEquals(false, HidFailure.BINDING_TIMEOUT.indicatesLikelyDeviceIncompatibility())
        assertEquals(false, HidFailure.REGISTRATION_TIMEOUT.indicatesLikelyDeviceIncompatibility())
        assertEquals(false, HidFailure.CONNECTION_REJECTED.indicatesLikelyDeviceIncompatibility())
    }

    @Test
    fun retryPolicy_appliesExponentialBackoffAndBoundedJitter() {
        val policy = RetryPolicy(initialDelayMillis = 400, maxDelayMillis = 1_600, jitterRatio = 0.25)

        assertEquals(300, policy.delayMillis(attempt = 1, jitter = -1.0))
        assertEquals(1_000, policy.delayMillis(attempt = 2, jitter = 1.0))
        assertEquals(1_600, policy.delayMillis(attempt = 4, jitter = 0.0))
    }

    @Test
    fun capability_reportsRejectedRegistrationCommand() = runTest {
        val repository = BluetoothCapabilityRepository(
            registrationState = MutableStateFlow(false),
            requestRegistration = { false },
        )

        assertEquals(
            listOf(BluetoothCapability.Checking, BluetoothCapability.RegistrationRejected),
            repository.capability().toList(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun capability_timeoutIsInconclusiveNotUnsupported() = runTest {
        val repository = BluetoothCapabilityRepository(
            registrationState = MutableStateFlow(false),
            requestRegistration = { true },
            callbackTimeoutMillis = 8_000,
        )

        val result = repository.capability().toList()
        advanceTimeBy(8_000)

        assertEquals(BluetoothCapability.Checking, result.first())
        assertEquals(BluetoothCapability.Inconclusive(8_000), result.last())
    }
}
