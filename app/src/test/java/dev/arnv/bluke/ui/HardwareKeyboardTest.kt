package dev.arnv.bluke.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyboardTest {
    private fun plan(
        keyCode: Int,
        phoneLayer: Boolean = false,
        layerChar: Int = 0,
        baseChar: Int = 0,
        typedChar: Int = 0,
        modifiers: Set<Int> = emptySet(),
    ) = HardwareKeyMap.plan(keyCode, phoneLayer, layerChar, baseChar, typedChar, modifiers)

    @Test
    fun modeFollowsDetectedKeyboardOnlyInAuto() {
        assertTrue(HardwareKeyboardMode.AUTO.isActive(hasPhysicalKeyboard = true))
        assertFalse(HardwareKeyboardMode.AUTO.isActive(hasPhysicalKeyboard = false))
        assertTrue(HardwareKeyboardMode.ON.isActive(hasPhysicalKeyboard = false))
        assertFalse(HardwareKeyboardMode.OFF.isActive(hasPhysicalKeyboard = true))
        assertEquals(HardwareKeyboardMode.AUTO, HardwareKeyboardMode.fromPreference(null))
    }

    @Test
    fun lettersAndSpecialKeysKeepTheirPosition() {
        assertEquals(KeyboardLayouts.KEY_A, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_A))
        assertEquals(KeyboardLayouts.KEY_Z, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_Z))
        assertEquals(KeyboardLayouts.KEY_1, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_1))
        assertEquals(KeyboardLayouts.KEY_0, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_0))
        assertEquals(KeyboardLayouts.KEY_BACKSPACE, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_DEL))
        assertEquals(KeyboardLayouts.KEY_DELETE, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(KeyboardLayouts.KEY_F12, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_F12))
        assertEquals(KeyboardLayouts.KEY_UP, HardwareKeyMap.usageFor(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun systemKeysAreLeftToAndroid() {
        listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER,
        ).forEach { assertNull("key $it", plan(it)) }
    }

    @Test
    fun physicalShiftCtrlAndMetaGoThroughTheKeyboard() {
        assertEquals(
            HardwareKeyPress(KeyboardLayouts.MOD_LSHIFT, throughKeyboard = true),
            plan(KeyEvent.KEYCODE_SHIFT_LEFT),
        )
        assertEquals(KeyboardLayouts.MOD_RCTRL, plan(KeyEvent.KEYCODE_CTRL_RIGHT)?.usage)
        assertEquals(KeyboardLayouts.MOD_LWIN, plan(KeyEvent.KEYCODE_META_LEFT)?.usage)
        assertTrue(HardwareKeyMap.isPhoneLayerKey(KeyEvent.KEYCODE_ALT_LEFT))
        assertTrue(HardwareKeyMap.isPhoneLayerKey(KeyEvent.KEYCODE_SYM))
    }

    @Test
    fun phoneAltTypesThePrintedSymbol() {
        // Alt+W on a Titan-style keyboard types "1".
        val press = plan(KeyEvent.KEYCODE_W, phoneLayer = true, layerChar = '1'.code, baseChar = 'w'.code)
        assertEquals(HardwareKeyPress(KeyboardLayouts.KEY_1, throughKeyboard = false), press)

        // A shifted symbol adds Shift for that key only.
        val at = plan(KeyEvent.KEYCODE_Q, phoneLayer = true, layerChar = '@'.code, baseChar = 'q'.code)
        assertEquals(KeyboardLayouts.KEY_2, at?.usage)
        assertEquals(listOf(KeyboardLayouts.MOD_LSHIFT), at?.addedModifiers)
    }

    @Test
    fun heldShiftIsLiftedForUnshiftedSymbols() {
        val press = plan(
            KeyEvent.KEYCODE_W,
            phoneLayer = true,
            layerChar = '1'.code,
            baseChar = 'W'.code,
            modifiers = setOf(KeyboardLayouts.MOD_LSHIFT),
        )
        assertEquals(listOf(KeyboardLayouts.MOD_LSHIFT), press?.suppressedModifiers)
        assertTrue(press?.addedModifiers.orEmpty().isEmpty())
    }

    @Test
    fun phoneAltWithoutPrintedSymbolActsAsHostAlt() {
        // Full keyboards have no Alt layer, so Alt+Tab stays Alt+Tab.
        val press = plan(KeyEvent.KEYCODE_TAB, phoneLayer = true, layerChar = '\t'.code, baseChar = '\t'.code)
        assertEquals(KeyboardLayouts.KEY_TAB, press?.usage)
        assertTrue(press!!.throughKeyboard)
        assertEquals(listOf(KeyboardLayouts.MOD_LALT), press.addedModifiers)
    }

    @Test
    fun hostShortcutModifiersSkipTheSymbolLayer() {
        val press = plan(
            KeyEvent.KEYCODE_W,
            phoneLayer = true,
            layerChar = '1'.code,
            baseChar = 'w'.code,
            modifiers = setOf(KeyboardLayouts.MOD_LCTRL),
        )
        assertEquals(KeyboardLayouts.KEY_W, press?.usage)
        assertEquals(listOf(KeyboardLayouts.MOD_LALT), press?.addedModifiers)
    }

    @Test
    fun keysWithoutUsPositionSendTheirCharacter() {
        val press = plan(KeyEvent.KEYCODE_AT, typedChar = '@'.code)
        assertEquals(KeyboardLayouts.KEY_2, press?.usage)
        assertEquals(listOf(KeyboardLayouts.MOD_LSHIFT), press?.addedModifiers)
        assertNull(plan(KeyEvent.KEYCODE_AT, typedChar = '€'.code))
    }

    @Test
    fun usCharacterTableCoversPrintableAscii() {
        (0x20..0x7E).map(Int::toChar).forEach { char ->
            assertTrue("missing '$char'", HardwareKeyMap.usageForCharacter(char) != null)
        }
        assertEquals(KeyboardLayouts.KEY_SLASH to true, HardwareKeyMap.usageForCharacter('?'))
        assertEquals(KeyboardLayouts.KEY_APOSTROPHE to false, HardwareKeyMap.usageForCharacter('\''))
    }

    @Test
    fun stripHasNoLetterKeysAndKeepsModifiersInPlace() {
        val base = TouchKeyboardLayouts.hardwareStripLayout(TouchLayer.BASE)
        val fn = TouchKeyboardLayouts.hardwareStripLayout(TouchLayer.FN)
        listOf(base, fn).forEach { rows ->
            assertEquals(2, rows.size)
            rows.forEach { row -> assertEquals(10f, row.maxOf { it.x + it.widthRatio }, 0.01f) }
            val keys = rows.flatten()
            assertTrue(keys.none { it.keyCode == 0 })
            assertTrue(keys.none { it.keyCode in KeyboardLayouts.KEY_A..KeyboardLayouts.KEY_Z })
            assertTrue(keys.none { it.keyCode == KeyboardLayouts.MOD_LSHIFT })
        }
        val pinned = setOf(
            TouchKeyboardLayouts.KEY_LAYER_FN,
            KeyboardLayouts.MOD_LCTRL,
            KeyboardLayouts.MOD_LWIN,
            KeyboardLayouts.MOD_LALT,
        )
        fun positions(rows: List<List<KeyLayoutInfo>>) =
            rows.flatten().filter { it.keyCode in pinned }.associate { it.keyCode to (it.x to it.y) }
        assertEquals(positions(base), positions(fn))
        assertEquals(KeyboardLayouts.KEY_ESC, base[0][0].keyCode)
        assertEquals(KeyboardLayouts.KEY_F1, fn[0][0].keyCode)
    }
}
