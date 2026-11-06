package dev.arnv.bluke.ui

import android.view.KeyEvent

/**
 * The one-shot/locked modifier keys on the Phone Keyboard key bar.
 *
 * A phone IME has no Ctrl or Alt, so these latch instead of being held: tapping cycles a modifier
 * OFF -> ONCE (applies to the next keystroke, then clears) -> LOCKED (applies until tapped again)
 * -> OFF. A plain tap cycle rather than double-tap timing keeps the behaviour predictable and
 * testable.
 */
class ModifierLatch {
    enum class State { OFF, ONCE, LOCKED }

    private val states = linkedMapOf<Int, State>()

    fun stateOf(modifier: Int): State = states[modifier] ?: State.OFF

    /** An immutable copy for the UI, which redraws from snapshots rather than this live map. */
    fun snapshot(): Map<Int, State> = states.toMap()

    fun tap(modifier: Int) {
        states[modifier] = when (stateOf(modifier)) {
            State.OFF -> State.ONCE
            State.ONCE -> State.LOCKED
            State.LOCKED -> State.OFF
        }
    }

    /** Modifiers that apply to the keystroke about to be sent, in the order they were latched. */
    fun active(): List<Int> = states.filterValues { it != State.OFF }.keys.toList()

    /** Called after each keystroke: one-shot modifiers have now been used up. */
    fun consumeOnce() {
        states.entries.filter { it.value == State.ONCE }.forEach { states[it.key] = State.OFF }
    }

    fun clear() = states.clear()
}

/**
 * Turns what the phone IME does into HID keystrokes, with no local copy of the text.
 *
 * The host owns the text and the caret. Earlier versions mirrored the IME's text field and diffed
 * it against what had been sent, which drifted out of step as soon as the host changed the text on
 * its own - shell tab completion, history recall, clicking the caret elsewhere - and from then on
 * every backspace or autocorrect landed in the wrong place. Here every IME action becomes a
 * keystroke the moment it happens, the same way a hardware keyboard behaves, so there is nothing to
 * fall out of sync.
 *
 * Android-free apart from key code constants so it can be unit-tested directly; [HidInputView]
 * feeds it from a real InputConnection.
 */
class PhoneKeyboardBridge(
    private val hostLayout: () -> HostLayouts.HostLayout,
    private val unicodeMode: () -> UnicodeEntry.UnicodeEntryMode,
    val modifiers: ModifierLatch,
    /** Receives ordered (usage code, isPress) strokes for the HID queue. */
    private val emit: (List<Pair<Int, Boolean>>) -> Unit,
    /** Characters the host layout (and Unicode fallback) could not produce. */
    private val onDropped: (String) -> Unit = {},
    /** Told what each keystroke means, for the on-screen record of what was sent. */
    private val echo: KeystrokeEcho? = null,
) {

    /** Types [text] on the host, applying any latched modifiers per character. */
    fun typeText(text: CharSequence) {
        if (text.isEmpty()) return
        val out = mutableListOf<Pair<Int, Boolean>>()
        val dropped = StringBuilder()
        // Code points, not chars, so an emoji is one unit for Unicode entry and for one-shot
        // modifiers alike.
        val s = text.toString()
        var i = 0
        while (i < s.length) {
            val end = s.offsetByCodePoints(i, 1)
            val unit = s.substring(i, end)
            i = end
            val mods = modifiers.active()
            // A shortcut needs a real key. Unicode entry is a key sequence of its own, and held
            // modifiers would turn it into a string of unrelated shortcuts.
            val mode = if (mods.isEmpty()) unicodeMode() else UnicodeEntry.UnicodeEntryMode.OFF
            val translation = HidCharMap.translate(unit, hostLayout(), mode)
            dropped.append(translation.droppedChars)
            if (translation.events.isEmpty()) continue
            out += withModifiers(mods, translation.events.map { it.keyCode to it.isPress })
            echo?.typed(unit, modifiers.active())
            modifiers.consumeOnce()
        }
        if (out.isNotEmpty()) emit(out)
        if (dropped.isNotEmpty()) onDropped(dropped.toString())
    }

    /** Taps a non-text key (Enter, arrows, F-keys...) with latched and [extraModifiers] held. */
    fun tapKey(keyCode: Int, extraModifiers: List<Int> = emptyList()) {
        val mods = (modifiers.active() + extraModifiers).distinct()
        emit(withModifiers(mods, listOf(keyCode to true, keyCode to false)))
        echo?.pressed(keyCode, mods)
        modifiers.consumeOnce()
    }

    fun deleteBefore(count: Int) = repeat(count.coerceAtLeast(0)) { tapKey(KeyboardLayouts.KEY_BACKSPACE) }

    fun deleteAfter(count: Int) = repeat(count.coerceAtLeast(0)) { tapKey(KeyboardLayouts.KEY_DELETE) }

    /**
     * Handles a raw key event from the IME (raw-input IMEs send Backspace, Enter and arrows this
     * way, and some send every letter like this too).
     *
     * [char] is the event's printable character with Ctrl/Alt/Meta stripped, or null.
     * Returns false when the event means nothing to the host.
     */
    fun onAndroidKey(
        androidKeyCode: Int,
        char: Char?,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): Boolean {
        val extra = buildList {
            if (ctrl) add(KeyboardLayouts.MOD_LCTRL)
            if (alt) add(KeyboardLayouts.MOD_LALT)
            if (meta) add(KeyboardLayouts.MOD_LWIN)
        }
        androidKeyToHid(androidKeyCode)?.let {
            tapKey(it, extra)
            return true
        }
        if (char == null) return false
        if (extra.isEmpty()) {
            typeText(char.toString())
        } else {
            // Ctrl+C from a hardware-style key event: translate the character for the layout, then
            // hold the modifiers around it.
            val translation = HidCharMap.translate(char.toString(), hostLayout(), unicodeMode())
            if (translation.events.isEmpty()) return false
            val mods = (modifiers.active() + extra).distinct()
            emit(withModifiers(mods, translation.events.map { it.keyCode to it.isPress }))
            echo?.typed(char.toString(), mods)
            modifiers.consumeOnce()
        }
        return true
    }

    companion object {
        /**
         * Holds [mods] around [strokes], then releases them in reverse.
         *
         * Strokes that press or release one of [mods] themselves are dropped: the character map
         * wraps uppercase letters in Shift, and letting that inner Shift release go through would
         * cut a latched Shift short in the middle of the keystroke.
         */
        fun withModifiers(mods: List<Int>, strokes: List<Pair<Int, Boolean>>): List<Pair<Int, Boolean>> {
            if (mods.isEmpty()) return strokes
            return mods.map { it to true } +
                strokes.filter { it.first !in mods } +
                mods.reversed().map { it to false }
        }

        /** Non-printing Android keys that have a direct HID equivalent. */
        fun androidKeyToHid(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> KeyboardLayouts.KEY_BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> KeyboardLayouts.KEY_DELETE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyboardLayouts.KEY_ENTER
            KeyEvent.KEYCODE_TAB -> KeyboardLayouts.KEY_TAB
            KeyEvent.KEYCODE_ESCAPE -> KeyboardLayouts.KEY_ESC
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardLayouts.KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardLayouts.KEY_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> KeyboardLayouts.KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> KeyboardLayouts.KEY_DOWN
            KeyEvent.KEYCODE_MOVE_HOME -> KeyboardLayouts.KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> KeyboardLayouts.KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> KeyboardLayouts.KEY_PAGEUP
            KeyEvent.KEYCODE_PAGE_DOWN -> KeyboardLayouts.KEY_PAGEDOWN
            KeyEvent.KEYCODE_INSERT -> KeyboardLayouts.KEY_INSERT
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
                KeyboardLayouts.KEY_F1 + (keyCode - KeyEvent.KEYCODE_F1)
            else -> null
        }
    }
}
