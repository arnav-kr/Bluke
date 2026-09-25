package dev.arnv.bluke.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.gamepadLayoutDataStore by preferencesDataStore(name = "gamepad_layout")

class LayoutRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.gamepadLayoutDataStore
    private val migrationMutex = Mutex()

    suspend fun load(consoleId: String): Map<String, Float> {
        migrateLegacyValuesOnce()
        return dataStore.data.first().asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith("${consoleId}_") && value is Float) name to value else null
        }.toMap()
    }

    suspend fun save(values: Map<String, Float>) {
        if (values.isEmpty()) return
        migrateLegacyValuesOnce()
        dataStore.edit { preferences ->
            values.forEach { (name, value) -> preferences[floatPreferencesKey(name)] = value }
        }
    }

    suspend fun clear(consoleId: String) {
        migrateLegacyValuesOnce()
        dataStore.edit { preferences ->
            val names = preferences.asMap().keys
                .filter { it.name.startsWith("${consoleId}_") }
                .map { it.name }
            names.forEach { preferences.remove(floatPreferencesKey(it)) }
        }
    }

    private suspend fun migrateLegacyValuesOnce() = migrationMutex.withLock {
        val current = dataStore.data.first()
        if (current[MIGRATION_COMPLETE] == true) return@withLock

        val legacyValues = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).all
            .filter { (key, value) ->
                CONSOLE_PREFIXES.any(key::startsWith) && value is Float
            }
        dataStore.edit { preferences ->
            legacyValues.forEach { (name, value) ->
                preferences[floatPreferencesKey(name)] = value as Float
            }
            preferences[MIGRATION_COMPLETE] = true
        }
    }

    private companion object {
        val MIGRATION_COMPLETE = booleanPreferencesKey("legacy_layout_migrated")
        val CONSOLE_PREFIXES = listOf("xbox_series_", "playstation_5_")
    }
}
