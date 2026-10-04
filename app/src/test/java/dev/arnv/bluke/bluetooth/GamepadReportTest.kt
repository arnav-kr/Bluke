package dev.arnv.bluke.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadReportTest {
    @Test
    fun hatSwitch_mapsEightDirectionsAndNeutral() {
        val expected = mapOf(
            0x01 to 0,
            0x09 to 1,
            0x08 to 2,
            0x0A to 3,
            0x02 to 4,
            0x06 to 5,
            0x04 to 6,
            0x05 to 7,
            0x00 to GAMEPAD_HAT_NEUTRAL,
            0x03 to GAMEPAD_HAT_NEUTRAL,
            0x0C to GAMEPAD_HAT_NEUTRAL,
        )

        expected.forEach { (mask, hat) -> assertEquals(hat, dpadMaskToHat(mask)) }
    }

    @Test
    fun report_reservesStandardDpadButtonSlotsAndUsesHatNibble() {
        val report = buildGamepadReport(
            buttonMask = (1 shl GAMEPAD_GUIDE_BUTTON_INDEX) or
                (1 shl GAMEPAD_TOUCHPAD_BUTTON_INDEX),
            dpadMask = 0x08,
            leftX = -1f,
            leftY = 0f,
            rightX = 1f,
            rightY = 0f,
        )

        assertEquals(GAMEPAD_REPORT_SIZE_BYTES, report.size)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x05, 0x02,
                0x00, 0x00,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0x7F,
            ),
            report,
        )
    }

    @Test
    fun webCompatibility_reportsCardinalDpadAsButtonsAndNeutralHat() {
        val report = buildGamepadReport(
            buttonMask = 1 shl GAMEPAD_GUIDE_BUTTON_INDEX,
            dpadMask = 0x01,
            leftX = 0f,
            leftY = 0f,
            rightX = 0f,
            rightY = 0f,
            dpadOutputMode = GamepadDpadOutputMode.WEB_BUTTONS,
        )

        assertEquals(0x10, report[1].toInt() and 0xFF)
        assertEquals(0x01, report[2].toInt() and 0xFF)
        assertEquals(GAMEPAD_HAT_NEUTRAL, report[3].toInt() and 0xFF)
    }

    @Test
    fun webCompatibility_reportsDiagonalAsTwoButtons() {
        val report = buildGamepadReport(
            buttonMask = 0,
            dpadMask = 0x09,
            leftX = 0f,
            leftY = 0f,
            rightX = 0f,
            rightY = 0f,
            dpadOutputMode = GamepadDpadOutputMode.WEB_BUTTONS,
        )

        assertEquals(0x90, report[1].toInt() and 0xFF)
        assertEquals(GAMEPAD_HAT_NEUTRAL, report[3].toInt() and 0xFF)
    }

    @Test
    fun nativeMode_clearsReservedDpadButtonBits() {
        val report = buildGamepadReport(
            buttonMask = 0x0F shl GAMEPAD_DPAD_FIRST_BUTTON_INDEX,
            dpadMask = 0x02,
            leftX = 0f,
            leftY = 0f,
            rightX = 0f,
            rightY = 0f,
        )

        assertEquals(0, report[1].toInt() and 0xF0)
        assertEquals(4, report[3].toInt() and 0xFF)
    }

    @Test
    fun outputModePreference_defaultsToNativeHat() {
        assertEquals(GamepadDpadOutputMode.NATIVE_HAT, GamepadDpadOutputMode.fromPreference(null))
        assertEquals(GamepadDpadOutputMode.NATIVE_HAT, GamepadDpadOutputMode.fromPreference("unknown"))
        assertEquals(
            GamepadDpadOutputMode.WEB_BUTTONS,
            GamepadDpadOutputMode.fromPreference(GamepadDpadOutputMode.WEB_BUTTONS.preferenceValue),
        )
    }

    @Test
    fun outputModeNext_cyclesNativeAndroidAndWeb() {
        assertEquals(
            GamepadDpadOutputMode.ANDROID,
            GamepadDpadOutputMode.NATIVE_HAT.next(),
        )
        assertEquals(GamepadDpadOutputMode.WEB_BUTTONS, GamepadDpadOutputMode.ANDROID.next())
        assertEquals(
            GamepadDpadOutputMode.NATIVE_HAT,
            GamepadDpadOutputMode.WEB_BUTTONS.next(),
        )
    }

    @Test
    fun neutralReport_releasesButtonsHatAndCentersAxes() {
        val report = buildGamepadReport(0, 0, 0f, 0f, 0f, 0f)

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x0F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
            ),
            report,
        )
    }

    @Test
    fun auxiliaryButtonsDoNotOverlapCanonicalDpadIndices() {
        assertEquals(16, GAMEPAD_GUIDE_BUTTON_INDEX)
        assertEquals(17, GAMEPAD_SHARE_BUTTON_INDEX)
        assertEquals(18, GAMEPAD_TOUCHPAD_BUTTON_INDEX)
        assertEquals(24, GAMEPAD_BUTTON_COUNT + GAMEPAD_BUTTON_PADDING_BITS)
        assertEquals(12, GAMEPAD_REPORT_SIZE_BYTES)
    }

    @Test
    fun androidMode_mapsEveryStandardButtonWithoutCollisions() {
        val destinations = listOf(0, 1, 3, 4, 6, 7, 8, 9, 10, 11, 13, 14)
        destinations.forEachIndexed { source, destination ->
            assertEquals(1 shl destination, androidGamepadButtonMask(1 shl source))
        }
        assertEquals(1 shl 12, androidGamepadButtonMask(1 shl GAMEPAD_GUIDE_BUTTON_INDEX))
        assertEquals(12, destinations.distinct().size)
    }

    @Test
    fun nativeAndWeb_keepTheirExistingButtonOrdering() {
        val native = buildGamepadReport(-1, 0x09, 0f, 0f, 0f, 0f)
        val web = buildGamepadReport(-1, 0x09, 0f, 0f, 0f, 0f, GamepadDpadOutputMode.WEB_BUTTONS)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x0F, 0x07, 0x01), native.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x9F.toByte(), 0x07, 0x0F), web.copyOfRange(0, 4))
    }

    @Test
    fun androidMode_usesHatEvenWhenStickClickAndGuideOccupyWebDpadSlots() {
        val report = buildGamepadReport(
            (1 shl 10) or (1 shl 11) or (1 shl GAMEPAD_GUIDE_BUTTON_INDEX),
            0x09, 0f, 0f, 0f, 0f, GamepadDpadOutputMode.ANDROID,
        )
        assertEquals(0x70, report[1].toInt() and 0xFF)
        assertEquals(0, report[2].toInt() and 0xFF)
        assertEquals(1, report[3].toInt() and 0xFF)
    }

    @Test
    fun androidMode_preservesExtrasAndIgnoresReservedAndOverflowInputBits() {
        assertEquals((1 shl 17) or (1 shl 18), androidGamepadButtonMask((1 shl 17) or (1 shl 18)))
        assertEquals(0, androidGamepadButtonMask((0xF shl 12) or (1 shl 25)))
        val report = buildGamepadReport(-1, 0, 0f, 0f, 0f, 0f, GamepadDpadOutputMode.ANDROID)
        assertEquals(0xDB, report[0].toInt() and 0xFF)
        assertEquals(0x7F, report[1].toInt() and 0xFF)
        assertEquals(0x06, report[2].toInt() and 0xFF)
        assertEquals(GAMEPAD_REPORT_SIZE_BYTES, report.size)
    }

    @Test
    fun allModes_shareNeutralPacketAndUnchangedAxisEncoding() {
        val nativeNeutral = buildGamepadReport(0, 0, 0f, 0f, 0f, 0f)
        val nativeAxes = buildGamepadReport(0, 0, -1f, 1f, 0.5f, -0.5f).copyOfRange(4, 12)
        GamepadDpadOutputMode.entries.forEach { mode ->
            assertArrayEquals(nativeNeutral, buildGamepadReport(0, 0, 0f, 0f, 0f, 0f, mode))
            assertArrayEquals(nativeAxes, buildGamepadReport(0, 0, -1f, 1f, 0.5f, -0.5f, mode).copyOfRange(4, 12))
            assertEquals(mode, GamepadDpadOutputMode.fromPreference(mode.preferenceValue))
        }
    }
}
