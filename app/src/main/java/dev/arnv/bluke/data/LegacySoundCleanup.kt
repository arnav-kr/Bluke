package dev.arnv.bluke.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.io.File

private const val LEGACY_SOUND_CLEANUP_DONE = "legacy_sound_cleanup_done"

/** Preferences left behind by the removed key sound feature. */
internal val LEGACY_SOUND_PREFERENCE_KEYS = listOf(
    "key_sound_enabled",
    "sound_toggle",
    "sound_preferences_schema",
    "cycle_key_sounds",
    "sound_cycle_schema",
    "selected_builtin_key_sound",
    "custom_sound_pack_id",
)

/**
 * One-time removal of the settings and files (imported sound packs, synthesized samples)
 * left by the removed key sound feature. Does file I/O, so call it off the main thread.
 */
fun removeLegacySoundData(context: Context) {
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (preferences.getBoolean(LEGACY_SOUND_CLEANUP_DONE, false)) return
    try {
        File(context.filesDir, "soundpacks").deleteRecursively()
        context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("pb_") && file.name.endsWith(".wav")
        }?.forEach(File::delete)
    } catch (e: SecurityException) {
        Log.w("LegacySoundCleanup", "Could not delete old sound files", e)
    }
    removeLegacySoundPreferences(preferences)
}

internal fun removeLegacySoundPreferences(preferences: SharedPreferences) {
    preferences.edit {
        LEGACY_SOUND_PREFERENCE_KEYS.forEach(::remove)
        putBoolean(LEGACY_SOUND_CLEANUP_DONE, true)
    }
}
