package com.flexkeys.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.widget.LinearLayout

/** A vertical layout drawn as one large frosted glass sheet. Base class for the panels. */
open class GlassPanelLayout(context: Context) : LinearLayout(context) {

    protected var host: GlassHost? = null
        private set
    private val offset = PointF()
    private val rect = RectF()

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
    }

    protected fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        onHostAttached()
    }

    /** Called once the theme is available. */
    protected open fun onHostAttached() = Unit

    override fun onDraw(canvas: Canvas) {
        val h = host ?: return
        h.backdropOffset(this, offset)
        val inset = h.theme.dp(4f)
        rect.set(inset, inset, width - inset, height - inset)
        h.painter.draw(canvas, rect, h.theme.cornerRadiusPx + inset, h.backdrop, offset.x, offset.y, h.theme.panelFill)
    }
}
