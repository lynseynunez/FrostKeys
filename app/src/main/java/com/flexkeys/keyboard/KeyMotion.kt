package com.flexkeys.keyboard

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** How a key moves when it is pressed. */
object KeyMotion {

    const val SINK = 0
    const val POP = 1
    const val BOUNCE = 2
    const val TILT = 3
    const val FLOAT = 4
    const val NONE = 5

    private const val BOUNCE_MS = 450f

    /**
     * Transforms [canvas] around the key for [style]. [p] is the press progress (0..1, easing in and out).
     * Returns true while the motion is still running after the key was released, so the caller keeps redrawing.
     */
    fun apply(
        canvas: Canvas, style: Int, key: RectF, pressed: Boolean, pressedAt: Long, releasedAt: Long,
        p: Float, now: Long, viewWidth: Int,
    ): Boolean {
        val cx = key.centerX()
        val cy = key.centerY()
        when (style) {
            NONE -> Unit
            POP -> if (p > 0f) {
                val s = 1f + 0.14f * p
                canvas.scale(s, s, cx, cy)
            }
            BOUNCE -> {
                if (pressed) {
                    val s = 1f - 0.07f * p
                    canvas.scale(s, s, cx, cy)
                } else if (releasedAt > 0L) {
                    val t = (now - releasedAt) / BOUNCE_MS
                    if (t < 1f) {
                        // A damped spring: overshoots, then settles.
                        val s = 1f - 0.07f * exp(-5f * t) * kotlin.math.cos(t * 2.4f * PI.toFloat()) + 0.05f * sin(t * PI.toFloat() * 2f) * (1f - t)
                        canvas.scale(s, s, cx, cy)
                        return true
                    }
                }
            }
            TILT -> if (p > 0f) {
                // Keys on the left tip left, keys on the right tip right.
                val side = if (viewWidth > 0) (cx / viewWidth - 0.5f) * 2f else 0f
                canvas.rotate(side * 7f * p, cx, cy)
                val s = 1f - 0.03f * p
                canvas.scale(s, s, cx, cy)
            }
            FLOAT -> if (p > 0f) {
                canvas.translate(0f, -key.height() * 0.14f * p)
                val s = 1f + 0.06f * p
                canvas.scale(s, s, cx, cy)
            }
            else -> if (p > 0f) {
                val s = 1f - 0.05f * p
                canvas.scale(s, s, cx, cy)
            }
        }
        return false
    }
}
