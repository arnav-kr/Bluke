package dev.arnv.bluke

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import dev.arnv.bluke.ui.InputModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The mode cycle used to be six copies of the same int-to-string mapping, and a mode missing from
 * one copy would quietly drop out of the rotation. These tests pin the behaviour now that the
 * mapping lives in one place.
 */
@RunWith(RobolectricTestRunner::class)
class InputModesTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("input_modes_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun setEnabled(vararg keys: String) {
        prefs.edit().putStringSet("cycle_connection_modes", keys.toSet()).commit()
    }

    @Test
    fun `system keyboard is enabled by default`() {
        // The whole point of promoting it to a mode: no settings trip required.
        assertTrue(InputModes.enabled(prefs).contains(InputModes.SYSTEM_KEYBOARD))
    }

    @Test
    fun `all four modes are in the default rotation`() {
        assertEquals(
            listOf(
                InputModes.KEYBOARD,
                InputModes.TOUCHPAD,
                InputModes.GAMEPAD,
                InputModes.SYSTEM_KEYBOARD
            ),
            InputModes.enabled(prefs)
        )
    }

    @Test
    fun `every mode has a distinct preference key and display name`() {
        val keys = (0 until InputModes.ALL_COUNT).map { InputModes.prefKey(it) }
        val names = (0 until InputModes.ALL_COUNT).map { InputModes.displayName(it) }
        assertEquals(InputModes.ALL_COUNT, keys.toSet().size)
        assertEquals(InputModes.ALL_COUNT, names.toSet().size)
    }

    @Test
    fun `default enabled set covers every mode`() {
        assertEquals(InputModes.ALL_COUNT, InputModes.DEFAULT_ENABLED.size)
    }

    @Test
    fun `cycle wraps around to the first enabled mode`() {
        assertEquals(InputModes.SYSTEM_KEYBOARD, InputModes.next(prefs, InputModes.GAMEPAD))
        assertEquals(InputModes.KEYBOARD, InputModes.next(prefs, InputModes.SYSTEM_KEYBOARD))
    }

    @Test
    fun `cycle skips disabled modes`() {
        setEnabled("keyboard", "system_keyboard")
        assertEquals(InputModes.SYSTEM_KEYBOARD, InputModes.next(prefs, InputModes.KEYBOARD))
        assertEquals(InputModes.KEYBOARD, InputModes.next(prefs, InputModes.SYSTEM_KEYBOARD))
    }

    @Test
    fun `cycling from a mode that is no longer enabled still advances`() {
        // Previously indexOf returned -1 here, and one call site fed that straight into a modulo.
        setEnabled("touchpad", "gamepad")
        val next = InputModes.next(prefs, InputModes.SYSTEM_KEYBOARD)
        assertTrue(next == InputModes.TOUCHPAD || next == InputModes.GAMEPAD)
    }

    @Test
    fun `an empty selection falls back to keyboard rather than crashing`() {
        setEnabled()
        assertEquals(listOf(InputModes.KEYBOARD), InputModes.enabled(prefs))
        assertEquals(InputModes.KEYBOARD, InputModes.next(prefs, InputModes.KEYBOARD))
    }

    @Test
    fun `a single enabled mode cycles to itself`() {
        setEnabled("system_keyboard")
        assertEquals(InputModes.SYSTEM_KEYBOARD, InputModes.next(prefs, InputModes.SYSTEM_KEYBOARD))
    }
}
