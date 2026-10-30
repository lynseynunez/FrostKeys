package com.flexkeys.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.flexkeys.theme.GlassPainter

/** A background that paints a pane of glass with the keyboard's painter: a soft tint with the same highlight as the glass keys. */
class GlassTileDrawable(private val painter: GlassPainter, private val fill: Int, private val radiusPx: Float) : Drawable() {

    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        painter.draw(canvas, rect, radiusPx, null, 0f, 0f, fill, rim = false)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Shared tints for glass tiles, so every panel's chips, fields and buttons look like the same glass. */
object GlassTiles {
    /** A light veil of black (or white on a light theme): the blurred sheet shows through. */
    fun neutral(t: com.flexkeys.theme.GlassTheme): Int =
        com.flexkeys.theme.GlassTheme.withAlpha(if (t.dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE, 0.22f)

    /** The accent as a pane of glass: translucent, not a flat fill. */
    fun accent(t: com.flexkeys.theme.GlassTheme): Int = com.flexkeys.theme.GlassTheme.withAlpha(t.accentFill, 0.62f)

    fun tile(host: GlassHost?, color: Int, radiusPx: Float): Drawable =
        if (host != null) GlassTileDrawable(host.painter, color, radiusPx)
        else android.graphics.drawable.GradientDrawable().apply { cornerRadius = radiusPx; setColor(color) }
}
