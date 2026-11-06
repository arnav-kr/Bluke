package dev.arnv.bluke.ui

/**
 * A short, screenkey-style record of what Phone Keyboard just sent to the host.
 *
 * Phone Keyboard keeps no text of its own, so without this the only feedback is the host's screen,
 * which is often across the room. Plain typing collects into one run of text; anything else -
 * Enter, arrows, shortcuts - becomes its own labelled entry, and repeats of the same key collapse
 * into a count ("⌫ ×3") so holding Backspace does not push everything else out.
 */
class KeystrokeEcho(
    private val maxEntries: Int = 8,
    private val maxTextLength: Int = 32,
    private val maxPastePreview: Int = 48,
) {
    sealed interface Entry {
        data class Text(val text: String) : Entry
        data class Key(val label: String, val count: Int = 1) : Entry

        /**
         * A clipboard send: the start of what was pasted, on one line, and its full length in
         * characters so a cut-off preview still says how much went out.
         */
        data class Pasted(val preview: String, val length: Int, val truncated: Boolean) : Entry
    }

    private val mutableEntries = ArrayDeque<Entry>()
    val entries: List<Entry> get() = mutableEntries.toList()

    /**
     * Records typed text. With only Shift (or nothing) held it reads as text; with Ctrl, Alt or
     * Super it is a shortcut and is shown like one.
     */
    fun typed(text: String, modifiers: List<Int>) {
        if (text.isEmpty()) return
        val shortcutMods = modifiers.filter { it !in SHIFTS }
        if (shortcutMods.isNotEmpty()) {
            key(comboLabel(modifiers, text.uppercase()))
            return
        }
        val shown = if (modifiers.any { it in SHIFTS }) text.uppercase() else text
        when (val last = mutableEntries.lastOrNull()) {
            is Entry.Text -> mutableEntries[mutableEntries.lastIndex] =
                Entry.Text((last.text + shown).takeLast(maxTextLength))
            else -> add(Entry.Text(shown.takeLast(maxTextLength)))
        }
    }

    /** Records a non-text key such as Enter or an arrow, with whatever modifiers were held. */
    fun pressed(keyCode: Int, modifiers: List<Int>) = key(comboLabel(modifiers, keyLabel(keyCode)))

    /**
     * Records a clipboard send by its content. Line breaks and tabs show as symbols so the preview
     * stays on one line, and a long clip is cut to its start, which is what identifies it.
     */
    fun pasted(text: String) {
        if (text.isEmpty()) return
        val oneLine = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", "⏎")
            .replace("\t", "⇥")
        val length = oneLine.codePointCount(0, oneLine.length)
        val truncated = length > maxPastePreview
        val preview = if (truncated) {
            oneLine.substring(0, oneLine.offsetByCodePoints(0, maxPastePreview)) + "…"
        } else {
            oneLine
        }
        add(Entry.Pasted(preview, text.codePointCount(0, text.length), truncated))
    }

    /** Records something that is not a single key or text. */
    fun key(label: String) {
        val last = mutableEntries.lastOrNull()
        if (last is Entry.Key && last.label == label) {
            mutableEntries[mutableEntries.lastIndex] = last.copy(count = last.count + 1)
        } else {
            add(Entry.Key(label))
        }
    }

    fun clear() = mutableEntries.clear()

    private fun add(entry: Entry) {
        mutableEntries.addLast(entry)
        while (mutableEntries.size > maxEntries) mutableEntries.removeFirst()
    }

    companion object {
        private val SHIFTS = setOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT)

        // Desktop convention, whatever order the modifiers were latched in.
        private val MODIFIER_ORDER = listOf(
            KeyboardLayouts.MOD_LCTRL to "Ctrl",
            KeyboardLayouts.MOD_LALT to "Alt",
            KeyboardLayouts.MOD_LSHIFT to "Shift",
            KeyboardLayouts.MOD_LWIN to "Super",
        )

        fun comboLabel(modifiers: List<Int>, key: String): String {
            val normalized = modifiers.map { leftHand(it) }.toSet()
            val names = MODIFIER_ORDER.filter { it.first in normalized }.map { it.second }
            return (names + key).joinToString("+")
        }

        private fun leftHand(modifier: Int): Int =
            if (modifier in KeyboardLayouts.MOD_RCTRL..KeyboardLayouts.MOD_RWIN) modifier - 4 else modifier

        fun keyLabel(keyCode: Int): String = when (keyCode) {
            KeyboardLayouts.KEY_ENTER -> "⏎"
            KeyboardLayouts.KEY_BACKSPACE -> "⌫"
            KeyboardLayouts.KEY_TAB -> "Tab"
            KeyboardLayouts.KEY_ESC -> "Esc"
            KeyboardLayouts.KEY_SPACE -> "Space"
            KeyboardLayouts.KEY_DELETE -> "Del"
            KeyboardLayouts.KEY_INSERT -> "Ins"
            KeyboardLayouts.KEY_HOME -> "Home"
            KeyboardLayouts.KEY_END -> "End"
            KeyboardLayouts.KEY_PAGEUP -> "PgUp"
            KeyboardLayouts.KEY_PAGEDOWN -> "PgDn"
            KeyboardLayouts.KEY_LEFT -> "←"
            KeyboardLayouts.KEY_RIGHT -> "→"
            KeyboardLayouts.KEY_UP -> "↑"
            KeyboardLayouts.KEY_DOWN -> "↓"
            KeyboardLayouts.KEY_CAPSLOCK -> "Caps"
            KeyboardLayouts.KEY_PRINTSCREEN -> "PrtSc"
            KeyboardLayouts.MOD_LWIN -> "Super"
            in KeyboardLayouts.KEY_A..KeyboardLayouts.KEY_Z -> ('A' + (keyCode - KeyboardLayouts.KEY_A)).toString()
            in KeyboardLayouts.KEY_1..KeyboardLayouts.KEY_9 -> ('1' + (keyCode - KeyboardLayouts.KEY_1)).toString()
            KeyboardLayouts.KEY_0 -> "0"
            in KeyboardLayouts.KEY_F1..KeyboardLayouts.KEY_F12 -> "F${keyCode - KeyboardLayouts.KEY_F1 + 1}"
            else -> "0x%02X".format(keyCode)
        }
    }
}
