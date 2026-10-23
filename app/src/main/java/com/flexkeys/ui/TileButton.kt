package com.flexkeys.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.flexkeys.keyboard.Icon
import com.flexkeys.keyboard.Icons

/** A key-shaped glass tile with an icon or a label, used by the editing panel. */
class TileButton(context: Context, private val icon: Icon? = null, private val label: String? = null) : View(context) {

    var onClick: (() -> Unit)? = null
    var repeatWhileHeld = false
    var active = false
        set(value) { field = value; invalidate() }

    private var host: GlassHost? = null
    private var pressedNow = false
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private val repeater = object : Runnable {
        override fun run() {
            onClick?.invoke()
            handler.postDelayed(this, 60L)
        }
    }

    init {
        contentDescription = label
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val t = host?.theme ?: return
        val inset = t.keySpacingPx / 2f + t.dp(1f)
        rect.set(inset, inset, width - inset, height - inset)
        paint.color = if (active) t.activeFill else t.keyFill
        canvas.drawRoundRect(rect, t.cornerRadiusPx, t.cornerRadiusPx, paint)
        if (pressedNow) {
            paint.color = t.pressedOverlay
            canvas.drawRoundRect(rect, t.cornerRadiusPx, t.cornerRadiusPx, paint)
        }
        val color = if (active) t.onActive else t.textColor
        val i = icon
        if (i != null) {
            Icons.draw(canvas, i, rect.centerX(), rect.centerY(), minOf(rect.width(), rect.height()) * 0.42f, color)
        } else {
            textPaint.color = color
            textPaint.textSize = minOf(rect.height() * 0.32f, t.dp(16f)) * t.fontScale.coerceIn(0.8f, 1.3f)
            val fm = textPaint.fontMetrics
            canvas.drawText(label ?: "", rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedNow = true
                invalidate()
                if (repeatWhileHeld) {
                    onClick?.invoke()
                    handler.postDelayed(repeater, 420L)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeater)
                pressedNow = false
                invalidate()
                if (!repeatWhileHeld && event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) onClick?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeater)
                pressedNow = false
                invalidate()
            }
        }
        return true
    }
}
