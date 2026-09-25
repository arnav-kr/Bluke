package dev.arnv.bluke.bluetooth

import dev.arnv.bluke.ui.KeyboardLayouts
import dev.arnv.bluke.ui.consumerControlForFnKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumerControlTest {
    @Test
    fun fnFunctionKeysMapToStandardConsumerUsages() {
        assertEquals(ConsumerControl.MUTE, consumerControlForFnKey(KeyboardLayouts.KEY_F1))
        assertEquals(ConsumerControl.VOLUME_DOWN, consumerControlForFnKey(KeyboardLayouts.KEY_F2))
        assertEquals(ConsumerControl.VOLUME_UP, consumerControlForFnKey(KeyboardLayouts.KEY_F3))
        assertEquals(ConsumerControl.PLAY_PAUSE, consumerControlForFnKey(KeyboardLayouts.KEY_F5))
        assertEquals(ConsumerControl.SLEEP, consumerControlForFnKey(KeyboardLayouts.KEY_F12))
        assertNull(consumerControlForFnKey(KeyboardLayouts.KEY_A))
    }

    @Test
    fun consumerReportUsesLittleEndianUsageAndZeroForRelease() {
        assertArrayEquals(
            byteArrayOf(0xE9.toByte(), 0x00),
            buildConsumerControlReport(ConsumerControl.VOLUME_UP),
        )
        assertArrayEquals(byteArrayOf(0x00, 0x00), buildConsumerControlReport(null))
    }

    @Test
    fun fnKeysUseLocalCodeOutsideTheKeyboardUsageByteRange() {
        assertEquals(
            KeyboardLayouts.KEY_FN,
            KeyboardLayouts.getLayout(
                dev.arnv.bluke.ui.KeyboardLayoutType.OBLIVION_75,
                dev.arnv.bluke.ui.KeyboardCharacterLayout.US_QWERTY,
            ).flatten().first { it.legend == "Fn" }.keyCode,
        )
    }
}
