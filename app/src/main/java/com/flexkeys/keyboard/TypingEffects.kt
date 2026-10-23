package com.flexkeys.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small animations drawn over the keys while typing: sparkles, ripples, or warp-speed streaks that
 * appear when typing fast. Pure drawing code; the keyboard view feeds it key presses.
 */
class TypingEffects(private val density: Float) {

    companion object {
        const val OFF = 0
        const val SPARKLES = 1
        const val RIPPLE = 2
        const val WARP = 3

        private const val MAX_PARTICLES = 70
        private const val RIPPLE_MS = 480L
        /** Key presses inside this window decide how fast the user is typing. */
        private const val SPEED_WINDOW_MS = 900L
        private const val WARP_LINES = 30
    }

    private class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val life: Long, val size: Float, val tinted: Boolean)
    private class Ring(val x: Float, val y: Float, val born: Long, val reach: Float)

    private val sparks = ArrayList<Spark>()
    private val rings = ArrayList<Ring>()
    private val presses = ArrayDeque<Long>()
    private var warpLevel = 0f
    private var lastFrame = 0L
    private val lineAngles = FloatArray(WARP_LINES) { Random.nextFloat() * 2f * PI.toFloat() }
    private val linePhase = FloatArray(WARP_LINES) { Random.nextFloat() }
    private val lineSpeed = FloatArray(WARP_LINES) { 0.6f + Random.nextFloat() * 0.8f }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Path()

    /** 0..1: how strong the effects are (fewer, smaller, fainter at low values). */
    var strength = 0.5f
    private val fade get() = 0.3f + 0.7f * strength

    /** True while something is still animating, so the caller should keep redrawing. */
    val active: Boolean get() = sparks.isNotEmpty() || rings.isNotEmpty() || warpLevel > 0.01f

    fun onPress(style: Int, x: Float, y: Float, reach: Float, now: Long) {
        when (style) {
            SPARKLES -> repeat(1 + (4f * strength).roundToInt()) {
                if (sparks.size >= MAX_PARTICLES) sparks.removeAt(0)
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val speed = (40f + Random.nextFloat() * 80f) * density * (0.5f + 0.5f * strength)
                sparks.add(Spark(
                    x, y, cos(angle) * speed, sin(angle) * speed - 25f * density, now,
                    420L + Random.nextLong(300L), (2.2f + Random.nextFloat() * 2.8f) * density * (0.6f + 0.4f * strength), Random.nextInt(3) == 0,
                ))
            }
            RIPPLE -> {
                if (rings.size >= 8) rings.removeAt(0)
                rings.add(Ring(x, y, now, reach))
            }
            WARP -> {
                presses.addLast(now)
                while (presses.isNotEmpty() && now - presses.first() > SPEED_WINDOW_MS) presses.removeFirst()
            }
        }
    }

    /** Draws everything for time [now] over an area of [w] x [h]. [accent] tints some of the effects. */
    fun draw(canvas: Canvas, now: Long, w: Float, h: Float, accent: Int) {
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame).coerceIn(0L, 64L)) / 1000f
        lastFrame = now

        sparks.removeAll { now - it.born > it.life }
        for (s in sparks) {
            val t = (now - s.born).toFloat() / s.life
            val sec = (now - s.born) / 1000f
            val px = s.x + s.vx * sec
            val py = s.y + s.vy * sec + 60f * density * sec * sec
            paint.style = Paint.Style.FILL
            val base = if (s.tinted) accent or 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            paint.color = (base and 0x00FFFFFF) or (((1f - t) * 230f * fade).toInt().coerceIn(0, 255) shl 24)
            drawStar(canvas, px, py, s.size * (1f - 0.4f * t))
        }

        rings.removeAll { now - it.born > RIPPLE_MS }
        for (r in rings) {
            val t = (now - r.born).toFloat() / RIPPLE_MS
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = (0x00FFFFFF) or (((1f - t) * 150f * fade).toInt().coerceIn(0, 255) shl 24)
            canvas.drawCircle(r.x, r.y, r.reach * (0.6f + 0.4f * strength) * (0.25f + 0.85f * t), paint)
        }

        // Typing fast (about 5 presses within the window) fades in streaks flying outward from the middle.
        while (presses.isNotEmpty() && now - presses.first() > SPEED_WINDOW_MS) presses.removeFirst()
        val target = ((presses.size - 3) / 4f).coerceIn(0f, 1f)
        warpLevel = if (target > warpLevel) minOf(target, warpLevel + 5f * dt) else maxOf(target, warpLevel - 1.4f * dt)
        if (warpLevel > 0.01f) drawWarp(canvas, now, w, h)
    }

    private fun drawWarp(canvas: Canvas, now: Long, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val max = hypot(cx, cy)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.6f * density
        val time = now / 1000f
        for (i in 0 until WARP_LINES) {
            val p = (linePhase[i] + time * lineSpeed[i]) % 1f
            val r0 = max * p * p
            val r1 = r0 + max * 0.12f * (0.3f + p)
            val a = (warpLevel * 0.45f * fade * (1f - p * 0.5f) * 255f).toInt().coerceIn(0, 255)
            paint.color = (0x00FFFFFF) or (a shl 24)
            val c = cos(lineAngles[i])
            val s = sin(lineAngles[i])
            canvas.drawLine(cx + c * r0, cy + s * r0, cx + c * r1, cy + s * r1, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** A four-point sparkle with concave sides. */
    private fun drawStar(canvas: Canvas, x: Float, y: Float, r: Float) {
        star.reset()
        star.moveTo(x, y - r)
        star.quadTo(x, y, x + r, y)
        star.quadTo(x, y, x, y + r)
        star.quadTo(x, y, x - r, y)
        star.quadTo(x, y, x, y - r)
        star.close()
        canvas.drawPath(star, paint)
    }
}
