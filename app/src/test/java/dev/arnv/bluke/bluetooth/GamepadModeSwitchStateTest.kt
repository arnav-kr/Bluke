package dev.arnv.bluke.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadModeSwitchStateTest {
    @Test fun modeToggleIsEnabledOnlyWhenIdle() {
        assertTrue(canChangeGamepadMode(GamepadModeSwitchState.IDLE))
        assertFalse(canChangeGamepadMode(GamepadModeSwitchState.REGISTERING))
        assertFalse(canChangeGamepadMode(GamepadModeSwitchState.RECONNECTING))
    }
}
