package dev.arnv.bluke.sound

import android.content.SharedPreferences
import androidx.core.content.edit

private const val SOUND_PREFERENCES_SCHEMA = 1
private const val SOUND_PREFERENCES_SCHEMA_KEY = "sound_preferences_schema"
const val KEY_SOUND_ENABLED_PREFERENCE = "key_sound_enabled"
private const val LEGACY_SOUND_TOGGLE_PREFERENCE = "sound_toggle"

/** Normalizes legacy sound settings once without overriding an explicit mute choice. */
fun migrateSoundPreferences(preferences: SharedPreferences) {
    if (preferences.getInt(SOUND_PREFERENCES_SCHEMA_KEY, 0) >= SOUND_PREFERENCES_SCHEMA) return

    val soundEnabled = when {
        preferences.contains(KEY_SOUND_ENABLED_PREFERENCE) ->
            preferences.getBoolean(KEY_SOUND_ENABLED_PREFERENCE, true)
        preferences.contains(LEGACY_SOUND_TOGGLE_PREFERENCE) ->
            preferences.getBoolean(LEGACY_SOUND_TOGGLE_PREFERENCE, true)
        else -> true
    }
    preferences.edit {
        putBoolean(KEY_SOUND_ENABLED_PREFERENCE, soundEnabled)
        putBoolean(LEGACY_SOUND_TOGGLE_PREFERENCE, soundEnabled)
        putInt(SOUND_PREFERENCES_SCHEMA_KEY, SOUND_PREFERENCES_SCHEMA)
    }
}
