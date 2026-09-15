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
    fun report_keepsElevenByteWireSizeAndUsesHatNibble() {
        val report = buildGamepadReport(
            buttonMask = (1 shl 12) or (1 shl 14),
            dpadMask = 0x08,
            leftX = -1f,
            leftY = 0f,
            rightX = 1f,
            rightY = 0f,
        )

        assertEquals(11, report.size)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x50, 0x02,
                0x00, 0x00,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0x7F,
            ),
            report,
        )
    }

    @Test
    fun neutralReport_releasesButtonsHatAndCentersAxes() {
        val report = buildGamepadReport(0, 0, 0f, 0f, 0f, 0f)

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x0F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0x7F,
            ),
            report,
        )
    }
}
