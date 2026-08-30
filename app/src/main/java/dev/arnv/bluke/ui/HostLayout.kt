package dev.arnv.bluke.ui

/**
 * Host keyboard layouts, expressed as character -> keystroke tables.
 *
 * HID is not a text protocol: a keyboard sends the *scancode* of a physical key position, and the
 * host decides what character that position means by applying whatever keyboard layout the user
 * configured in their OS. Bluke therefore has to send the scancode that, once the host decodes it,
 * yields the character the user actually typed.
 *
 * That makes the host's layout an input to the translation, not something we can detect - there is
 * no way to query it over the HID Device profile. The user has to tell us, the same way the Lock
 * State Synchronization setting already asks them to declare their host OS family.
 *
 * ## Verification status
 *
 * Only [US] is exercised by the on-screen keycaps today, so it is the only table with real-world
 * coverage behind it. Every other table here is derived from the published layout for that locale
 * and is *unverified against physical hardware*. They are believed correct but should be treated as
 * best-effort until someone confirms them against a real host; see the `verified` flag.
 */
object HostLayouts {

    /**
     * A character expressed as a keystroke.
     *
     * [altGr] maps to Right Alt, which non-US layouts use as a third level to reach characters that
     * have no dedicated key (Hungarian and German both put much of their punctuation there).
     */
    data class Stroke(
        val keyCode: Int,
        val shift: Boolean = false,
        val altGr: Boolean = false
    )

    /**
     * A dead key: a keystroke that modifies the *next* keystroke rather than emitting a character
     * itself. Continental layouts build accented letters this way - press the acute-accent key, then
     * the base letter, and the host composes them.
     */
    data class DeadKeySequence(val dead: Stroke, val base: Stroke)

    /**
     * One host keyboard layout.
     *
     * @param id stable key persisted in preferences; never change these.
     * @param verified whether the table has been checked against real hardware.
     */
    data class HostLayout(
        val id: String,
        val displayName: String,
        val verified: Boolean,
        /** Direct single-keystroke mappings. */
        val direct: Map<Char, Stroke>,
        /** Characters reachable only as dead-key + base-letter pairs. */
        val deadKeys: Map<Char, DeadKeySequence> = emptyMap()
    ) {
        /** Every character this layout can produce without falling back to Unicode entry. */
        fun canType(c: Char): Boolean = direct.containsKey(c) || deadKeys.containsKey(c)
    }

    // ---------------------------------------------------------------------------------------------
    // Shared building blocks
    // ---------------------------------------------------------------------------------------------

    private fun k(code: Int) = Stroke(code)
    private fun s(code: Int) = Stroke(code, shift = true)
    private fun a(code: Int) = Stroke(code, altGr = true)

    private val K = KeyboardLayouts

    /** Letter rows shared by every QWERTY-family layout, plus the keys nobody moves. */
    private fun commonBase(): MutableMap<Char, Stroke> = buildMap {
        for (c in 'a'..'z') put(c, k(K.KEY_A + (c - 'a')))
        for (c in 'A'..'Z') put(c, s(K.KEY_A + (c - 'A')))
        put(' ', k(K.KEY_SPACE))
        put('\n', k(K.KEY_ENTER))
        put('\t', k(K.KEY_TAB))
    }.toMutableMap()

    /** The digit row, unshifted, identical across all the Latin layouts modelled here. */
    private fun MutableMap<Char, Stroke>.putDigits() {
        put('1', k(K.KEY_1)); put('2', k(K.KEY_2)); put('3', k(K.KEY_3))
        put('4', k(K.KEY_4)); put('5', k(K.KEY_5)); put('6', k(K.KEY_6))
        put('7', k(K.KEY_7)); put('8', k(K.KEY_8)); put('9', k(K.KEY_9))
        put('0', k(K.KEY_0))
    }

    /** Swaps Y and Z, which is what makes a QWERTZ layout QWERTZ. */
    private fun MutableMap<Char, Stroke>.qwertz() {
        put('y', k(K.KEY_Z)); put('z', k(K.KEY_Y))
        put('Y', s(K.KEY_Z)); put('Z', s(K.KEY_Y))
    }

    // ---------------------------------------------------------------------------------------------
    // US QWERTY - the default, and the only table with real coverage behind it
    // ---------------------------------------------------------------------------------------------

    private val US = HostLayout(
        id = "us",
        displayName = "US QWERTY",
        verified = true,
        direct = buildMap {
            putAll(commonBase())
            putDigits()
            put('!', s(K.KEY_1)); put('@', s(K.KEY_2)); put('#', s(K.KEY_3))
            put('$', s(K.KEY_4)); put('%', s(K.KEY_5)); put('^', s(K.KEY_6))
            put('&', s(K.KEY_7)); put('*', s(K.KEY_8)); put('(', s(K.KEY_9))
            put(')', s(K.KEY_0))
            put('-', k(K.KEY_MINUS)); put('_', s(K.KEY_MINUS))
            put('=', k(K.KEY_EQUAL)); put('+', s(K.KEY_EQUAL))
            put('[', k(K.KEY_LBRACKET)); put('{', s(K.KEY_LBRACKET))
            put(']', k(K.KEY_RBRACKET)); put('}', s(K.KEY_RBRACKET))
            put('\\', k(K.KEY_BACKSLASH)); put('|', s(K.KEY_BACKSLASH))
            put(';', k(K.KEY_SEMICOLON)); put(':', s(K.KEY_SEMICOLON))
            put('\'', k(K.KEY_APOSTROPHE)); put('"', s(K.KEY_APOSTROPHE))
            put('`', k(K.KEY_GRAVE)); put('~', s(K.KEY_GRAVE))
            put(',', k(K.KEY_COMMA)); put('<', s(K.KEY_COMMA))
            put('.', k(K.KEY_PERIOD)); put('>', s(K.KEY_PERIOD))
            put('/', k(K.KEY_SLASH)); put('?', s(K.KEY_SLASH))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // UK QWERTY - US with a handful of substitutions
    // ---------------------------------------------------------------------------------------------

    private val UK = HostLayout(
        id = "uk",
        displayName = "UK QWERTY",
        verified = false,
        direct = buildMap {
            putAll(US.direct)
            // The UK layout moves " to Shift+2 and @ to Shift+', and adds £ on Shift+3.
            put('"', s(K.KEY_2))
            put('@', s(K.KEY_APOSTROPHE))
            put('£', s(K.KEY_3))
            put('#', k(K.KEY_BACKSLASH))
            put('~', s(K.KEY_BACKSLASH))
            put('\\', k(K.KEY_NONUS_BACKSLASH))
            put('|', s(K.KEY_NONUS_BACKSLASH))
            put('€', a(K.KEY_4))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // German QWERTZ
    // ---------------------------------------------------------------------------------------------

    private val DE = HostLayout(
        id = "de",
        displayName = "German QWERTZ",
        verified = false,
        direct = buildMap {
            putAll(commonBase())
            putDigits()
            qwertz()
            put('!', s(K.KEY_1)); put('"', s(K.KEY_2)); put('§', s(K.KEY_3))
            put('$', s(K.KEY_4)); put('%', s(K.KEY_5)); put('&', s(K.KEY_6))
            put('/', s(K.KEY_7)); put('(', s(K.KEY_8)); put(')', s(K.KEY_9))
            put('=', s(K.KEY_0))
            put('ß', k(K.KEY_MINUS)); put('?', s(K.KEY_MINUS))
            put('ü', k(K.KEY_LBRACKET)); put('Ü', s(K.KEY_LBRACKET))
            put('ö', k(K.KEY_SEMICOLON)); put('Ö', s(K.KEY_SEMICOLON))
            put('ä', k(K.KEY_APOSTROPHE)); put('Ä', s(K.KEY_APOSTROPHE))
            put('+', k(K.KEY_RBRACKET)); put('*', s(K.KEY_RBRACKET))
            put('#', k(K.KEY_BACKSLASH)); put('\'', s(K.KEY_BACKSLASH))
            put(',', k(K.KEY_COMMA)); put(';', s(K.KEY_COMMA))
            put('.', k(K.KEY_PERIOD)); put(':', s(K.KEY_PERIOD))
            put('-', k(K.KEY_SLASH)); put('_', s(K.KEY_SLASH))
            put('<', k(K.KEY_NONUS_BACKSLASH)); put('>', s(K.KEY_NONUS_BACKSLASH))
            put('@', a(K.KEY_Q)); put('€', a(K.KEY_E))
            put('\\', a(K.KEY_MINUS)); put('~', a(K.KEY_RBRACKET))
            put('|', a(K.KEY_NONUS_BACKSLASH))
            put('{', a(K.KEY_7)); put('[', a(K.KEY_8))
            put(']', a(K.KEY_9)); put('}', a(K.KEY_0))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // Hungarian QWERTZ - unusually rich in accented letters with dedicated keys
    // ---------------------------------------------------------------------------------------------

    private val HU = HostLayout(
        id = "hu",
        displayName = "Hungarian QWERTZ",
        verified = false,
        direct = buildMap {
            putAll(commonBase())
            putDigits()
            qwertz()
            put('\'', s(K.KEY_1)); put('"', s(K.KEY_2)); put('+', s(K.KEY_3))
            put('!', s(K.KEY_4)); put('%', s(K.KEY_5)); put('/', s(K.KEY_6))
            put('=', s(K.KEY_7)); put('(', s(K.KEY_8)); put(')', s(K.KEY_9))
            put('ö', k(K.KEY_0)); put('Ö', s(K.KEY_0))
            put('ü', k(K.KEY_MINUS)); put('Ü', s(K.KEY_MINUS))
            put('ó', k(K.KEY_EQUAL)); put('Ó', s(K.KEY_EQUAL))
            put('ő', k(K.KEY_LBRACKET)); put('Ő', s(K.KEY_LBRACKET))
            put('ú', k(K.KEY_RBRACKET)); put('Ú', s(K.KEY_RBRACKET))
            put('é', k(K.KEY_SEMICOLON)); put('É', s(K.KEY_SEMICOLON))
            put('á', k(K.KEY_APOSTROPHE)); put('Á', s(K.KEY_APOSTROPHE))
            put('ű', k(K.KEY_BACKSLASH)); put('Ű', s(K.KEY_BACKSLASH))
            put('í', k(K.KEY_NONUS_BACKSLASH)); put('Í', s(K.KEY_NONUS_BACKSLASH))
            put('0', k(K.KEY_GRAVE)); put('§', s(K.KEY_GRAVE))
            put(',', k(K.KEY_COMMA)); put('?', s(K.KEY_COMMA))
            put('.', k(K.KEY_PERIOD)); put(':', s(K.KEY_PERIOD))
            put('-', k(K.KEY_SLASH)); put('_', s(K.KEY_SLASH))
            // Third level: Hungarian puts most ASCII punctuation behind AltGr.
            put('~', a(K.KEY_1)); put('ˇ', a(K.KEY_2)); put('^', a(K.KEY_3))
            put('˘', a(K.KEY_4)); put('°', a(K.KEY_5)); put('˛', a(K.KEY_6))
            put('`', a(K.KEY_7)); put('˙', a(K.KEY_8)); put('´', a(K.KEY_9))
            put('\\', a(K.KEY_Q)); put('|', a(K.KEY_W)); put('€', a(K.KEY_U))
            put('[', a(K.KEY_F)); put(']', a(K.KEY_G))
            put('{', a(K.KEY_B)); put('}', a(K.KEY_N))
            // On the Hungarian layout < and > sit on AltGr + the physical Y and X keys. Because
            // QWERTZ swaps Y and Z, the '<' legend is reached through the KEY_Z position.
            put('<', a(K.KEY_Y)); put('>', a(K.KEY_Z))
            put('@', a(K.KEY_V)); put('#', a(K.KEY_X))
            put('&', a(K.KEY_C)); put('*', a(K.KEY_MINUS))
            put('$', a(K.KEY_NONUS_BACKSLASH))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // French AZERTY
    // ---------------------------------------------------------------------------------------------

    private val FR = HostLayout(
        id = "fr",
        displayName = "French AZERTY",
        verified = false,
        direct = buildMap {
            putAll(commonBase())
            // AZERTY moves three letter positions relative to QWERTY.
            put('a', k(K.KEY_Q)); put('A', s(K.KEY_Q))
            put('q', k(K.KEY_A)); put('Q', s(K.KEY_A))
            put('z', k(K.KEY_W)); put('Z', s(K.KEY_W))
            put('w', k(K.KEY_Z)); put('W', s(K.KEY_Z))
            put('m', k(K.KEY_SEMICOLON)); put('M', s(K.KEY_SEMICOLON))
            // Digits require Shift; the unshifted row carries accented letters instead.
            put('1', s(K.KEY_1)); put('2', s(K.KEY_2)); put('3', s(K.KEY_3))
            put('4', s(K.KEY_4)); put('5', s(K.KEY_5)); put('6', s(K.KEY_6))
            put('7', s(K.KEY_7)); put('8', s(K.KEY_8)); put('9', s(K.KEY_9))
            put('0', s(K.KEY_0))
            put('&', k(K.KEY_1)); put('é', k(K.KEY_2)); put('"', k(K.KEY_3))
            put('\'', k(K.KEY_4)); put('(', k(K.KEY_5)); put('-', k(K.KEY_6))
            put('è', k(K.KEY_7)); put('_', k(K.KEY_8)); put('ç', k(K.KEY_9))
            put('à', k(K.KEY_0))
            put(')', k(K.KEY_MINUS)); put('°', s(K.KEY_MINUS))
            put('=', k(K.KEY_EQUAL)); put('+', s(K.KEY_EQUAL))
            put('ù', k(K.KEY_APOSTROPHE)); put('%', s(K.KEY_APOSTROPHE))
            put('*', k(K.KEY_BACKSLASH)); put('µ', s(K.KEY_BACKSLASH))
            put(',', k(K.KEY_M)); put('?', s(K.KEY_M))
            put(';', k(K.KEY_COMMA)); put('.', s(K.KEY_COMMA))
            put(':', k(K.KEY_PERIOD)); put('/', s(K.KEY_PERIOD))
            put('!', k(K.KEY_SLASH)); put('§', s(K.KEY_SLASH))
            put('²', k(K.KEY_GRAVE))
            put('<', k(K.KEY_NONUS_BACKSLASH)); put('>', s(K.KEY_NONUS_BACKSLASH))
            put('@', a(K.KEY_0)); put('#', a(K.KEY_3)); put('€', a(K.KEY_E))
            put('[', a(K.KEY_5)); put(']', a(K.KEY_MINUS))
            put('{', a(K.KEY_4)); put('}', a(K.KEY_EQUAL))
            put('\\', a(K.KEY_8)); put('|', a(K.KEY_6))
            put('~', a(K.KEY_2)); put('`', a(K.KEY_7)); put('^', a(K.KEY_9))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // Italian QWERTY
    // ---------------------------------------------------------------------------------------------

    private val IT = HostLayout(
        id = "it",
        displayName = "Italian QWERTY",
        verified = false,
        direct = buildMap {
            putAll(commonBase())
            putDigits()
            put('!', s(K.KEY_1)); put('"', s(K.KEY_2)); put('£', s(K.KEY_3))
            put('$', s(K.KEY_4)); put('%', s(K.KEY_5)); put('&', s(K.KEY_6))
            put('/', s(K.KEY_7)); put('(', s(K.KEY_8)); put(')', s(K.KEY_9))
            put('=', s(K.KEY_0))
            put('\'', k(K.KEY_MINUS)); put('?', s(K.KEY_MINUS))
            put('ì', k(K.KEY_EQUAL)); put('^', s(K.KEY_EQUAL))
            put('è', k(K.KEY_LBRACKET)); put('é', s(K.KEY_LBRACKET))
            put('+', k(K.KEY_RBRACKET)); put('*', s(K.KEY_RBRACKET))
            put('ò', k(K.KEY_SEMICOLON)); put('ç', s(K.KEY_SEMICOLON))
            put('à', k(K.KEY_APOSTROPHE)); put('°', s(K.KEY_APOSTROPHE))
            put('ù', k(K.KEY_BACKSLASH)); put('§', s(K.KEY_BACKSLASH))
            put('\\', k(K.KEY_NONUS_BACKSLASH)); put('|', s(K.KEY_NONUS_BACKSLASH))
            put(',', k(K.KEY_COMMA)); put(';', s(K.KEY_COMMA))
            put('.', k(K.KEY_PERIOD)); put(':', s(K.KEY_PERIOD))
            put('-', k(K.KEY_SLASH)); put('_', s(K.KEY_SLASH))
            put('@', a(K.KEY_SEMICOLON)); put('#', a(K.KEY_APOSTROPHE))
            put('€', a(K.KEY_E))
            put('[', a(K.KEY_LBRACKET)); put(']', a(K.KEY_RBRACKET))
            put('{', Stroke(K.KEY_LBRACKET, shift = true, altGr = true))
            put('}', Stroke(K.KEY_RBRACKET, shift = true, altGr = true))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // Spanish QWERTY
    // ---------------------------------------------------------------------------------------------

    private val ES = HostLayout(
        id = "es",
        displayName = "Spanish QWERTY",
        verified = false,
        direct = buildMap {
            putAll(commonBase())
            putDigits()
            put('!', s(K.KEY_1)); put('"', s(K.KEY_2)); put('·', s(K.KEY_3))
            put('$', s(K.KEY_4)); put('%', s(K.KEY_5)); put('&', s(K.KEY_6))
            put('/', s(K.KEY_7)); put('(', s(K.KEY_8)); put(')', s(K.KEY_9))
            put('=', s(K.KEY_0))
            put('\'', k(K.KEY_MINUS)); put('?', s(K.KEY_MINUS))
            put('¡', k(K.KEY_EQUAL)); put('¿', s(K.KEY_EQUAL))
            put('`', k(K.KEY_LBRACKET)); put('^', s(K.KEY_LBRACKET))
            put('+', k(K.KEY_RBRACKET)); put('*', s(K.KEY_RBRACKET))
            put('ñ', k(K.KEY_SEMICOLON)); put('Ñ', s(K.KEY_SEMICOLON))
            put('´', k(K.KEY_APOSTROPHE)); put('¨', s(K.KEY_APOSTROPHE))
            put('ç', k(K.KEY_BACKSLASH)); put('Ç', s(K.KEY_BACKSLASH))
            put('º', k(K.KEY_GRAVE)); put('ª', s(K.KEY_GRAVE))
            put('<', k(K.KEY_NONUS_BACKSLASH)); put('>', s(K.KEY_NONUS_BACKSLASH))
            put(',', k(K.KEY_COMMA)); put(';', s(K.KEY_COMMA))
            put('.', k(K.KEY_PERIOD)); put(':', s(K.KEY_PERIOD))
            put('-', k(K.KEY_SLASH)); put('_', s(K.KEY_SLASH))
            put('@', a(K.KEY_2)); put('#', a(K.KEY_3)); put('€', a(K.KEY_E))
            put('[', a(K.KEY_LBRACKET)); put(']', a(K.KEY_RBRACKET))
            put('{', a(K.KEY_APOSTROPHE)); put('}', a(K.KEY_BACKSLASH))
            put('\\', a(K.KEY_GRAVE)); put('|', a(K.KEY_1)); put('~', a(K.KEY_4))
        },
        // Spanish reaches its accented vowels through the acute-accent dead key.
        deadKeys = buildMap {
            val acute = k(K.KEY_APOSTROPHE)
            put('á', DeadKeySequence(acute, k(K.KEY_A)))
            put('é', DeadKeySequence(acute, k(K.KEY_E)))
            put('í', DeadKeySequence(acute, k(K.KEY_I)))
            put('ó', DeadKeySequence(acute, k(K.KEY_O)))
            put('ú', DeadKeySequence(acute, k(K.KEY_U)))
            put('Á', DeadKeySequence(acute, s(K.KEY_A)))
            put('É', DeadKeySequence(acute, s(K.KEY_E)))
            put('Í', DeadKeySequence(acute, s(K.KEY_I)))
            put('Ó', DeadKeySequence(acute, s(K.KEY_O)))
            put('Ú', DeadKeySequence(acute, s(K.KEY_U)))
            val diaeresis = s(K.KEY_APOSTROPHE)
            put('ü', DeadKeySequence(diaeresis, k(K.KEY_U)))
            put('Ü', DeadKeySequence(diaeresis, s(K.KEY_U)))
        }
    )

    // ---------------------------------------------------------------------------------------------
    // Nordic layouts - Swedish/Finnish share a table; Norwegian and Danish swap two keys
    // ---------------------------------------------------------------------------------------------

    private fun nordicBase(): MutableMap<Char, Stroke> = buildMap {
        putAll(commonBase())
        putDigits()
        put('!', s(K.KEY_1)); put('"', s(K.KEY_2)); put('#', s(K.KEY_3))
        put('%', s(K.KEY_5)); put('&', s(K.KEY_6))
        put('/', s(K.KEY_7)); put('(', s(K.KEY_8)); put(')', s(K.KEY_9))
        put('=', s(K.KEY_0))
        put('+', k(K.KEY_MINUS)); put('?', s(K.KEY_MINUS))
        put('´', k(K.KEY_EQUAL)); put('`', s(K.KEY_EQUAL))
        put('å', k(K.KEY_LBRACKET)); put('Å', s(K.KEY_LBRACKET))
        put('\'', k(K.KEY_BACKSLASH)); put('*', s(K.KEY_BACKSLASH))
        put(',', k(K.KEY_COMMA)); put(';', s(K.KEY_COMMA))
        put('.', k(K.KEY_PERIOD)); put(':', s(K.KEY_PERIOD))
        put('-', k(K.KEY_SLASH)); put('_', s(K.KEY_SLASH))
        put('<', k(K.KEY_NONUS_BACKSLASH)); put('>', s(K.KEY_NONUS_BACKSLASH))
        put('@', a(K.KEY_2)); put('$', a(K.KEY_4)); put('€', a(K.KEY_E))
        put('{', a(K.KEY_7)); put('[', a(K.KEY_8))
        put(']', a(K.KEY_9)); put('}', a(K.KEY_0))
        put('\\', a(K.KEY_NONUS_BACKSLASH))
        put('|', Stroke(K.KEY_NONUS_BACKSLASH, shift = true, altGr = true))
        put('~', a(K.KEY_RBRACKET))
    }.toMutableMap()

    private val SE = HostLayout(
        id = "se",
        displayName = "Swedish / Finnish",
        verified = false,
        direct = buildMap {
            putAll(nordicBase())
            put('ö', k(K.KEY_SEMICOLON)); put('Ö', s(K.KEY_SEMICOLON))
            put('ä', k(K.KEY_APOSTROPHE)); put('Ä', s(K.KEY_APOSTROPHE))
            put('¨', k(K.KEY_RBRACKET)); put('^', s(K.KEY_RBRACKET))
        }
    )

    private val NO = HostLayout(
        id = "no",
        displayName = "Norwegian",
        verified = false,
        direct = buildMap {
            putAll(nordicBase())
            // Norwegian and Danish use ø/æ where Swedish has ö/ä, on the same two keys.
            put('ø', k(K.KEY_SEMICOLON)); put('Ø', s(K.KEY_SEMICOLON))
            put('æ', k(K.KEY_APOSTROPHE)); put('Æ', s(K.KEY_APOSTROPHE))
            put('¨', k(K.KEY_RBRACKET)); put('^', s(K.KEY_RBRACKET))
        }
    )

    private val DK = HostLayout(
        id = "dk",
        displayName = "Danish",
        verified = false,
        direct = buildMap {
            putAll(nordicBase())
            put('æ', k(K.KEY_SEMICOLON)); put('Æ', s(K.KEY_SEMICOLON))
            put('ø', k(K.KEY_APOSTROPHE)); put('Ø', s(K.KEY_APOSTROPHE))
            put('¨', k(K.KEY_RBRACKET)); put('^', s(K.KEY_RBRACKET))
        }
    )

    /** Every layout offered in settings, in display order. US first as the default. */
    val ALL: List<HostLayout> = listOf(US, UK, DE, HU, FR, IT, ES, SE, NO, DK)

    val DEFAULT: HostLayout = US

    fun byId(id: String?): HostLayout = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
