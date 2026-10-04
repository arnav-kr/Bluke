package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal enum class GamepadModeSwitchState { IDLE, REGISTERING, RECONNECTING }

internal fun canChangeGamepadMode(state: GamepadModeSwitchState): Boolean =
    state == GamepadModeSwitchState.IDLE

internal suspend fun <T : Any> awaitGamepadModeReconnect(
    pending: StateFlow<T?>,
    request: T?,
    timeoutMillis: Long = 60_000L,
): Boolean {
    // A fast reconnect can finish before the caller captures/subscribes to its request.
    if (request == null) return true
    return withTimeoutOrNull(timeoutMillis) {
        pending.first { it != request }
        true
    } == true
}
