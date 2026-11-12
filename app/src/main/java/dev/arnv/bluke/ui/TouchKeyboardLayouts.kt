package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.ConsumerControl

/** Which page of the Touch layout is showing. */
enum class TouchLayer { BASE, SYMBOLS, FN }

/** Each layer key toggles its own layer on and off. */
fun TouchLayer.afterLayerKey(keyCode: Int): TouchLayer = when (keyCode) {
    TouchKeyboardLayouts.KEY_LAYER_SYMBOLS -> if (this == TouchLayer.SYMBOLS) TouchLayer.BASE else TouchLayer.SYMBOLS
    TouchKeyboardLayouts.KEY_LAYER_FN -> if (this == TouchLayer.FN) TouchLayer.BASE else TouchLayer.FN
    else -> this
}

/**
 * A 10-key-wide layout for typing with fingers in portrait.
 * Layer keys and modifiers sit in the same spot on every layer, so a finger
 * that is down while the layer changes keeps pressing the same key.
 */
object TouchKeyboardLayouts {
    // Local-only key codes, never sent to the host (like KeyboardLayouts.KEY_FN).
    const val KEY_LAYER_SYMBOLS = 0x101
    const val KEY_LAYER_FN = 0x102
    private const val KEY_MEDIA_BASE = 0x110

    val LAYER_KEYS = setOf(KEY_LAYER_SYMBOLS, KEY_LAYER_FN)

    fun mediaKeyCode(control: ConsumerControl): Int = KEY_MEDIA_BASE + control.ordinal

    fun consumerControlFor(keyCode: Int): ConsumerControl? =
        ConsumerControl.entries.getOrNull(keyCode - KEY_MEDIA_BASE)

    private const val NAV_ROW =
        """[{w:1.25},"Esc",{w:1.25},"Tab",{w:1.25},"Delete","←","↓","↑","→",{w:2.25},"@SYM"]"""
    private const val BOTTOM_ROW =
        """[{w:1.25},"Ctrl","Win","Alt","<\n,",{w:2.5},"",">\n.","@FN",{w:1.25},"Enter"]"""

    private val BASE_KLE = """
        [$NAV_ROW,
        ["Q","W","E","R","T","Y","U","I","O","P"],
        [{x:0.5},"A","S","D","F","G","H","J","K","L"],
        [{w:1.5},"Shift","Z","X","C","V","B","N","M",{w:1.5},"Backspace"],
        $BOTTOM_ROW]
    """.trimIndent()

    private val SYMBOLS_KLE = """
        [$NAV_ROW,
        ["!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0"],
        [{x:0.5},"~\n`","_\n-","+\n=","{\n[","}\n]","|\n\\",":\n;","\"\n'","?\n/"],
        [{w:1.5},"Shift","Home","End","PgUp","PgDn","Ins","Caps","PrtSc",{w:1.5},"Backspace"],
        $BOTTOM_ROW]
    """.trimIndent()

    private val FN_KLE = """
        [$NAV_ROW,
        ["F1","F2","F3","F4","F5","F6","F7","F8","F9","F10"],
        [{w:1.25},"F11",{w:1.25},"F12",{w:1.25},"Mute",{w:1.25},"Vol −",{w:1.25},"Vol +",{w:1.25},"Prev",{w:1.25},"Play",{w:1.25},"Next"],
        [{w:1.5},"Shift","Dim","Bright","Sleep","Home","End","PgUp","PgDn",{w:1.5},"Backspace"],
        $BOTTOM_ROW]
    """.trimIndent()

    private val mediaLegends: Map<String, ConsumerControl> = mapOf(
        "Mute" to ConsumerControl.MUTE,
        "Vol −" to ConsumerControl.VOLUME_DOWN,
        "Vol +" to ConsumerControl.VOLUME_UP,
        "Prev" to ConsumerControl.PREVIOUS_TRACK,
        "Play" to ConsumerControl.PLAY_PAUSE,
        "Next" to ConsumerControl.NEXT_TRACK,
        "Dim" to ConsumerControl.BRIGHTNESS_DOWN,
        "Bright" to ConsumerControl.BRIGHTNESS_UP,
        "Sleep" to ConsumerControl.SLEEP,
    )

    // Shorter labels for narrow keys.
    private val shortLabels: Map<Int, String> = mapOf(
        KeyboardLayouts.KEY_BACKSPACE to "⌫",
        KeyboardLayouts.KEY_DELETE to "Del",
    )

    fun getLayout(
        layer: TouchLayer,
        characterLayout: KeyboardCharacterLayout = KeyboardCharacterLayout.US_QWERTY,
    ): List<List<KeyLayoutInfo>> {
        val kle = when (layer) {
            TouchLayer.BASE -> BASE_KLE
            TouchLayer.SYMBOLS -> SYMBOLS_KLE
            TouchLayer.FN -> FN_KLE
        }
        return build(kle, layer, characterLayout)
    }

    // Hardware keyboard strip: only the keys phone keyboards lack. The layer key and
    // modifiers keep their spot on both pages, like the Touch layout.
    private const val STRIP_MODIFIERS = """{w:1.5},"@FN",{w:1.5},"Ctrl",{w:1.5},"Win",{w:1.5},"Alt""""

    private val STRIP_BASE_KLE = """
        [["Esc","Tab","Home","End","PgUp","PgDn","Delete","Ins","↑","PrtSc"],
        [$STRIP_MODIFIERS,{x:1},"←","↓","→"]]
    """.trimIndent()

    private val STRIP_FN_KLE = """
        [["F1","F2","F3","F4","F5","F6","F7","F8","F9","F10"],
        [$STRIP_MODIFIERS,"F11","F12","Vol −","Vol +"]]
    """.trimIndent()

    /**
     * Two-row strip shown in hardware keyboard mode. [layer] FN shows F-keys and
     * volume; any other layer shows navigation keys.
     */
    fun hardwareStripLayout(layer: TouchLayer): List<List<KeyLayoutInfo>> {
        val fnPage = layer == TouchLayer.FN
        return build(if (fnPage) STRIP_FN_KLE else STRIP_BASE_KLE, layer, KeyboardCharacterLayout.US_QWERTY)
            .map { row ->
                row.map { key ->
                    if (key.keyCode == KEY_LAYER_FN) key.copy(legend = if (fnPage) "Nav" else "F1–12") else key
                }
            }
    }

    private fun build(
        kle: String,
        layer: TouchLayer,
        characterLayout: KeyboardCharacterLayout,
    ): List<List<KeyLayoutInfo>> {
        val occurrences = mutableMapOf<Int, Int>()
        return KeyboardLayouts.parseKle(kle)
            .withCharacterLayout(characterLayout)
            .map { row ->
                row.map { key ->
                    val local = localKey(key, layer)
                        ?: shortLabels[key.keyCode]?.let { key.copy(legend = it) }
                        ?: key
                    val count = occurrences.getOrDefault(local.keyCode, 0)
                    occurrences[local.keyCode] = count + 1
                    local.copy(styleId = "${local.keyCode}:$count")
                }
            }
    }

    private fun localKey(key: KeyLayoutInfo, layer: TouchLayer): KeyLayoutInfo? {
        val (code, legend) = when (key.legend) {
            "@SYM" -> KEY_LAYER_SYMBOLS to if (layer == TouchLayer.SYMBOLS) "ABC" else "?123"
            "@FN" -> KEY_LAYER_FN to if (layer == TouchLayer.FN) "ABC" else "Fn"
            else -> {
                val control = mediaLegends[key.legend] ?: return null
                mediaKeyCode(control) to key.legend
            }
        }
        return key.copy(
            legend = legend,
            keyCode = code,
            physicalKeyCode = code,
            category = KeyColorCategory.MOD,
        )
    }
}
