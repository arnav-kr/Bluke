package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private enum class RegistrationBehavior {
    REJECT_COMMAND,
    ACCEPT_WITH_CALLBACK,
    ACCEPT_WITHOUT_CALLBACK,
}

private class FakeBluetoothRegistrationFacade(
    initialRegistered: Boolean = false,
    behaviors: List<RegistrationBehavior>,
) : BluetoothRegistrationFacade {
    override val registrationState = MutableStateFlow(initialRegistered)
    private val remainingBehaviors = ArrayDeque(behaviors)

    var registerCalls = 0
        private set
    var unregisterCalls = 0
        private set

    override fun unregisterApp() {
        unregisterCalls++
        registrationState.value = false
    }

    override fun registerApp(): Boolean {
        registerCalls++
        return when (remainingBehaviors.removeFirst()) {
            RegistrationBehavior.REJECT_COMMAND -> false
            RegistrationBehavior.ACCEPT_WITH_CALLBACK -> {
                registrationState.value = true
                true
            }
            RegistrationBehavior.ACCEPT_WITHOUT_CALLBACK -> true
        }
    }
}

abstract class BluetoothRegistrationFacadeContract {
    protected abstract val expectedApi: Int

    private val retryPolicy = RetryPolicy(
        maxAttempts = 3,
        initialDelayMillis = 10,
        maxDelayMillis = 40,
        jitterRatio = 0.0,
    )

    @Test
    fun matrixRunsOnExpectedApi() {
        assertEquals(expectedApi, RuntimeEnvironment.getApiLevel())
    }

    @Test
    fun acceptedCallbackRegistersOnFirstAttempt() = runTest {
        val facade = FakeBluetoothRegistrationFacade(
            behaviors = listOf(RegistrationBehavior.ACCEPT_WITH_CALLBACK),
        )
        val coordinator = coordinator(facade)

        assertEquals(RegistrationResult.Registered(1), coordinator.register(forceReset = false))
        assertEquals(1, facade.registerCalls)
        assertEquals(1, facade.unregisterCalls)
    }

    @Test
    fun rejectedCommandsStopAtHardAttemptCeiling() = runTest {
        val facade = FakeBluetoothRegistrationFacade(
            behaviors = List(3) { RegistrationBehavior.REJECT_COMMAND },
        )

        assertEquals(RegistrationResult.Rejected(3), coordinator(facade).register(forceReset = false))
        assertEquals(3, facade.registerCalls)
        assertEquals(3, facade.unregisterCalls)
    }

    @Test
    fun missingOemCallbacksRemainInconclusiveAfterThreeAttempts() = runTest {
        val facade = FakeBluetoothRegistrationFacade(
            behaviors = List(3) { RegistrationBehavior.ACCEPT_WITHOUT_CALLBACK },
        )

        val result = coordinator(facade).register(forceReset = false)

        assertEquals(RegistrationResult.TimedOut(3, CALLBACK_TIMEOUT_MILLIS), result)
        assertEquals(3, facade.registerCalls)
        assertEquals(3, facade.unregisterCalls)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun lateOemCallbackResumesPassiveWaitWithoutFourthCommand() = runTest {
        val facade = FakeBluetoothRegistrationFacade(
            behaviors = List(3) { RegistrationBehavior.ACCEPT_WITHOUT_CALLBACK },
        )
        val coordinator = coordinator(facade)
        assertTrue(coordinator.register(forceReset = false) is RegistrationResult.TimedOut)

        val resumed = async {
            coordinator.awaitLateRegistration()
            true
        }
        runCurrent()
        assertFalse(resumed.isCompleted)

        facade.registrationState.value = true
        runCurrent()

        assertTrue(resumed.await())
        assertEquals(3, facade.registerCalls)
    }

    @Test
    fun existingRegistrationSkipsCommandsUnlessResetIsForced() = runTest {
        val alreadyRegistered = FakeBluetoothRegistrationFacade(
            initialRegistered = true,
            behaviors = emptyList(),
        )
        assertEquals(
            RegistrationResult.Registered(0),
            coordinator(alreadyRegistered).register(forceReset = false),
        )
        assertEquals(0, alreadyRegistered.registerCalls)
        assertEquals(0, alreadyRegistered.unregisterCalls)

        val forced = FakeBluetoothRegistrationFacade(
            initialRegistered = true,
            behaviors = listOf(RegistrationBehavior.ACCEPT_WITH_CALLBACK),
        )
        assertEquals(RegistrationResult.Registered(1), coordinator(forced).register(forceReset = true))
        assertEquals(1, forced.registerCalls)
        assertEquals(1, forced.unregisterCalls)
    }

    private fun coordinator(
        facade: FakeBluetoothRegistrationFacade,
    ) = HidRegistrationCoordinator(
        facade = facade,
        retryPolicy = retryPolicy,
        callbackTimeoutMillis = CALLBACK_TIMEOUT_MILLIS,
        initialCleanupDelayMillis = 0,
        jitter = { 0.0 },
    )

    private companion object {
        const val CALLBACK_TIMEOUT_MILLIS = 100L
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BluetoothRegistrationFacadeApi28Test : BluetoothRegistrationFacadeContract() {
    override val expectedApi = 28
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BluetoothRegistrationFacadeApi31Test : BluetoothRegistrationFacadeContract() {
    override val expectedApi = 31
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothRegistrationFacadeApi36Test : BluetoothRegistrationFacadeContract() {
    override val expectedApi = 36
}
