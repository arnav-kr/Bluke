package dev.arnv.bluke.bluetooth

import dev.arnv.bluke.bluetooth.HidRegistrationAttempt.State
import org.junit.Assert.*
import org.junit.Test

class HidRegistrationAttemptTest {
    @Test fun commandAcceptanceIsNotRegistrationConfirmation() {
        val attempt = HidRegistrationAttempt(GamepadDpadOutputMode.ANDROID)
        attempt.commandCompleted(true)
        assertEquals(State.WAITING, attempt.state.value)
        assertTrue(attempt.statusChanged(true))
        assertEquals(State.REGISTERED, attempt.state.value)
    }

    @Test fun rejectedAttemptCannotBecomeRegisteredFromLateCallback() {
        val attempt = HidRegistrationAttempt(GamepadDpadOutputMode.ANDROID)
        attempt.commandCompleted(false)
        assertFalse(attempt.statusChanged(true))
        assertEquals(State.REJECTED, attempt.state.value)
    }

    @Test fun oldSuccessDuringTeardownDoesNotCompleteRegistration() {
        val attempt = HidRegistrationAttempt(GamepadDpadOutputMode.NATIVE_HAT)
        attempt.commandCompleted(true)
        attempt.statusChanged(true)
        attempt.beginUnregister()
        assertFalse(attempt.statusChanged(true))
        assertEquals(State.UNREGISTERING, attempt.state.value)
        assertTrue(attempt.statusChanged(false))
        assertEquals(State.UNREGISTERED, attempt.state.value)
        assertFalse(attempt.statusChanged(true))
    }

    @Test fun missingRegistrationCallbackStillRequiresTeardownOfAcceptedCommand() {
        val attempt = HidRegistrationAttempt(GamepadDpadOutputMode.ANDROID)
        attempt.commandCompleted(true)
        attempt.beginUnregister()
        assertEquals(State.UNREGISTERING, attempt.state.value)
        assertTrue(attempt.statusChanged(false))
    }

    @Test fun retiredAttemptCannotConfirmReplacement() {
        val old = HidRegistrationAttempt(GamepadDpadOutputMode.NATIVE_HAT)
        old.commandCompleted(true)
        old.beginUnregister()
        old.statusChanged(false)
        val replacement = HidRegistrationAttempt(GamepadDpadOutputMode.ANDROID)
        replacement.commandCompleted(true)
        assertFalse(old.statusChanged(true))
        assertEquals(State.WAITING, replacement.state.value)
    }
}
