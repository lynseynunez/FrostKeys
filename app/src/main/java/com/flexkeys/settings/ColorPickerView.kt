package com.flexkeys.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Accent color picker: a row of preset swatches plus a hue slider for any other color. */
class ColorPickerView(context: Context) : View(context) {

    var onColorChanged: ((Int) -> Unit)? = null

    var color: Int = PRESETS[0]
        set(value) {
            field = value or 0xFF000000.toInt()
            invalidate()
        }

    /** Color used for the selection ring; set to the settings text color. */
    var ringColor: Int = Color.BLACK

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val hueRect = RectF()
    private var hueShader: Shader? = null

    private val swatchRow get() = 44f * density
    private val gap get() = 14f * density
    private val barHeight get() = 26f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (swatchRow + gap + barHeight + 4 * density).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 6f * density
        hueRect.set(pad, swatchRow + gap, w - pad, swatchRow + gap + barHeight)
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360f, SAT, VAL)) }
        hueShader = LinearGradient(hueRect.left, 0f, hueRect.right, 0f, hues, null, Shader.TileMode.CLAMP)
    }

    private fun swatchCenterX(i: Int): Float {
        val step = (width - 12f * density) / PRESETS.size
        return 6f * density + step * (i + 0.5f)
    }

    private fun swatchRadius(): Float =
        minOf(15f * density, (width - 12f * density) / PRESETS.size / 2f - 3f * density)

    override fun onDraw(canvas: Canvas) {
        val r = swatchRadius()
        val cy = swatchRow / 2f
        PRESETS.forEachIndexed { i, c ->
            paint.color = c
            val cx = swatchCenterX(i)
            canvas.drawCircle(cx, cy, r, paint)
            if (c == color) {
                ringPaint.color = ringColor
                canvas.drawCircle(cx, cy, r + 4f * density, ringPaint)
            }
        }
        paint.shader = hueShader
        val radius = barHeight / 2f
        canvas.drawRoundRect(hueRect, radius, radius, paint)
        paint.shader = null
        if (color !in PRESETS) {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            val x = hueRect.left + radius + (hueRect.width() - 2 * radius) * hsv[0] / 360f
            paint.color = color
            canvas.drawCircle(x, hueRect.centerY(), radius - 3f * density, paint)
            ringPaint.color = Color.WHITE
            canvas.drawCircle(x, hueRect.centerY(), radius - 1.5f * density, ringPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pick(event.x, event.y, event.actionMasked == MotionEvent.ACTION_DOWN)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private var draggingHue = false

    private fun pick(x: Float, y: Float, down: Boolean) {
        if (down) draggingHue = y > swatchRow
        val newColor = if (draggingHue) {
            val radius = barHeight / 2f
            val f = ((x - hueRect.left - radius) / (hueRect.width() - 2 * radius)).coerceIn(0f, 1f)
            Color.HSVToColor(floatArrayOf(f * 359.9f, SAT, VAL))
        } else {
            if (!down) return
            val i = PRESETS.indices.minByOrNull { kotlin.math.abs(swatchCenterX(it) - x) } ?: return
            PRESETS[i]
        }
        if (newColor != color) {
            color = newColor
            onColorChanged?.invoke(newColor)
        }
    }

    companion object {
        private const val SAT = 0.62f
        private const val VAL = 0.98f

        val PRESETS = intArrayOf(
            0xFF5B8CFF.toInt(), 0xFF7C5CFF.toInt(), 0xFFE85DBA.toInt(), 0xFFFF6B6B.toInt(),
            0xFFFF9F43.toInt(), 0xFFF5C518.toInt(), 0xFF40C463.toInt(), 0xFF20C997.toInt(),
            0xFF22B8CF.toInt(), 0xFF8A93A6.toInt(),
        )
    }
}
