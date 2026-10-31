package com.flexkeys.ui

import android.graphics.Path

/** A sakura petal: a rounded teardrop with a small notch at its wide end, drawn in a unit box (-1..1). */
object Petal {

    /** Shared outline; callers translate, rotate and scale the canvas, never change the path. */
    val path: Path = Path().apply {
        moveTo(0f, 1f)
        cubicTo(0.95f, 0.45f, 0.95f, -0.62f, 0.3f, -1f)
        lineTo(0f, -0.76f)
        lineTo(-0.3f, -1f)
        cubicTo(-0.95f, -0.62f, -0.95f, 0.45f, 0f, 1f)
        close()
    }

    /** Soft pink that reads on both dark and light glass. */
    fun color(dark: Boolean, shade: Float): Int {
        // shade 0..1 picks between a pale and a deeper pink.
        val pale = if (dark) 0xFFFFC9DA.toInt() else 0xFFF8A9C6.toInt()
        val deep = if (dark) 0xFFFF9EC0.toInt() else 0xFFEE7FA8.toInt()
        return com.flexkeys.theme.GlassTheme.lerp(pale, deep, shade.coerceIn(0f, 1f))
    }
}
