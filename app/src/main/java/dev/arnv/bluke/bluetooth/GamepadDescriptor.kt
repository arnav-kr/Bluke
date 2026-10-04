package dev.arnv.bluke.bluetooth

internal val DESKTOP_HID_DESCRIPTOR = byteArrayOf(
    0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x06.toByte(),         // USAGE (Keyboard)
    0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
    0x85.toByte(), 0x01.toByte(),         //   REPORT_ID (1)
    0x05.toByte(), 0x07.toByte(),         //   USAGE_PAGE (Keyboard)
    0x19.toByte(), 0xe0.toByte(),         //   USAGE_MINIMUM (Keyboard LeftControl)
    0x29.toByte(), 0xe7.toByte(),         //   USAGE_MAXIMUM (Keyboard Right GUI)
    0x15.toByte(), 0x00.toByte(),         //   LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),         //   LOGICAL_MAXIMUM (1)
    0x75.toByte(), 0x01.toByte(),         //   REPORT_SIZE (1)
    0x95.toByte(), 0x08.toByte(),         //   REPORT_COUNT (8)
    0x81.toByte(), 0x02.toByte(),         //   INPUT (Data,Var,Abs) - Modifier byte
    0x95.toByte(), 0x01.toByte(),         //   REPORT_COUNT (1)
    0x75.toByte(), 0x08.toByte(),         //   REPORT_SIZE (8)
    0x81.toByte(), 0x03.toByte(),         //   INPUT (Cnst,Var,Abs) - Reserved byte
    0x95.toByte(), 0x05.toByte(),         //   REPORT_COUNT (5)
    0x75.toByte(), 0x01.toByte(),         //   REPORT_SIZE (1)
    0x05.toByte(), 0x08.toByte(),         //   USAGE_PAGE (LEDs)
    0x19.toByte(), 0x01.toByte(),         //   USAGE_MINIMUM (Num Lock)
    0x29.toByte(), 0x05.toByte(),         //   USAGE_MAXIMUM (Kana)
    0x91.toByte(), 0x02.toByte(),         //   OUTPUT (Data,Var,Abs) - LED report
    0x95.toByte(), 0x01.toByte(),         //   REPORT_COUNT (1)
    0x75.toByte(), 0x03.toByte(),         //   REPORT_SIZE (3)
    0x91.toByte(), 0x03.toByte(),         //   OUTPUT (Cnst,Var,Abs) - LED report padding
    0x95.toByte(), 0x06.toByte(),         //   REPORT_COUNT (6)
    0x75.toByte(), 0x08.toByte(),         //   REPORT_SIZE (8)
    0x15.toByte(), 0x00.toByte(),         //   LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x65.toByte(),         //   LOGICAL_MAXIMUM (101)
    0x05.toByte(), 0x07.toByte(),         //   USAGE_PAGE (Keyboard)
    0x19.toByte(), 0x00.toByte(),         //   USAGE_MINIMUM (Reserved)
    0x29.toByte(), 0x65.toByte(),         //   USAGE_MAXIMUM (Keyboard Application)
    0x81.toByte(), 0x00.toByte(),         //   INPUT (Data,Ary,Abs) - Keycodes (6 bytes)
    0xc0.toByte(),                        // END_COLLECTION

    // Mouse/Trackpad (Report ID 2)
    0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x02.toByte(),         // USAGE (Mouse)
    0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
    0x85.toByte(), 0x02.toByte(),         //   REPORT_ID (2)
    0x09.toByte(), 0x01.toByte(),         //   USAGE (Pointer)
    0xa1.toByte(), 0x00.toByte(),         //   COLLECTION (Physical)
    0x05.toByte(), 0x09.toByte(),         //     USAGE_PAGE (Button)
    0x19.toByte(), 0x01.toByte(),         //     USAGE_MINIMUM (Button 1)
    0x29.toByte(), 0x03.toByte(),         //     USAGE_MAXIMUM (Button 3)
    0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),         //     LOGICAL_MAXIMUM (1)
    0x95.toByte(), 0x03.toByte(),         //     REPORT_COUNT (3)
    0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
    0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - L, R, M clicks
    0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
    0x75.toByte(), 0x05.toByte(),         //     REPORT_SIZE (5)
    0x81.toByte(), 0x03.toByte(),         //     INPUT (Cnst,Var,Abs) - padding
    0x05.toByte(), 0x01.toByte(),         //     USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x30.toByte(),         //     USAGE (X)
    0x09.toByte(), 0x31.toByte(),         //     USAGE (Y)
    0x15.toByte(), 0x81.toByte(),         //     LOGICAL_MINIMUM (-127)
    0x25.toByte(), 0x7f.toByte(),         //     LOGICAL_MAXIMUM (127)
    0x75.toByte(), 0x08.toByte(),         //     REPORT_SIZE (8)
    0x95.toByte(), 0x02.toByte(),         //     REPORT_COUNT (2)
    0x81.toByte(), 0x06.toByte(),         //     INPUT (Data,Var,Rel) - delta X and Y movement
    0x09.toByte(), 0x38.toByte(),         //     USAGE (Wheel)
    0x15.toByte(), 0x81.toByte(),         //     LOGICAL_MINIMUM (-127)
    0x25.toByte(), 0x7f.toByte(),         //     LOGICAL_MAXIMUM (127)
    0x75.toByte(), 0x08.toByte(),         //     REPORT_SIZE (8)
    0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
    0x81.toByte(), 0x06.toByte(),         //     INPUT (Data,Var,Rel) - scroll wheel
    0xc0.toByte(),                        //   END_COLLECTION
    0xc0.toByte(),                        // END_COLLECTION

    // Gamepad (Report ID 3)
    0x05.toByte(), 0x01.toByte(),         // USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x05.toByte(),         // USAGE (Gamepad)
    0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
    0x85.toByte(), 0x03.toByte(),         //   REPORT_ID (3)
    0x05.toByte(), 0x09.toByte(),         //   USAGE_PAGE (Button)
    0x19.toByte(), 0x01.toByte(),         //     USAGE_MINIMUM (Button 1)
    0x29.toByte(), GAMEPAD_BUTTON_COUNT.toByte(), // USAGE_MAXIMUM (Button 19)
    0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),         //     LOGICAL_MAXIMUM (1)
    0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
    0x95.toByte(), GAMEPAD_BUTTON_COUNT.toByte(), // REPORT_COUNT (19)
    0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - 19 Buttons
    0x75.toByte(), 0x01.toByte(),         //     REPORT_SIZE (1)
    0x95.toByte(), GAMEPAD_BUTTON_PADDING_BITS.toByte(), // REPORT_COUNT (5)
    0x81.toByte(), 0x03.toByte(),         //     INPUT (Cnst,Var,Abs) - button padding
    0x05.toByte(), 0x01.toByte(),         //     USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x39.toByte(),         //     USAGE (Hat Switch)
    0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x07.toByte(),         //     LOGICAL_MAXIMUM (7)
    0x35.toByte(), 0x00.toByte(),         //     PHYSICAL_MINIMUM (0)
    0x46.toByte(), 0x3b.toByte(), 0x01.toByte(), // PHYSICAL_MAXIMUM (315)
    0x65.toByte(), 0x14.toByte(),         //     UNIT (English Rotation, degrees)
    0x75.toByte(), 0x04.toByte(),         //     REPORT_SIZE (4)
    0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
    0x81.toByte(), 0x42.toByte(),         //     INPUT (Data,Var,Abs,Null)
    0x65.toByte(), 0x00.toByte(),         //     UNIT (None)
    0x75.toByte(), 0x04.toByte(),         //     REPORT_SIZE (4)
    0x95.toByte(), 0x01.toByte(),         //     REPORT_COUNT (1)
    0x81.toByte(), 0x03.toByte(),         //     INPUT (Cnst,Var,Abs) - byte padding
    0x05.toByte(), 0x01.toByte(),         //     USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x30.toByte(),         //     USAGE (X) - Left Stick X
    0x09.toByte(), 0x31.toByte(),         //     USAGE (Y) - Left Stick Y
    0x09.toByte(), 0x32.toByte(),         //     USAGE (Z) - Right Stick X
    0x09.toByte(), 0x33.toByte(),         //     USAGE (Rx) - Right Stick Y
    0x15.toByte(), 0x00.toByte(),         //     LOGICAL_MINIMUM (0)
    0x27.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (65535)
    0x75.toByte(), 0x10.toByte(),         //     REPORT_SIZE (16)
    0x95.toByte(), 0x04.toByte(),         //     REPORT_COUNT (4)
    0x81.toByte(), 0x02.toByte(),         //     INPUT (Data,Var,Abs) - 4 16-bit Axes (X, Y, Z, Rx)
    0xc0.toByte(),                        // END_COLLECTION (Application)

    // Consumer controls (Report ID 4)
    0x05.toByte(), 0x0c.toByte(),         // USAGE_PAGE (Consumer)
    0x09.toByte(), 0x01.toByte(),         // USAGE (Consumer Control)
    0xa1.toByte(), 0x01.toByte(),         // COLLECTION (Application)
    0x85.toByte(), 0x04.toByte(),         //   REPORT_ID (4)
    0x15.toByte(), 0x00.toByte(),         //   LOGICAL_MINIMUM (0)
    0x26.toByte(), 0xff.toByte(), 0x03.toByte(), // LOGICAL_MAXIMUM (1023)
    0x19.toByte(), 0x00.toByte(),         //   USAGE_MINIMUM (Unassigned)
    0x2a.toByte(), 0xff.toByte(), 0x03.toByte(), // USAGE_MAXIMUM (1023)
    0x75.toByte(), 0x10.toByte(),         //   REPORT_SIZE (16)
    0x95.toByte(), 0x01.toByte(),         //   REPORT_COUNT (1)
    0x81.toByte(), 0x00.toByte(),         //   INPUT (Data,Ary,Abs)
    0xc0.toByte()                         // END_COLLECTION (Application)
)

// Android expects right-stick Z/Rz; preserve every other usage and report bit.
internal fun hidDescriptorForMode(mode: GamepadDpadOutputMode): ByteArray {
    val descriptor = DESKTOP_HID_DESCRIPTOR.copyOf()
    if (mode != GamepadDpadOutputMode.ANDROID) return descriptor
    val axes = byteArrayOf(0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x33)
    val offsets = (0..descriptor.size - axes.size).filter { offset ->
        axes.indices.all { descriptor[offset + it] == axes[it] }
    }
    check(offsets.size == 1) { "Expected exactly one gamepad axis usage sequence" }
    descriptor[offsets.single() + axes.lastIndex] = 0x35 // Rz (Android AXIS_RZ)
    return descriptor
}

internal fun requiresGamepadDescriptorRestart(
    previous: GamepadDpadOutputMode,
    next: GamepadDpadOutputMode,
): Boolean = (previous == GamepadDpadOutputMode.ANDROID) !=
    (next == GamepadDpadOutputMode.ANDROID)

/** Wait for the latest descriptor choice without cancelling a registration already in flight. */
internal suspend fun registerLatestGamepadDescriptor(
    selectedMode: () -> GamepadDpadOutputMode,
    registeredMode: () -> GamepadDpadOutputMode?,
    register: suspend () -> Boolean,
): Boolean {
    do {
        if (!register()) return false
    } while (registeredMode()?.let {
        requiresGamepadDescriptorRestart(it, selectedMode())
    } != false)
    return true
}

