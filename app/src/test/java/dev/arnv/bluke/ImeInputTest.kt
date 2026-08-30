package dev.arnv.bluke

import dev.arnv.bluke.ui.HidCharMap
import dev.arnv.bluke.ui.HostLayouts
import dev.arnv.bluke.ui.KeyboardLayouts
import dev.arnv.bluke.ui.UnicodeEntry
import dev.arnv.bluke.ui.UnicodeEntry.UnicodeEntryMode
import dev.arnv.bluke.ui.computeImeDiff
import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the translation from IME text back into HID key events.
 *
 * The diffing is the fragile part: the IME rewrites text it has already handed us whenever
 * autocorrect or a word suggestion fires, so the reconciliation has to stay exact or the host
 * ends up with duplicated or half-deleted words.
 */
class ImeInputTest {

  private val us = HostLayouts.byId("us")

  /** The usage codes of the keys actually tapped, ignoring modifier press/release bookkeeping. */
  private fun typedKeys(text: String, layout: HostLayouts.HostLayout = us): List<Int> =
    HidCharMap.translate(text, layout).events
      .filter { it.isPress && it.keyCode !in 0xE0..0xE7 }
      .map { it.keyCode }

  // -------------------------------------------------------------------------------------------
  // Diffing
  // -------------------------------------------------------------------------------------------

  @Test
  fun plainAppend_sendsOnlyTheNewCharacters() {
    val diff = computeImeDiff("hel", "hello", us)
    assertEquals(0, diff.backspaces)
    assertEquals(listOf(KeyboardLayouts.KEY_L, KeyboardLayouts.KEY_O), pressedKeys(diff))
  }

  @Test
  fun noChange_producesNothing() {
    val diff = computeImeDiff("hello", "hello", us)
    assertEquals(0, diff.backspaces)
    assertTrue(diff.events.isEmpty())
    assertEquals("", diff.droppedChars)
  }

  @Test
  fun deletion_sendsBackspacesOnly() {
    val diff = computeImeDiff("hello", "hel", us)
    assertEquals(2, diff.backspaces)
    assertTrue(diff.events.isEmpty())
  }

  @Test
  fun autocorrectRewrite_backspacesTheDivergingTailAndRetypesIt() {
    // Gboard turning "teh" into "the": common prefix is "t", so two backspaces then "he".
    val diff = computeImeDiff("teh", "the", us)
    assertEquals(2, diff.backspaces)
    assertEquals(listOf(KeyboardLayouts.KEY_H, KeyboardLayouts.KEY_E), pressedKeys(diff))
  }

  @Test
  fun droppedCharacters_areNotRecordedAsSent() {
    // Otherwise the next edit would backspace over text the host never received.
    val diff = computeImeDiff("", "hi😀", us, UnicodeEntryMode.OFF)
    assertEquals("hi", diff.sentText)
    assertTrue(diff.droppedChars.isNotEmpty())
  }

  @Test
  fun mirrorTracksOnlyDeliveredText_acrossSuccessiveEdits() {
    val first = computeImeDiff("", "a😀b", us, UnicodeEntryMode.OFF)
    assertEquals("ab", first.sentText)
    // The host holds "ab"; deleting the emoji in the field must not emit a stray backspace.
    val second = computeImeDiff(first.sentText, "ab", us, UnicodeEntryMode.OFF)
    assertEquals(0, second.backspaces)
    assertTrue(second.events.isEmpty())
  }

  private fun pressedKeys(diff: dev.arnv.bluke.ui.ImeDiff): List<Int> =
    diff.events.filter { it.isPress && it.keyCode !in 0xE0..0xE7 }.map { it.keyCode }

  // -------------------------------------------------------------------------------------------
  // US layout
  // -------------------------------------------------------------------------------------------

  @Test
  fun uppercase_isShifted() {
    val events = HidCharMap.translate("H", us).events
    assertTrue("Shift must be pressed", events.any { it.keyCode == KeyboardLayouts.MOD_LSHIFT && it.isPress })
    assertTrue("Shift must be released", events.any { it.keyCode == KeyboardLayouts.MOD_LSHIFT && !it.isPress })
    assertEquals(listOf(KeyboardLayouts.KEY_H), typedKeys("H"))
  }

  @Test
  fun shiftedSymbols_mapToTheirBaseKey() {
    assertEquals(listOf(KeyboardLayouts.KEY_1), typedKeys("!"))
    assertEquals(listOf(KeyboardLayouts.KEY_SLASH), typedKeys("?"))
    assertEquals(listOf(KeyboardLayouts.KEY_2), typedKeys("@"))
  }

  @Test
  fun everyModifierPressIsBalancedByARelease() {
    // An unbalanced modifier would leave the host stuck in Shift or AltGr.
    for (layout in HostLayouts.ALL) {
      val sample = layout.direct.keys.joinToString("")
      val events = HidCharMap.translate(sample, layout).events
      for (mod in 0xE0..0xE7) {
        val presses = events.count { it.keyCode == mod && it.isPress }
        val releases = events.count { it.keyCode == mod && !it.isPress }
        assertEquals("${layout.id} modifier ${mod.toString(16)} unbalanced", presses, releases)
      }
    }
  }

  @Test
  fun smartQuotes_fallBackToAsciiEquivalents() {
    assertEquals(listOf(KeyboardLayouts.KEY_APOSTROPHE), typedKeys("’"))
    assertEquals(listOf(KeyboardLayouts.KEY_MINUS), typedKeys("—"))
    assertEquals("", HidCharMap.translate("it’s", us).droppedChars)
  }

  @Test
  fun ellipsis_expandsToThreePeriods() {
    assertEquals(
      listOf(KeyboardLayouts.KEY_PERIOD, KeyboardLayouts.KEY_PERIOD, KeyboardLayouts.KEY_PERIOD),
      typedKeys("…")
    )
  }

  @Test
  fun untypableCharacters_areReportedNotSilentlySkipped() {
    val result = HidCharMap.translate("hi 😀", us)
    assertTrue("emoji should be reported as dropped", result.droppedChars.isNotEmpty())
  }

  // -------------------------------------------------------------------------------------------
  // Non-US layouts
  // -------------------------------------------------------------------------------------------

  @Test
  fun qwertzLayouts_swapYAndZ() {
    // The whole point of a send-layout: 'z' on a German host is the physical Y key position.
    val de = HostLayouts.byId("de")
    assertEquals(listOf(KeyboardLayouts.KEY_Y), typedKeys("z", de))
    assertEquals(listOf(KeyboardLayouts.KEY_Z), typedKeys("y", de))
  }

  @Test
  fun hungarianTypesAccentedVowelsDirectly() {
    // These are exactly the characters the US table has to drop.
    val hu = HostLayouts.byId("hu")
    for (c in listOf('á', 'é', 'í', 'ó', 'ö', 'ő', 'ú', 'ü', 'ű')) {
      assertTrue("Hungarian should type $c", hu.canType(c))
      assertEquals("no drops for $c", "", HidCharMap.translate(c.toString(), hu).droppedChars)
    }
    assertTrue("US cannot type á", !us.canType('á'))
  }

  @Test
  fun germanTypesUmlautsAndSharpS() {
    val de = HostLayouts.byId("de")
    for (c in listOf('ä', 'ö', 'ü', 'ß', 'Ä', 'Ö', 'Ü')) {
      assertEquals("no drops for $c", "", HidCharMap.translate(c.toString(), de).droppedChars)
    }
  }

  @Test
  fun azertyMovesLetterPositions() {
    val fr = HostLayouts.byId("fr")
    assertEquals(listOf(KeyboardLayouts.KEY_Q), typedKeys("a", fr))
    assertEquals(listOf(KeyboardLayouts.KEY_A), typedKeys("q", fr))
    assertEquals(listOf(KeyboardLayouts.KEY_W), typedKeys("z", fr))
  }

  @Test
  fun azertyDigitsRequireShift() {
    val fr = HostLayouts.byId("fr")
    val events = HidCharMap.translate("1", fr).events
    assertTrue(events.any { it.keyCode == KeyboardLayouts.MOD_LSHIFT && it.isPress })
  }

  @Test
  fun altGrCharactersUseRightAltNotLeft() {
    // Left Alt would open menus on the host instead of producing a character.
    val de = HostLayouts.byId("de")
    val events = HidCharMap.translate("@", de).events
    assertTrue("must use Right Alt", events.any { it.keyCode == KeyboardLayouts.MOD_RALT && it.isPress })
    assertFalse("must not use Left Alt", events.any { it.keyCode == KeyboardLayouts.MOD_LALT })
  }

  @Test
  fun spanishReachesAccentsThroughDeadKeys() {
    val es = HostLayouts.byId("es")
    assertTrue(es.canType('á'))
    // Dead key then base letter: two taps for one visible character.
    assertEquals(listOf(KeyboardLayouts.KEY_APOSTROPHE, KeyboardLayouts.KEY_A), typedKeys("á", es))
  }

  @Test
  fun nordicLayoutsDifferOnlyInTwoKeys() {
    val se = HostLayouts.byId("se")
    val no = HostLayouts.byId("no")
    val dk = HostLayouts.byId("dk")
    assertTrue(se.canType('ö') && se.canType('ä'))
    assertTrue(no.canType('ø') && no.canType('æ'))
    // Danish swaps æ and ø relative to Norwegian.
    assertEquals(no.direct['æ'], dk.direct['ø'])
    assertEquals(no.direct['ø'], dk.direct['æ'])
  }

  @Test
  fun everyLayoutCanTypeBasicAsciiAndNewlines() {
    val essential = "abcdefghijklmnopqrstuvwxyz0123456789 .,-\n"
    for (layout in HostLayouts.ALL) {
      val dropped = HidCharMap.translate(essential, layout).droppedChars
      assertEquals("${layout.id} cannot type: $dropped", "", dropped)
    }
  }

  @Test
  fun layoutIdsAreUniqueAndStable() {
    val ids = HostLayouts.ALL.map { it.id }
    assertEquals("ids must be unique - they are persisted", ids.size, ids.distinct().size)
    assertEquals("us", HostLayouts.DEFAULT.id)
    assertEquals("unknown ids must fall back to the default", HostLayouts.DEFAULT, HostLayouts.byId("nonsense"))
    assertEquals(HostLayouts.DEFAULT, HostLayouts.byId(null))
  }

  @Test
  fun noLayoutAssignsTheSameKeystrokeToTwoCharacters() {
    // A collision means one of the two characters is simply wrong - the host can only produce one
    // of them for that keystroke. This caught two hand-entry mistakes while writing the tables.
    for (layout in HostLayouts.ALL) {
      val byStroke = layout.direct.entries.groupBy({ it.value }, { it.key })
      val collisions = byStroke.filterValues { it.size > 1 }
      assertTrue(
        "${layout.id} maps one keystroke to several characters: " +
          collisions.entries.joinToString { "${it.key} -> ${it.value}" },
        collisions.isEmpty()
      )
    }
  }

  @Test
  fun onlyUsLayoutClaimsToBeVerified() {
    // Guards the honesty of the settings UI badge.
    assertTrue(HostLayouts.byId("us").verified)
    assertTrue(HostLayouts.ALL.filter { it.verified }.map { it.id } == listOf("us"))
  }

  // -------------------------------------------------------------------------------------------
  // Unicode entry fallback
  // -------------------------------------------------------------------------------------------

  @Test
  fun unicodeOff_dropsWhatTheLayoutCannotType() {
    val result = HidCharMap.translate("α", us, UnicodeEntryMode.OFF)
    assertEquals("α", result.droppedChars)
    assertTrue(result.events.isEmpty())
  }

  @Test
  fun linuxUnicodeEntry_wrapsHexInCtrlShiftU() {
    // α is U+03B1.
    val result = HidCharMap.translate("α", us, UnicodeEntryMode.LINUX_IBUS)
    assertEquals("", result.droppedChars)
    val codes = result.events.filter { it.isPress }.map { it.keyCode }
    assertTrue(codes.contains(KeyboardLayouts.MOD_LCTRL))
    assertTrue(codes.contains(KeyboardLayouts.KEY_U))
    assertTrue("must commit with Enter", codes.contains(KeyboardLayouts.KEY_ENTER))
  }

  @Test
  fun linuxUnicodeEntry_typesTheCorrectHexDigits() {
    // U+00E9 is é; expect the digits 0, 0, e, 9 after the Ctrl+Shift+U prefix.
    val events = UnicodeEntry.sequenceFor(0xE9, UnicodeEntryMode.LINUX_IBUS)
    val taps = events.filter { it.isPress && it.keyCode !in 0xE0..0xE7 }.map { it.keyCode }
    val afterU = taps.dropWhile { it != KeyboardLayouts.KEY_U }.drop(1)
    assertEquals(
      listOf(KeyboardLayouts.KEY_E, KeyboardLayouts.KEY_9, KeyboardLayouts.KEY_ENTER),
      afterU
    )
  }

  @Test
  fun windowsUnicodeEntry_usesKeypadDigitsWithAltHeld() {
    val events = UnicodeEntry.sequenceFor(0xE9, UnicodeEntryMode.WINDOWS_ALT)
    assertEquals(KeyboardLayouts.MOD_LALT, events.first().keyCode)
    assertTrue(events.first().isPress)
    assertEquals(KeyboardLayouts.MOD_LALT, events.last().keyCode)
    assertFalse(events.last().isPress)
    // 0xE9 = 233 decimal, typed on the keypad (0x59..0x62), never the top row.
    val taps = events.filter { it.isPress && it.keyCode !in 0xE0..0xE7 }.map { it.keyCode }
    assertEquals(listOf(0x5A, 0x5B, 0x5B), taps)
  }

  @Test
  fun windowsUnicodeEntry_refusesAstralCharacters() {
    // Alt+numpad cannot express codepoints above the BMP, so emoji must be refused not mangled.
    assertTrue(UnicodeEntry.sequenceFor(0x1F600, UnicodeEntryMode.WINDOWS_ALT).isEmpty())
    val result = HidCharMap.translate("😀", us, UnicodeEntryMode.WINDOWS_ALT)
    assertEquals("😀", result.droppedChars)
  }

  @Test
  fun macOsUnicodeEntry_padsToFourHexDigits() {
    val events = UnicodeEntry.sequenceFor(0xE9, UnicodeEntryMode.MACOS_HEX)
    val taps = events.filter { it.isPress && it.keyCode !in 0xE0..0xE7 }.map { it.keyCode }
    assertEquals(
      listOf(KeyboardLayouts.KEY_0, KeyboardLayouts.KEY_0, KeyboardLayouts.KEY_E, KeyboardLayouts.KEY_9),
      taps
    )
  }

  @Test
  fun macOsUnicodeEntry_sendsAstralCharsAsSurrogatePairs() {
    // U+1F600 is D83D DE00; macOS Unicode Hex Input takes four digits at a time.
    val events = UnicodeEntry.sequenceFor(0x1F600, UnicodeEntryMode.MACOS_HEX)
    val taps = events.filter { it.isPress && it.keyCode !in 0xE0..0xE7 }.map { it.keyCode }
    assertEquals("two UTF-16 units, four digits each", 8, taps.size)
  }

  @Test
  fun emojiIsHandledAsOneCodepointNotBrokenSurrogates() {
    // Naive per-Char iteration would report two dropped halves instead of one character.
    val result = HidCharMap.translate("😀", us, UnicodeEntryMode.OFF)
    assertEquals(1, result.droppedChars.codePointCount(0, result.droppedChars.length))
  }

  @Test
  fun unicodeFallbackAppliesOnlyToUnreachableCharacters() {
    // Plain ASCII must still go through the layout, not the much slower Unicode path.
    val result = HidCharMap.translate("a", us, UnicodeEntryMode.LINUX_IBUS)
    val codes = result.events.filter { it.isPress }.map { it.keyCode }
    assertEquals(listOf(KeyboardLayouts.KEY_A), codes)
  }

  // -------------------------------------------------------------------------------------------
  // Clipboard path
  // -------------------------------------------------------------------------------------------

  @Test
  fun clipboardText_translatesThroughTheSameLayoutMapping() {
    val hu = HostLayouts.byId("hu")
    val text = "árvíztűrő tükörfúrógép"
    assertEquals("", HidCharMap.translate(text, hu).droppedChars)
    // The same string is mostly untypable on a US host.
    assertTrue(HidCharMap.untypableIn(text, us).isNotEmpty())
  }

  @Test
  fun untypableIn_ignoresTheUnicodeFallbackByDesign() {
    // Used to warn before a clipboard send, so it must report the layout's own limits.
    assertEquals("α", HidCharMap.untypableIn("α", us))
    assertEquals("", HidCharMap.untypableIn("abc", us))
  }
}
