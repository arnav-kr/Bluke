package dev.arnv.bluke.bluetooth

internal const val GAMEPAD_HAT_NEUTRAL = 0x0F

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

internal fun buildGamepadReport(
    buttonMask: Int,
    dpadMask: Int,
    leftX: Float,
    leftY: Float,
    rightX: Float,
    rightY: Float,
): ByteArray {
    val report = ByteArray(11)
    report[0] = (buttonMask and 0xFF).toByte()
    report[1] = ((buttonMask ushr 8) and 0xFF).toByte()
    report[2] = dpadMaskToHat(dpadMask).toByte()

    listOf(leftX, leftY, rightX, rightY).forEachIndexed { index, value ->
        val axis = ((value.coerceIn(-1f, 1f) + 1f) * 32767.5f)
            .toInt()
            .coerceIn(0, 65535)
        val offset = 3 + index * 2
        report[offset] = (axis and 0xFF).toByte()
        report[offset + 1] = ((axis ushr 8) and 0xFF).toByte()
    }
    return report
}
