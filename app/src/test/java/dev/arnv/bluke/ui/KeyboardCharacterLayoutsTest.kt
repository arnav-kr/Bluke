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

        assertEquals("A", keys.single { it.keyCode == KeyboardLayouts.KEY_Q }.legend)
        assertEquals("Q", keys.single { it.keyCode == KeyboardLayouts.KEY_A }.legend)
        assertEquals("M", keys.single { it.keyCode == KeyboardLayouts.KEY_SEMICOLON }.legend)
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

        assertEquals("Z", qwertz.single { it.keyCode == KeyboardLayouts.KEY_Y }.legend)
        assertEquals("Y", qwertz.single { it.keyCode == KeyboardLayouts.KEY_Z }.legend)
        assertEquals("'", dvorak.single { it.keyCode == KeyboardLayouts.KEY_Q }.legend)
        assertEquals("A", dvorak.single { it.keyCode == KeyboardLayouts.KEY_A }.legend)
    }

    @Test
    fun unknownPreferenceSafelyFallsBackToQwerty() {
        assertEquals(
            KeyboardCharacterLayout.US_QWERTY,
            KeyboardCharacterLayout.fromPreference("future_layout"),
        )
    }
}
