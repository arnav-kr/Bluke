package dev.arnv.bluke.bluetooth

internal const val GAMEPAD_HAT_NEUTRAL = 0x0F
internal const val GAMEPAD_DPAD_MODE_PREFERENCE = "gamepad_dpad_output_mode"
internal const val GAMEPAD_DPAD_FIRST_BUTTON_INDEX = 12
internal const val GAMEPAD_GUIDE_BUTTON_INDEX = 16
internal const val GAMEPAD_SHARE_BUTTON_INDEX = 17
internal const val GAMEPAD_TOUCHPAD_BUTTON_INDEX = 18
internal const val GAMEPAD_BUTTON_COUNT = 19
internal const val GAMEPAD_BUTTON_PADDING_BITS = 5
internal const val GAMEPAD_REPORT_SIZE_BYTES = 12
private const val GAMEPAD_DPAD_BUTTON_MASK = 0x0F shl GAMEPAD_DPAD_FIRST_BUTTON_INDEX

internal enum class GamepadDpadOutputMode(val preferenceValue: String) {
    NATIVE_HAT("native_hat"),
    WEB_BUTTONS("web_buttons");

    fun next(): GamepadDpadOutputMode = when (this) {
        NATIVE_HAT -> WEB_BUTTONS
        WEB_BUTTONS -> NATIVE_HAT
    }

    companion object {
        fun fromPreference(value: String?): GamepadDpadOutputMode =
            entries.firstOrNull { it.preferenceValue == value } ?: NATIVE_HAT
    }
}

internal fun dpadMaskToHat(mask: Int): Int = when (mask and 0x0F) {
    0x01 -> 0 // up
    0x09 -> 1 // up-right
    0x08 -> 2 // right
    0x0A -> 3 // down-right
    0x02 -> 4 // down
    0x06 -> 5 // down-left
    0x04 -> 6 // left
    0x05 -> 7 // up-left
    else -> GAMEPAD_HAT_NEUTRAL
}

internal fun dpadMaskToButtonMask(mask: Int): Int =
    (mask and 0x0F) shl GAMEPAD_DPAD_FIRST_BUTTON_INDEX

internal fun buildGamepadReport(
    buttonMask: Int,
    dpadMask: Int,
    leftX: Float,
    leftY: Float,
    rightX: Float,
    rightY: Float,
    dpadOutputMode: GamepadDpadOutputMode = GamepadDpadOutputMode.NATIVE_HAT,
): ByteArray {
    val buttonsWithoutDpad = buttonMask and GAMEPAD_DPAD_BUTTON_MASK.inv()
    val encodedButtonMask = when (dpadOutputMode) {
        GamepadDpadOutputMode.NATIVE_HAT -> buttonsWithoutDpad
        GamepadDpadOutputMode.WEB_BUTTONS -> buttonsWithoutDpad or dpadMaskToButtonMask(dpadMask)
    }
    val report = ByteArray(GAMEPAD_REPORT_SIZE_BYTES)
    report[0] = (encodedButtonMask and 0xFF).toByte()
    report[1] = ((encodedButtonMask ushr 8) and 0xFF).toByte()
    report[2] = ((encodedButtonMask ushr 16) and 0x07).toByte()
    report[3] = when (dpadOutputMode) {
        GamepadDpadOutputMode.NATIVE_HAT -> dpadMaskToHat(dpadMask).toByte()
        GamepadDpadOutputMode.WEB_BUTTONS -> GAMEPAD_HAT_NEUTRAL.toByte()
    }

    listOf(leftX, leftY, rightX, rightY).forEachIndexed { index, value ->
        val axis = ((value.coerceIn(-1f, 1f) + 1f) * 32767.5f)
            .toInt()
            .coerceIn(0, 65535)
        val offset = 4 + index * 2
        report[offset] = (axis and 0xFF).toByte()
        report[offset + 1] = ((axis ushr 8) and 0xFF).toByte()
    }
    return report
}
