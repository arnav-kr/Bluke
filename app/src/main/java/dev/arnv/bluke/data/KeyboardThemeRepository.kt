package dev.arnv.bluke.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.arnv.bluke.ui.BuiltinKeyboardTheme
import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardLayoutType
import dev.arnv.bluke.ui.KeyboardThemeCatalog
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val KEYBOARD_GEOMETRY_PREFERENCE = "keyboard_geometry"
const val KEYBOARD_THEME_PREFERENCE = "keyboard_theme_id"
const val CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE = "cycle_keyboard_geometries"
const val CYCLE_KEYBOARD_THEMES_PREFERENCE = "cycle_keyboard_themes"

class KeyboardThemeRepository(context: Context) {
    private val file = File(context.filesDir, "keyboard-themes.json")
    private val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun selectedThemeId(): String =
        preferences.getString(KEYBOARD_THEME_PREFERENCE, KeyboardThemeCatalog.defaultTheme.id)
            ?: KeyboardThemeCatalog.defaultTheme.id

    fun selectTheme(id: String) {
        val validId = resolve(id)?.id ?: KeyboardThemeCatalog.defaultTheme.id
        preferences.edit { putString(KEYBOARD_THEME_PREFERENCE, validId) }
    }

    fun resolve(id: String?): KeyboardThemeDefinition? =
        KeyboardThemeCatalog.builtIn(id) ?: listCustomThemes().firstOrNull { it.id == id }

    fun selectedTheme(): KeyboardThemeDefinition =
        resolve(selectedThemeId()) ?: KeyboardThemeCatalog.defaultTheme.also { selectTheme(it.id) }

    fun allThemes(): List<KeyboardThemeDefinition> = KeyboardThemeCatalog.builtIns + listCustomThemes()

    fun listCustomThemes(): List<KeyboardThemeDefinition> = runCatching {
        if (!file.isFile) return emptyList()
        val root = JSONObject(file.readText())
        val themes = root.optJSONArray("themes") ?: JSONArray()
        buildList {
            for (index in 0 until themes.length()) {
                add(parseTheme(themes.getJSONObject(index)))
            }
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun save(theme: KeyboardThemeDefinition): KeyboardThemeDefinition {
        require(theme.id.startsWith(CUSTOM_KEYBOARD_THEME_PREFIX))
        val normalized = theme.copy(name = theme.name.trim().take(40).ifEmpty { "Custom keyboard" }, editable = true)
        val themes = listCustomThemes().filterNot { it.id == normalized.id } + normalized
        writeThemes(themes)
        return normalized
    }

    fun delete(id: String) {
        if (!id.startsWith(CUSTOM_KEYBOARD_THEME_PREFIX)) return
        writeThemes(listCustomThemes().filterNot { it.id == id })
        if (selectedThemeId() == id) selectTheme(KeyboardThemeCatalog.defaultTheme.id)
    }

    private fun writeThemes(themes: List<KeyboardThemeDefinition>) {
        val root = JSONObject().put("version", 1).put(
            "themes",
            JSONArray().apply { themes.forEach { put(themeToJson(it)) } },
        )
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString(2))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun themeToJson(theme: KeyboardThemeDefinition): JSONObject = JSONObject()
        .put("id", theme.id)
        .put("name", theme.name)
        .put("plate", theme.plateArgb)
        .put("case", theme.caseArgb)
        .put("caseMetallic", theme.caseMetallic)
        .put("alpha", styleToJson(theme.alphaStyle))
        .put("modifier", styleToJson(theme.modifierStyle))
        .put("accent", styleToJson(theme.accentStyle))
        .put(
            "overrides",
            JSONObject().apply {
                theme.keyOverrides.forEach { (key, style) -> put(key, styleToJson(style)) }
            },
        )

    private fun parseTheme(json: JSONObject): KeyboardThemeDefinition {
        val overrides = json.optJSONObject("overrides") ?: JSONObject()
        val parsedOverrides = buildMap {
            overrides.keys().forEach { key -> put(key, parseStyle(overrides.getJSONObject(key))) }
        }
        return KeyboardThemeDefinition(
            id = json.getString("id"),
            name = json.optString("name", "Custom keyboard").take(40),
            plateArgb = json.getInt("plate"),
            caseArgb = json.optInt("case", 0xFF1E1E20.toInt()),
            caseMetallic = json.optBoolean("caseMetallic", false),
            alphaStyle = parseStyle(json.getJSONObject("alpha")),
            modifierStyle = parseStyle(json.getJSONObject("modifier")),
            accentStyle = parseStyle(json.getJSONObject("accent")),
            keyOverrides = parsedOverrides,
            editable = true,
        )
    }

    private fun styleToJson(style: KeyboardKeyStyle): JSONObject = JSONObject()
        .put("background", style.backgroundArgb)
        .put("legend", style.legendArgb)
        .put("legendScale", style.legendScale.toDouble())

    private fun parseStyle(json: JSONObject): KeyboardKeyStyle = KeyboardKeyStyle(
        backgroundArgb = json.getInt("background"),
        legendArgb = json.getInt("legend"),
        legendScale = json.optDouble("legendScale", 1.0).toFloat().coerceIn(0.7f, 1.5f),
    )
}

fun migrateKeyboardCustomizationPreferences(preferences: SharedPreferences) {
    val schema = preferences.getInt("keyboard_customization_schema", 0)
    if (schema >= 2) return
    if (schema == 1) {
        val selectedGeometry = KeyboardGeometry.fromPreference(
            preferences.getString(KEYBOARD_GEOMETRY_PREFERENCE, null),
        )
        val geometryNames = migrateStoredGeometryNames(
            preferences.getStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, null),
        )
        preferences.edit {
            putString(KEYBOARD_GEOMETRY_PREFERENCE, selectedGeometry.name)
            putStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, geometryNames)
            putInt("keyboard_customization_schema", 2)
        }
        return
    }
    val legacyNames = preferences.getStringSet(
        "cycle_keyboard_layouts",
        KeyboardLayoutType.entries.mapTo(mutableSetOf()) { it.name },
    ).orEmpty()
    val migration = legacyKeyboardCustomizationMigration(legacyNames)
    preferences.edit {
        putString(KEYBOARD_GEOMETRY_PREFERENCE, migration.selectedGeometry.name)
        putString(KEYBOARD_THEME_PREFERENCE, migration.selectedTheme.id)
        putStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, migration.geometryNames)
        putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, migration.themeIds)
        putInt("keyboard_customization_schema", 2)
    }
}

internal fun migrateStoredGeometryNames(storedNames: Set<String>?): Set<String> =
    storedNames
        ?.mapNotNullTo(mutableSetOf()) { KeyboardGeometry.fromStoredName(it) }
        ?.mapTo(mutableSetOf()) { it.name }
        ?.ifEmpty { KeyboardGeometry.entries.mapTo(mutableSetOf()) { it.name } }
        ?: KeyboardGeometry.entries.mapTo(mutableSetOf()) { it.name }

internal data class KeyboardCustomizationMigration(
    val selectedGeometry: KeyboardGeometry,
    val selectedTheme: BuiltinKeyboardTheme,
    val geometryNames: Set<String>,
    val themeIds: Set<String>,
)

internal fun legacyKeyboardCustomizationMigration(legacyNames: Set<String>): KeyboardCustomizationMigration {
    val legacyTypes = legacyNames.mapNotNull { name ->
        KeyboardLayoutType.entries.firstOrNull { it.name == name }
    }.ifEmpty { KeyboardLayoutType.entries.toList() }
    return KeyboardCustomizationMigration(
        selectedGeometry = KeyboardGeometry.COMPACT_75,
        selectedTheme = BuiltinKeyboardTheme.OBLIVION,
        geometryNames = legacyTypes.mapTo(mutableSetOf()) { KeyboardGeometry.fromLegacy(it).name },
        themeIds = legacyTypes.mapTo(mutableSetOf()) { BuiltinKeyboardTheme.fromLegacy(it).id },
    )
}
