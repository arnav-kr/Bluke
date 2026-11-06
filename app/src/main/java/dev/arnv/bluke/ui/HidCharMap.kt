package dev.arnv.bluke.ui

import dev.arnv.bluke.ui.HostLayouts.HostLayout
import dev.arnv.bluke.ui.UnicodeEntry.KeyEvent
import dev.arnv.bluke.ui.UnicodeEntry.UnicodeEntryMode

/**
 * Turns text into the key events that reproduce it on the host.
 *
 * The phone's keyboard app hands us finished text rather than key presses, so anything typed there has to be
 * turned back into scancodes before it can go out over HID. Which scancode produces which character
 * depends on the layout configured on the *host*, so translation is done against a [HostLayout]
 * chosen by the user - see [HostLayouts] for why this cannot be detected automatically.
 *
 * Characters the chosen layout cannot reach fall through to [UnicodeEntry], and whatever survives
 * neither route is reported so the user learns about it instead of silently losing text.
 */
object HidCharMap {

    /**
     * ASCII stand-ins for characters an IME emits freely that most Latin layouts have no key for.
     * Autocorrect produces smart quotes and dashes constantly, so without this ordinary sentences
     * would be pushed down the Unicode-entry path or dropped outright.
     */
    private val SUBSTITUTES: Map<Char, String> = mapOf(
        '‘' to "'",   // left single quote
        '’' to "'",   // right single quote / apostrophe
        '“' to "\"",  // left double quote
        '”' to "\"",  // right double quote
        '–' to "-",   // en dash
        '—' to "-",   // em dash
        '…' to "...", // ellipsis
        ' ' to " ",   // non-breaking space
        '\r' to "\n"
    )

    /** Outcome of translating a run of text. */
    data class Translation(
        val events: List<KeyEvent>,
        /** Characters that neither the layout nor Unicode entry could produce. */
        val droppedChars: String
    )

    private fun tap(code: Int) = listOf(KeyEvent(code, true), KeyEvent(code, false))

    /**
     * Expands a [HostLayouts.Stroke] into press/release events, wrapping it in whichever modifiers
     * it needs. Modifiers are released again immediately so each stroke is self-contained - it costs
     * a few extra reports but means a partially-sent burst can never leave a modifier stuck down.
     */
    private fun strokeEvents(stroke: HostLayouts.Stroke): List<KeyEvent> {
        val out = mutableListOf<KeyEvent>()
        if (stroke.shift) out.add(KeyEvent(KeyboardLayouts.MOD_LSHIFT, true))
        // Right Alt is AltGr; the left one would be a plain Alt and would trigger menus instead.
        if (stroke.altGr) out.add(KeyEvent(KeyboardLayouts.MOD_RALT, true))
        out.addAll(tap(stroke.keyCode))
        if (stroke.altGr) out.add(KeyEvent(KeyboardLayouts.MOD_RALT, false))
        if (stroke.shift) out.add(KeyEvent(KeyboardLayouts.MOD_LSHIFT, false))
        return out
    }

    /** Types Unicode-entry hex digits through the host layout rather than at US key positions. */
    private fun hexKeyFor(layout: HostLayout): (Char) -> List<KeyEvent>? =
        { c -> layout.direct[c]?.let(::strokeEvents) }

    /**
     * The events for a single [Char], or null when it cannot be produced at all.
     *
     * Surrogates are handled by [translate] rather than here, since a lone half is meaningless.
     */
    private fun translateChar(
        c: Char,
        layout: HostLayout,
        unicodeMode: UnicodeEntryMode
    ): List<KeyEvent>? {
        layout.direct[c]?.let { stroke ->
            val events = strokeEvents(stroke)
            // A dead key only arms an accent; Space turns it into the bare symbol.
            return if (c in layout.deadSymbols) events + tap(KeyboardLayouts.KEY_SPACE) else events
        }
        layout.deadKeys[c]?.let { seq ->
            // A dead key emits nothing itself; the host composes it with the following keystroke.
            return strokeEvents(seq.dead) + strokeEvents(seq.base)
        }
        val replacement = SUBSTITUTES[c]
        if (replacement != null) {
            // Substitutes expand to plain ASCII, so no further substitution is needed. If even the
            // replacement is unreachable on this layout we fall through to Unicode entry below
            // rather than giving up.
            val strokes = replacement.map { layout.direct[it] }
            if (strokes.all { it != null }) {
                return strokes.filterNotNull().flatMap { strokeEvents(it) }
            }
        }
        val viaUnicode = UnicodeEntry.sequenceFor(c.code, unicodeMode, hexKeyFor(layout))
        return viaUnicode.ifEmpty { null }
    }

    /**
     * Translates [text] into key events for [layout], using [unicodeMode] for anything the layout
     * cannot reach.
     */
    fun translate(
        text: String,
        layout: HostLayout,
        unicodeMode: UnicodeEntryMode = UnicodeEntryMode.OFF
    ): Translation {
        val events = mutableListOf<KeyEvent>()
        val dropped = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            // A Windows line break is one line break, not two Enters.
            if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                i++
                continue
            }
            // Codepoints outside the BMP arrive as surrogate pairs; emoji must be handled whole.
            if (Character.isHighSurrogate(c) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1])
            ) {
                val codePoint = Character.toCodePoint(c, text[i + 1])
                val seq = UnicodeEntry.sequenceFor(codePoint, unicodeMode, hexKeyFor(layout))
                if (seq.isEmpty()) {
                    dropped.appendCodePoint(codePoint)
                } else {
                    events.addAll(seq)
                }
                i += 2
                continue
            }
            val translated = translateChar(c, layout, unicodeMode)
            if (translated == null) {
                dropped.append(c)
            } else {
                events.addAll(translated)
            }
            i++
        }
        return Translation(events, dropped.toString())
    }
}
