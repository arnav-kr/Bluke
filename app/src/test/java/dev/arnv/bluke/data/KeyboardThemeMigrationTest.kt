package dev.arnv.bluke.data

import dev.arnv.bluke.ui.BuiltinKeyboardTheme
import dev.arnv.bluke.ui.KeyColorCategory
import dev.arnv.bluke.ui.KeyLayoutInfo
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardLayoutType
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeMigrationTest {
    @Test
    fun identicalLegacyLayoutsCollapseButThemesRemainSeparate() {
        val migration = legacyKeyboardCustomizationMigration(
            KeyboardLayoutType.entries.mapTo(mutableSetOf()) { it.name },
        )

        assertEquals(KeyboardGeometry.entries.mapTo(mutableSetOf()) { it.name }, migration.geometryNames)
        assertEquals(BuiltinKeyboardTheme.entries.mapTo(mutableSetOf()) { it.id }, migration.themeIds)
        assertEquals(7, migration.geometryNames.size)
        assertEquals(KeyboardLayoutType.entries.size, migration.themeIds.size)
        assertEquals(KeyboardGeometry.COMPACT_75, migration.selectedGeometry)
        assertEquals(BuiltinKeyboardTheme.OBLIVION, migration.selectedTheme)
    }

    @Test
    fun schemaOneGeometryNamesMigrateWithoutRepeatingIdenticalLayouts() {
        val migrated = migrateStoredGeometryNames(
            setOf("OLIVIA_75", "DRACULA_75", "MODEL_M_75", "NINE_ZERO_ZERO_NINE", "MIZU_65"),
        )

        assertEquals(
            setOf(KeyboardGeometry.CLASSIC_75.name, KeyboardGeometry.BALANCED_65.name),
            migrated,
        )
        assertEquals(KeyboardGeometry.CLASSIC_75, KeyboardGeometry.fromPreference("DRACULA_75"))
        assertEquals(KeyboardGeometry.COMPACT_75, KeyboardGeometry.fromPreference("OBLIVION_75"))
    }

    @Test
    fun invalidOrEmptyLegacySelectionFallsBackToAllChoices() {
        val invalid = legacyKeyboardCustomizationMigration(setOf("REMOVED_LAYOUT"))
        val empty = legacyKeyboardCustomizationMigration(emptySet())

        assertEquals(KeyboardGeometry.entries.size, invalid.geometryNames.size)
        assertEquals(BuiltinKeyboardTheme.entries.size, invalid.themeIds.size)
        assertEquals(invalid, empty)
    }

    @Test
    fun perKeyOverrideWinsWithoutChangingGroupDefault() {
        val groupStyle = KeyboardKeyStyle(0xFF111111.toInt(), 0xFFEEEEEE.toInt())
        val overrideStyle = KeyboardKeyStyle(0xFFAA0000.toInt(), 0xFFFFFFFF.toInt(), 1.2f)
        val overriddenKey = KeyLayoutInfo(
            legend = "A",
            styleId = "4:0",
            keyCode = 4,
            category = KeyColorCategory.ALPHA,
        )
        val groupKey = KeyLayoutInfo(
            legend = "B",
            styleId = "5:0",
            keyCode = 5,
            category = KeyColorCategory.ALPHA,
        )
        val theme = KeyboardThemeDefinition(
            id = "custom:test",
            name = "Test",
            plateArgb = 0xFF000000.toInt(),
            alphaStyle = groupStyle,
            modifierStyle = groupStyle,
            accentStyle = groupStyle,
            keyOverrides = mapOf(overriddenKey.styleId to overrideStyle),
            editable = true,
        )

        assertEquals(overrideStyle, theme.styleFor(overriddenKey))
        assertEquals(groupStyle, theme.styleFor(groupKey))
        assertNotEquals(theme.styleFor(overriddenKey), theme.styleFor(groupKey))
    }

    @Test
    fun legacyThemeGetsSafeCaseDefaults() {
        val theme = KeyboardThemeDefinition(
            id = "custom:legacy",
            name = "Legacy",
            plateArgb = 0xFF000000.toInt(),
            alphaStyle = KeyboardKeyStyle(0, 0),
            modifierStyle = KeyboardKeyStyle(0, 0),
            accentStyle = KeyboardKeyStyle(0, 0),
        )

        assertEquals(0xFF1E1E20.toInt(), theme.caseArgb)
        assertFalse(theme.caseMetallic)
    }

    @Test
    fun everyLayoutProducesStableUniqueStyleIds() {
        KeyboardGeometry.entries.forEach { geometry ->
            val keys = dev.arnv.bluke.ui.KeyboardLayouts.getLayout(geometry).flatten()
            assertTrue("${geometry.name} has no keys", keys.isNotEmpty())
            assertEquals(keys.size, keys.map { it.styleId }.toSet().size)
            assertTrue(keys.all { it.styleId.isNotBlank() })
        }
    }
}
