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

    /** Gboard's symbol on each letter position: shown in the key's corner and reachable by long-press. */
    private val topSymbols = listOf("%", "\\", "|", "=", "[", "]", "<", ">", "{", "}")
    private val middleSymbols = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
    private val lowerSymbols = listOf("*", "\"", "'", ":", ";", "!", "?")

    // Note: these lists must stay above letterLayouts, which uses them while the object initializes.
    /** Shared by every letter layout: digits 1-0 as long-press on the top row. */
    private const val DIGITS = "1234567890"

    val letterLayouts: List<LayoutDef> = listOf(
        letters("qwerty", "QWERTY", "qwertyuiop", "asdfghjkl", "zxcvbnm"),
        letters("qwertz", "QWERTZ", "qwertzuiop", "asdfghjkl", "yxcvbnm"),
        letters("azerty", "AZERTY", "azertyuiop", "qsdfghjklm", "wxcvbn"),
        letters("colemak", "Colemak", "qwfpgjluy'", "arstdhneio", "zxcvbkm"),
    )

    fun letterLayout(id: String): LayoutDef = letterLayouts.firstOrNull { it.id == id } ?: letterLayouts[0]

    fun forLayer(layer: Layer, lettersId: String, numberRow: Boolean, punctuation: Boolean = false): LayoutDef {
        val base = when (layer) {
            Layer.LETTERS -> letterLayout(lettersId).let { if (numberRow) withNumberRow(it) else it }
            // The symbol pages always have Gboard's own bottom row.
            Layer.SYMBOLS -> return symbols
            Layer.SYMBOLS_EXTRA -> return symbolsExtra
            Layer.NUMPAD -> return numpad
        }
        return if (punctuation) withPunctuation(base) else base
    }

    /** Gboard-style bottom row with comma and period beside a narrower space bar. */
    private fun withPunctuation(layout: LayoutDef): LayoutDef {
        val last = layout.rows.last().keys
        val switch = last.first { it.type == KeyType.LAYER }.copy(width = 1.3f)
        val row = RowDef(
            listOf(
                switch,
                KeyDef("emoji", KeyType.EMOJI, 1f),
                KeyDef(",", popup = listOf(";", ":", "'", "\"", "-", "!", "?")),
                KeyDef(" ", KeyType.SPACE, 3.9f),
                KeyDef(".", popup = listOf("…", "?", "!", ",", "'", "\"", "-", ":", ";", "@", "#")),
                KeyDef("enter", KeyType.ENTER, 1.8f),
            )
        )
        return layout.copy(rows = layout.rows.dropLast(1) + row)
    }

    // ---- builders -------------------------------------------------------------------------

    /** [digit] (top row without a number row) and [symbol] come first in the long-press list, then accents. */
    private fun letterKey(c: Char, digit: String?, symbol: String?): KeyDef {
        val popups = buildList {
            if (digit != null) add(digit)
            if (symbol != null) add(symbol)
            accents[c]?.forEach { add(it.toString()) }
        }
        return KeyDef(label = c.toString(), popup = popups, hint = digit ?: symbol)
    }

    private fun charKey(s: String, width: Float = 1f): KeyDef {
        val popups = symbolPopups[s]?.map { it.toString() } ?: emptyList()
        // No corner hint: the extra characters are still available with a long press.
        return KeyDef(label = s, width = width, popup = popups)
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
        val top = row1.mapIndexed { i, c -> letterKey(c, DIGITS.getOrNull(i)?.toString(), topSymbols.getOrNull(i)) }
        val middle = row2.mapIndexed { i, c -> letterKey(c, null, middleSymbols.getOrNull(i)) }
        val side = (LayoutDef.UNITS - row3.length) / 2f
        val bottomLetters = buildList {
            add(shift(side))
            row3.forEachIndexed { i, c -> add(letterKey(c, null, lowerSymbols.getOrNull(i))) }
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
            // With a number row the top letters show their symbol instead of the digit.
            if (index == 0) {
                RowDef(row.keys.map { key ->
                    val symbol = key.popup.getOrNull(1)?.takeIf { !it[0].isLetterOrDigit() }
                    key.copy(hint = symbol, popup = key.popup.drop(1))
                })
            } else {
                row
            }
        }
        return layout.copy(rows = listOf(digits) + rows)
    }

    /** Gboard's symbol bottom row: ABC, a side key, the number pad key, space, a side key, return. */
    private fun symbolBottomRow(left: String, right: String) = RowDef(
        listOf(
            KeyDef("ABC", KeyType.LAYER, 1.5f, target = Layer.LETTERS),
            charKey(left),
            KeyDef("1234", KeyType.LAYER, 1f, target = Layer.NUMPAD),
            KeyDef(" ", KeyType.SPACE, 4f),
            charKey(right),
            KeyDef("enter", KeyType.ENTER, 1.5f),
        )
    )

    /** Same arrangement as Gboard's symbols page. */
    val symbols = LayoutDef(
        "symbols", "Symbols",
        listOf(
            RowDef("1234567890".map { charKey(it.toString()) }),
            RowDef(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { charKey(it) }),
            RowDef(
                listOf(KeyDef("=\\<", KeyType.LAYER, 1.5f, target = Layer.SYMBOLS_EXTRA)) +
                    listOf("*", "\"", "'", ":", ";", "!", "?").map { charKey(it) } +
                    backspace(1.5f)
            ),
            symbolBottomRow(",", "."),
        )
    )

    /** Gboard's second symbols page (the =\< key). */
    val symbolsExtra = LayoutDef(
        "symbols_extra", "More symbols",
        listOf(
            RowDef(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆").map { charKey(it) }),
            RowDef(listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map { charKey(it) }),
            RowDef(
                listOf(KeyDef("?123", KeyType.LAYER, 1.5f, target = Layer.SYMBOLS)) +
                    listOf("%", "©", "®", "™", "✓", "[", "]").map { charKey(it) } +
                    backspace(1.5f)
            ),
            symbolBottomRow("<", ">"),
        )
    )

    /** Used for number, phone and date fields. */
    val numpad = LayoutDef(
        "numpad", "Numbers",
        listOf(
            RowDef(listOf(charKey("1", 2.5f), charKey("2", 2.5f), charKey("3", 2.5f), KeyDef("-", width = 2.5f, popup = listOf("+", "(", ")", "/", ",", "*", "#")))),
            RowDef(listOf(charKey("4", 2.5f), charKey("5", 2.5f), charKey("6", 2.5f), KeyDef(" ", KeyType.SPACE, 2.5f))),
            RowDef(listOf(charKey("7", 2.5f), charKey("8", 2.5f), charKey("9", 2.5f), backspace(2.5f))),
            RowDef(
                listOf(
                    KeyDef("ABC", KeyType.LAYER, 2.5f, target = Layer.LETTERS),
                    KeyDef("0", width = 2.5f, popup = listOf("+", "*", "#")),
                    KeyDef(".", width = 2.5f, popup = listOf("+", "(", ")", "/", "#", "*")),
                    KeyDef("enter", KeyType.ENTER, 2.5f),
                )
            ),
        )
    )
}
