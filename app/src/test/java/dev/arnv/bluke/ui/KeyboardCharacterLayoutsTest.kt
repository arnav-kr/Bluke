package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardCharacterLayoutsTest {
    @Test
    fun azertyChangesLegendsWithoutChangingPhysicalHidUsages() {
        val keys = KeyboardLayouts.getLayout(
            KeyboardLayoutType.OBLIVION_75,
            KeyboardCharacterLayout.FRENCH_AZERTY,
        ).flatten()

        val physicalQ = keys.single { it.physicalKeyCode == KeyboardLayouts.KEY_Q }
        val physicalA = keys.single { it.physicalKeyCode == KeyboardLayouts.KEY_A }
        val physicalSemicolon = keys.single { it.physicalKeyCode == KeyboardLayouts.KEY_SEMICOLON }
        assertEquals("A", physicalQ.legend)
        assertEquals(KeyboardLayouts.KEY_A, physicalQ.keyCode)
        assertEquals("Q", physicalA.legend)
        assertEquals(KeyboardLayouts.KEY_Q, physicalA.keyCode)
        assertEquals("M", physicalSemicolon.legend)
        assertEquals(KeyboardLayouts.KEY_M, physicalSemicolon.keyCode)
    }

    @Test
    fun qwertzAndDvorakUsePhysicalPositions() {
        val qwertz = KeyboardLayouts.getLayout(
            KeyboardLayoutType.OBLIVION_75,
            KeyboardCharacterLayout.GERMAN_QWERTZ,
        ).flatten()
        val dvorak = KeyboardLayouts.getLayout(
            KeyboardLayoutType.OBLIVION_75,
            KeyboardCharacterLayout.DVORAK,
        ).flatten()

        assertEquals("Z", qwertz.single { it.physicalKeyCode == KeyboardLayouts.KEY_Y }.legend)
        assertEquals(KeyboardLayouts.KEY_Z, qwertz.single { it.physicalKeyCode == KeyboardLayouts.KEY_Y }.keyCode)
        assertEquals("Y", qwertz.single { it.physicalKeyCode == KeyboardLayouts.KEY_Z }.legend)
        assertEquals("'", dvorak.single { it.physicalKeyCode == KeyboardLayouts.KEY_Q }.legend)
        assertEquals(KeyboardLayouts.KEY_APOSTROPHE, dvorak.single { it.physicalKeyCode == KeyboardLayouts.KEY_Q }.keyCode)
        assertEquals("A", dvorak.single { it.physicalKeyCode == KeyboardLayouts.KEY_A }.legend)
    }

    @Test
    fun unknownPreferenceSafelyFallsBackToQwerty() {
        assertEquals(
            KeyboardCharacterLayout.US_QWERTY,
            KeyboardCharacterLayout.fromPreference("future_layout"),
        )
    }
}
