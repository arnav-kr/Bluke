package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Commands and callbacks are serialized on the Bluetooth callback executor. */
internal class HidRegistrationAttempt(val mode: GamepadDpadOutputMode) {
    enum class State { PENDING, WAITING, REGISTERED, UNREGISTERING, UNREGISTERED, REJECTED }
    private val mutableState = MutableStateFlow(State.PENDING)
    val state: StateFlow<State> = mutableState

    fun commandCompleted(accepted: Boolean) {
        check(mutableState.value == State.PENDING)
        mutableState.value = if (accepted) State.WAITING else State.REJECTED
    }

    fun beginUnregister() {
        if (mutableState.value in setOf(State.WAITING, State.REGISTERED)) {
            mutableState.value = State.UNREGISTERING
        }
    }

    fun statusChanged(registered: Boolean): Boolean {
        val current = mutableState.value
        if (current in setOf(State.PENDING, State.REJECTED, State.UNREGISTERED)) return false
        if (registered && current == State.UNREGISTERING) return false
        mutableState.value = if (registered) State.REGISTERED else State.UNREGISTERED
        return true
    }
}
