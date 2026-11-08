package com.flexkeys.suggest

import kotlin.math.hypot

/** Where letters sit on a QWERTY keyboard, so a tap near a key's edge can point at the neighbor it may have meant. */
object KeyGeometry {

    private val positions: Map<Char, Pair<Float, Float>> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, i.toFloat() to 0f) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, i + 0.5f to 1f) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, i + 1.5f to 2f) }
    }

    /** The distance between two letter keys in key widths, or null if either is not a letter key. */
    fun distance(a: Char, b: Char): Float? {
        val p = positions[a.lowercaseChar()] ?: return null
        val q = positions[b.lowercaseChar()] ?: return null
        return hypot(q.first - p.first, q.second - p.second)
    }

    /** The unit direction (x to the right, y downward) from key [from] to key [to], or null if either is not a letter key. */
    fun direction(from: Char, to: Char): Pair<Float, Float>? {
        val a = positions[from.lowercaseChar()] ?: return null
        val b = positions[to.lowercaseChar()] ?: return null
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len < 1e-4f) return null
        return dx / len to dy / len
    }

    /**
     * How much a tap that landed [fx] (right) and [fy] (down) of the key's center, as fractions of the key size,
     * leans toward [to] when [from] was the key hit: +0.5 at the very edge toward it, -0.5 at the far edge, 0 in the middle.
     * Null when the tap position is unknown.
     */
    fun lean(from: Char, to: Char, fx: Float, fy: Float): Float? {
        if (fx.isNaN() || fy.isNaN()) return null
        val d = direction(from, to) ?: return null
        return (fx * d.first + fy * d.second).coerceIn(-0.5f, 0.5f)
    }
}
