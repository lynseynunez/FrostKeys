package com.flexkeys.settings

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.PhotoFit
import com.flexkeys.theme.Prefs

/**
 * Lets the user choose which part of the background photo sits behind the keyboard: drag to move
 * the bright window over the photo, pinch to zoom.
 */
class PhotoAdjustActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var cropView: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        cropView = CropView(this).apply {
            zoom = prefs.photoZoom / 100f
            panX = prefs.photoPanX / 100f
            panY = prefs.photoPanY / 100f
            // The keyboard is roughly the screen wide and 300dp tall.
            windowAspect = resources.displayMetrics.widthPixels / (300f * density)
        }
        val hint = TextView(this).apply {
            text = "Drag to move, pinch to zoom. The bright area is what shows behind the keyboard."
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(20f), dp(24f), dp(12f))
        }
        fun pill(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (primary) 0xFF15171C.toInt() else Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(24f).toFloat()
                setColor(if (primary) Color.WHITE else 0x33FFFFFF)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(48f), 1f).apply { setMargins(dp(6f), 0, dp(6f), 0) }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(20f))
            addView(pill("Reset", false) { cropView.reset() })
            addView(pill("Cancel", false) { finish() })
            addView(pill("Done", true) { save() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0C10.toInt())
            fitsSystemWindows = true
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(FrameLayout(context).apply { addView(cropView) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        val app = applicationContext
        Thread {
            val bitmap = BackgroundImageStore.load(app, 1200)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (bitmap == null) finish() else cropView.setPhoto(bitmap)
            }
        }.start()
    }

    private fun save() {
        prefs.photoZoom = (cropView.zoom * 100f).toInt().coerceIn(100, 400)
        prefs.photoPanX = (cropView.panX * 100f).toInt().coerceIn(-100, 100)
        prefs.photoPanY = (cropView.panY * 100f).toInt().coerceIn(-100, 100)
        prefs.backgroundVersion = System.currentTimeMillis()
        finish()
    }

    private class CropView(context: Context) : View(context) {
        var zoom = 1f
        var panX = 0f
        var panY = 0f
        var windowAspect = 1.4f

        private var photo: Bitmap? = null
        private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dimPaint = Paint().apply { color = 0xAA000000.toInt() }
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2f * context.resources.displayMetrics.density
        }
        private val dest = RectF()
        private val window = RectF()
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false

        private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyZoom(zoom * detector.scaleFactor)
                return true
            }
        })

        fun setPhoto(bitmap: Bitmap) {
            photo = bitmap
            invalidate()
        }

        fun reset() {
            zoom = 1f
            panX = 0f
            panY = 0f
            invalidate()
        }

        /** The window's size inside the photo at zoom 1, in photo pixels. */
        private fun windowSize(bmp: Bitmap): Pair<Float, Float> {
            val w = minOf(bmp.width.toFloat(), bmp.height * windowAspect)
            return w to w / windowAspect
        }

        private fun fit(bmp: Bitmap): PhotoFit.Fit {
            val (ww, wh) = windowSize(bmp)
            return PhotoFit.compute(bmp.width, bmp.height, ww, wh, zoom, panX, panY)
        }

        private fun applyZoom(newZoom: Float) {
            val bmp = photo ?: return
            // Keep the window's center where it is while zooming.
            val (ww, wh) = windowSize(bmp)
            val before = fit(bmp)
            val cx = (-before.dx + ww / 2f) / before.scale
            val cy = (-before.dy + wh / 2f) / before.scale
            zoom = newZoom.coerceIn(1f, 4f)
            moveWindowCenterTo(bmp, cx, cy)
            invalidate()
        }

        private fun moveWindowCenterTo(bmp: Bitmap, cx: Float, cy: Float) {
            val (ww, wh) = windowSize(bmp)
            val scale = PhotoFit.compute(bmp.width, bmp.height, ww, wh, zoom, 0f, 0f).scale
            val regionW = ww / scale
            val regionH = wh / scale
            val left = (cx - regionW / 2f).coerceIn(0f, (bmp.width - regionW).coerceAtLeast(0f))
            val top = (cy - regionH / 2f).coerceIn(0f, (bmp.height - regionH).coerceAtLeast(0f))
            val overflowX = bmp.width * scale - ww
            val overflowY = bmp.height * scale - wh
            panX = if (overflowX > 0.5f) (left * scale / overflowX * 2f - 1f).coerceIn(-1f, 1f) else 0f
            panY = if (overflowY > 0.5f) (top * scale / overflowY * 2f - 1f).coerceIn(-1f, 1f) else 0f
        }

        override fun onDraw(canvas: Canvas) {
            val bmp = photo ?: return
            val pad = 16f * resources.displayMetrics.density
            val s = minOf((width - 2 * pad) / bmp.width, (height - 2 * pad) / bmp.height)
            val dw = bmp.width * s
            val dh = bmp.height * s
            dest.set((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
            canvas.drawBitmap(bmp, null, dest, photoPaint)

            val (ww, wh) = windowSize(bmp)
            val f = fit(bmp)
            val left = -f.dx / f.scale
            val top = -f.dy / f.scale
            window.set(
                dest.left + left * s, dest.top + top * s,
                dest.left + (left + ww / f.scale) * s, dest.top + (top + wh / f.scale) * s,
            )
            // Dim everything outside the window.
            canvas.drawRect(dest.left, dest.top, dest.right, window.top, dimPaint)
            canvas.drawRect(dest.left, window.bottom, dest.right, dest.bottom, dimPaint)
            canvas.drawRect(dest.left, window.top, window.left, window.bottom, dimPaint)
            canvas.drawRect(window.right, window.top, dest.right, window.bottom, dimPaint)
            canvas.drawRect(window, edgePaint)
        }

        override fun performClick(): Boolean = super.performClick()

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val bmp = photo ?: return true
            scaler.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    dragging = true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    // Re-anchor on the remaining finger so the window doesn't jump.
                    val idx = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == 0) 1 else 0
                    if (idx < event.pointerCount) {
                        lastX = event.getX(idx)
                        lastY = event.getY(idx)
                    }
                }
                MotionEvent.ACTION_MOVE -> if (dragging && !scaler.isInProgress && event.pointerCount == 1) {
                    val dw = dest.width()
                    val s = if (dw > 0f) dw / bmp.width else 1f
                    val (ww, wh) = windowSize(bmp)
                    val f = fit(bmp)
                    val cx = (-f.dx + ww / 2f) / f.scale + (event.x - lastX) / s
                    val cy = (-f.dy + wh / 2f) / f.scale + (event.y - lastY) / s
                    moveWindowCenterTo(bmp, cx, cy)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                } else {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }
}
