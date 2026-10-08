package dev.arnv.bluke.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundPreferencesTest {
    private val preferences by lazy {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("sound-migration-test", Context.MODE_PRIVATE)
    }

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun freshInstallDefaultsSoundOn() {
        migrateSoundPreferences(preferences)

        assertTrue(preferences.getBoolean(KEY_SOUND_ENABLED_PREFERENCE, false))
    }

    @Test
    fun legacyToggleMigratesForExistingInstall() {
        preferences.edit().putBoolean("sound_toggle", true).commit()

        migrateSoundPreferences(preferences)

        assertTrue(preferences.getBoolean(KEY_SOUND_ENABLED_PREFERENCE, false))
    }

    @Test
    fun explicitMuteIsPreserved() {
        preferences.edit()
            .putBoolean(KEY_SOUND_ENABLED_PREFERENCE, false)
            .putBoolean("sound_toggle", true)
            .commit()

        migrateSoundPreferences(preferences)

        assertFalse(preferences.getBoolean(KEY_SOUND_ENABLED_PREFERENCE, true))
        assertFalse(preferences.getBoolean("sound_toggle", true))
    }

    @Test
    fun builtInSoundSelectionSurvivesActivityRecreation() {
        assertEquals(SwitchType.CHERRY_MX_BROWN, selectedBuiltInSound(preferences))

        preferences.edit()
            .putString(SELECTED_BUILT_IN_SOUND_PREFERENCE, SwitchType.TOPRE.name)
            .commit()

        assertEquals(SwitchType.TOPRE, selectedBuiltInSound(preferences))
        assertEquals("built_in:TOPRE", builtInSoundProfileId(SwitchType.TOPRE))
    }

    @Test
    fun legacyCycleSelectionMigratesAndIncludesExistingCustomPacks() {
        preferences.edit()
            .putStringSet(CYCLE_KEY_SOUNDS_PREFERENCE, setOf(SwitchType.TOPRE.name))
            .commit()

        val selected = soundCycleSelection(preferences, listOf("office"))

        assertEquals(setOf("built_in:TOPRE", "custom:office"), selected)
    }

    @Test
    fun explicitNativeCycleSelectionIsPreserved() {
        saveSoundCycleSelection(preferences, setOf("custom:office"))

        assertEquals(setOf("custom:office"), soundCycleSelection(preferences, listOf("office", "other")))
    }
}
