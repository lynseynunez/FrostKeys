package com.flexkeys.keyboard

/** The color sets the key spotlight can use, and how a color is picked for each press. */
object SpotlightPalettes {

    val names = listOf("Accent", "Blue, purple, pink", "Sunset", "Ocean", "Candy", "Aurora", "Rainbow", "Custom")

    const val ACCENT = 0
    const val GRADIENT = 1
    const val CUSTOM = 7

    private val sunset = intArrayOf(0xFFFF9A3C.toInt(), 0xFFFF5D73.toInt(), 0xFFB44CFF.toInt())
    private val ocean = intArrayOf(0xFF2DD4BF.toInt(), 0xFF38BDF8.toInt(), 0xFF6366F1.toInt())
    private val candy = intArrayOf(0xFFFF7AC6.toInt(), 0xFFFFD166.toInt(), 0xFF7AD7FF.toInt())
    private val aurora = intArrayOf(0xFF5EF2A0.toInt(), 0xFF3DD6FF.toInt(), 0xFFB07CFF.toInt())
    private val rainbow = intArrayOf(
        0xFFFF4D4D.toInt(), 0xFFFFB84D.toInt(), 0xFFFFF04D.toInt(), 0xFF4DFF88.toInt(), 0xFF4DC3FF.toInt(),
        0xFF7A5CFF.toInt(), 0xFFFF5DB1.toInt(),
    )

    /** The default custom colors: blue, purple, pink. */
    val defaultCustom = intArrayOf(0xFF4D7CFF.toInt(), 0xFF9B5CFF.toInt(), 0xFFFF5DB1.toInt())

    /** The colors for palette [index]. [accent] and [gradient] come from the theme; [custom] is the user's own set. */
    fun colors(index: Int, accent: Int, gradient: IntArray, custom: IntArray): IntArray = when (index) {
        ACCENT -> intArrayOf(accent or 0xFF000000.toInt())
        GRADIENT -> gradient
        2 -> sunset
        3 -> ocean
        4 -> candy
        5 -> aurora
        6 -> rainbow
        CUSTOM -> if (custom.isEmpty()) defaultCustom else custom
        else -> gradient
    }

    /** A color along [colors] at [fraction] (0..1), blending between neighbors. */
    fun colorAt(colors: IntArray, fraction: Float): Int {
        if (colors.isEmpty()) return 0xFFFFFFFF.toInt()
        if (colors.size == 1) return colors[0]
        val pos = fraction.coerceIn(0f, 1f) * (colors.size - 1)
        val i = pos.toInt().coerceAtMost(colors.size - 2)
        return blend(colors[i], colors[i + 1], pos - i)
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) + ((((b ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) * f)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Custom colors are saved as comma-separated hex, like "FF4D7CFF,FF9B5CFF". */
    fun parseCustom(text: String): IntArray =
        text.split(',').mapNotNull { it.trim().takeIf { s -> s.length in 6..8 }?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt()) }
            .take(5).toIntArray()

    fun formatCustom(colors: IntArray): String = colors.joinToString(",") { "%08X".format(it) }
}
