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
        assertEquals(listOf(GamepadDpadOutputMode.ANDROID, GamepadDpadOutputMode.WEB_BUTTONS), attempts)
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

    @Test fun desktopProfilesKeepOriginalDescriptorExactly() {
        for (mode in listOf(GamepadDpadOutputMode.NATIVE_HAT, GamepadDpadOutputMode.WEB_BUTTONS)) {
            val descriptor = hidDescriptorForMode(mode)
            assertEquals(237, descriptor.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(descriptor)
                .joinToString("") { "%02X".format(it.toInt() and 0xff) }
            assertEquals("6A99D5607B74B803329498B709761B898616A6F7905527456FDCD5A3BBD2DB62", hash)
        }
    }

    @Test fun androidChangesOnlyRightVerticalAxisUsage() {
        val desktop = hidDescriptorForMode(GamepadDpadOutputMode.NATIVE_HAT)
        val android = hidDescriptorForMode(GamepadDpadOutputMode.ANDROID)
        assertEquals(desktop.size, android.size)
        val changed = desktop.indices.filter { desktop[it] != android[it] }
        assertEquals(1, changed.size)
        val index = changed.single()
        assertEquals(0x33, desktop[index].toInt())
        assertEquals(0x35, android[index].toInt())
        assertArrayEquals(byteArrayOf(0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35),
            android.copyOfRange(index - 7, index + 1))
    }

    @Test fun onlyCrossingAndroidBoundaryRequiresRegistrationRestart() {
        for (previous in GamepadDpadOutputMode.entries) {
            for (next in GamepadDpadOutputMode.entries) {
                assertEquals((previous == GamepadDpadOutputMode.ANDROID) !=
                    (next == GamepadDpadOutputMode.ANDROID),
                    requiresGamepadDescriptorRestart(previous, next))
            }
        }
    }

    @Test fun descriptorsAreIndependentCopies() {
        hidDescriptorForMode(GamepadDpadOutputMode.ANDROID).fill(0)
        desktopProfilesKeepOriginalDescriptorExactly()
        androidChangesOnlyRightVerticalAxisUsage()
    }

    @Test fun androidRightAxesKeepIndependentSixteenBitFields() {
        val report = buildGamepadReport(0, 0, 0f, 0f, -1f, 1f, GamepadDpadOutputMode.ANDROID)
        assertEquals(12, report.size)
        assertArrayEquals(byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()), report.copyOfRange(8, 12))
        val reversed = buildGamepadReport(0, 0, 0f, 0f, 1f, -1f, GamepadDpadOutputMode.ANDROID)
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0), reversed.copyOfRange(8, 12))
    }
}
