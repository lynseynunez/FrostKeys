package com.flexkeys.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Shader
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
        const val BLOOM = 4
        const val INK = 5
        const val FIRECRACKER = 6
        const val PAINT = 7
        const val SPOTLIGHT = 8
        const val PETALS = 9

        private const val MAX_PARTICLES = 70
        private const val RIPPLE_MS = 480L
        /** Key presses inside this window decide how fast the user is typing. */
        private const val SPEED_WINDOW_MS = 900L
        private const val WARP_LINES = 30
    }

    private class Spark(
        val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val life: Long, val size: Float,
        val tinted: Boolean, val color: Int = 0, val trail: Boolean = false,
        /** A tumbling sakura petal: starting angle, spin in degrees a second. */
        val petal: Boolean = false, val angle: Float = 0f, val spin: Float = 0f,
    )
    /** A soft colored glow that spreads out from a key and fades. */
    private class Bloom(val x: Float, val y: Float, val born: Long, val life: Long, val reach: Float, val shader: RadialGradient)
    /** A paint dot that appears where it lands and fades. */
    private class Dot(val x: Float, val y: Float, val born: Long, val life: Long, val radius: Float, val color: Int)
    private class Ring(val x: Float, val y: Float, val born: Long, val reach: Float)

    private val sparks = ArrayList<Spark>()
    private val rings = ArrayList<Ring>()
    private val blooms = ArrayList<Bloom>()
    private val dots = ArrayList<Dot>()
    private val hsv = FloatArray(3)

    /** The theme's accent color, used when the spotlight uses only the accent. Set before each press. */
    var accent: Int = 0xFF5B8CFF.toInt()


    /** A bright, soft color with a random hue. */
    private fun vivid(hue: Float = Random.nextFloat() * 360f, sat: Float = 0.55f): Int {
        hsv[0] = hue
        hsv[1] = sat
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    private fun bloom(x: Float, y: Float, reach: Float, life: Long, color: Int, now: Long) {
        if (blooms.size >= 10) blooms.removeAt(0)
        val shader = RadialGradient(0f, 0f, 1f, intArrayOf(color or 0xFF000000.toInt(), (color and 0x00FFFFFF) or 0x66000000, color and 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        blooms.add(Bloom(x, y, now, life, reach, shader))
    }
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
    val active: Boolean get() = sparks.isNotEmpty() || rings.isNotEmpty() || blooms.isNotEmpty() || dots.isNotEmpty() || warpLevel > 0.01f

    /** The colors the spotlight uses, and whether they change on every press or follow the key's place on the keyboard. */
    var spotlightColors: IntArray = intArrayOf(0xFF4D7CFF.toInt(), 0xFF9B5CFF.toInt(), 0xFFFF5DB1.toInt())
    var spotlightCycle = false
    private var pressCount = 0

    /** [xFraction] is where the key sits across the keyboard, 0 (left) to 1 (right). */
    fun onPress(style: Int, x: Float, y: Float, reach: Float, now: Long, xFraction: Float = 0.5f) {
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
            PETALS -> repeat(2 + (3f * strength).roundToInt()) {
                if (sparks.size >= MAX_PARTICLES) sparks.removeAt(0)
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val speed = (20f + Random.nextFloat() * 55f) * density * (0.5f + 0.5f * strength)
                sparks.add(Spark(
                    x, y, cos(angle) * speed, sin(angle) * speed - 40f * density, now, 900L + Random.nextLong(700L),
                    (4.2f + Random.nextFloat() * 3.2f) * density * (0.6f + 0.4f * strength), false,
                    com.flexkeys.ui.Petal.color(true, Random.nextFloat()), petal = true,
                    angle = Random.nextFloat() * 360f, spin = (Random.nextFloat() - 0.5f) * 420f,
                ))
            }
            RIPPLE -> {
                if (rings.size >= 8) rings.removeAt(0)
                rings.add(Ring(x, y, now, reach))
            }
            SPOTLIGHT -> {
                // A wide soft pool of the accent color with a small bright core, fading slowly so it
                // trails behind the finger from key to key.
                val soft = if (spotlightCycle) {
                    pressCount++
                    // Step through the colors a press at a time, blending so the change is smooth.
                    SpotlightPalettes.colorAt(spotlightColors, (pressCount % 8) / 7f)
                } else {
                    SpotlightPalettes.colorAt(spotlightColors, xFraction)
                }
                bloom(x, y, reach * 2.6f * (0.6f + 0.4f * strength), 900L, soft, now)
                bloom(x, y, reach * 0.9f * (0.6f + 0.4f * strength), 500L, 0xFFFFFFFF.toInt(), now)
            }
            BLOOM -> bloom(x, y, reach * 1.9f * (0.6f + 0.4f * strength), 800L, vivid(), now)
            INK -> {
                val hue = Random.nextFloat() * 360f
                for (i in 0 until 3) {
                    val a = Random.nextFloat() * 2f * PI.toFloat()
                    val d = reach * (0.2f + Random.nextFloat() * 0.6f)
                    bloom(x + cos(a) * d, y + sin(a) * d, reach * (1.0f + Random.nextFloat() * 0.6f) * (0.6f + 0.4f * strength),
                        900L + Random.nextLong(300L), vivid(hue + i * 55f), now)
                }
            }
            FIRECRACKER -> {
                val hue = Random.nextFloat() * 360f
                repeat(4 + (8f * strength).roundToInt()) {
                    if (sparks.size >= MAX_PARTICLES) sparks.removeAt(0)
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val speed = (110f + Random.nextFloat() * 130f) * density * (0.6f + 0.4f * strength)
                    sparks.add(Spark(x, y, cos(angle) * speed, sin(angle) * speed, now, 450L + Random.nextLong(350L),
                        2.2f * density, false, vivid(hue + Random.nextFloat() * 70f, 0.7f), trail = true))
                }
            }
            PAINT -> {
                val hue = Random.nextFloat() * 360f
                repeat(3 + (6f * strength).roundToInt()) {
                    if (dots.size >= 40) dots.removeAt(0)
                    val a = Random.nextFloat() * 2f * PI.toFloat()
                    val d = reach * 1.5f * Random.nextFloat()
                    dots.add(Dot(x + cos(a) * d, y + sin(a) * d, now, 900L,
                        (2.5f + Random.nextFloat() * 6f) * density * (0.6f + 0.4f * strength), vivid(hue + Random.nextFloat() * 50f, 0.65f)))
                }
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
            val base = when {
                s.color != 0 -> s.color
                s.tinted -> accent or 0xFF000000.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val alpha = (((1f - t) * 230f * fade).toInt().coerceIn(0, 255) shl 24)
            paint.color = (base and 0x00FFFFFF) or alpha
            if (s.petal) {
                // A petal drifts down slowly and spins; it flutters edge-on now and then.
                val flip = 0.35f + 0.65f * kotlin.math.abs(cos(sec * 5f + s.angle))
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(s.angle + s.spin * sec)
                canvas.scale(s.size * flip, s.size * 1.15f)
                canvas.drawPath(com.flexkeys.ui.Petal.path, paint)
                canvas.restore()
            } else if (s.trail) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s.size
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(px, py, px - s.vx * 0.06f, py - (s.vy + 120f * density * sec) * 0.06f, paint)
                paint.strokeCap = Paint.Cap.BUTT
            } else {
                drawStar(canvas, px, py, s.size * (1f - 0.4f * t))
            }
        }

        blooms.removeAll { now - it.born > it.life }
        paint.style = Paint.Style.FILL
        for (b in blooms) {
            val t = (now - b.born).toFloat() / b.life
            val r = b.reach * (0.35f + 0.65f * (1f - (1f - t) * (1f - t)))
            canvas.save()
            canvas.translate(b.x, b.y)
            canvas.scale(r, r)
            paint.shader = b.shader
            paint.alpha = ((1f - t) * 255f * (0.35f + 0.65f * strength)).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, 1f, paint)
            paint.shader = null
            canvas.restore()
        }

        dots.removeAll { now - it.born > it.life }
        paint.style = Paint.Style.FILL
        for (d in dots) {
            val t = (now - d.born).toFloat() / d.life
            val grow = if (t < 0.12f) t / 0.12f else 1f
            paint.color = (d.color and 0x00FFFFFF) or (((1f - t) * 220f * fade).toInt().coerceIn(0, 255) shl 24)
            canvas.drawCircle(d.x, d.y, d.radius * grow, paint)
            canvas.drawCircle(d.x + d.radius * 1.5f, d.y - d.radius * 0.8f, d.radius * 0.35f * grow, paint)
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
