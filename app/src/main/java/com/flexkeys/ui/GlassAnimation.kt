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

        private const val BLOBS = 4
        private const val BUBBLE_COUNT = 14
    }

    private class Bubble(var x: Float, var y: Float, val size: Float, val speed: Float, val wobble: Float, val phase: Float)

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

    fun draw(canvas: Canvas, style: Int, w: Float, h: Float, intensity: Float, theme: GlassTheme) {
        if (w <= 0f || h <= 0f) return
        when (style) {
            AURORA -> aurora(canvas, w, h, intensity, theme)
            LIGHT_FLOW -> lightFlow(canvas, w, h, intensity, theme)
            BUBBLES -> bubbles(canvas, w, h, intensity, theme)
        }
    }

    private fun aurora(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme) {
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
            val cx = w * (0.5f + 0.42f * sin(a))
            val cy = h * (0.5f + 0.38f * cos(a * 0.8f + i))
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

    private fun lightFlow(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme) {
        paint.shader = band
        for (i in 0 until 3) {
            val travel = (sin(clock * (0.30f + 0.11f * i) + i * 2.1f) * 0.5f + 0.5f)
            val width = w * (0.55f + 0.25f * i)
            matrix.setScale(width, width)
            matrix.postRotate(if (i % 2 == 0) 22f else -28f)
            matrix.postTranslate(-width * 0.3f + travel * (w + width * 0.6f), h * 0.5f)
            band.setLocalMatrix(matrix)
            paint.alpha = (255f * (if (theme.dark) 0.14f else 0.26f) * intensity).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
    }

    private fun bubbles(canvas: Canvas, w: Float, h: Float, intensity: Float, theme: GlassTheme) {
        paint.style = Paint.Style.STROKE
        val unit = minOf(w, h * 3f)
        for (b in bubbles) {
            // Rise at the bubble's own speed and wrap around at the top.
            val rise = (b.y - clock * b.speed).mod(1.15f)
            val y = h * (1.05f - rise)
            val x = w * (b.x + sin(clock * 0.6f + b.phase) * b.wobble)
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
