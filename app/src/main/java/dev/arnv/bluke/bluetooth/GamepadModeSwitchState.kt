package dev.arnv.bluke.bluetooth

internal enum class GamepadModeSwitchState { IDLE, REGISTERING, RECONNECTING }

internal fun canChangeGamepadMode(state: GamepadModeSwitchState): Boolean =
    state == GamepadModeSwitchState.IDLE
