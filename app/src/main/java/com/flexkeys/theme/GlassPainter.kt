package com.flexkeys.theme

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Draws "liquid glass" panels: blurred backdrop clipped to a rounded rect, a translucent
 * tint, a top highlight and a soft rim that is bright at the top and fades at the bottom.
 */
class GlassPainter(var theme: GlassTheme) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val local = RectF()

    // Gradients are built in panel-local coordinates (0..height) and cached per height, so a
    // whole keyboard of same-height keys shares a couple of shader objects.
    private var cacheTheme: GlassTheme? = null
    private val highlightShaders = HashMap<Int, Shader>()
    private val borderShaders = HashMap<Int, Shader>()

    /**
     * @param ox x of [rect]'s coordinate origin within the backdrop
     * @param oy y of [rect]'s coordinate origin within the backdrop
     * @param pressed 0..1 press animation progress
     */
    fun draw(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        backdrop: GlassBackdrop?,
        ox: Float,
        oy: Float,
        fill: Int,
        pressed: Float = 0f,
        rim: Boolean = true,
        gradient: Shader? = null,
        gradientAlpha: Float = 0f,
    ) {
        val t = theme
        if (cacheTheme !== t) {
            highlightShaders.clear()
            borderShaders.clear()
            cacheTheme = t
        }
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        val r = radius.coerceAtMost(minOf(w, h) / 2f)

        canvas.save()
        canvas.translate(rect.left, rect.top)
        if (pressed > 0f) {
            val s = 1f - 0.05f * pressed
            canvas.scale(s, s, w / 2f, h / 2f)
        }
        local.set(0f, 0f, w, h)

        // Soft contact shadow just below the panel.
        if (rim) {
            fillPaint.color = t.shadow
            local.offset(0f, t.dp(1.2f))
            canvas.drawRoundRect(local, r, r, fillPaint)
            local.offset(0f, -t.dp(1.2f))
        }

        // Frosted backdrop.
        if (backdrop != null) {
            canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(local, r, r, Path.Direction.CW)
            canvas.clipPath(clipPath)
            backdrop.drawBlurred(canvas, ox + rect.left, oy + rect.top)
            canvas.restore()
        }

        // Tint.
        fillPaint.color = fill
        canvas.drawRoundRect(local, r, r, fillPaint)
        // Slice of the keyboard-wide gradient that lies under this panel.
        if (gradient != null && gradientAlpha > 0f) {
            // Draw in backdrop coordinates (shift the canvas) so the shared shader is never modified.
            val gx = ox + rect.left
            val gy = oy + rect.top
            canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(local, r, r, Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.translate(-gx, -gy)
            gradientPaint.shader = gradient
            gradientPaint.alpha = (gradientAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(gx, gy, gx + w, gy + h, gradientPaint)
            canvas.restore()
        }
        if (pressed > 0f) {
            fillPaint.color = GlassTheme.withAlpha(t.pressedOverlay, (t.pressedOverlay ushr 24) / 255f * pressed)
            canvas.drawRoundRect(local, r, r, fillPaint)
        }

        // Specular highlight on the upper half.
        val hk = h.toInt()
        highlightPaint.shader = highlightShaders.getOrPut(hk) {
            LinearGradient(0f, 0f, 0f, h, intArrayOf(t.highlight, t.highlight and 0x00FFFFFF), floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(local, r, r, highlightPaint)

        // Rim light.
        if (rim) {
            val stroke = t.dp(1f)
            borderPaint.strokeWidth = stroke
            borderPaint.shader = borderShaders.getOrPut(hk) {
                LinearGradient(0f, 0f, 0f, h, t.borderTop, t.borderBottom, Shader.TileMode.CLAMP)
            }
            local.inset(stroke / 2f, stroke / 2f)
            canvas.drawRoundRect(local, r, r, borderPaint)
        }
        canvas.restore()
    }
}
