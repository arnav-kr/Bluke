package dev.arnv.bluke.bluetooth

import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GamepadDescriptorTest {
    @Test fun switchUsesLatestModeWhenToggleArrivesDuringRegistration() = runBlocking {
        var selected = GamepadDpadOutputMode.ANDROID
        var registered: GamepadDpadOutputMode? = null
        val attempts = mutableListOf<GamepadDpadOutputMode>()
        assertTrue(registerLatestGamepadDescriptor({ selected }, { registered }) {
            attempts.add(selected)
            registered = selected
            if (attempts.size == 1) selected = GamepadDpadOutputMode.WEB_BUTTONS
            true
        })
        assertEquals(listOf(GamepadDpadOutputMode.ANDROID), attempts)
    }

    @Test fun nativeToWebDuringRegistrationNeedsNoSecondDescriptor() = runBlocking {
        var selected = GamepadDpadOutputMode.NATIVE_HAT
        var attempts = 0
        assertTrue(registerLatestGamepadDescriptor({ selected }, { GamepadDpadOutputMode.NATIVE_HAT }) {
            attempts++
            selected = GamepadDpadOutputMode.WEB_BUTTONS
            true
        })
        assertEquals(1, attempts)
    }

    @Test fun failedSwitchStopsWithoutUnboundedRetry() = runBlocking {
        var attempts = 0
        assertFalse(registerLatestGamepadDescriptor({ GamepadDpadOutputMode.ANDROID }, { null }) {
            attempts++
            false
        })
        assertEquals(1, attempts)
    }

    @Test fun allProfilesShareDescriptorAndPreserveOriginalFields() {
        for (mode in GamepadDpadOutputMode.entries) {
            val descriptor = hidDescriptorForMode(mode)
            assertEquals(239, descriptor.size)
            assertArrayEquals(hidDescriptorForMode(GamepadDpadOutputMode.NATIVE_HAT), descriptor)
            val axes = byteArrayOf(0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x33, 0x09, 0x35)
            val offset = (0..descriptor.size - axes.size).single { start ->
                axes.indices.all { descriptor[start + it] == axes[it] }
            }
            val original = descriptor.toMutableList()
            original.removeAt(offset + 9)
            original.removeAt(offset + 8)
            assertEquals(0x95.toByte(), original[offset + 17])
            assertEquals(5.toByte(), original[offset + 18])
            original[offset + 18] = 4
            val hash = MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
                .joinToString("") { "%02X".format(it.toInt() and 0xff) }
            assertEquals("6A99D5607B74B803329498B709761B898616A6F7905527456FDCD5A3BBD2DB62", hash)
        }
    }

    @Test fun androidAndDesktopAdvertiseIdenticalAxes() {
        val desktop = hidDescriptorForMode(GamepadDpadOutputMode.NATIVE_HAT)
        val android = hidDescriptorForMode(GamepadDpadOutputMode.ANDROID)
        assertEquals(desktop.size, android.size)
        val changed = desktop.indices.filter { desktop[it] != android[it] }
        assertTrue(changed.isEmpty())
    }

    @Test fun noModeTransitionRequiresRegistrationRestart() {
        for (previous in GamepadDpadOutputMode.entries) {
            for (next in GamepadDpadOutputMode.entries) {
                assertFalse(requiresGamepadDescriptorRestart(previous, next))
            }
        }
    }

    @Test fun descriptorsAreIndependentCopies() {
        hidDescriptorForMode(GamepadDpadOutputMode.ANDROID).fill(0)
        allProfilesShareDescriptorAndPreserveOriginalFields()
        androidAndDesktopAdvertiseIdenticalAxes()
    }

    @Test fun androidRightAxesKeepIndependentSixteenBitFields() {
        val report = buildGamepadReport(0, 0, 0f, 0f, -1f, 1f, GamepadDpadOutputMode.ANDROID)
        assertEquals(14, report.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0xff.toByte(), 0xff.toByte()), report.copyOfRange(8, 14))
        val reversed = buildGamepadReport(0, 0, 0f, 0f, 1f, -1f, GamepadDpadOutputMode.ANDROID)
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0, 0), reversed.copyOfRange(8, 14))
    }

    @Test fun descriptorInputLengthsMatchReportEncoders() {
        val descriptor = hidDescriptorForMode(GamepadDpadOutputMode.ANDROID)
        val bits = mutableMapOf<Int, Int>()
        var id = 0
        var size = 0
        var count = 0
        var offset = 0
        while (offset < descriptor.size) {
            val prefix = descriptor[offset++].toInt() and 255
            val length = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
            var value = 0
            repeat(length) { value = value or ((descriptor[offset++].toInt() and 255) shl (8 * it)) }
            when (prefix and 0xfc) {
                0x84 -> id = value
                0x74 -> size = value
                0x94 -> count = value
                0x80 -> bits[id] = bits.getOrDefault(id, 0) + size * count
            }
        }
        assertEquals(mapOf(1 to 64, 2 to 32, 3 to 112, 4 to 16), bits)
        assertEquals(GAMEPAD_REPORT_SIZE_BYTES * 8, bits[3])
    }
}
