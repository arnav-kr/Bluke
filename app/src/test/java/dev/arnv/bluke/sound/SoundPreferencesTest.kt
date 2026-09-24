package dev.arnv.bluke.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
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
}
