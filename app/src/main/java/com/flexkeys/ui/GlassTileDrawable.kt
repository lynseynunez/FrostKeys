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
