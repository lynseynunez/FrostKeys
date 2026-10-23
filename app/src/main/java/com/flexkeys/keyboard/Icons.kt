package com.flexkeys.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/** Simple stroked icons drawn with paths so they look identical on every device font. */
enum class Icon {
    BACKSPACE, SHIFT, SHIFT_ON, CAPS_LOCK, ENTER, ARROW_RIGHT, ARROW_LEFT, SEARCH, SEND, DONE,
    EMOJI, CLIPBOARD, SETTINGS, KEYBOARD, PIN, PIN_ON, TRASH, CLOCK, CLOSE,
    MENU, HIDE, GLOBE, GIF, TRANSLATE, SWAP, MIC, PHRASES, FANCY, AI, SLIDERS,
}

object Icons {

    private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val path = Path()
    private val oval = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Draws [icon] centered on (cx, cy) inside a square of side [size]. */
    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.085f
        fun x(v: Float) = cx + v * s
        fun y(v: Float) = cy + v * s
        fun poly(vararg pts: Float, close: Boolean) {
            path.reset()
            path.moveTo(x(pts[0]), y(pts[1]))
            var i = 2
            while (i < pts.size) {
                path.lineTo(x(pts[i]), y(pts[i + 1]))
                i += 2
            }
            if (close) path.close()
            canvas.drawPath(path, paint)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x(x1), y(y1), x(x2), y(y2), paint)
        fun circle(ox: Float, oy: Float, r: Float) = canvas.drawCircle(x(ox), y(oy), r * s, paint)
        fun filled(block: () -> Unit) {
            paint.style = Paint.Style.FILL_AND_STROKE
            block()
            paint.style = Paint.Style.STROKE
        }

        when (icon) {
            Icon.BACKSPACE -> {
                poly(-0.48f, 0f, -0.22f, -0.3f, 0.46f, -0.3f, 0.46f, 0.3f, -0.22f, 0.3f, close = true)
                line(-0.02f, -0.12f, 0.22f, 0.12f)
                line(0.22f, -0.12f, -0.02f, 0.12f)
            }
            Icon.SHIFT, Icon.SHIFT_ON, Icon.CAPS_LOCK -> {
                val pts = floatArrayOf(0f, -0.42f, 0.42f, 0.02f, 0.18f, 0.02f, 0.18f, 0.32f, -0.18f, 0.32f, -0.18f, 0.02f, -0.42f, 0.02f)
                if (icon == Icon.SHIFT) poly(*pts, close = true) else filled { poly(*pts, close = true) }
                if (icon == Icon.CAPS_LOCK) line(-0.18f, 0.46f, 0.18f, 0.46f)
            }
            Icon.ENTER -> {
                poly(0.36f, -0.34f, 0.36f, 0.1f, -0.34f, 0.1f, close = false)
                poly(-0.14f, -0.1f, -0.36f, 0.1f, -0.14f, 0.3f, close = false)
            }
            Icon.ARROW_RIGHT -> {
                line(-0.38f, 0f, 0.36f, 0f)
                poly(0.1f, -0.26f, 0.38f, 0f, 0.1f, 0.26f, close = false)
            }
            Icon.ARROW_LEFT -> {
                line(0.38f, 0f, -0.36f, 0f)
                poly(-0.1f, -0.26f, -0.38f, 0f, -0.1f, 0.26f, close = false)
            }
            Icon.GIF -> {
                // A rounded tag with "GIF" written inside in rounded strokes.
                oval.set(x(-0.45f), y(-0.31f), x(0.45f), y(0.31f))
                canvas.drawRoundRect(oval, s * 0.17f, s * 0.17f, paint)
                val old = paint.strokeWidth
                paint.strokeWidth = s * 0.075f
                val r = 0.125f
                val gx = -0.155f
                oval.set(x(gx - r), y(-r), x(gx + r), y(r))
                canvas.drawArc(oval, -45f, -270f, false, paint)
                line(gx + 0.01f, 0.01f, gx + r, 0.01f)
                line(gx + r, 0.01f, gx + r, 0.085f)
                line(0.045f, -r, 0.045f, r)
                line(0.125f, -r, 0.125f, r)
                line(0.125f, -r, 0.275f, -r)
                line(0.125f, -0.005f, 0.245f, -0.005f)
                paint.strokeWidth = old
            }
            Icon.TRANSLATE -> {
                // An outlined tile with a character from another script, behind a solid tile with an A cut out of it.
                val layer = canvas.saveLayer(RectF(cx - s, cy - s, cx + s, cy + s), null)
                oval.set(x(-0.46f), y(-0.44f), x(0.08f), y(0.10f))
                canvas.drawRoundRect(oval, s * 0.07f, s * 0.07f, paint)
                val oldSize = paint.textSize
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = s * 0.30f
                canvas.drawText("文", x(-0.20f), y(-0.19f) + s * 0.30f * 0.35f, paint)
                // A thin gap around the front tile so the two tiles read as separate.
                paint.xfermode = clearMode
                oval.set(x(-0.095f), y(-0.135f), x(0.495f), y(0.475f))
                canvas.drawRoundRect(oval, s * 0.1f, s * 0.1f, paint)
                paint.xfermode = null
                oval.set(x(-0.06f), y(-0.10f), x(0.46f), y(0.44f))
                canvas.drawRoundRect(oval, s * 0.08f, s * 0.08f, paint)
                paint.xfermode = clearMode
                paint.isFakeBoldText = true
                paint.textSize = s * 0.34f
                canvas.drawText("A", x(0.20f), y(0.17f) + s * 0.34f * 0.36f, paint)
                paint.isFakeBoldText = false
                paint.xfermode = null
                paint.textSize = oldSize
                paint.style = Paint.Style.STROKE
                canvas.restoreToCount(layer)
            }
            Icon.SLIDERS -> {
                // Three horizontal tracks with a knob each.
                line(-0.44f, -0.28f, 0.44f, -0.28f)
                line(-0.44f, 0f, 0.44f, 0f)
                line(-0.44f, 0.28f, 0.44f, 0.28f)
                val old = paint.style
                paint.style = Paint.Style.FILL
                circle(0.12f, -0.28f, 0.1f)
                circle(-0.18f, 0f, 0.1f)
                circle(0.22f, 0.28f, 0.1f)
                paint.style = old
            }
            Icon.AI -> {
                // A large four-point sparkle with a small one beside it.
                fun sparkle(cx0: Float, cy0: Float, r: Float) {
                    path.reset()
                    path.moveTo(x(cx0), y(cy0 - r))
                    path.quadTo(x(cx0), y(cy0), x(cx0 + r), y(cy0))
                    path.quadTo(x(cx0), y(cy0), x(cx0), y(cy0 + r))
                    path.quadTo(x(cx0), y(cy0), x(cx0 - r), y(cy0))
                    path.quadTo(x(cx0), y(cy0), x(cx0), y(cy0 - r))
                    path.close()
                    canvas.drawPath(path, paint)
                }
                sparkle(-0.08f, 0.08f, 0.40f)
                sparkle(0.32f, -0.32f, 0.17f)
            }
            Icon.FANCY -> {
                // A big and a small "a" side by side, drawn as strokes.
                val old = paint.strokeWidth
                paint.strokeWidth = s * 0.085f
                oval.set(x(-0.46f), y(-0.12f), x(-0.02f), y(0.40f))
                canvas.drawArc(oval, 0f, 360f, false, paint)
                line(-0.02f, -0.12f, -0.02f, 0.40f)
                poly(0.14f, 0.40f, 0.30f, -0.40f, 0.46f, 0.40f, close = false)
                line(0.19f, 0.14f, 0.41f, 0.14f)
                paint.strokeWidth = old
            }
            Icon.PHRASES -> {
                // A speech bubble with two lines of text.
                oval.set(x(-0.46f), y(-0.40f), x(0.46f), y(0.22f))
                canvas.drawRoundRect(oval, s * 0.14f, s * 0.14f, paint)
                poly(-0.22f, 0.22f, -0.28f, 0.44f, 0f, 0.22f, close = false)
                line(-0.26f, -0.20f, 0.26f, -0.20f)
                line(-0.26f, 0f, 0.08f, 0f)
            }
            Icon.MIC -> {
                oval.set(x(-0.17f), y(-0.46f), x(0.17f), y(0.10f))
                canvas.drawRoundRect(oval, s * 0.17f, s * 0.17f, paint)
                oval.set(x(-0.36f), y(-0.24f), x(0.36f), y(0.30f))
                canvas.drawArc(oval, 0f, 180f, false, paint)
                line(0f, 0.30f, 0f, 0.46f)
                line(-0.16f, 0.46f, 0.16f, 0.46f)
            }
            Icon.SWAP -> {
                line(-0.38f, -0.16f, 0.36f, -0.16f)
                poly(0.18f, -0.32f, 0.38f, -0.16f, 0.18f, 0f, close = false)
                line(0.38f, 0.16f, -0.36f, 0.16f)
                poly(-0.18f, 0f, -0.38f, 0.16f, -0.18f, 0.32f, close = false)
            }
            Icon.SEARCH -> {
                circle(-0.08f, -0.08f, 0.27f)
                line(0.12f, 0.12f, 0.38f, 0.38f)
            }
            Icon.SEND -> {
                poly(-0.38f, -0.36f, 0.42f, 0f, -0.38f, 0.36f, -0.26f, 0f, close = true)
                line(-0.26f, 0f, 0.1f, 0f)
            }
            Icon.DONE -> poly(-0.36f, 0.02f, -0.1f, 0.28f, 0.4f, -0.28f, close = false)
            Icon.EMOJI -> {
                circle(0f, 0f, 0.4f)
                filled {
                    circle(-0.14f, -0.1f, 0.035f)
                    circle(0.14f, -0.1f, 0.035f)
                }
                oval.set(x(-0.2f), y(-0.04f), x(0.2f), y(0.24f))
                canvas.drawArc(oval, 25f, 130f, false, paint)
            }
            Icon.CLIPBOARD -> {
                oval.set(x(-0.3f), y(-0.34f), x(0.3f), y(0.44f))
                canvas.drawRoundRect(oval, s * 0.06f, s * 0.06f, paint)
                oval.set(x(-0.13f), y(-0.44f), x(0.13f), y(-0.26f))
                canvas.drawRoundRect(oval, s * 0.04f, s * 0.04f, paint)
                line(-0.14f, -0.04f, 0.14f, -0.04f)
                line(-0.14f, 0.12f, 0.14f, 0.12f)
                line(-0.14f, 0.28f, 0.04f, 0.28f)
            }
            Icon.SETTINGS -> {
                // Three meshing gears.
                val old = paint.strokeWidth
                fun gear(gx: Float, gy: Float) {
                    val r = 0.2f
                    paint.strokeWidth = r * 0.46f * s
                    circle(gx, gy, r * 0.62f)
                    paint.strokeWidth = r * 0.3f * s
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0)
                        val c = Math.cos(a).toFloat()
                        val sn = Math.sin(a).toFloat()
                        line(gx + c * r * 0.8f, gy + sn * r * 0.8f, gx + c * r, gy + sn * r)
                    }
                }
                gear(0.21f, -0.21f)
                gear(-0.21f, -0.12f)
                gear(0.08f, 0.21f)
                paint.strokeWidth = old
            }
            Icon.KEYBOARD -> {
                oval.set(x(-0.46f), y(-0.3f), x(0.46f), y(0.3f))
                canvas.drawRoundRect(oval, s * 0.07f, s * 0.07f, paint)
                val old = paint.strokeWidth
                paint.strokeWidth = s * 0.1f
                for (row in 0..1) for (col in 0..3) {
                    canvas.drawPoint(x(-0.27f + col * 0.18f), y(-0.12f + row * 0.13f), paint)
                }
                paint.strokeWidth = old
                line(-0.18f, 0.16f, 0.18f, 0.16f)
            }
            Icon.PIN, Icon.PIN_ON -> {
                val pts = floatArrayOf(-0.26f, 0.1f, 0.26f, 0.1f, 0.14f, -0.08f, 0.14f, -0.4f, -0.14f, -0.4f, -0.14f, -0.08f)
                if (icon == Icon.PIN_ON) filled { poly(*pts, close = true) } else poly(*pts, close = true)
                line(0f, 0.1f, 0f, 0.44f)
            }
            Icon.TRASH -> {
                line(-0.36f, -0.28f, 0.36f, -0.28f)
                poly(-0.12f, -0.28f, -0.12f, -0.4f, 0.12f, -0.4f, 0.12f, -0.28f, close = false)
                poly(-0.27f, -0.28f, -0.21f, 0.42f, 0.21f, 0.42f, 0.27f, -0.28f, close = false)
                line(-0.07f, -0.1f, -0.07f, 0.26f)
                line(0.07f, -0.1f, 0.07f, 0.26f)
            }
            Icon.CLOCK -> {
                circle(0f, 0f, 0.4f)
                poly(0f, -0.24f, 0f, 0f, 0.18f, 0.12f, close = false)
            }
            Icon.MENU -> filled {
                for (dx in listOf(-0.17f, 0.17f)) for (dy in listOf(-0.17f, 0.17f)) circle(dx, dy, 0.07f)
            }
            Icon.GLOBE -> {
                circle(0f, 0f, 0.42f)
                oval.set(x(-0.18f), y(-0.42f), x(0.18f), y(0.42f))
                canvas.drawOval(oval, paint)
                line(-0.42f, 0f, 0.42f, 0f)
                line(-0.36f, -0.21f, 0.36f, -0.21f)
                line(-0.36f, 0.21f, 0.36f, 0.21f)
            }
            Icon.HIDE -> poly(-0.3f, -0.1f, 0f, 0.18f, 0.3f, -0.1f, close = false)
            Icon.CLOSE -> {
                line(-0.28f, -0.28f, 0.28f, 0.28f)
                line(0.28f, -0.28f, -0.28f, 0.28f)
            }
        }
    }
}
