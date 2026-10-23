package com.flexkeys.fancy

/**
 * Restyles plain text into Unicode lookalikes (bold, script, circled...). Only English letters and
 * digits change; everything else, including accents and emoji, is left as typed.
 */
object FancyText {

    /** Letters and digits start at the given code points; [exceptions] are the few letters Unicode placed elsewhere. */
    private class Block(val upper: Int, val lower: Int, val digit: Int = -1, val exceptions: Map<Char, Int> = emptyMap())

    enum class Style(val label: String) {
        BOLD("Bold"), ITALIC("Italic"), BOLD_ITALIC("Bold italic"), SCRIPT("Script"), BOLD_SCRIPT("Bold script"),
        FRAKTUR("Gothic"), DOUBLE_STRUCK("Double"), SANS("Sans"), SANS_BOLD("Sans bold"), SANS_ITALIC("Sans italic"),
        MONOSPACE("Mono"), CIRCLED("Circled"), SMALL_CAPS("Small caps"), STRIKETHROUGH("Strike"),
        UNDERLINE("Underline"), FULLWIDTH("Wide"),
    }

    private val blocks: Map<Style, Block> = mapOf(
        Style.BOLD to Block(0x1D400, 0x1D41A, 0x1D7CE),
        Style.ITALIC to Block(0x1D434, 0x1D44E, exceptions = mapOf('h' to 0x210E)),
        Style.BOLD_ITALIC to Block(0x1D468, 0x1D482),
        Style.SCRIPT to Block(
            0x1D49C, 0x1D4B6,
            exceptions = mapOf(
                'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112,
                'M' to 0x2133, 'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134,
            ),
        ),
        Style.BOLD_SCRIPT to Block(0x1D4D0, 0x1D4EA),
        Style.FRAKTUR to Block(
            0x1D504, 0x1D51E,
            exceptions = mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128),
        ),
        Style.DOUBLE_STRUCK to Block(
            0x1D538, 0x1D552, 0x1D7D8,
            exceptions = mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124),
        ),
        Style.SANS to Block(0x1D5A0, 0x1D5BA, 0x1D7E2),
        Style.SANS_BOLD to Block(0x1D5D4, 0x1D5EE, 0x1D7EC),
        Style.SANS_ITALIC to Block(0x1D608, 0x1D622),
        Style.MONOSPACE to Block(0x1D670, 0x1D68A, 0x1D7F6),
        Style.FULLWIDTH to Block(0xFF21, 0xFF41, 0xFF10),
    )

    private const val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

    fun apply(style: Style, text: String): String = when (style) {
        Style.CIRCLED -> map(text) { c ->
            when (c) {
                in 'A'..'Z' -> 0x24B6 + (c - 'A')
                in 'a'..'z' -> 0x24D0 + (c - 'a')
                in '1'..'9' -> 0x2460 + (c - '1')
                '0' -> 0x24EA
                else -> -1
            }
        }
        Style.SMALL_CAPS -> map(text) { c -> if (c in 'a'..'z') SMALL_CAPS[c - 'a'].code else -1 }
        Style.STRIKETHROUGH -> combine(text, '̶')
        Style.UNDERLINE -> combine(text, '̲')
        else -> {
            val block = blocks.getValue(style)
            map(text) { c ->
                block.exceptions[c] ?: when (c) {
                    in 'A'..'Z' -> block.upper + (c - 'A')
                    in 'a'..'z' -> block.lower + (c - 'a')
                    in '0'..'9' -> if (block.digit >= 0) block.digit + (c - '0') else -1
                    else -> -1
                }
            }
        }
    }

    /** A short sample of the style for its chip. */
    fun sample(style: Style): String = apply(style, "Aa")

    private inline fun map(text: String, code: (Char) -> Int): String {
        val out = StringBuilder(text.length * 2)
        for (c in text) {
            val cp = code(c)
            if (cp < 0) out.append(c) else out.appendCodePoint(cp)
        }
        return out.toString()
    }

    private fun combine(text: String, mark: Char): String {
        val out = StringBuilder(text.length * 2)
        for (c in text) {
            out.append(c)
            if (!c.isWhitespace() && !Character.isSurrogate(c)) out.append(mark)
        }
        return out.toString()
    }
}
