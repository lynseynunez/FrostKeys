package com.flexkeys.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.theme.GlassBackdrop
import com.flexkeys.theme.GlassPainter
import com.flexkeys.theme.GlassTheme

/**
 * Top-level keyboard view, used both by the input method and by the live preview in the
 * settings app. Draws the shared backdrop, hosts the keyboard and draws its popups on top.
 */
class KeyboardRootView(context: Context, initialTheme: GlassTheme) : LinearLayout(context), GlassHost {

    override var theme: GlassTheme = initialTheme
        private set
    override val backdrop = GlassBackdrop().apply { setTheme(initialTheme) }
    override val painter = GlassPainter(initialTheme)

    val keyboardView = KeyboardView(context)

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        addView(keyboardView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun applyTheme(newTheme: GlassTheme) {
        theme = newTheme
        painter.theme = newTheme
        backdrop.setTheme(newTheme)
        keyboardView.onThemeChanged()
        invalidateTree(this)
    }

    /** Called when the backdrop photo changed. */
    fun onBackdropChanged() = invalidateTree(this)

    private fun invalidateTree(view: View) {
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdrop.setSize(w, h)
        invalidateTree(this)
    }

    override fun onDraw(canvas: Canvas) {
        backdrop.drawBase(canvas)
    }

    private val overlayOffset = PointF()

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (keyboardView.isShown) {
            backdropOffset(keyboardView, overlayOffset)
            canvas.save()
            canvas.translate(overlayOffset.x, overlayOffset.y)
            keyboardView.drawOverlay(canvas)
            canvas.restore()
        }
    }

    override fun backdropOffset(view: View, out: PointF) {
        var x = 0f
        var y = 0f
        var v: View? = view
        while (v != null && v !== this) {
            x += v.left + v.translationX
            y += v.top + v.translationY
            val parent = v.parent as? View ?: break
            x -= parent.scrollX
            y -= parent.scrollY
            v = parent
        }
        out.set(x, y)
    }

    override fun invalidateOverlay() = invalidate()

    fun release() = backdrop.release()
}
