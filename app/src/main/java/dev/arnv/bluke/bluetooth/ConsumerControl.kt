package dev.arnv.bluke.bluetooth

/** Standard usages from the USB HID Consumer usage page (0x0C). */
enum class ConsumerControl(
    val usageId: Int,
    val shortcutLabel: String,
    val actionLabel: String,
) {
    MUTE(0x00E2, "F1", "Mute"),
    VOLUME_DOWN(0x00EA, "F2", "Volume down"),
    VOLUME_UP(0x00E9, "F3", "Volume up"),
    PREVIOUS_TRACK(0x00B6, "F4", "Previous"),
    PLAY_PAUSE(0x00CD, "F5", "Play / pause"),
    NEXT_TRACK(0x00B5, "F6", "Next"),
    BRIGHTNESS_DOWN(0x0070, "F7", "Brightness down"),
    BRIGHTNESS_UP(0x006F, "F8", "Brightness up"),
    SLEEP(0x0032, "F12", "Sleep"),
}

fun buildConsumerControlReport(control: ConsumerControl?): ByteArray {
    val usage = control?.usageId ?: 0
    return byteArrayOf(
        (usage and 0xFF).toByte(),
        ((usage ushr 8) and 0xFF).toByte(),
    )
}
