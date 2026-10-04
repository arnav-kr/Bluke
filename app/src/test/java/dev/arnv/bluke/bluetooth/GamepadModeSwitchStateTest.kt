package dev.arnv.bluke.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class GamepadModeSwitchStateTest {
    @Test fun alreadyFinishedRequestDoesNotLeaveSpinnerWaiting() = runTest {
        assertTrue(awaitGamepadModeReconnect(MutableStateFlow<Int?>(null), null, 1))
        assertTrue(awaitGamepadModeReconnect(MutableStateFlow<Int?>(null), 1, 1))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun loadingWaitsUntilReconnectRequestFinishes() = runTest {
        val pending = MutableStateFlow<Int?>(1)
        val waiter = async { awaitGamepadModeReconnect(pending, 1) }
        runCurrent()
        assertFalse(waiter.isCompleted)
        pending.value = null
        assertTrue(waiter.await())
    }

    @Test fun replacingRequestReleasesOldLoadingWait() = runTest {
        val pending = MutableStateFlow<Int?>(2)
        assertTrue(awaitGamepadModeReconnect(pending, 1))
        org.junit.Assert.assertEquals(2, pending.value)
    }

    @Test fun stalledReconnectHasBoundedLoadingWait() = runTest {
        assertFalse(awaitGamepadModeReconnect(MutableStateFlow<Int?>(1), 1, 60_000))
    }

    @Test fun modeToggleIsEnabledOnlyWhenIdle() {
        assertTrue(canChangeGamepadMode(GamepadModeSwitchState.IDLE))
        assertFalse(canChangeGamepadMode(GamepadModeSwitchState.REGISTERING))
        assertFalse(canChangeGamepadMode(GamepadModeSwitchState.RECONNECTING))
    }
}
