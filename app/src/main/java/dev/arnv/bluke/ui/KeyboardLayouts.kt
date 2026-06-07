package dev.arnv.bluke.ui

import dev.arnv.bluke.ui.theme.*
import androidx.compose.ui.graphics.Color

enum class KeyColorCategory {
    ALPHA,
    MOD,
    ACCENT
}

enum class KeyboardLayoutType(val displayName: String) {
    OLIVIA_75("Olivia WKL 75%"),
    DRACULA_75("Dracula 75%"),
    CAFE_65("Cafe 65%"),
    HHKB_60("HHKB 60%"),
    MODEL_M_VINTAGE("Model M Retro 75%"),
    MIZU_65("GMK Mizu 65%"),
    LASER_75("GMK Laser 75%"),
    OBLIVION_75("GMK Oblivion 75%"),
    NINE_ZERO_ZERO_NINE_TKL("GMK 9009 TKL"),
    EIGHT_ZERO_ZERO_EIGHT_65("GMK 8008 65%")
}

enum class CaseColor(val displayName: String, val caseColor: Color, val metallic: Boolean) {
    BLACK("Black", Color(0xFF1E1E20), false),
    GRAY("Gray", Color(0xFF5A5C61), false),
    SILVER("Silver", Color(0xFFD1D5DB), true),
    WHITE("White", Color(0xFFF9FAFB), false),
    BEIGE("Beige", Color(0xFFE6DFD3), false),
    ROSE_GOLD("Rose Gold", Color(0xFFE8C3B9), true),
    LIGHT_GOLD("Light Gold", Color(0xFFEAD0A8), true),
    CUSTOM("Custom", Color(0xFF3F51B5), false);

    fun getActualColor(sharedPrefs: android.content.SharedPreferences): Color {
        return if (this == CUSTOM) {
            val r = sharedPrefs.getInt("custom_case_color_r", 63)
            val g = sharedPrefs.getInt("custom_case_color_g", 81)
            val b = sharedPrefs.getInt("custom_case_color_b", 181)
            Color(r, g, b)
        } else {
            this.caseColor
        }
    }

    fun getActualMetallic(sharedPrefs: android.content.SharedPreferences): Boolean {
        return if (this == CUSTOM) {
            sharedPrefs.getBoolean("custom_case_color_metallic", false)
        } else {
            this.metallic
        }
    }
}

data class KeyboardPalette(
    val bgCode: Color,
    val alphaBg: Color,
    val alphaLegend: Color,
    val modBg: Color,
    val modLegend: Color,
    val accentBg: Color,
    val accentLegend: Color,
    val layoutTitle: String
)

object Colorways {
    val PALETTES = mapOf(
        KeyboardLayoutType.OLIVIA_75 to KeyboardPalette(
            bgCode = Color(0xFF242427),
            alphaBg = Color(0xFFE5E1D8),
            alphaLegend = Color(0xFFC8A392),
            modBg = Color(0xFF1B1B1E),
            modLegend = Color(0xFFC8A392),
            accentBg = Color(0xFFE5C4B4),
            accentLegend = Color(0xFF242427),
            layoutTitle = "Olivia"
        ),
        KeyboardLayoutType.DRACULA_75 to KeyboardPalette(
            bgCode = Color(0xFF14151A),
            alphaBg = Color(0xFF282A36),
            alphaLegend = Color(0xFFF8F8F2),
            modBg = Color(0xFF1E1F29),
            modLegend = Color(0xFF6272A4),
            accentBg = Color(0xFFFF79C6),
            accentLegend = Color(0xFF14151A),
            layoutTitle = "Dracula"
        ),
        KeyboardLayoutType.CAFE_65 to KeyboardPalette(
            bgCode = Color(0xFF3F3229),
            alphaBg = Color(0xFFE8DECF),
            alphaLegend = Color(0xFF574338),
            modBg = Color(0xFF574338),
            modLegend = Color(0xFFE8DECF),
            accentBg = Color(0xFFCD9E70),
            accentLegend = Color(0xFF241C16),
            layoutTitle = "Cafe"
        ),
        KeyboardLayoutType.HHKB_60 to KeyboardPalette(
            bgCode = Color(0xFF2E2F32),
            alphaBg = Color(0xFFF0F1F4),
            alphaLegend = Color(0xFF1C1D1F),
            modBg = Color(0xFF4C4D50),
            modLegend = Color(0xFFF0F1F4),
            accentBg = Color(0xFFD11A2A),
            accentLegend = Color(0xFFFFFFFF),
            layoutTitle = "HHKB"
        ),
        KeyboardLayoutType.MODEL_M_VINTAGE to KeyboardPalette(
            bgCode = Color(0xFFE2DDD3),
            alphaBg = Color(0xFFEAEAEA),
            alphaLegend = Color(0xFF202020),
            modBg = Color(0xFFCCCCCC),
            modLegend = Color(0xFF202020),
            accentBg = Color(0xFF5D7B93),
            accentLegend = Color(0xFFFFFFFF),
            layoutTitle = "Model M"
        ),
        KeyboardLayoutType.MIZU_65 to KeyboardPalette(
            bgCode = Color(0xFF1B263B),
            alphaBg = Color(0xFFB7D8EB),
            alphaLegend = Color(0xFF243746),
            modBg = Color(0xFF243746),
            modLegend = Color(0xFFF8F7F3),
            accentBg = Color(0xFFF8F7F3),
            accentLegend = Color(0xFF243746),
            layoutTitle = "Mizu"
        ),
        KeyboardLayoutType.LASER_75 to KeyboardPalette(
            bgCode = Color(0xFF1E1330),
            alphaBg = Color(0xFF2F1C75),
            alphaLegend = Color(0xFF00A2B0),
            modBg = Color(0xFF26204B),
            modLegend = Color(0xFFCE234E),
            accentBg = Color(0xFFCE234E),
            accentLegend = Color(0xFF26204B),
            layoutTitle = "Laser"
        ),
        KeyboardLayoutType.OBLIVION_75 to KeyboardPalette(
            bgCode = Color(0xFF1E1E1E),
            alphaBg = Color(0xFFD7D6D2),
            alphaLegend = Color(0xFF4D4D4D),
            modBg = Color(0xFF4D4D4D),
            modLegend = Color(0xFFB7B2A6),
            accentBg = Color(0xFFD64827),
            accentLegend = Color(0xFF2E2E2E),
            layoutTitle = "Oblivion"
        ),
        KeyboardLayoutType.NINE_ZERO_ZERO_NINE_TKL to KeyboardPalette(
            bgCode = Color(0xFF333333),
            alphaBg = Color(0xFFDDD7CB),
            alphaLegend = Color(0xFF383537),
            modBg = Color(0xFFBCB59F),
            modLegend = Color(0xFF383537),
            accentBg = Color(0xFFD99089),
            accentLegend = Color(0xFF383537),
            layoutTitle = "9009"
        ),
        KeyboardLayoutType.EIGHT_ZERO_ZERO_EIGHT_65 to KeyboardPalette(
            bgCode = Color(0xFF1D1E22),
            alphaBg = Color(0xFF9BA7B7),
            alphaLegend = Color(0xFF3F4754),
            modBg = Color(0xFF3F4754),
            modLegend = Color(0xFFFE588C),
            accentBg = Color(0xFFFE588C),
            accentLegend = Color(0xFF1D1E22),
            layoutTitle = "8008"
        )
    )
}

data class KeyLayoutInfo(
    val legend: String,
    val shiftedLegend: String = "",
    val widthRatio: Float = 1.0f,
    val heightRatio: Float = 1.0f,
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val keyCode: Int = 0,
    val category: KeyColorCategory = KeyColorCategory.ALPHA
)

object KeyboardLayouts {
    // Standard HID Keyboard codes
    const val KEY_A = 0x04
    const val KEY_B = 0x05
    const val KEY_C = 0x06
    const val KEY_D = 0x07
    const val KEY_E = 0x08
    const val KEY_F = 0x09
    const val KEY_G = 0x0A
    const val KEY_H = 0x0B
    const val KEY_I = 0x0C
    const val KEY_J = 0x0D
    const val KEY_K = 0x0E
    const val KEY_L = 0x0F
    const val KEY_M = 0x10
    const val KEY_N = 0x11
    const val KEY_O = 0x12
    const val KEY_P = 0x13
    const val KEY_Q = 0x14
    const val KEY_R = 0x15
    const val KEY_S = 0x16
    const val KEY_T = 0x17
    const val KEY_U = 0x18
    const val KEY_V = 0x19
    const val KEY_W = 0x1A
    const val KEY_X = 0x1B
    const val KEY_Y = 0x1C
    const val KEY_Z = 0x1D

    const val KEY_1 = 0x1E
    const val KEY_2 = 0x1F
    const val KEY_3 = 0x20
    const val KEY_4 = 0x21
    const val KEY_5 = 0x22
    const val KEY_6 = 0x23
    const val KEY_7 = 0x24
    const val KEY_8 = 0x25
    const val KEY_9 = 0x26
    const val KEY_0 = 0x27

    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_MINUS = 0x2D
    const val KEY_EQUAL = 0x2E
    const val KEY_LBRACKET = 0x2F
    const val KEY_RBRACKET = 0x30
    const val KEY_BACKSLASH = 0x31
    const val KEY_SEMICOLON = 0x33
    const val KEY_APOSTROPHE = 0x34
    const val KEY_GRAVE = 0x35
    const val KEY_COMMA = 0x36
    const val KEY_PERIOD = 0x37
    const val KEY_SLASH = 0x38
    const val KEY_CAPSLOCK = 0x39

    const val KEY_F1 = 0x3A
    const val KEY_F2 = 0x3B
    const val KEY_F3 = 0x3C
    const val KEY_F4 = 0x3D
    const val KEY_F5 = 0x3E
    const val KEY_F6 = 0x3F
    const val KEY_F7 = 0x40
    const val KEY_F8 = 0x41
    const val KEY_F9 = 0x42
    const val KEY_F10 = 0x43
    const val KEY_F11 = 0x44
    const val KEY_F12 = 0x45

    const val KEY_PRINTSCREEN = 0x46
    const val KEY_SCROLLLOCK = 0x47
    const val KEY_PAUSE = 0x48
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGEUP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGEDOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52
    const val KEY_NUMLOCK = 0x53

    // Modifiers (Bits in byte 0)
    const val MOD_LCTRL = 0xE0
    const val MOD_LSHIFT = 0xE1
    const val MOD_LALT = 0xE2
    const val MOD_LWIN = 0xE3
    const val MOD_RCTRL = 0xE4
    const val MOD_RSHIFT = 0xE5
    const val MOD_RALT = 0xE6
    const val MOD_RWIN = 0xE7

    fun getLayout(type: KeyboardLayoutType): List<List<KeyLayoutInfo>> {
        val kle = getKleString(type)
        return parseKleString(kle)
    }

    private fun getKleString(type: KeyboardLayoutType): String {
        return when (type) {
            KeyboardLayoutType.OLIVIA_75 -> """
                [[{c:"#f1beb0",t:"#2b2b2b"},"Esc",{x:0.5,c:"#e1dbd1"},"F1","F2","F3","F4",{x:0.5,c:"#2b2b2b",t:"#f1beb0"},"F5","F6","F7","F8",{x:0.5,c:"#e1dbd1",t:"#2b2b2b"},"F9","F10","F11","F12",{x:0.5,c:"#2b2b2b",t:"#f1beb0"},"Delete"],
                [{y:0.25},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#2b2b2b",t:"#f1beb0",w:2},"Backspace","Home"],
                [{w:1.5},"Tab",{c:"#e1dbd1",t:"#2b2b2b"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{c:"#2b2b2b",t:"#f1beb0",w:1.5},"|\n\\","PgUp"],
                [{w:1.75},"Caps Lock",{c:"#e1dbd1",t:"#2b2b2b"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#2b2b2b",t:"#f1beb0",w:2.25},"Enter","PgDn"],
                [{w:2.25},"Shift",{c:"#e1dbd1",t:"#2b2b2b"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#2b2b2b",t:"#f1beb0",w:1.75},"Shift",{c:"#f1beb0",t:"#2b2b2b"},"↑",{c:"#2b2b2b",t:"#f1beb0"},"End"],
                [{w:1.5},"Ctrl",{x:0.75,w:1.5},"Alt",{c:"#f1beb0",t:"#000000",a:7,w:7},"",{c:"#2b2b2b",t:"#f1beb0",a:4,w:1.5},"Win",{x:0.75},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.DRACULA_75 -> """
                [[{c:"#bd93f9",t:"#282a36"},"Esc",{x:0.5,c:"#44475a",t:"#f8f8f2"},"F1","F2","F3","F4",{x:0.5,c:"#282a36"},"F5","F6","F7","F8",{x:0.5,c:"#44475a"},"F9","F10","F11","F12",{x:0.5,c:"#282a36"},"Delete"],
                [{y:0.25},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#282a36",t:"#ff79c6",w:2},"Backspace","Home"],
                [{t:"#ff79c6",w:1.5},"Tab",{c:"#44475a",t:"#f8f8f2"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{c:"#282a36",w:1.5},"|\n\\","PgUp"],
                [{t:"#bd93f9",w:1.75},"Caps Lock",{c:"#44475a",t:"#f8f8f2"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#bd93f9",t:"#282a36",w:2.25},"Enter","PgDn"],
                [{c:"#282a36",t:"#8be9fd",w:2.25},"Shift",{c:"#44475a",t:"#f8f8f2"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#282a36",t:"#8be9fd",w:1.75},"Shift",{c:"#ff79c6",t:"#282a36"},"↑",{c:"#282a36",t:"#ff79c6"},"End"],
                [{w:1.5},"Ctrl",{x:0.75,w:1.5},"Alt",{c:"#ff79c6",t:"#000000",a:7,w:7},"",{c:"#282a36",t:"#8be9fd",a:4,w:1.5},"Win",{x:0.75},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.CAFE_65 -> """
                [[{c:"#dec19b",t:"#3f3a37"},"Esc",{c:"#cfcfc5"},"!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#3f3a37",t:"#cfcfc5",w:2},"Backspace","~\n`"],
                [{w:1.5},"Tab",{c:"#cfcfc5",t:"#3f3a37"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#3f3a37",t:"#cfcfc5"},"Delete"],
                [{w:1.75},"Caps Lock",{c:"#cfcfc5",t:"#3f3a37"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#3f3a37",t:"#cfcfc5",w:2.25},"Enter","PgUp"],
                [{w:2.25},"Shift",{c:"#cfcfc5",t:"#3f3a37"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#3f3a37",t:"#cfcfc5",w:1.75},"Shift",{c:"#dec19b",t:"#3f3a37"},"↑","PgDn"],
                [{w:1.25},"Ctrl","Win","Alt",{c:"#dec19b",t:"#3f3a37",w:6.25},"",{c:"#3f3a37",t:"#cfcfc5"},"Alt","Fn","Ctrl",{x:0.5,c:"#dec19b",t:"#3f3a37"},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.HHKB_60 -> """
                [[{c:"#b2b3b5"},"Esc",{c:"#cbc8c3"},"!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=","|\n\\","~\n`"],
                [{c:"#b2b3b5", w:1.5},"Tab",{c:"#cbc8c3"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{c:"#b2b3b5",w:1.5},"Backspace"],
                [{w:1.75},"Ctrl",{c:"#cbc8c3"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#b2b3b5",w:2.25},"Enter"],
                [{w:2.25},"Shift",{c:"#cbc8c3"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#b2b3b5",w:1.75},"Shift","Fn"],
                [{x:1.5},"Alt",{w:1.5},"Win",{c:"#cbc8c3",w:6},"",{c:"#b2b3b5",w:1.5},"Win","Alt"]]
            """.trimIndent()

            KeyboardLayoutType.MODEL_M_VINTAGE -> """
                [[{c:"#b9afa3"},"Esc",{x:0.5,c:"#fdf5f3"},"F1","F2","F3","F4",{x:0.5,c:"#b9afa3"},"F5","F6","F7","F8",{x:0.5,c:"#fdf5f3"},"F9","F10","F11","F12",{x:0.5,c:"#b9afa3"},"Delete"],
                [{y:0.25,c:"#fdf5f3"},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#b9afa3",w:2},"Backspace","Home"],
                [{w:1.5},"Tab",{c:"#fdf5f3"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#b9afa3"},"PgUp"],
                [{w:1.75},"Caps Lock",{c:"#fdf5f3"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#b9afa3",w:2.25},"Enter","PgDn"],
                [{c:"#b9afa3",w:2.25},"Shift",{c:"#fdf5f3"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#b9afa3",w:1.75},"Shift",{c:"#b9afa3"},"↑","End"],
                [{w:1.5},"Ctrl",{x:0.75,w:1.5},"Alt",{c:"#fdf5f3",w:7},"",{c:"#b9afa3",w:1.5},"Win",{x:0.75},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.MIZU_65 -> """
                [[{c:"#f8f7f3",t:"#243746"},"Esc",{c:"#b7d8eb"},"!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#243746",t:"#f8f7f3",w:2},"Backspace","~\n`"],
                [{w:1.5},"Tab",{c:"#b7d8eb",t:"#243746"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#243746",t:"#f8f7f3"},"Delete"],
                [{w:1.75},"Caps Lock",{c:"#b7d8eb",t:"#243746"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#243746",t:"#f8f7f3",w:2.25},"Enter","PgUp"],
                [{w:2.25},"Shift",{c:"#b7d8eb",t:"#243746"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#243746",t:"#f8f7f3",w:1.75},"Shift",{c:"#b7d8eb",t:"#243746"},"↑","PgDn"],
                [{w:1.25},"Ctrl",{w:1.25},"Win",{w:1.25},"Alt",{c:"#f8f7f3",t:"#000000",w:6.25},"",{c:"#243746",t:"#f8f7f3"},"Alt","Fn","Ctrl",{c:"#b7d8eb",t:"#243746"},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.LASER_75 -> """
                [[{c:"#ce234e",t:"#26204b"},"Esc",{c:"#2f1c75",t:"#00a2b0"},"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12",{c:"#ce234e",t:"#26204b"},"PrtSc","Scroll","Delete"],
                [{c:"#2f1c75",t:"#00a2b0"},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#26204b",t:"#ce234e",w:2},"Backspace","Home"],
                [{w:1.5},"Tab",{c:"#2f1c75",t:"#00a2b0"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#26204b",t:"#ce234e"},"PgUp"],
                [{w:1.75},"Caps Lock",{c:"#2f1c75",t:"#00a2b0"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#26204b",t:"#ce234e",w:2.25},"Enter","PgDn"],
                [{w:2.25},"Shift",{c:"#2f1c75",t:"#00a2b0"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#26204b",t:"#ce234e",w:1.75},"Shift",{c:"#ce234e",t:"#26204b"},"↑",{c:"#26204b",t:"#ce234e"},"End"],
                [{w:1.25},"Ctrl",{w:1.25},"Win",{w:1.25},"Alt",{c:"#ce234e",t:"#26204b",w:6.25},"",{c:"#26204b",t:"#ce234e"},"Alt","Fn","Ctrl",{c:"#ce234e",t:"#26204b"},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.OBLIVION_75 -> """
                [[{c:"#d64827",t:"#2a82ad"},"Esc",{c:"#4d4d4d",t:"#b7b2a6"},"F1","F2","F3","F4",{x:0.5,c:"#d7d6d2"},"F5","F6","F7","F8",{x:0.5,c:"#4d4d4d",t:"#b7b2a6"},"F9","F10","F11","F12",{x:1.0,c:"#d64827"},"Delete"],
                [{y:0.25,c:"#d7d6d2",t:"#4d4d4d"},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#4d4d4d",t:"#d64827",w:2},"Backspace","Home"],
                [{w:1.5},"Tab",{c:"#d7d6d2",t:"#4d4d4d"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#4d4d4d",t:"#b7b2a6"},"PgUp"],
                [{w:1.75},"Caps Lock",{c:"#d7d6d2",t:"#4d4d4d"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#4d4d4d",t:"#d64827",w:2.25},"Enter","PgDn"],
                [{c:"#4d4d4d",t:"#d64827",w:2.25},"Shift",{c:"#d7d6d2",t:"#4d4d4d"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#4d4d4d",t:"#d64827",w:1.75},"Shift",{c:"#d64827",t:"#2a82ad"},"↑",{c:"#4d4d4d",t:"#b7b2a6"},"End"],
                [{w:1.25},"Ctrl",{w:1.25},"Win",{w:1.25},"Alt",{c:"#d64827",t:"#2a82ad",w:6.25},"",{c:"#4d4d4d",t:"#d64827"},"Alt","Fn","Ctrl",{c:"#d64827",t:"#2a82ad"},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.NINE_ZERO_ZERO_NINE_TKL -> """
                [[{c:"#bcb59f",t:"#383537"},"Esc",{x:0.5,c:"#ddd7cb"},"F1","F2","F3","F4",{x:0.5,c:"#bcb59f"},"F5","F6","F7","F8",{x:0.5,c:"#ddd7cb"},"F9","F10","F11","F12",{x:0.5,c:"#bcb59f"},"Delete"],
                [{y:0.25,c:"#ddd7cb"},"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=",{c:"#bcb59f",w:2},"Backspace","Home"],
                [{w:1.5},"Tab",{c:"#ddd7cb"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{w:1.5},"|\n\\",{c:"#bcb59f"},"PgUp"],
                [{w:1.75},"Caps Lock",{c:"#ddd7cb"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#bcb59f",w:2.25},"Enter","PgDn"],
                [{w:2.25},"Shift",{c:"#ddd7cb"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#bcb59f",w:1.75},"Shift",{c:"#d99089"},"↑",{c:"#bcb59f"},"End"],
                [{w:1.5},"Ctrl",{x:0.75,w:1.5},"Alt",{c:"#d99089",w:7},"",{c:"#bcb59f",w:1.5},"Win",{x:0.75},"←","↓","→"]]
            """.trimIndent()

            KeyboardLayoutType.EIGHT_ZERO_ZERO_EIGHT_65 -> """
                [[{c:"#fe588c"},"Esc",{c:"#9ba7b7",t:"#3f4754"},"!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=","|\n\\","~\n`",{c:"#3f4754",t:"#fe588c"},"Delete"],
                [{w:1.5},"Tab",{c:"#9ba7b7",t:"#3f4754"},"Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]",{c:"#3f4754",t:"#fe588c",w:1.5},"Backspace","PgUp"],
                [{w:1.75},"Caps Lock",{c:"#9ba7b7",t:"#3f4754"},"A","S","D","F","G","H","J","K","L",":\n;","\"\n'",{c:"#fe588c",t:"#000000",w:2.25},"Enter",{c:"#3f4754",t:"#fe588c"},"PgDn"],
                [{w:2.25},"Shift",{c:"#9ba7b7",t:"#3f4754"},"Z","X","C","V","B","N","M","<\n,",">\n.","?\n/",{c:"#3f4754",t:"#fe588c",w:1.75},"Shift","↑","Fn"],
                [{w:1.5},"Ctrl",{x:0.75,w:1.5},"Alt",{c:"#fe588c",t:"#000000",w:7},"",{c:"#3f4754",t:"#fe588c",w:1.5},"Win",{x:0.75},"←","↓","→"]]
            """.trimIndent()
        }
    }

    private fun parseKleString(kle: String): List<List<KeyLayoutInfo>> {
        val rows = mutableListOf<List<KeyLayoutInfo>>()
        
        var index = 0
        val length = kle.length
        
        var currentY = 0.0f
        var rowIndex = 0
        
        // Key state modifiers
        var keyW = 1.0f
        var keyH = 1.0f
        
        while (index < length) {
            // Find start of row '['
            while (index < length && kle[index] != '[') {
                index++
            }
            if (index >= length) break
            index++ // Skip '['
            
            // Start of a new row: increment Y if not the first row
            if (rowIndex > 0) {
                currentY += 1.0f
            } else {
                currentY = 0.0f
            }
            
            var currentX = 0.0f
            var formatNextKey = false
            val currentRow = mutableListOf<KeyLayoutInfo>()
            
            while (index < length) {
                // Skip spaces and commas
                while (index < length && (kle[index].isWhitespace() || kle[index] == ',')) {
                    index++
                }
                if (index >= length) break
                val nextChar = kle[index]
                if (nextChar == ']') {
                    index++ // Skip ']'
                    rowIndex++
                    break // Row completed
                }
                
                if (nextChar == '{') {
                    index++
                    val objStart = index
                    while (index < length && kle[index] != '}') {
                        index++
                    }
                    val objText = kle.substring(objStart, index)
                    index++ // Skip '}'
                    
                    // Parse keys in json-like objText: e.g., w:2, x:0.5
                    val pairs = objText.split(",")
                    for (pair in pairs) {
                        val parts = pair.split(":")
                        if (parts.size == 2) {
                            val k = parts[0].trim().replace("\"", "").replace("'", "")
                            val v = parts[1].trim().replace("\"", "").replace("'", "")
                            when (k) {
                                "w" -> keyW = v.toFloatOrNull() ?: 1.0f
                                "h" -> keyH = v.toFloatOrNull() ?: 1.0f
                                "x" -> currentX += v.toFloatOrNull() ?: 0.0f
                                "y" -> currentY += v.toFloatOrNull() ?: 0.0f
                            }
                        }
                    }
                    formatNextKey = true
                } else if (nextChar == '"' || nextChar == '\'') {
                    val quoteChar = nextChar
                    index++
                    val strStart = index
                    var escaped = false
                    while (index < length) {
                        if (kle[index] == '\\' && !escaped) {
                            escaped = true
                        } else if (kle[index] == quoteChar && !escaped) {
                            break
                        } else {
                            escaped = false
                        }
                        index++
                    }
                    val rawLegend = if (strStart < index) kle.substring(strStart, index) else ""
                    index++ // Skip quote char
                    
                    val parts = rawLegend.split("\\n", "\n")
                    val mLegend = if (parts.isNotEmpty()) parts[0].replace("\\\"", "\"").replace("\\\\", "\\") else ""
                    val mSublegend = if (parts.size > 1) parts[1].replace("\\\"", "\"").replace("\\\\", "\\") else ""
                    
                    val hasSub = mSublegend.isNotEmpty()
                    val pLegend = if (hasSub) mSublegend else mLegend
                    val pShifted = if (hasSub) mLegend else ""
                    
                    val kCode = getKeycodeForLegend(pLegend)
                    val cat = getCategoryForLegend(pLegend)
                    
                    currentRow.add(
                        KeyLayoutInfo(
                            legend = pLegend,
                            shiftedLegend = pShifted,
                            widthRatio = keyW,
                            heightRatio = keyH,
                            x = currentX,
                            y = currentY,
                            keyCode = kCode,
                            category = cat
                        )
                    )
                    
                    currentX += keyW
                    keyW = 1.0f
                    keyH = 1.0f
                    formatNextKey = false
                } else {
                    index++
                }
            }
            if (currentRow.isNotEmpty()) {
                rows.add(currentRow)
            }
        }
        return rows
    }

    private fun getKeycodeForLegend(legend: String): Int {
        val clean = legend.trim().lowercase()
        return when (clean) {
            "esc" -> KEY_ESC
            "f1" -> KEY_F1
            "f2" -> KEY_F2
            "f3" -> KEY_F3
            "f4" -> KEY_F4
            "f5" -> KEY_F5
            "f6" -> KEY_F6
            "f7" -> KEY_F7
            "f8" -> KEY_F8
            "f9" -> KEY_F9
            "f10" -> KEY_F10
            "f11" -> KEY_F11
            "f12" -> KEY_F12
            "prtsc", "prt sc", "prtsc\nnmlk" -> KEY_PRINTSCREEN
            "scroll lock", "scroll", "scr lk", "pausescrlk" -> KEY_SCROLLLOCK
            "pause", "pause\nbreak" -> KEY_PAUSE
            "insert", "ins" -> KEY_INSERT
            "home" -> KEY_HOME
            "pgup", "page up" -> KEY_PAGEUP
            "delete", "del", "delete\ninsert" -> KEY_DELETE
            "backspace", "bspc" -> KEY_BACKSPACE
            "end" -> KEY_END
            "pgdn", "page down" -> KEY_PAGEDOWN
            "tab" -> KEY_TAB
            "caps lock", "caps" -> KEY_CAPSLOCK
            "num lock", "nmlk", "num" -> KEY_NUMLOCK
            "enter", "return" -> KEY_ENTER
            "shift", "lshift" -> MOD_LSHIFT
            "rshift" -> MOD_RSHIFT
            "ctrl", "lctrl" -> MOD_LCTRL
            "rctrl" -> MOD_RCTRL
            "win", "meta", "lwin", "cmd" -> MOD_LWIN
            "rwin" -> MOD_RWIN
            "alt", "lalt", "opt" -> MOD_LALT
            "ralt" -> MOD_RALT
            "spacebar", "space", "" -> KEY_SPACE
            "←", "left" -> KEY_LEFT
            "↑", "up" -> KEY_UP
            "↓", "down" -> KEY_DOWN
            "→", "right" -> KEY_RIGHT
            "~", "`" -> KEY_GRAVE
            "!" -> KEY_1
            "@" -> KEY_2
            "#" -> KEY_3
            "$" -> KEY_4
            "%" -> KEY_5
            "^" -> KEY_6
            "&" -> KEY_7
            "*" -> KEY_8
            "(" -> KEY_9
            ")" -> KEY_0
            "-" -> KEY_MINUS
            "=" -> KEY_EQUAL
            "[" -> KEY_LBRACKET
            "]" -> KEY_RBRACKET
            "\\" -> KEY_BACKSLASH
            ";" -> KEY_SEMICOLON
            "'" -> KEY_APOSTROPHE
            "," -> KEY_COMMA
            "." -> KEY_PERIOD
            "/" -> KEY_SLASH
            else -> {
                if (clean.length == 1) {
                    val c = clean[0]
                    when (c) {
                        in 'a'..'z' -> KEY_A + (c - 'a')
                        in '0'..'9' -> {
                            if (c == '0') KEY_0 else KEY_1 + (c - '1')
                        }
                        else -> 0
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun getCategoryForLegend(legend: String): KeyColorCategory {
        val clean = legend.trim().lowercase()
        if (clean == "esc" || clean == "enter" || clean == "return" || clean == "spacebar" || clean == "space") {
            return KeyColorCategory.ACCENT
        }
        if (clean in listOf("↑", "↓", "←", "→", "up", "down", "left", "right")) {
            return KeyColorCategory.ACCENT
        }
        val mods = listOf(
            "tab", "caps lock", "caps", "shift", "lshift", "rshift", "ctrl", "lctrl", "rctrl",
            "alt", "lalt", "ralt", "opt", "win", "meta", "lwin", "rwin", "cmd", "fn", "fn2", "bspc", "backspace",
            "insert", "ins", "home", "pgup", "page up", "delete", "del", "end", "pgdn", "page down",
            "prtsc", "prt sc", "scroll lock", "scroll", "scr lk", "pausescrlk", "pause", "num lock", "menu"
        )
        if (clean in mods) {
            return KeyColorCategory.MOD
        }
        if (clean.startsWith("f") && clean.substring(1).toIntOrNull() in 1..12) {
            return KeyColorCategory.MOD
        }
        return KeyColorCategory.ALPHA
    }

    fun getCharForKeycode(keyCode: Int, shiftPressed: Boolean): String {
        return when (keyCode) {
            KEY_A -> if (shiftPressed) "A" else "a"
            KEY_B -> if (shiftPressed) "B" else "b"
            KEY_C -> if (shiftPressed) "C" else "c"
            KEY_D -> if (shiftPressed) "D" else "d"
            KEY_E -> if (shiftPressed) "E" else "e"
            KEY_F -> if (shiftPressed) "F" else "f"
            KEY_G -> if (shiftPressed) "G" else "g"
            KEY_H -> if (shiftPressed) "H" else "h"
            KEY_I -> if (shiftPressed) "I" else "i"
            KEY_J -> if (shiftPressed) "J" else "j"
            KEY_K -> if (shiftPressed) "K" else "k"
            KEY_L -> if (shiftPressed) "L" else "l"
            KEY_M -> if (shiftPressed) "M" else "m"
            KEY_N -> if (shiftPressed) "N" else "n"
            KEY_O -> if (shiftPressed) "O" else "o"
            KEY_P -> if (shiftPressed) "P" else "p"
            KEY_Q -> if (shiftPressed) "Q" else "q"
            KEY_R -> if (shiftPressed) "R" else "r"
            KEY_S -> if (shiftPressed) "S" else "s"
            KEY_T -> if (shiftPressed) "T" else "t"
            KEY_U -> if (shiftPressed) "U" else "u"
            KEY_V -> if (shiftPressed) "V" else "v"
            KEY_W -> if (shiftPressed) "W" else "w"
            KEY_X -> if (shiftPressed) "X" else "x"
            KEY_Y -> if (shiftPressed) "Y" else "y"
            KEY_Z -> if (shiftPressed) "Z" else "z"
            KEY_1 -> if (shiftPressed) "!" else "1"
            KEY_2 -> if (shiftPressed) "@" else "2"
            KEY_3 -> if (shiftPressed) "#" else "3"
            KEY_4 -> if (shiftPressed) "$" else "4"
            KEY_5 -> if (shiftPressed) "%" else "5"
            KEY_6 -> if (shiftPressed) "^" else "6"
            KEY_7 -> if (shiftPressed) "&" else "7"
            KEY_8 -> if (shiftPressed) "*" else "8"
            KEY_9 -> if (shiftPressed) "(" else "9"
            KEY_0 -> if (shiftPressed) ")" else "0"
            KEY_SPACE -> " "
            KEY_MINUS -> if (shiftPressed) "_" else "-"
            KEY_EQUAL -> if (shiftPressed) "+" else "="
            KEY_LBRACKET -> if (shiftPressed) "{" else "["
            KEY_RBRACKET -> if (shiftPressed) "}" else "]"
            KEY_BACKSLASH -> if (shiftPressed) "|" else "\\"
            KEY_SEMICOLON -> if (shiftPressed) ":" else ";"
            KEY_APOSTROPHE -> if (shiftPressed) "\"" else "'"
            KEY_GRAVE -> if (shiftPressed) "~" else "`"
            KEY_COMMA -> if (shiftPressed) "<" else ","
            KEY_PERIOD -> if (shiftPressed) ">" else "."
            KEY_SLASH -> if (shiftPressed) "?" else "/"
            else -> ""
        }
    }
}
