package dev.arnv.bluke

import dev.arnv.bluke.ui.ImeKeyBar
import dev.arnv.bluke.ui.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key bar is user data persisted as a flat string, so the risks are silent loss (a bar that
 * comes back shorter than it went in) and unusable keys reaching the renderer.
 */
class ImeKeyBarTest {

    @Test
    fun `round trip preserves row and key order`() {
        val rows = listOf(
            listOf("tab", "esc", "pipe"),
            listOf("home", "end"),
            listOf("f1", "f12", "super")
        )
        assertEquals(rows, ImeKeyBar.deserialize(ImeKeyBar.serialize(rows)))
    }

    @Test
    fun `default rows survive a round trip`() {
        assertEquals(
            ImeKeyBar.DEFAULT_ROWS,
            ImeKeyBar.deserialize(ImeKeyBar.serialize(ImeKeyBar.DEFAULT_ROWS))
        )
    }

    @Test
    fun `every default row key exists in the catalog`() {
        ImeKeyBar.DEFAULT_ROWS.flatten().forEach { id ->
            assertNotNull("default key '$id' is not in the catalog", ImeKeyBar.byId(id))
        }
    }

    @Test
    fun `catalog ids are unique`() {
        val ids = ImeKeyBar.CATALOG.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog labels are non-empty`() {
        assertTrue(ImeKeyBar.CATALOG.all { it.label.isNotBlank() })
    }

    @Test
    fun `unknown ids are dropped but known ones survive`() {
        // A bar written by a newer build must degrade, not reset.
        val parsed = ImeKeyBar.deserialize("tab,not_a_real_key,esc")
        assertEquals(listOf(listOf("tab", "esc")), parsed)
    }

    @Test
    fun `blank and null storage fall back to defaults`() {
        assertEquals(ImeKeyBar.DEFAULT_ROWS, ImeKeyBar.deserialize(null))
        assertEquals(ImeKeyBar.DEFAULT_ROWS, ImeKeyBar.deserialize(""))
        assertEquals(ImeKeyBar.DEFAULT_ROWS, ImeKeyBar.deserialize("   "))
    }

    @Test
    fun `entirely unknown storage falls back to defaults rather than an empty bar`() {
        assertEquals(ImeKeyBar.DEFAULT_ROWS, ImeKeyBar.deserialize("nope,also_nope"))
    }

    @Test
    fun `empty rows are dropped rather than rendering as gaps`() {
        assertEquals(listOf(listOf("tab")), ImeKeyBar.deserialize("tab||"))
        assertEquals(emptyList<List<String>>(), ImeKeyBar.resolve(listOf(emptyList())))
    }

    @Test
    fun `duplicate keys within a row are collapsed`() {
        assertEquals(listOf(listOf("tab", "esc")), ImeKeyBar.deserialize("tab,tab,esc"))
    }

    @Test
    fun `the same key may appear in different rows`() {
        val rows = listOf(listOf("pipe"), listOf("pipe"))
        assertEquals(rows, ImeKeyBar.deserialize(ImeKeyBar.serialize(rows)))
    }

    @Test
    fun `row and key limits are enforced on load`() {
        val tooManyRows = (1..ImeKeyBar.MAX_ROWS + 3).map { listOf("tab") }
        assertEquals(ImeKeyBar.MAX_ROWS, ImeKeyBar.deserialize(ImeKeyBar.serialize(tooManyRows)).size)

        val longRow = listOf(ImeKeyBar.CATALOG.take(ImeKeyBar.MAX_KEYS_PER_ROW + 5).map { it.id })
        assertEquals(
            ImeKeyBar.MAX_KEYS_PER_ROW,
            ImeKeyBar.deserialize(ImeKeyBar.serialize(longRow)).first().size
        )
    }

    @Test
    fun `serialize skips empty rows`() {
        assertEquals("tab,esc", ImeKeyBar.serialize(listOf(listOf("tab", "esc"), emptyList())))
    }

    @Test
    fun `no catalog id contains a delimiter`() {
        // Ids are packed into one string, so a delimiter inside an id would corrupt every bar
        // containing it.
        assertTrue(ImeKeyBar.CATALOG.none { it.id.contains(",") || it.id.contains("|") })
    }

    @Test
    fun `resolve maps ids to definitions in order`() {
        val resolved = ImeKeyBar.resolve(listOf(listOf("esc", "tab")))
        assertEquals(listOf("Esc", "Tab"), resolved.single().map { it.label })
    }

    @Test
    fun `resolve skips ids this build does not know`() {
        val resolved = ImeKeyBar.resolve(listOf(listOf("tab", "from_the_future")))
        assertEquals(listOf("Tab"), resolved.single().map { it.label })
    }

    @Test
    fun `symbol keys are character actions so they follow the host layout`() {
        // A fixed scancode for pipe would be wrong on any non-US layout.
        val pipe = ImeKeyBar.byId("pipe")
        assertTrue(pipe?.action is ImeKeyBar.Action.Char)
        assertEquals('|', (pipe?.action as ImeKeyBar.Action.Char).char)
    }

    @Test
    fun `host navigation keys are the expected combos`() {
        val back = ImeKeyBar.byId("nav_back")?.action as ImeKeyBar.Action.Combo
        assertEquals(listOf(KeyboardLayouts.MOD_LALT), back.modifiers)
        assertEquals(KeyboardLayouts.KEY_LEFT, back.keyCode)
    }

    @Test
    fun `function keys map to distinct scancodes`() {
        val codes = (1..12).mapNotNull { n ->
            (ImeKeyBar.byId("f$n")?.action as? ImeKeyBar.Action.Key)?.keyCode
        }
        assertEquals(12, codes.size)
        assertEquals(12, codes.toSet().size)
        assertEquals(KeyboardLayouts.KEY_F1, codes.first())
        assertEquals(KeyboardLayouts.KEY_F12, codes.last())
    }

    @Test
    fun `unknown id lookup returns null instead of a placeholder`() {
        assertNull(ImeKeyBar.byId("definitely_not_a_key"))
    }

    @Test
    fun `every catalog entry belongs to a listed category`() {
        assertTrue(ImeKeyBar.CATALOG.all { it.category in ImeKeyBar.CATEGORIES })
    }
}
