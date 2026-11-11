package dev.arnv.bluke.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import dev.arnv.bluke.data.LEGACY_SOUND_PREFERENCE_KEYS
import dev.arnv.bluke.data.removeLegacySoundData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Plain Application so BlukeApplication's own cleanup thread does not race the test.
@Config(sdk = [36], application = Application::class)
class KeyVibrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        preferences.edit { clear() }
    }

    @Test
    fun keyVibrationDefaultsToOn() {
        assertTrue(preferences.isKeyVibrationEnabled())
        assertEquals(KeyVibrationStrength.MEDIUM, preferences.keyVibrationStrength())
    }

    @Test
    fun keyVibrationFollowsOldHapticsSettingUntilSetExplicitly() {
        preferences.edit { putBoolean(HAPTICS_PREFERENCE, false) }
        assertFalse(preferences.isKeyVibrationEnabled())

        preferences.edit { putBoolean(KEY_VIBRATION_PREFERENCE, true) }
        assertTrue(preferences.isKeyVibrationEnabled())
    }

    @Test
    fun unknownStrengthFallsBackToMedium() {
        assertEquals(KeyVibrationStrength.STRONG, KeyVibrationStrength.fromPreference("strong"))
        assertEquals(KeyVibrationStrength.MEDIUM, KeyVibrationStrength.fromPreference("bogus"))
    }

    @Test
    fun legacySoundDataIsRemovedOnce() {
        preferences.edit {
            LEGACY_SOUND_PREFERENCE_KEYS.forEach { putString(it, "x") }
            putBoolean(HAPTICS_PREFERENCE, false)
        }
        val pack = File(context.filesDir, "soundpacks/pack/config.json").apply {
            parentFile!!.mkdirs()
            writeText("{}")
        }
        val sample = File(context.cacheDir, "pb_press_MX_BROWN_0.wav").apply { writeText("") }
        val unrelated = File(context.cacheDir, "keep.wav").apply { writeText("") }

        removeLegacySoundData(context)

        LEGACY_SOUND_PREFERENCE_KEYS.forEach { assertFalse(it, preferences.contains(it)) }
        assertFalse(preferences.getBoolean(HAPTICS_PREFERENCE, true))
        assertFalse(pack.exists())
        assertFalse(sample.exists())
        assertTrue(unrelated.exists())

        // A second run is a no-op.
        preferences.edit { putString("key_sound_enabled", "x") }
        removeLegacySoundData(context)
        assertTrue(preferences.contains("key_sound_enabled"))
    }
}
