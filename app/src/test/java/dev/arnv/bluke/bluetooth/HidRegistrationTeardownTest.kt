package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HidRegistrationTeardownTest {
    @Test fun delayedTeardownMustFinishBeforeRegisterIsCalled() = runTest {
        val teardown = CompletableDeferred<Boolean>()
        var registerCalls = 0
        val facade = object : BluetoothRegistrationFacade {
            override val registrationState = MutableStateFlow(true)
            override suspend fun unregisterApp(): Boolean {
                val result = teardown.await()
                registrationState.value = false
                return result
            }
            override suspend fun registerApp(): Boolean {
                registerCalls++
                registrationState.value = true
                return true
            }
        }
        val result = async { HidRegistrationCoordinator(facade, RetryPolicy(), initialCleanupDelayMillis = 0).register(true) }
        runCurrent()
        assertEquals(0, registerCalls)
        teardown.complete(true)
        assertEquals(RegistrationResult.Registered(1), result.await())
    }

    @Test fun teardownFailureStopsBeforeReplacingDescriptor() = runTest {
        var registerCalls = 0
        val facade = object : BluetoothRegistrationFacade {
            override val registrationState = MutableStateFlow(true)
            override suspend fun unregisterApp() = false
            override suspend fun registerApp(): Boolean { registerCalls++; return true }
        }
        val result = HidRegistrationCoordinator(facade, RetryPolicy(), initialCleanupDelayMillis = 0).register(true)
        assertEquals(RegistrationResult.TeardownFailed, result)
        assertEquals(0, registerCalls)
    }
}
