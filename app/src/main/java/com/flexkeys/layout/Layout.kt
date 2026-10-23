package com.flexkeys.layout

/** What a key does when tapped. */
enum class KeyType {
    /** Commits [KeyDef.output] (or the shifted label for letters). */
    CHAR,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    /** Switches to [KeyDef.target]. */
    LAYER,
    EMOJI,
    /** Empty space used to indent a row; not drawn and not touchable. */
    SPACER,
}

/** Which set of keys is showing. */
enum class Layer { LETTERS, SYMBOLS, SYMBOLS_EXTRA, NUMPAD }

/**
 * A single key. [width] is in key units; every row is laid out on a grid that is
 * [LayoutDef.UNITS] units wide and shorter rows are centered.
 */
data class KeyDef(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val width: Float = 1f,
    val output: String = label,
    val popup: List<String> = emptyList(),
    /** Small hint drawn in the key corner, usually the first popup character. */
    val hint: String? = null,
    val target: Layer? = null,
) {
    val isLetter: Boolean get() = type == KeyType.CHAR && output.length == 1 && output[0].isLetter()
    val isModifier: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE && type != KeyType.SPACER
}

data class RowDef(val keys: List<KeyDef>) {
    val units: Float get() = keys.sumOf { it.width.toDouble() }.toFloat()
}

data class LayoutDef(val id: String, val name: String, val rows: List<RowDef>) {
    companion object {
        const val UNITS = 10f
    }
}
