package dev.arnv.bluke.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyboardThemeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class KeyboardThemeRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        File(context.filesDir, "keyboard-themes.json").delete()
    }

    @Test
    fun savedThemesAreVisibleToOtherRepositoryInstances() {
        val first = KeyboardThemeRepository(context)
        val second = KeyboardThemeRepository(context)
        assertTrue(second.listCustomThemes().isEmpty())
        first.save(KeyboardThemeGenerator.blank("${CUSTOM_KEYBOARD_THEME_PREFIX}a", "Alpha"))
        assertEquals(listOf("Alpha"), second.listCustomThemes().map { it.name })
        first.delete("${CUSTOM_KEYBOARD_THEME_PREFIX}a")
        assertTrue(second.listCustomThemes().isEmpty())
    }

    @Test
    fun cacheNoticesTheFileChangingOnDisk() {
        val repository = KeyboardThemeRepository(context)
        repository.save(KeyboardThemeGenerator.blank("${CUSTOM_KEYBOARD_THEME_PREFIX}a", "Alpha"))
        assertEquals(1, repository.listCustomThemes().size)
        File(context.filesDir, "keyboard-themes.json").delete()
        assertTrue(repository.listCustomThemes().isEmpty())
    }

    @Test
    fun importGivesNewIdAndUniqueName() {
        val repository = KeyboardThemeRepository(context)
        val source = KeyboardThemeGenerator.blank("${CUSTOM_KEYBOARD_THEME_PREFIX}a", "Mizu")
        val first = repository.importTheme(source)
        val second = repository.importTheme(source)
        assertEquals("Mizu (2)", first.name) // Built-in "Mizu" already exists.
        assertEquals("Mizu (3)", second.name)
        assertNotEquals(first.id, second.id)
        assertEquals(2, repository.listCustomThemes().size)
    }

    @Test
    fun oneBrokenEntryDoesNotHideTheOthers() {
        val repository = KeyboardThemeRepository(context)
        repository.save(KeyboardThemeGenerator.blank("${CUSTOM_KEYBOARD_THEME_PREFIX}a", "Good"))
        val file = File(context.filesDir, "keyboard-themes.json")
        val root = org.json.JSONObject(file.readText())
        root.getJSONArray("themes").put(org.json.JSONObject().put("id", "custom:broken"))
        file.writeText(root.toString())
        file.setLastModified(file.lastModified() + 5_000)
        assertEquals(listOf("Good"), repository.listCustomThemes().map { it.name })
    }
}
