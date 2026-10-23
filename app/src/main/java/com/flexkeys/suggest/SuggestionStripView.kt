package com.flexkeys.suggest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.flexkeys.keyboard.Icon
import com.flexkeys.keyboard.Icons
import com.flexkeys.theme.Fonts
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.findGlassHost

/**
 * The glass bar above the keys. Shows up to three suggestions, or a toolbar (clipboard, emoji,
 * one-handed, settings, hide) when there is nothing to suggest or the menu button is tapped.
 */
class SuggestionStripView(context: Context) : View(context) {

    enum class Action(val icon: Icon, val label: String) {
        CLIPBOARD(Icon.CLIPBOARD, "Clipboard"),
        EMOJI(Icon.EMOJI, "Emoji"),
        GIF(Icon.GIF, "GIFs"),
        TRANSLATE(Icon.TRANSLATE, "Translate"),
        MIC(Icon.MIC, "Voice"),
        PHRASES(Icon.PHRASES, "Phrases"),
        FANCY(Icon.FANCY, "Fancy text"),
        AI(Icon.AI, "AI rewrite"),
        SETTINGS(Icon.SETTINGS, "Settings"),
        HIDE(Icon.HIDE, "Hide"),
    }

    private val prefs = Prefs.get(context)
    private var cachedItems = ""
    private var cachedActions = ToolbarItems.defaults
    /** The actions in the top row, as chosen in the menu. */
    private val toolbarActions: List<Action>
        get() {
            val saved = prefs.toolbarItems
            if (saved != cachedItems) {
                cachedItems = saved
                cachedActions = ToolbarItems.parse(saved)
            }
            return cachedActions
        }

    interface Listener {
        fun onSuggestionPicked(index: Int, text: String)
        fun onSuggestionLongPressed(text: String)
        fun onAction(action: Action)
        fun onStripTouched()
        /** The four-dot button was tapped: open or close the tools menu. */
        fun onMenuTapped()
    }

    var listener: Listener? = null

    var suggestions: Suggestions = Suggestions.EMPTY
        set(value) {
            field = value
            if (value.slots.isNotEmpty()) toolbarOpen = false
            invalidate()
        }

    /** Highlights the toolbar button of the panel that is currently open. */
    var activeAction: Action? = null
        set(value) {
            field = value
            toolbarOpen = value != null
            invalidate()
        }

    /** True while the tools menu is open: the button becomes a back arrow. */
    var menuOpen = false
        set(value) {
            field = value
            invalidate()
        }

    private var toolbarOpen = false
    private val showingToolbar get() = toolbarOpen || suggestions.slots.isEmpty()

    private var host: GlassHost? = null
    private val offset = PointF()
    private val barRect = RectF()
    private val tmp = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private var pressedTarget = NONE
    private var longPressFired = false
    private val longPress = Runnable {
        val idx = pressedTarget
        if (!showingToolbar && idx in suggestions.slots.indices) {
            longPressFired = true
            listener?.onSuggestionLongPressed(suggestions.slots[idx])
        }
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif-medium", Typeface.BOLD)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val scale = host?.theme?.heightScale?.coerceIn(0.85f, 1.15f) ?: 1f
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HEIGHT_DP * density * scale).toInt())
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val menuWidth get() = height.toFloat()

    override fun onDraw(canvas: Canvas) {
        val h = host ?: return
        val t = h.theme
        h.backdropOffset(this, offset)
        // The strip sits directly on the keyboard's glass sheet, like iOS.
        barRect.set(dp(5f), dp(4f), width - dp(5f), height - dp(2f))

        val iconSize = barRect.height() * 0.5f
        // Menu button on the left.
        tmp.set(barRect.left, barRect.top, barRect.left + menuWidth, barRect.bottom)
        drawPressed(canvas, MENU, tmp)
        Icons.draw(canvas, if (menuOpen) Icon.ARROW_LEFT else Icon.MENU,
            tmp.centerX(), tmp.centerY(), iconSize * 0.9f, t.secondaryTextColor)

        val contentLeft = barRect.left + menuWidth
        val contentWidth = barRect.right - contentLeft - dp(6f)
        if (showingToolbar) {
            val actions = toolbarActions
            val slot = contentWidth / actions.size
            actions.forEachIndexed { i, action ->
                tmp.set(contentLeft + i * slot, barRect.top, contentLeft + (i + 1) * slot, barRect.bottom)
                if (action == activeAction) {
                    fillPaint.color = t.accentSoft
                    val r = tmp.height() * 0.42f
                    canvas.drawCircle(tmp.centerX(), tmp.centerY(), r, fillPaint)
                }
                drawPressed(canvas, TOOLBAR + i, tmp)
                Icons.draw(canvas, action.icon, tmp.centerX(), tmp.centerY(), iconSize, t.textColor)
            }
            return
        }

        val slots = suggestions.slots
        val count = 3
        val slot = contentWidth / count
        dividerPaint.color = t.divider
        dividerPaint.strokeWidth = dp(1f)
        for (i in 0 until count) {
            tmp.set(contentLeft + i * slot, barRect.top, contentLeft + (i + 1) * slot, barRect.bottom)
            if (i > 0) canvas.drawLine(tmp.left, tmp.top + dp(10f), tmp.left, tmp.bottom - dp(10f), dividerPaint)
            val word = slots.getOrNull(i) ?: continue
            val highlighted = i == suggestions.highlighted || i == suggestions.clipIndex
            if (highlighted) {
                fillPaint.color = t.accentSoft
                val pill = RectF(tmp).apply { inset(dp(4f), dp(5f)) }
                canvas.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, fillPaint)
            }
            drawPressed(canvas, i, tmp)
            Fonts.apply(textPaint, context, t.fontId, if (highlighted || (suggestions.highlighted < 0 && i == 1)) 620 else 440)
            textPaint.textSize = barRect.height() * 0.40f * t.fontScale.coerceIn(0.8f, 1.3f)
            textPaint.color = if (highlighted || i == 1) t.textColor else t.secondaryTextColor
            val label = if (i == suggestions.typedIndex && suggestions.correction != null) "“$word”" else word
            val shown = TextUtils.ellipsize(label, textPaint, tmp.width() - dp(10f), TextUtils.TruncateAt.END).toString()
            val fm = textPaint.fontMetrics
            canvas.drawText(shown, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
        }
    }

    private fun drawPressed(canvas: Canvas, target: Int, rect: RectF) {
        if (target != pressedTarget) return
        val t = host?.theme ?: return
        fillPaint.color = t.pressedOverlay
        val r = RectF(rect).apply { inset(dp(3f), dp(4f)) }
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fillPaint)
    }

    private fun targetAt(x: Float): Int {
        val left = dp(5f)
        if (x < left + menuWidth) return MENU
        val contentLeft = left + menuWidth
        val contentWidth = width - dp(5f) - contentLeft - dp(6f)
        return if (showingToolbar) {
            val n = toolbarActions.size
            TOOLBAR + ((x - contentLeft) / (contentWidth / n)).toInt().coerceIn(0, n - 1)
        } else {
            ((x - contentLeft) / (contentWidth / 3)).toInt().coerceIn(0, 2)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedTarget = targetAt(event.x)
                longPressFired = false
                listener?.onStripTouched()
                handler.postDelayed(longPress, 500L)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                val target = pressedTarget
                pressedTarget = NONE
                invalidate()
                if (!longPressFired && target == targetAt(event.x)) fire(target)
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                pressedTarget = NONE
                invalidate()
            }
        }
        return true
    }

    private fun fire(target: Int) {
        when {
            target == MENU -> listener?.onMenuTapped()
            target >= TOOLBAR -> {
                val action = toolbarActions[target - TOOLBAR]
                listener?.onAction(action)
            }
            !showingToolbar -> suggestions.slots.getOrNull(target)?.let { listener?.onSuggestionPicked(target, it) }
        }
    }

    private companion object {
        const val HEIGHT_DP = 46f
        const val NONE = -1
        const val MENU = -2
        const val TOOLBAR = 100
    }
}
