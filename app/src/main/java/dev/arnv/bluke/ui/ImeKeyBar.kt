package dev.arnv.bluke.ui

import android.content.SharedPreferences

/**
 * The user-arranged rows of extra keys shown under the System Keyboard staging field.
 *
 * Phone IMEs can reach most of these characters, but only behind a symbol page or a long-press, and
 * they offer nothing at all for Home/End/F-keys/host shortcuts. Rather than guess one fixed set,
 * users assemble their own rows from [CATALOG] - the same idea as a terminal app's configurable key
 * bar.
 *
 * Keys are referenced by stable string id. Ids are the persisted format, so renaming one is a
 * breaking change; unknown ids are dropped on load so that a downgrade cannot corrupt a bar.
 */
object ImeKeyBar {

    /** What pressing a catalog entry does. */
    sealed interface Action {
        /** Tap a single HID scancode. */
        data class Key(val keyCode: Int) : Action

        /** Hold [modifiers], tap [keyCode], release. */
        data class Combo(val modifiers: List<Int>, val keyCode: Int) : Action

        /**
         * Type a character through the host layout mapping.
         *
         * Symbols sit behind different shift/AltGr combinations per layout, so they cannot be a
         * fixed scancode.
         */
        data class Char(val char: kotlin.Char) : Action

        /** Built-in behaviours that are not keystrokes. */
        enum class Builtin { SEND_CLIPBOARD, CLEAR_STAGED, ENTER_AND_CLEAR }

        data class Special(val builtin: Builtin) : Action
    }

    data class KeyDef(
        val id: String,
        val label: String,
        val category: String,
        val action: Action
    )

    private const val CAT_SYMBOLS = "Symbols"
    private const val CAT_NAVIGATION = "Navigation"
    private const val CAT_EDITING = "Editing"
    private const val CAT_FUNCTION = "Function"
    private const val CAT_SHORTCUTS = "Shortcuts"
    private const val CAT_ACTIONS = "Actions"

    private fun char(id: String, label: String, c: Char) =
        KeyDef(id, label, CAT_SYMBOLS, Action.Char(c))

    private fun key(id: String, label: String, category: String, code: Int) =
        KeyDef(id, label, category, Action.Key(code))

    private fun combo(id: String, label: String, mods: List<Int>, code: Int) =
        KeyDef(id, label, CAT_SHORTCUTS, Action.Combo(mods, code))

    private val CTRL = listOf(KeyboardLayouts.MOD_LCTRL)
    private val ALT = listOf(KeyboardLayouts.MOD_LALT)
    private val CTRL_SHIFT = listOf(KeyboardLayouts.MOD_LCTRL, KeyboardLayouts.MOD_LSHIFT)

    /** Every key a bar can contain, in the order the picker presents them. */
    val CATALOG: List<KeyDef> = listOf(
        // Symbols - reachable on a phone IME, but never in one tap.
        char("pipe", "|", '|'),
        char("backslash", "\\", '\\'),
        char("slash", "/", '/'),
        char("tilde", "~", '~'),
        char("backtick", "`", '`'),
        char("underscore", "_", '_'),
        char("minus", "-", '-'),
        char("equals", "=", '='),
        char("plus", "+", '+'),
        char("brace_open", "{", '{'),
        char("brace_close", "}", '}'),
        char("bracket_open", "[", '['),
        char("bracket_close", "]", ']'),
        char("paren_open", "(", '('),
        char("paren_close", ")", ')'),
        char("lt", "<", '<'),
        char("gt", ">", '>'),
        char("amp", "&", '&'),
        char("pct", "%", '%'),
        char("dollar", "$", '$'),
        char("hash", "#", '#'),
        char("at", "@", '@'),
        char("star", "*", '*'),
        char("caret", "^", '^'),
        char("bang", "!", '!'),
        char("question", "?", '?'),
        char("colon", ":", ':'),
        char("semicolon", ";", ';'),
        char("quote", "'", '\''),
        char("dquote", "\"", '"'),

        // Navigation.
        key("tab", "Tab", CAT_NAVIGATION, KeyboardLayouts.KEY_TAB),
        key("esc", "Esc", CAT_NAVIGATION, KeyboardLayouts.KEY_ESC),
        key("home", "Home", CAT_NAVIGATION, KeyboardLayouts.KEY_HOME),
        key("end", "End", CAT_NAVIGATION, KeyboardLayouts.KEY_END),
        key("pgup", "PgUp", CAT_NAVIGATION, KeyboardLayouts.KEY_PAGEUP),
        key("pgdn", "PgDn", CAT_NAVIGATION, KeyboardLayouts.KEY_PAGEDOWN),
        key("left", "←", CAT_NAVIGATION, KeyboardLayouts.KEY_LEFT),
        key("up", "↑", CAT_NAVIGATION, KeyboardLayouts.KEY_UP),
        key("down", "↓", CAT_NAVIGATION, KeyboardLayouts.KEY_DOWN),
        key("right", "→", CAT_NAVIGATION, KeyboardLayouts.KEY_RIGHT),

        // Editing.
        key("del", "Del", CAT_EDITING, KeyboardLayouts.KEY_DELETE),
        key("backspace", "Bksp", CAT_EDITING, KeyboardLayouts.KEY_BACKSPACE),
        key("insert", "Ins", CAT_EDITING, KeyboardLayouts.KEY_INSERT),
        key("capslock", "Caps", CAT_EDITING, KeyboardLayouts.KEY_CAPSLOCK),
        key("printscreen", "PrtSc", CAT_EDITING, KeyboardLayouts.KEY_PRINTSCREEN),
        key("space", "Space", CAT_EDITING, KeyboardLayouts.KEY_SPACE),

        // Function keys.
        key("f1", "F1", CAT_FUNCTION, KeyboardLayouts.KEY_F1),
        key("f2", "F2", CAT_FUNCTION, KeyboardLayouts.KEY_F2),
        key("f3", "F3", CAT_FUNCTION, KeyboardLayouts.KEY_F3),
        key("f4", "F4", CAT_FUNCTION, KeyboardLayouts.KEY_F4),
        key("f5", "F5", CAT_FUNCTION, KeyboardLayouts.KEY_F5),
        key("f6", "F6", CAT_FUNCTION, KeyboardLayouts.KEY_F6),
        key("f7", "F7", CAT_FUNCTION, KeyboardLayouts.KEY_F7),
        key("f8", "F8", CAT_FUNCTION, KeyboardLayouts.KEY_F8),
        key("f9", "F9", CAT_FUNCTION, KeyboardLayouts.KEY_F9),
        key("f10", "F10", CAT_FUNCTION, KeyboardLayouts.KEY_F10),
        key("f11", "F11", CAT_FUNCTION, KeyboardLayouts.KEY_F11),
        key("f12", "F12", CAT_FUNCTION, KeyboardLayouts.KEY_F12),

        // Host shortcuts. Sent to the computer, not to this phone.
        KeyDef("super", "Super", CAT_SHORTCUTS, Action.Key(KeyboardLayouts.MOD_LWIN)),
        combo("nav_back", "Back", ALT, KeyboardLayouts.KEY_LEFT),
        combo("nav_fwd", "Fwd", ALT, KeyboardLayouts.KEY_RIGHT),
        combo("alt_tab", "Alt+Tab", ALT, KeyboardLayouts.KEY_TAB),
        combo("copy", "Copy", CTRL, KeyboardLayouts.KEY_C),
        combo("paste", "Paste", CTRL, KeyboardLayouts.KEY_V),
        combo("cut", "Cut", CTRL, KeyboardLayouts.KEY_X),
        combo("undo", "Undo", CTRL, KeyboardLayouts.KEY_Z),
        combo("redo", "Redo", CTRL_SHIFT, KeyboardLayouts.KEY_Z),
        combo("select_all", "Sel All", CTRL, KeyboardLayouts.KEY_A),
        combo("save", "Save", CTRL, KeyboardLayouts.KEY_S),
        combo("find", "Find", CTRL, KeyboardLayouts.KEY_F),
        combo("close_tab", "Ctrl+W", CTRL, KeyboardLayouts.KEY_W),
        combo("ctrl_c", "Ctrl+C", CTRL, KeyboardLayouts.KEY_C),
        combo("ctrl_d", "Ctrl+D", CTRL, KeyboardLayouts.KEY_D),
        combo("ctrl_l", "Ctrl+L", CTRL, KeyboardLayouts.KEY_L),
        combo("ctrl_r", "Ctrl+R", CTRL, KeyboardLayouts.KEY_R),

        // App actions.
        KeyDef("enter", "Enter", CAT_ACTIONS, Action.Special(Action.Builtin.ENTER_AND_CLEAR)),
        KeyDef(
            "send_clipboard", "Clipboard", CAT_ACTIONS,
            Action.Special(Action.Builtin.SEND_CLIPBOARD)
        ),
        KeyDef("clear_staged", "Clear", CAT_ACTIONS, Action.Special(Action.Builtin.CLEAR_STAGED))
    )

    private val BY_ID: Map<String, KeyDef> = CATALOG.associateBy { it.id }

    fun byId(id: String): KeyDef? = BY_ID[id]

    /** Categories in catalog order, for grouping the picker. */
    val CATEGORIES: List<String> = CATALOG.map { it.category }.distinct()

    /**
     * Starting layout: the symbols a phone IME buries, then caret movement, then host navigation.
     *
     * Deliberately three modest rows rather than everything available - a wall of keys is as
     * unhelpful as too few, and the point is that users retune it.
     */
    val DEFAULT_ROWS: List<List<String>> = listOf(
        listOf("tab", "esc", "pipe", "backslash", "slash", "tilde", "backtick"),
        listOf("home", "end", "pgup", "pgdn", "del", "enter"),
        listOf("left", "up", "down", "right", "nav_back", "nav_fwd")
    )

    const val MAX_ROWS = 6
    const val MAX_KEYS_PER_ROW = 10

    private const val PREF_KEY = "ime_key_bar_rows"

    // Rows are ordered, so a StringSet will not do. Neither delimiter can occur in a key id.
    private const val ROW_SEPARATOR = "|"
    private const val KEY_SEPARATOR = ","

    fun serialize(rows: List<List<String>>): String =
        rows.filter { it.isNotEmpty() }.joinToString(ROW_SEPARATOR) { row ->
            row.joinToString(KEY_SEPARATOR)
        }

    /**
     * Parses stored rows, discarding anything unrecognised.
     *
     * Ids absent from the catalog are dropped rather than rejected wholesale, so a bar written by a
     * newer version degrades to the keys this version understands instead of resetting.
     */
    fun deserialize(stored: String?): List<List<String>> {
        if (stored.isNullOrBlank()) return DEFAULT_ROWS
        val rows = stored.split(ROW_SEPARATOR)
            .map { row ->
                row.split(KEY_SEPARATOR)
                    .map { it.trim() }
                    .filter { BY_ID.containsKey(it) }
                    .distinct()
                    .take(MAX_KEYS_PER_ROW)
            }
            .filter { it.isNotEmpty() }
            .take(MAX_ROWS)
        // An empty result means the stored value was entirely unusable; fall back rather than
        // leaving the user with no keys and no obvious way to get them back.
        return rows.ifEmpty { DEFAULT_ROWS }
    }

    fun load(prefs: SharedPreferences): List<List<String>> =
        deserialize(prefs.getString(PREF_KEY, null))

    fun save(prefs: SharedPreferences, rows: List<List<String>>) {
        prefs.edit().putString(PREF_KEY, serialize(rows)).apply()
    }

    /** Resolves stored ids to definitions, skipping ids this build does not know. */
    fun resolve(rows: List<List<String>>): List<List<KeyDef>> =
        rows.map { row -> row.mapNotNull { BY_ID[it] } }.filter { it.isNotEmpty() }
}
