package com.flexkeys.layout

/**
 * All built-in layouts. To add a new letter layout, append a [letters] call to
 * [letterLayouts] with its three rows of letters; popups, digit hints and the
 * shared bottom row are filled in automatically.
 */
object Layouts {

    private val accents: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā",
        'c' to "çćč",
        'd' to "ď",
        'e' to "èéêëēėę",
        'g' to "ğ",
        'i' to "îïíīįì",
        'l' to "ł",
        'n' to "ñń",
        'o' to "ôöòóœøōõ",
        'r' to "ř",
        's' to "ßśš",
        't' to "ť",
        'u' to "ûüùúū",
        'y' to "ÿý",
        'z' to "žźż",
    )

    private val symbolPopups: Map<String, String> = mapOf(
        "1" to "¹½⅓¼⅛", "2" to "²⅔", "3" to "³¾⅜", "4" to "⁴", "5" to "⅝", "7" to "⅞",
        "0" to "ⁿ∅", "$" to "€£¥¢₹₩₽", "-" to "–—_·", "+" to "±", "(" to "<{[", ")" to ">}]",
        "/" to "\\", "&" to "§", "'" to "‘’‚‹›", "\"" to "“”„«»", "?" to "¿‽", "!" to "¡",
        "%" to "‰", "*" to "★†‡", "#" to "№", "=" to "≠≈∞", "." to "…",
    )

    /** Shared by every letter layout: digits 1-0 as long-press on the top row. */
    private const val DIGITS = "1234567890"

    val letterLayouts: List<LayoutDef> = listOf(
        letters("qwerty", "QWERTY", "qwertyuiop", "asdfghjkl", "zxcvbnm"),
        letters("qwertz", "QWERTZ", "qwertzuiop", "asdfghjkl", "yxcvbnm"),
        letters("azerty", "AZERTY", "azertyuiop", "qsdfghjklm", "wxcvbn"),
        letters("colemak", "Colemak", "qwfpgjluy'", "arstdhneio", "zxcvbkm"),
    )

    fun letterLayout(id: String): LayoutDef = letterLayouts.firstOrNull { it.id == id } ?: letterLayouts[0]

    fun forLayer(layer: Layer, lettersId: String, numberRow: Boolean): LayoutDef = when (layer) {
        Layer.LETTERS -> letterLayout(lettersId).let { if (numberRow) withNumberRow(it) else it }
        Layer.SYMBOLS -> symbols
        Layer.SYMBOLS_EXTRA -> symbolsExtra
        Layer.NUMPAD -> numpad
    }

    // ---- builders -------------------------------------------------------------------------

    private fun letterKey(c: Char, hint: String?): KeyDef {
        val popups = buildList {
            if (hint != null) add(hint)
            accents[c]?.forEach { add(it.toString()) }
        }
        return KeyDef(label = c.toString(), popup = popups, hint = hint)
    }

    private fun charKey(s: String, width: Float = 1f): KeyDef {
        val popups = symbolPopups[s]?.map { it.toString() } ?: emptyList()
        return KeyDef(label = s, width = width, popup = popups, hint = popups.firstOrNull())
    }

    private fun shift(width: Float) = KeyDef("shift", KeyType.SHIFT, width)
    private fun backspace(width: Float) = KeyDef("backspace", KeyType.BACKSPACE, width)

    /** iOS-style bottom row: layer switch, emoji, a wide space bar and a wide return key. */
    private fun bottomRow(switchLabel: String, switchTarget: Layer) = RowDef(
        listOf(
            KeyDef(switchLabel, KeyType.LAYER, 1.3f, target = switchTarget),
            KeyDef("emoji", KeyType.EMOJI, 1.2f),
            KeyDef(" ", KeyType.SPACE, 5f),
            KeyDef("enter", KeyType.ENTER, 2.5f),
        )
    )

    /** Builds a letter layout from three rows of characters. */
    fun letters(id: String, name: String, row1: String, row2: String, row3: String): LayoutDef {
        val top = row1.mapIndexed { i, c -> letterKey(c, DIGITS.getOrNull(i)?.toString()) }
        val middle = row2.map { letterKey(it, null) }
        val side = (LayoutDef.UNITS - row3.length) / 2f
        val bottomLetters = buildList {
            add(shift(side))
            row3.forEach { add(letterKey(it, null)) }
            add(backspace(side))
        }
        return LayoutDef(
            id, name,
            listOf(RowDef(top), RowDef(middle), RowDef(bottomLetters), bottomRow("123", Layer.SYMBOLS))
        )
    }

    private fun withNumberRow(layout: LayoutDef): LayoutDef {
        val digits = RowDef(DIGITS.map { charKey(it.toString()) })
        val rows = layout.rows.mapIndexed { index, row ->
            if (index == 0) RowDef(row.keys.map { it.copy(hint = null, popup = it.popup.drop(1)) }) else row
        }
        return layout.copy(rows = listOf(digits) + rows)
    }

    val symbols = LayoutDef(
        "symbols", "Symbols",
        listOf(
            RowDef("1234567890".map { charKey(it.toString()) }),
            RowDef(listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "+").map { charKey(it) }),
            RowDef(
                listOf(KeyDef("#+=", KeyType.LAYER, 1.5f, target = Layer.SYMBOLS_EXTRA)) +
                    listOf(".", ",", "?", "!", "'", "\"", "*").map { charKey(it) } +
                    backspace(1.5f)
            ),
            bottomRow("ABC", Layer.LETTERS),
        )
    )

    val symbolsExtra = LayoutDef(
        "symbols_extra", "More symbols",
        listOf(
            RowDef(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { charKey(it) }),
            RowDef(listOf("_", "\\", "#", "€", "£", "¥", "^", "=", "{", "}").map { charKey(it) }),
            RowDef(
                listOf(KeyDef("123", KeyType.LAYER, 1.5f, target = Layer.SYMBOLS)) +
                    listOf("%", "©", "®", "™", "✓", "[", "]").map { charKey(it) } +
                    backspace(1.5f)
            ),
            bottomRow("ABC", Layer.LETTERS),
        )
    )

    /** Used for number, phone and date fields. */
    val numpad = LayoutDef(
        "numpad", "Numbers",
        listOf(
            RowDef(listOf(charKey("1", 2.5f), charKey("2", 2.5f), charKey("3", 2.5f), charKey("-", 2.5f))),
            RowDef(listOf(charKey("4", 2.5f), charKey("5", 2.5f), charKey("6", 2.5f), charKey(",", 2.5f))),
            RowDef(listOf(charKey("7", 2.5f), charKey("8", 2.5f), charKey("9", 2.5f), backspace(2.5f))),
            RowDef(
                listOf(
                    KeyDef("*#", KeyType.LAYER, 2.5f, target = Layer.SYMBOLS),
                    KeyDef("0", width = 2.5f, popup = listOf("+")),
                    KeyDef(".", width = 2.5f, popup = listOf("+", "(", ")", "/", "#", "*")),
                    KeyDef("enter", KeyType.ENTER, 2.5f),
                )
            ),
        )
    )
}
