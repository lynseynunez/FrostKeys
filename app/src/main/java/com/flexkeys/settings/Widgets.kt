package com.flexkeys.settings

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.flexkeys.theme.GlassBackdrop
import com.flexkeys.theme.GlassTheme

/** Colors shared by the settings widgets. */
class SettingsPalette(val dark: Boolean, accent: Int) {
    val accent = accent or 0xFF000000.toInt()
    val onAccent = if (GlassTheme.luminance(this.accent) > 0.6f) 0xFF111318.toInt() else Color.WHITE
    val text = if (dark) 0xFFF2F4F8.toInt() else 0xFF111318.toInt()
    val secondary = if (dark) 0xFFA3A9B6.toInt() else 0xFF5B6170.toInt()
    val card = if (dark) 0x1FFFFFFF else 0xA6FFFFFF.toInt()
    val cardBorder = if (dark) 0x26FFFFFF else 0xD9FFFFFF.toInt()
    val track = if (dark) 0x33FFFFFF else 0x1F111318
    val tonal = if (dark) 0x26FFFFFF else 0x14111318
    val scrim = if (dark) 0x80000000.toInt() else 0x66FFFFFF
}

private fun View.dp(v: Float) = v * resources.displayMetrics.density

private fun animate(from: Float, to: Float, update: (Float) -> Unit): ValueAnimator =
    ValueAnimator.ofFloat(from, to).apply {
        duration = 220
        interpolator = DecelerateInterpolator()
        addUpdateListener { update(it.animatedValue as Float) }
        start()
    }

/** Rounded pill button. Primary buttons use the accent color, others a soft tonal fill. */
@SuppressLint("ViewConstructor")
class PillButton(context: Context, label: String, private val primary: Boolean, var palette: SettingsPalette) :
    View(context) {

    var label: String = label
        set(value) {
            field = value
            contentDescription = value
            requestLayout()
            invalidate()
        }

    var onClick: (() -> Unit)? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private var pressedNow = false

    init {
        isClickable = true
        contentDescription = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = dp(15f)
        val w = (textPaint.measureText(label) + dp(40f)).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), dp(48f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, dp(2f), width.toFloat(), height - dp(2f))
        fill.color = if (primary) palette.accent else palette.tonal
        if (pressedNow) fill.alpha = (fill.alpha * 0.7f).toInt()
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        textPaint.color = if (primary) palette.onAccent else palette.text
        textPaint.textSize = dp(15f)
        val fm = textPaint.fontMetrics
        canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedNow = true; invalidate() }
            MotionEvent.ACTION_UP -> {
                pressedNow = false
                invalidate()
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                    onClick?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> { pressedNow = false; invalidate() }
        }
        return true
    }
}

/** iOS-style segmented control with a sliding selection pill. */
@SuppressLint("ViewConstructor")
class SegmentedControl(
    context: Context,
    private val options: List<String>,
    selected: Int,
    var palette: SettingsPalette,
    private val onSelect: (Int) -> Unit,
) : View(context) {

    var selected = selected.coerceIn(0, options.size - 1)
        private set
    private var position = this.selected.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(42f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = palette.track
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        val seg = width.toFloat() / options.size
        val inset = dp(3f)
        rect.set(position * seg + inset, inset, (position + 1) * seg - inset, h - inset)
        paint.color = palette.accent
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
        textPaint.textSize = dp(14f)
        options.forEachIndexed { i, label ->
            val active = i == selected
            textPaint.color = if (active) palette.onAccent else palette.text
            textPaint.typeface = Typeface.create("sans-serif-medium", if (active) Typeface.BOLD else Typeface.NORMAL)
            val fm = textPaint.fontMetrics
            canvas.drawText(label, seg * i + seg / 2, h / 2 - (fm.ascent + fm.descent) / 2, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val i = (event.x / (width.toFloat() / options.size)).toInt().coerceIn(0, options.size - 1)
            if (i != selected) {
                val from = position
                selected = i
                animate(from, i.toFloat()) { position = it; invalidate() }
                onSelect(i)
            }
        }
        return true
    }
}

/** A thick, rounded slider with a filled accent track. */
@SuppressLint("ViewConstructor")
class GlassSlider(
    context: Context,
    private val min: Int,
    private val max: Int,
    value: Int,
    var palette: SettingsPalette,
    private val onChange: (Int) -> Unit,
) : View(context) {

    var value = value.coerceIn(min, max)
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(36f).toInt())
    }

    private val thumbRadius get() = dp(13f)

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val left = thumbRadius
        val right = width - thumbRadius
        val f = if (max == min) 0f else (value - min).toFloat() / (max - min)
        val x = left + (right - left) * f
        val trackH = dp(8f)
        rect.set(left, cy - trackH / 2, right, cy + trackH / 2)
        paint.color = palette.track
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, paint)
        rect.set(left, cy - trackH / 2, x, cy + trackH / 2)
        paint.color = palette.accent
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, paint)
        paint.color = 0x33000000
        canvas.drawCircle(x, cy + dp(1.5f), thumbRadius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, cy, thumbRadius, paint)
    }

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private fun setFromX(x: Float) {
        val f = ((x - thumbRadius) / (width - 2 * thumbRadius)).coerceIn(0f, 1f)
        val v = min + Math.round(f * (max - min))
        if (v != value) {
            value = v
            invalidate()
            onChange(v)
        }
    }

    /**
     * A slider only moves on a sideways drag or a tap. A finger that starts on it and travels up or down just
     * scrolls the page, so scrolling a long list can't change a setting by accident.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(event.x - downX) > slop && Math.abs(event.x - downX) > Math.abs(event.y - downY)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) setFromX(event.x)
            }
            MotionEvent.ACTION_UP -> {
                // A tap (no real movement) jumps to that spot.
                if (!dragging && Math.abs(event.x - downX) <= slop && Math.abs(event.y - downY) <= slop) setFromX(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}

/** iOS-style on/off switch with an animated knob. */
@SuppressLint("ViewConstructor")
class PillSwitch(context: Context, checked: Boolean, var palette: SettingsPalette, private val onToggle: (Boolean) -> Unit) :
    View(context) {

    var checked = checked
        private set
    private var position = if (checked) 1f else 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(52f).toInt(), dp(32f).toInt())
    }

    fun toggle() {
        checked = !checked
        animate(position, if (checked) 1f else 0f) { position = it; invalidate() }
        onToggle(checked)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = blend(palette.track, palette.accent, position)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        val r = h / 2 - dp(3f)
        val x = h / 2 + (width - h) * position
        paint.color = 0x33000000
        canvas.drawCircle(x, h / 2 + dp(1f), r, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, h / 2, r, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) toggle()
        return true
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a ushr shift and 0xFF) * (1 - t) + (b ushr shift and 0xFF) * t).toInt() shl shift
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

/** Full-screen blurred backdrop (gradient or the user's photo) behind the settings page. */
class BackdropView(context: Context) : View(context) {
    val backdrop = GlassBackdrop()
    var scrim: Int = 0
        set(value) { field = value; invalidate() }
    private val paint = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        backdrop.setSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        backdrop.drawBlurred(canvas, 0f, 0f)
        paint.color = scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
