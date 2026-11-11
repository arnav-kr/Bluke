package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KeyboardHostOsTest {

    @Test
    fun pcLayoutIsUnchanged() {
        val rows = KeyboardLayouts.getLayout(KeyboardGeometry.entries.first())
        assertSame(rows, rows.withHostOs(KeyboardHostOs.PC))
    }

    @Test
    fun macSwapsAltAndWinInPlace() {
        for (geometry in KeyboardGeometry.entries) {
            val pc = KeyboardLayouts.getLayout(geometry).flatten()
            val mac = KeyboardLayouts.getLayout(geometry).withHostOs(KeyboardHostOs.MAC).flatten()
            assertEquals(pc.size, mac.size)
            pc.zip(mac).forEach { (before, after) ->
                // Position, size and theme slot never move.
                assertEquals(before.x, after.x)
                assertEquals(before.widthRatio, after.widthRatio)
                assertEquals(before.styleId, after.styleId)
                when (before.keyCode) {
                    KeyboardLayouts.MOD_LALT -> assertMac(after, "Cmd", KeyboardLayouts.MOD_LWIN)
                    KeyboardLayouts.MOD_RALT -> assertMac(after, "Cmd", KeyboardLayouts.MOD_RWIN)
                    KeyboardLayouts.MOD_LWIN -> assertMac(after, "Opt", KeyboardLayouts.MOD_LALT)
                    KeyboardLayouts.MOD_RWIN -> assertMac(after, "Opt", KeyboardLayouts.MOD_RALT)
                    else -> assertEquals(before, after)
                }
            }
        }
    }

    @Test
    fun macPutsCommandNextToSpaceOnTouchLayout() {
        val bottom = TouchKeyboardLayouts.getLayout(TouchLayer.BASE).withHostOs(KeyboardHostOs.MAC).last()
        assertEquals(listOf("Ctrl", "Opt", "Cmd"), bottom.take(3).map { it.legend })
        assertEquals(KeyboardLayouts.MOD_LWIN, bottom[2].keyCode)
    }

    @Test
    fun preferenceRoundTripsAndDefaultsToPc() {
        KeyboardHostOs.entries.forEach {
            assertEquals(it, KeyboardHostOs.fromPreference(it.preferenceValue))
        }
        assertEquals(KeyboardHostOs.PC, KeyboardHostOs.fromPreference(null))
        assertEquals(KeyboardHostOs.PC, KeyboardHostOs.fromPreference("bogus"))
        assertEquals(KeyboardHostOs.MAC, KeyboardHostOs.PC.toggled())
        assertEquals(KeyboardHostOs.PC, KeyboardHostOs.MAC.toggled())
    }

    private fun assertMac(key: KeyLayoutInfo, legend: String, keyCode: Int) {
        assertEquals(legend, key.legend)
        assertEquals(keyCode, key.keyCode)
        assertEquals(keyCode, key.physicalKeyCode)
    }
}
