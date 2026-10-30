package com.flexkeys.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.flexkeys.theme.GlassTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slow animations living in the glass behind the keys: drifting color, flowing light, or rising
 * bubbles. Drawn over the blurred background inside the keyboard sheet.
 */
class GlassAnimation {

    companion object {
        const val OFF = 0
        const val AURORA = 1
        const val LIGHT_FLOW = 2
        const val BUBBLES = 3
        const val STARLIGHT = 4
        const val RAIN = 5
        const val FIREFLIES = 6
        const val WAVES = 7
        private const val DROP_COUNT = 46
        private const val FIREFLY_COUNT = 18
        private const val STAR_COUNT = 64

        private const val BLOBS = 4
        private const val BUBBLE_COUNT = 14
    }

    private class Bubble(var x: Float, var y: Float, val size: Float, val speed: Float, val wobble: Float, val phase: Float)

    private class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val rate: Float, val depth: Float)

    private val stars = List(STAR_COUNT) {
        Star(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat() * 1.3f, Random.nextFloat() * 6.28f,
            0.5f + Random.nextFloat() * 1.6f, 0.3f + Random.nextFloat() * 0.9f)
    }
    private val starPath = android.graphics.Path()

    private class Drop(val x: Float, val phase: Float, val speed: Float, val length: Float, val depth: Float)
    private val drops = List(DROP_COUNT) {
        Drop(Random.nextFloat(), Random.nextFloat(), 0.55f + Random.nextFloat() * 0.7f, 0.05f + Random.nextFloat() * 0.07f, 0.4f + Random.nextFloat() * 0.8f)
    }

    private class Firefly(val cx: Float, val cy: Float, val ax: Float, val ay: Float, val fx: Float, val fy: Float, val phase: Float, val pulse: Float, val size: Float)
    private val fireflies = List(FIREFLY_COUNT) {
        Firefly(0.08f + Random.nextFloat() * 0.84f, 0.1f + Random.nextFloat() * 0.8f, 0.04f + Random.nextFloat() * 0.1f, 0.06f + Random.nextFloat() * 0.18f,
            0.25f + Random.nextFloat() * 0.5f, 0.2f + Random.nextFloat() * 0.5f, Random.nextFloat() * 6.28f, 0.6f + Random.nextFloat() * 1.2f, 0.7f + Random.nextFloat() * 0.8f)
    }
    private val wavePath = android.graphics.Path()
    private val crestPath = android.graphics.Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private var blobShaders: List<Shader> = emptyList()
    private var shaderKey = 0
    private val band = LinearGradient(-0.5f, 0f, 0.5f, 0f,
        intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val bubbles = List(BUBBLE_COUNT) {
        Bubble(Random.nextFloat(), Random.nextFloat(), 0.5f + Random.nextFloat() * 0.8f, 0.04f + Random.nextFloat() * 0.06f,
            0.01f + Random.nextFloat() * 0.03f, Random.nextFloat() * 6.28f)
    }

    /** The animation's own clock in seconds; it advances by speed, so changing the speed never jumps. */
    private var clock = 0f
    private var last = 0L

    /** Advances time. Call once per frame before [draw]. */
    fun tick(now: Long, speed: Float) {
        val dt = if (last == 0L) 0f else ((now - last).coerceIn(0L, 100L)) / 1000f
        last = now
        clock += dt * (0.15f + 1.85f * speed)
    }

    /** Forget the previous frame time, e.g. when the keyboard was hidden for a while. */
    fun pause() {
        last = 0L
    }

    /** [tiltX] and [tiltY] (-1..1) shift the animation with the phone's movement; 0 leaves it alone. */
    fun draw(canvas: Canvas, style: Int, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float = 0f, tiltY: Float = 0f) {
        if (w <= 0f || h <= 0f) return
        when (style) {
            AURORA -> aurora(canvas, w, h, intensity, theme, tiltX, tiltY)
            LIGHT_FLOW -> lightFlow(canvas, w, h, intensity, theme, tiltX, tiltY)
            BUBBLES -> bubbles(canvas, w, h, intensity, theme, tiltX, tiltY)
            STARLIGHT -> starlight(canvas, w, h, intensity, theme, tiltX, tiltY)
            RAIN -> rain(canvas, w, h, intensity, theme, tiltX, tiltY)
            FIREFLIES -> fireflies(canvas, w, h, intensity, theme, tiltX, tiltY)
            WAVES -> waves(canvas, w, h, intensity, theme, tiltX, tiltY)
        }
    }

    private fun aurora(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        val key = theme.gradientColors.contentHashCode() xor theme.accent
        if (blobShaders.isEmpty() || key != shaderKey) {
            shaderKey = key
            val colors = theme.gradientColors.toList() + theme.accent
            blobShaders = List(BLOBS) { i ->
                val c = colors[i % colors.size] or 0xFF000000.toInt()
                RadialGradient(0f, 0f, 1f, intArrayOf(c, (c and 0x00FFFFFF) or 0x66000000, c and 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
        }
        paint.xfermode = if (theme.dark) screen else null
        val radius = maxOf(w * 0.42f, h * 0.9f)
        for (i in 0 until BLOBS) {
            val a = clock * (0.35f + 0.12f * i) + i * 1.9f
            // Each blob sits at its own depth, so tilting slides them by different amounts.
            val depth = 0.5f + 0.35f * i
            val cx = w * (0.5f + 0.42f * sin(a) + 0.30f * tiltX * depth)
            val cy = h * (0.5f + 0.38f * cos(a * 0.8f + i) + 0.35f * tiltY * depth)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(radius, radius)
            paint.shader = blobShaders[i]
            paint.alpha = (255f * (if (theme.dark) 0.42f else 0.30f) * intensity).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
        paint.shader = null
        paint.xfermode = null
    }

    private fun lightFlow(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        paint.shader = band
        for (i in 0 until 3) {
            val travel = (sin(clock * (0.30f + 0.11f * i) + i * 2.1f) * 0.5f + 0.5f + 0.4f * tiltX * (0.6f + 0.3f * i)).coerceIn(-0.2f, 1.2f)
            val width = w * (0.55f + 0.25f * i)
            matrix.setScale(width, width)
            matrix.postRotate((if (i % 2 == 0) 22f else -28f) + tiltY * 14f)
            matrix.postTranslate(-width * 0.3f + travel * (w + width * 0.6f), h * 0.5f)
            band.setLocalMatrix(matrix)
            paint.alpha = (255f * (if (theme.dark) 0.14f else 0.26f) * intensity).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
    }

    /** Small stars that twinkle in and out; bigger ones sit nearer and shift more as the phone tilts. */
    private fun starlight(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        paint.style = Paint.Style.FILL
        val unit = minOf(w, h * 3f) * 0.012f
        for (s in stars) {
            val twinkle = sin(clock * s.rate + s.phase) * 0.5f + 0.5f
            val glow = twinkle * twinkle * twinkle
            if (glow < 0.03f) continue
            val x = ((s.x + 0.035f * tiltX * s.depth + clock * 0.004f * s.depth) % 1f + 1f) % 1f * w
            val y = ((s.y + 0.05f * tiltY * s.depth) % 1f + 1f) % 1f * h
            val r = unit * s.size * (0.55f + 0.45f * glow)
            val a = (255f * glow * intensity * (if (theme.dark) 1f else 0.7f)).toInt().coerceIn(0, 255)
            paint.color = 0x00FFFFFF or (a shl 24)
            starPath.reset()
            starPath.moveTo(x, y - r)
            starPath.quadTo(x, y, x + r, y)
            starPath.quadTo(x, y, x, y + r)
            starPath.quadTo(x, y, x - r, y)
            starPath.quadTo(x, y, x, y - r)
            starPath.close()
            canvas.drawPath(starPath, paint)
            // The larger stars get a soft halo.
            if (s.size > 1.2f) {
                paint.color = 0x00FFFFFF or ((a * 0.18f).toInt() shl 24)
                canvas.drawCircle(x, y, r * 1.8f, paint)
            }
        }
    }

    /** Thin streaks falling at a slant that follows the tilt, with a tiny splash where each reaches the bottom. */
    private fun rain(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val slant = 0.18f + 0.35f * tiltX
        val fall = 1f + 0.25f * tiltY
        for (d in drops) {
            val t = (d.phase + clock * 0.35f * d.speed * fall) % 1f
            val len = h * d.length * 1.6f
            val x = ((d.x + t * slant * 0.5f) % 1f + 1f) % 1f * w
            val y = t * (h + len) - len
            val a = (255f * 0.34f * d.depth * intensity).toInt().coerceIn(0, 255)
            paint.strokeWidth = (0.8f + 1.0f * d.depth) * theme.density
            paint.color = 0x00FFFFFF or (a shl 24)
            canvas.drawLine(x, y, x - len * slant, y - len, paint)
            // A small ring where the drop lands, fading as it spreads.
            val landed = ((t - 0.92f) / 0.08f).coerceIn(0f, 1f)
            if (t > 0.92f) {
                paint.strokeWidth = 1f * theme.density
                paint.color = 0x00FFFFFF or (((1f - landed) * a * 0.9f).toInt().coerceIn(0, 255) shl 24)
                canvas.drawOval(x - landed * 14f * theme.density, h - 3f * theme.density - landed * 3f * theme.density,
                    x + landed * 14f * theme.density, h - 3f * theme.density + landed * 3f * theme.density, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    /** Soft warm lights drifting on slow loops and pulsing on and off. Tilt nudges them like a breeze. */
    private fun fireflies(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        paint.style = Paint.Style.FILL
        val warm = if (theme.dark) 0xE8FF7A else 0xFFB32E
        val unit = minOf(w, h * 3f) * 0.012f
        for (f in fireflies) {
            val x = (f.cx + f.ax * sin(clock * f.fx + f.phase) + 0.06f * tiltX * f.size) * w
            val y = (f.cy + f.ay * cos(clock * f.fy + f.phase * 1.7f) + 0.06f * tiltY * f.size) * h
            val pulse = sin(clock * f.pulse + f.phase) * 0.5f + 0.5f
            val glow = pulse * pulse
            if (glow < 0.04f) continue
            val r = unit * f.size
            val a = 255f * intensity * glow
            for ((i, scale) in floatArrayOf(4.2f, 2.6f, 1.5f, 0.7f).withIndex()) {
                val layerAlpha = a * floatArrayOf(0.08f, 0.14f, 0.28f, 0.85f)[i]
                paint.color = warm or (layerAlpha.toInt().coerceIn(0, 255) shl 24)
                canvas.drawCircle(x, y, r * scale, paint)
            }
        }
    }

    /** Layers of slow water: the surface slides with the phone's tilt and rises and falls as it tips forward and back. */
    private fun waves(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        val layers = 3
        val step = 10f * theme.density
        for (layer in 0 until layers) {
            val depth = 1f - layer * 0.28f
            val level = h * (0.62f + 0.12f * layer - 0.10f * tiltY)
            val amp = h * (0.05f + 0.012f * layer)
            val k = (2f * 3.1416f / w) * (1.3f + 0.5f * layer)
            val phase = clock * (0.9f + 0.35f * layer) * (if (layer % 2 == 0) 1f else -1f) + tiltX * 2.2f * depth + layer * 1.3f
            wavePath.reset()
            crestPath.reset()
            wavePath.moveTo(0f, h)
            var x = 0f
            while (x <= w + step) {
                val y = level + amp * sin(k * x + phase) + amp * 0.35f * sin(k * 2.3f * x - phase * 0.7f)
                wavePath.lineTo(x, y)
                if (x == 0f) crestPath.moveTo(x, y) else crestPath.lineTo(x, y)
                x += step
            }
            wavePath.lineTo(w + step, h)
            wavePath.close()
            paint.style = Paint.Style.FILL
            paint.color = 0x00FFFFFF or ((255f * (if (theme.dark) 0.07f else 0.14f) * intensity * depth).toInt().coerceIn(0, 255) shl 24)
            canvas.drawPath(wavePath, paint)
            // A brighter line along each crest.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * theme.density
            paint.color = 0x00FFFFFF or ((255f * 0.22f * intensity * depth).toInt().coerceIn(0, 255) shl 24)
            canvas.drawPath(crestPath, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun bubbles(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme, tiltX: Float, tiltY: Float) {
        paint.style = Paint.Style.STROKE
        val unit = minOf(w, h * 3f)
        for (b in bubbles) {
            // Rise at the bubble's own speed and wrap around at the top.
            val rise = (b.y - clock * b.speed).mod(1.15f)
            val y = h * (1.05f - rise) - tiltY * h * 0.12f * b.size
            val x = w * (b.x + sin(clock * 0.6f + b.phase) * b.wobble + tiltX * 0.18f * b.size)
            val r = unit * 0.035f * b.size
            val fade = (1f - kotlin.math.abs(rise - 0.575f) / 0.575f).coerceIn(0f, 1f)
            val a = 255f * fade * intensity
            paint.strokeWidth = 1.4f * theme.density
            paint.color = 0x00FFFFFF or ((a * 0.55f).toInt().coerceIn(0, 255) shl 24)
            canvas.drawCircle(x, y, r, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0x00FFFFFF or ((a * 0.12f).toInt().coerceIn(0, 255) shl 24)
            canvas.drawCircle(x, y, r, paint)
            paint.color = 0x00FFFFFF or ((a * 0.7f).toInt().coerceIn(0, 255) shl 24)
            canvas.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.2f, paint)
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL
    }
}
