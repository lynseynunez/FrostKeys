package com.flexkeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.layout.Layer
import com.flexkeys.layout.LayoutDef
import com.flexkeys.theme.Fonts
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.findGlassHost
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Custom-drawn keyboard. Lays out a [LayoutDef], draws every key as a glass panel and turns
 * touches into [Listener] calls. It knows nothing about text fields; the service decides what
 * each key does.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** A key went down. Used for haptics and sounds. */
        fun onKeyDown(key: KeyDef)

        /** A key was tapped (or, for backspace, pressed and repeated while held). */
        fun onKey(key: KeyDef)

        /** An alternate character was picked from a long-press popup. */
        fun onText(text: String)

        /** Long press on a key. Return true to consume it; otherwise the view's default applies. */
        fun onLongPress(key: KeyDef): Boolean

        /** Horizontal swipe on the space bar moves the cursor by [steps] characters. */
        fun onCursorMove(steps: Int)
    }

    class Key(val def: KeyDef, val cell: RectF, val visual: RectF) {
        var pressed = false
        var pressedAt = 0L
        var releasedAt = 0L
    }

    private class Pointer(var key: Key, val downX: Float, val downY: Float) {
        /** The tap was already handled (long press, repeat, popup...) so lifting does nothing. */
        var handled = false
        var repeating = false
        var cursorMode = false
        var cursorAnchorX = downX
        var popup: Popup? = null
        var longPress: Runnable? = null
    }

    private class Popup(val options: List<String>, val rect: RectF, val cellWidth: Float, var selected: Int)

    var listener: Listener? = null

    var layout: LayoutDef? = null
        set(value) {
            if (field == value) return
            field = value
            cancelAll()
            requestLayout()
            layoutKeys()
            invalidate()
        }

    var layer: Layer = Layer.LETTERS

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var enterAction: EnterAction = EnterAction.NEWLINE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var showKeyPreview = true

    private var host: GlassHost? = null
    private val keys = ArrayList<Key>()
    private val pointers = HashMap<Int, Pointer>()
    private var previewKey: Key? = null
    private val handler = Handler(Looper.getMainLooper())
    private val offset = PointF()
    private val tmpRect = RectF()
    private var rowHeight = 0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    // The space bar emblem is the supplied PNG (assets/emblem.png: black artwork on transparent),
    // tinted at draw time so it follows the theme's text color.
    private val emblemBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emblemRect = RectF()
    private var emblemBitmap: Bitmap? = null
    private var emblemTried = false
    private var emblemTint = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    /** Call after the theme changed: spacing, height and fonts may all be different. */
    fun onThemeChanged() {
        requestLayout()
        layoutKeys()
        invalidate()
    }

    private val theme: GlassTheme? get() = host?.theme

    // ---- measuring and layout ----------------------------------------------------------------

    /** Every page has the same total height; the rows share it equally (4 or 5 rows). */
    private fun computeRowHeight(t: GlassTheme, rows: Int): Float =
        t.dp(BASE_ROW_DP) * t.heightScale * TOTAL_ROWS / rows.coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val t = theme
        val rows = layout?.rows?.size ?: 4
        val height = if (t == null) 0 else (computeRowHeight(t, rows) * rows + 2 * t.dp(VERTICAL_PAD_DP)).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        keys.clear()
        val def = layout ?: return
        val t = theme ?: return
        if (width <= 0) return
        rowHeight = computeRowHeight(t, def.rows.size)
        val hPad = t.dp(3f)
        val vPad = t.dp(VERTICAL_PAD_DP)
        val unit = (width - 2 * hPad) / LayoutDef.UNITS
        val insetX = t.keySpacingPx / 2f
        val insetY = t.keySpacingPx / 2f + t.dp(3.5f)
        def.rows.forEachIndexed { r, row ->
            var x = hPad + (LayoutDef.UNITS - row.units) / 2f * unit
            val top = vPad + r * rowHeight
            for (k in row.keys) {
                val w = k.width * unit
                if (k.type != KeyType.SPACER) {
                    val cell = RectF(x, top, x + w, top + rowHeight)
                    val visual = RectF(cell).apply { inset(insetX, insetY) }
                    keys.add(Key(k, cell, visual))
                }
                x += w
            }
        }
    }

    // ---- drawing --------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val h = host ?: return
        val t = h.theme
        h.backdropOffset(this, offset)
        applyFonts(t)
        val now = SystemClock.uptimeMillis()
        var animating = false
        for (key in keys) {
            val p = pressProgress(key, now)
            if ((key.pressed && p < 1f) || (!key.pressed && p > 0f)) animating = true
            if (key.def.type == KeyType.SPACE) {
                drawSpaceCard(canvas, key, t, p)
            } else if (t.glassKeys) {
                drawGlassKeyBackground(canvas, key, t, h, p)
            } else {
                h.painter.draw(canvas, key.visual, t.cornerRadiusPx, null, offset.x, offset.y, fillFor(key, t), p,
                    gradient = h.glassGradient(), gradientAlpha = t.keyGradientAlpha)
            }
            canvas.save()
            if (p > 0f) {
                val s = 1f - 0.05f * p
                canvas.scale(s, s, key.visual.centerX(), key.visual.centerY())
            }
            drawKeyContent(canvas, key, t)
            canvas.restore()
        }
        if (animating) postInvalidateOnAnimation()
    }

    private var fontKey: String? = null

    // ---- the space bar as a frosted glass card ----
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPath = Path()
    private var cardSheen: Shader? = null
    private var cardEdge: Shader? = null
    private var cardTheme: GlassTheme? = null
    private var cardW = 0f
    private var cardH = 0f

    private fun buildCard(t: GlassTheme, w: Float, h: Float) {
        cardTheme = t
        cardW = w
        cardH = h
        // A soft diagonal sheen, like brushed glass: lighter toward one corner, no color. The
        // "Space bar glass" setting (k) scales how bright it is.
        val k = t.spaceGlass
        val hi = GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.10f + 0.40f * k else 0.30f + 0.40f * k)
        val lo = GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.02f + 0.04f * k else 0.06f)
        val end = GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.05f + 0.15f * k else 0.15f + 0.20f * k)
        cardSheen = LinearGradient(0f, 0f, w, h, intArrayOf(hi, lo, end), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        // A hairline edge only: no raised rim, so it reads as a pane of glass and not a button.
        cardEdge = LinearGradient(0f, 0f, w, h,
            GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.12f + 0.45f * k else 0.45f + 0.40f * k),
            GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.03f + 0.12f * k else 0.15f), Shader.TileMode.CLAMP)
    }

    /**
     * The space bar: neutral frosted glass like a launcher search bar. The blurred background shows
     * through a dark (or light) tint, with a faint brushed sheen and only a hairline edge.
     */
    private fun drawSpaceCard(canvas: Canvas, key: Key, t: GlassTheme, pressed: Float) {
        val v = key.visual
        val w = v.width()
        val h = v.height()
        if (cardTheme !== t || cardW != w || cardH != h) buildCard(t, w, h)
        val r = t.cornerRadiusPx.coerceAtMost(h / 2f)
        canvas.save()
        if (pressed > 0f) {
            val sc = 1f - 0.05f * pressed
            canvas.scale(sc, sc, v.centerX(), v.centerY())
        }
        canvas.translate(v.left, v.top)
        cardPath.reset()
        cardPath.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(cardPath)
        // Frosted: the blurred background shows through the pane.
        host?.backdrop?.drawBlurred(canvas, offset.x + v.left, offset.y + v.top)
        cardPaint.style = Paint.Style.FILL
        cardPaint.shader = null
        cardPaint.color = if (t.dark) GlassTheme.withAlpha(0xFF101012.toInt(), 0.30f) else GlassTheme.withAlpha(Color.WHITE, 0.30f)
        canvas.drawRect(0f, 0f, w, h, cardPaint)
        // A light veil over the blur makes the pane stand out; the setting controls how much.
        cardPaint.color = GlassTheme.withAlpha(Color.WHITE, if (t.dark) 0.02f + 0.20f * t.spaceGlass else 0.10f + 0.30f * t.spaceGlass)
        canvas.drawRect(0f, 0f, w, h, cardPaint)
        cardPaint.shader = cardSheen
        canvas.drawRect(0f, 0f, w, h, cardPaint)
        cardPaint.shader = null
        if (pressed > 0f) {
            cardPaint.color = GlassTheme.withAlpha(t.pressedOverlay, (t.pressedOverlay ushr 24) / 255f * pressed)
            canvas.drawRect(0f, 0f, w, h, cardPaint)
        }
        canvas.restore()

        // Faint light edge, brighter at the top left.
        val stroke = t.dp(0.75f)
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = stroke
        cardPaint.shader = cardEdge
        cardPath.reset()
        cardPath.addRoundRect(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f, r, r, Path.Direction.CW)
        canvas.drawPath(cardPath, cardPaint)
        cardPaint.shader = null
        cardPaint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun applyFonts(t: GlassTheme) {
        if (fontKey == t.fontId) return
        fontKey = t.fontId
        Fonts.apply(textPaint, context, t.fontId, 430)
        Fonts.apply(hintPaint, context, t.fontId, 500)
    }

    /**
     * Transparent style: no tile behind most keys. Only keys that carry state get one (active shift,
     * an action return key, the space bar's faint outline); a pressed key lights up softly.
     */
    private fun drawGlassKeyBackground(canvas: Canvas, key: Key, t: GlassTheme, h: GlassHost, p: Float) {
        val tile = when (key.def.type) {
            KeyType.ENTER -> if (enterAction == EnterAction.NEWLINE) 0 else t.accentFill
            KeyType.SPACE -> GlassTheme.withAlpha(t.keyFill, (t.keyFill ushr 24) / 255f * 0.55f)
            else -> 0
        }
        if (tile != 0) {
            h.painter.draw(canvas, key.visual, t.cornerRadiusPx, null, offset.x, offset.y, tile, p)
        } else if (p > 0f) {
            fillPaint.color = GlassTheme.withAlpha(t.pressedOverlay, (t.pressedOverlay ushr 24) / 255f * p)
            canvas.drawRoundRect(key.visual, t.cornerRadiusPx, t.cornerRadiusPx, fillPaint)
        }
    }

    private fun emblem(): Bitmap? {
        if (!emblemTried) {
            emblemTried = true
            emblemBitmap = try {
                context.assets.open("emblem.png").use { BitmapFactory.decodeStream(it) }
            } catch (e: java.io.IOException) {
                null
            }
        }
        return emblemBitmap
    }

    /** The space bar emblem: the supplied flower artwork in white, about the height of the letters. */
    private fun drawEmblemIcon(canvas: Canvas, cx: Float, cy: Float, t: GlassTheme) {
        val bitmap = emblem() ?: return
        val size = rowHeight * 0.40f * t.fontScale.coerceIn(0.8f, 1.3f) * t.emblemScale
        emblemRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        val color = if (t.glassKeys) t.glassTextFill else t.textColor
        if (color != emblemTint) {
            emblemTint = color
            emblemBitmapPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(bitmap, null, emblemRect, emblemBitmapPaint)
    }

    /** Key lettering. In the glass style it is translucent with a bright edge and the gradient shining through. */
    private fun drawKeyText(canvas: Canvas, text: String, cx: Float, cy: Float, t: GlassTheme) {
        if (!t.glassKeys) {
            drawCentered(canvas, text, cx, cy, textPaint)
            return
        }
        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        textPaint.setShadowLayer(t.dp(3f), 0f, t.dp(1.5f), t.glassTextShadow)
        textPaint.color = t.glassTextFill
        canvas.drawText(text, cx, baseline, textPaint)
        textPaint.clearShadowLayer()
        val gradient = host?.glassGradient()
        if (gradient != null && t.glassTextGradientAlpha > 0f) {
            // Draw in backdrop coordinates so this letter shows its own slice of the gradient.
            canvas.save()
            canvas.translate(-offset.x, -offset.y)
            textPaint.shader = gradient
            textPaint.alpha = (t.glassTextGradientAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawText(text, cx + offset.x, baseline + offset.y, textPaint)
            textPaint.shader = null
            canvas.restore()
        }
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = t.dp(0.7f)
        textPaint.color = t.glassTextRim
        canvas.drawText(text, cx, baseline, textPaint)
        textPaint.style = Paint.Style.FILL
    }

    private fun pressProgress(key: Key, now: Long): Float = when {
        key.pressed -> ((now - key.pressedAt) / PRESS_MS).coerceIn(0f, 1f)
        key.releasedAt > 0L -> (1f - (now - key.releasedAt) / RELEASE_MS).coerceIn(0f, 1f)
        else -> 0f
    }

    private val shiftActive get() = shiftState != ShiftState.OFF && layer == Layer.LETTERS

    private fun fillFor(key: Key, t: GlassTheme): Int = when (key.def.type) {
        // Return is plain glass for new lines and accent-colored for actions like Search or Send.
        KeyType.ENTER -> if (enterAction == EnterAction.NEWLINE) t.specialKeyFill else t.accentFill
        KeyType.SHIFT -> t.specialKeyFill
        KeyType.CHAR, KeyType.SPACE -> t.keyFill
        else -> t.specialKeyFill
    }

    private fun labelFor(def: KeyDef): String =
        if (def.isLetter && shiftState != ShiftState.OFF && layer == Layer.LETTERS) def.output.uppercase() else def.label

    private fun drawKeyContent(canvas: Canvas, key: Key, t: GlassTheme) {
        val v = key.visual
        val cx = v.centerX()
        val cy = v.centerY()
        val iconSize = minOf(v.height() * 0.46f, v.width() * 0.6f) * t.fontScale.coerceAtMost(1.25f)
        when (key.def.type) {
            KeyType.SHIFT -> {
                val icon = when {
                    layer != Layer.LETTERS -> Icon.SHIFT
                    shiftState == ShiftState.LOCKED -> Icon.CAPS_LOCK
                    shiftState == ShiftState.ONCE -> Icon.SHIFT_ON
                    else -> Icon.SHIFT
                }
                // Caps is shown by a filled arrow, not a highlighted key.
                Icons.draw(canvas, icon, cx, cy, iconSize, t.textColor)
            }
            KeyType.BACKSPACE -> Icons.draw(canvas, Icon.BACKSPACE, cx, cy, iconSize, t.textColor)
            KeyType.ENTER -> Icons.draw(canvas, enterAction.icon, cx, cy, iconSize,
                if (enterAction == EnterAction.NEWLINE) t.textColor else t.onAccent)
            KeyType.EMOJI -> Icons.draw(canvas, Icon.EMOJI, cx, cy, iconSize, t.textColor)
            KeyType.SPACE -> when {
                t.spaceEmblem == Prefs.SPACE_EMBLEM_ICON -> drawEmblemIcon(canvas, cx, cy, t)
                t.spaceEmblem.isNotEmpty() -> {
                    emblemPaint.textSize = minOf(v.height() * 0.5f, rowHeight * 0.36f) * t.fontScale.coerceIn(0.8f, 1.3f)
                    drawCentered(canvas, t.spaceEmblem, cx, cy, emblemPaint)
                }
            }
            KeyType.LAYER -> if (key.def.label == "1234") {
                // Gboard's number pad key: digits stacked in a small square.
                textPaint.textSize = rowHeight * 0.2f * t.fontScale
                textPaint.color = t.textColor
                val gap = textPaint.textSize * 0.55f
                drawKeyText(canvas, "1 2", cx, cy - gap, t)
                drawKeyText(canvas, "3 4", cx, cy + gap, t)
            } else {
                drawLabel(canvas, key, t, v, cx, cy)
            }
            KeyType.CHAR -> drawLabel(canvas, key, t, v, cx, cy)
            KeyType.SPACER -> Unit
        }
    }

    private fun drawLabel(canvas: Canvas, key: Key, t: GlassTheme, v: RectF, cx: Float, cy: Float) {
        val label = labelFor(key.def)
        val single = label.codePointCount(0, label.length) == 1
        textPaint.textSize = rowHeight * (if (single) 0.41f else 0.28f) * t.fontScale
        textPaint.color = t.textColor
        fitText(label, v.width() * 0.85f, textPaint)
        drawKeyText(canvas, label, cx, cy, t)
        val hint = key.def.hint
        if (hint != null) {
            hintPaint.textSize = rowHeight * 0.19f * t.fontScale
            hintPaint.color = t.secondaryTextColor
            canvas.drawText(hint, v.right - t.dp(5f), v.top + t.dp(3f) + hintPaint.textSize, hintPaint)
        }
    }

    private fun fitText(text: String, maxWidth: Float, paint: Paint) {
        val w = paint.measureText(text)
        if (w > maxWidth && w > 0f) paint.textSize = paint.textSize * maxWidth / w
    }

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    /**
     * Draws popups and the key preview. Called by the [GlassHost] after all children, with the
     * canvas translated to this view's top-left corner, so it may draw above the view.
     */
    fun drawOverlay(canvas: Canvas) {
        val h = host ?: return
        val t = h.theme
        h.backdropOffset(this, offset)
        val bubbleFill = GlassTheme.withAlpha(if (t.dark) 0xFF3A3D4A.toInt() else 0xFFFFFFFF.toInt(), 0.94f)

        val popup = pointers.values.firstNotNullOfOrNull { it.popup }
        if (popup != null) {
            h.painter.draw(canvas, popup.rect, t.cornerRadiusPx + t.dp(2f), h.backdrop, offset.x, offset.y, bubbleFill)
            textPaint.textSize = rowHeight * 0.38f * t.fontScale
            popup.options.forEachIndexed { i, option ->
                val left = popup.rect.left + i * popup.cellWidth
                tmpRect.set(left, popup.rect.top, left + popup.cellWidth, popup.rect.bottom)
                tmpRect.inset(t.dp(3f), t.dp(4f))
                if (i == popup.selected) {
                    fillPaint.color = t.accentFill
                    canvas.drawRoundRect(tmpRect, t.cornerRadiusPx, t.cornerRadiusPx, fillPaint)
                }
                textPaint.color = if (i == popup.selected) t.onAccent else t.textColor
                drawCentered(canvas, option, tmpRect.centerX(), tmpRect.centerY(), textPaint)
            }
            return
        }

        val key = previewKey ?: return
        val v = key.visual
        val w = maxOf(v.width() * 1.25f, t.dp(46f))
        val bh = rowHeight * 1.1f
        val cx = v.centerX().coerceIn(w / 2f + t.dp(2f), width - w / 2f - t.dp(2f))
        val top = maxOf(v.top - bh - t.dp(4f), -offset.y + t.dp(2f))
        tmpRect.set(cx - w / 2f, top, cx + w / 2f, top + bh)
        h.painter.draw(canvas, tmpRect, t.cornerRadiusPx + t.dp(2f), h.backdrop, offset.x, offset.y, bubbleFill)
        textPaint.textSize = rowHeight * 0.55f * t.fontScale
        textPaint.color = t.textColor
        val label = labelFor(key.def)
        fitText(label, w * 0.85f, textPaint)
        drawCentered(canvas, label, tmpRect.centerX(), tmpRect.centerY(), textPaint)
    }

    // ---- touch ----------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                onPointerDown(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) onPointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                onPointerUp(event.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        keys.firstOrNull { it.cell.contains(x, y) }?.let { return it }
        // Outside every cell (edges, centered short rows): pick the nearest key.
        return keys.minByOrNull { hypot(x - it.cell.centerX(), y - it.cell.centerY()) }
    }

    private fun onPointerDown(id: Int, x: Float, y: Float) {
        if (pointers.values.any { it.popup != null }) return
        // Rolling over to a new key while the previous one is still held types the previous one.
        for ((otherId, other) in pointers.entries.toList()) {
            if (other.key.def.type == KeyType.CHAR && !other.handled) onPointerUp(otherId)
        }
        val key = keyAt(x, y) ?: return
        val p = Pointer(key, x, y)
        pointers[id] = p
        press(key)
        listener?.onKeyDown(key.def)
        if (key.def.type == KeyType.BACKSPACE) {
            // Delete immediately for a snappy feel; holding repeats.
            p.handled = true
            listener?.onKey(key.def)
        }
        if (showKeyPreview && key.def.type == KeyType.CHAR) setPreview(key)
        scheduleLongPress(id, p)
    }

    private fun scheduleLongPress(id: Int, p: Pointer) {
        p.longPress?.let { handler.removeCallbacks(it) }
        val r = Runnable { onLongPressTimeout(id) }
        p.longPress = r
        handler.postDelayed(r, if (p.key.def.type == KeyType.BACKSPACE) REPEAT_START_MS else LONG_PRESS_MS)
    }

    private fun onLongPressTimeout(id: Int) {
        val p = pointers[id] ?: return
        if (p.cursorMode) return
        val def = p.key.def
        when {
            def.type == KeyType.BACKSPACE -> {
                p.repeating = true
                startRepeat(id, 0)
            }
            listener?.onLongPress(def) == true -> {
                p.handled = true
                setPreview(null)
            }
            def.type == KeyType.CHAR && def.popup.isNotEmpty() -> {
                p.handled = true
                openPopup(p)
            }
        }
    }

    private fun startRepeat(id: Int, count: Int) {
        val p = pointers[id] ?: return
        if (!p.repeating) return
        listener?.onKey(p.key.def)
        val r = Runnable { startRepeat(id, count + 1) }
        p.longPress = r
        handler.postDelayed(r, if (count > 15) REPEAT_FAST_MS else REPEAT_MS)
    }

    private fun openPopup(p: Pointer) {
        val h = host ?: return
        val t = h.theme
        h.backdropOffset(this, offset)
        val v = p.key.visual
        val options = p.key.def.popup.map {
            if (shiftState != ShiftState.OFF && layer == Layer.LETTERS) it.uppercase() else it
        }
        val n = options.size
        var cellWidth = maxOf(v.width(), t.dp(34f))
        val maxWidth = width - t.dp(8f)
        if (cellWidth * n > maxWidth) cellWidth = maxWidth / n
        val w = cellWidth * n
        val ph = rowHeight * 0.95f
        var left = v.centerX() - cellWidth / 2f
        if (left + w > width - t.dp(4f)) left = width - t.dp(4f) - w
        if (left < t.dp(4f)) left = t.dp(4f)
        val top = maxOf(v.top - ph - t.dp(6f), -offset.y + t.dp(2f))
        val selected = floor((v.centerX() - left) / cellWidth).toInt().coerceIn(0, n - 1)
        p.popup = Popup(options, RectF(left, top, left + w, top + ph), cellWidth, selected)
        previewKey = null
        host?.invalidateOverlay()
    }

    private fun onPointerMove(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        val popup = p.popup
        if (popup != null) {
            val idx = floor((x - popup.rect.left) / popup.cellWidth).toInt().coerceIn(0, popup.options.size - 1)
            if (idx != popup.selected) {
                popup.selected = idx
                host?.invalidateOverlay()
            }
            return
        }
        if (p.repeating || p.handled && !p.cursorMode) return
        val t = theme ?: return
        if (p.key.def.type == KeyType.SPACE) {
            if (!p.cursorMode && abs(x - p.downX) > t.dp(16f)) {
                p.cursorMode = true
                p.handled = true
                p.cursorAnchorX = x
                p.longPress?.let { handler.removeCallbacks(it) }
            }
            if (p.cursorMode) {
                val stepWidth = t.dp(11f)
                val steps = ((x - p.cursorAnchorX) / stepWidth).toInt()
                if (steps != 0) {
                    p.cursorAnchorX += steps * stepWidth
                    listener?.onCursorMove(steps)
                }
            }
            return
        }
        // Sliding a finger across character keys moves the press to the key under it.
        if (p.key.def.type != KeyType.CHAR) return
        val k = keyAt(x, y) ?: return
        if (k !== p.key && k.def.type == KeyType.CHAR && !p.key.cell.contains(x, y)) {
            release(p.key)
            p.key = k
            press(k)
            if (showKeyPreview) setPreview(k)
            scheduleLongPress(id, p)
        }
    }

    private fun onPointerUp(id: Int) {
        val p = pointers.remove(id) ?: return
        p.longPress?.let { handler.removeCallbacks(it) }
        p.repeating = false
        release(p.key)
        if (previewKey === p.key) setPreview(null)
        val popup = p.popup
        when {
            popup != null -> {
                p.popup = null
                host?.invalidateOverlay()
                listener?.onText(popup.options[popup.selected])
            }
            !p.handled -> listener?.onKey(p.key.def)
        }
    }

    fun cancelAll() {
        for (p in pointers.values) {
            p.longPress?.let { handler.removeCallbacks(it) }
            p.repeating = false
            release(p.key)
        }
        pointers.clear()
        previewKey = null
        host?.invalidateOverlay()
    }

    private fun press(key: Key) {
        key.pressed = true
        key.pressedAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun release(key: Key) {
        if (!key.pressed) return
        key.pressed = false
        key.releasedAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun setPreview(key: Key?) {
        if (previewKey === key) return
        previewKey = key
        host?.invalidateOverlay()
    }

    private companion object {
        const val BASE_ROW_DP = 54f
        /** Total key area in units of one 54dp row: the same as five rows at 86%. */
        const val TOTAL_ROWS = 4.3f
        const val VERTICAL_PAD_DP = 4f
        const val PRESS_MS = 70f
        const val RELEASE_MS = 200f
        const val LONG_PRESS_MS = 380L
        const val REPEAT_START_MS = 420L
        const val REPEAT_MS = 60L
        const val REPEAT_FAST_MS = 30L
    }
}
