package com.flexkeys.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.flexkeys.keyboard.Icon
import com.flexkeys.keyboard.Icons

/** A round icon (or short text) button used in panels. Optionally repeats while held. */
class GlassButton(context: Context, icon: Icon? = null, label: String? = null) : View(context) {

    var icon: Icon? = icon
        set(value) { field = value; invalidate() }
    var label: String? = label
        set(value) { field = value; invalidate() }
    /** Draw a filled glass disc behind the content even when not pressed. */
    var filled = false
    var selectedState = false
        set(value) { field = value; invalidate() }
    var repeatWhileHeld = false
    var onClick: (() -> Unit)? = null

    private var host: GlassHost? = null
    private var pressedNow = false
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val repeater = object : Runnable {
        override fun run() {
            onClick?.invoke()
            handler.postDelayed(this, REPEAT_MS)
        }
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
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.42f
        when {
            pressedNow -> {
                paint.color = t.pressedOverlay
                canvas.drawCircle(cx, cy, r, paint)
            }
            selectedState -> {
                paint.color = t.accentSoft
                canvas.drawCircle(cx, cy, r, paint)
            }
            filled -> {
                paint.color = t.specialKeyFill
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
        val i = icon
        if (i != null) {
            Icons.draw(canvas, i, cx, cy, r * 1.05f, t.textColor)
        } else {
            val text = label ?: return
            textPaint.color = t.textColor
            textPaint.textSize = r * 0.75f
            val fm = textPaint.fontMetrics
            canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
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
                    handler.postDelayed(repeater, REPEAT_START_MS)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeater)
                val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                pressedNow = false
                invalidate()
                if (!repeatWhileHeld && inside) onClick?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeater)
                pressedNow = false
                invalidate()
            }
        }
        return true
    }

    private companion object {
        const val REPEAT_START_MS = 420L
        const val REPEAT_MS = 60L
    }
}
