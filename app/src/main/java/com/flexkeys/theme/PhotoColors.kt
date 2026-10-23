package com.flexkeys.theme

import android.graphics.Bitmap
import android.graphics.Color

/** Reads a photo's overall brightness and its most prominent color, to match the glass to it. */
object PhotoColors {

    class Result(val luma: Float, val accent: Int)

    private const val DEFAULT_ACCENT = 0xFF5B8CFF.toInt()

    fun analyze(source: Bitmap): Result {
        val small = Bitmap.createScaledBitmap(source, 48, 48, true)
        val pixels = IntArray(48 * 48)
        small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        if (small !== source) small.recycle()
        return analyze(pixels)
    }

    /** Pure version working on ARGB pixels (so it can be tested without Android). */
    fun analyze(pixels: IntArray): Result {
        var lumaSum = 0.0
        val hueWeight = DoubleArray(36)
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            lumaSum += 0.2126f * r + 0.7152f * g + 0.0722f * b
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val sat = if (max == 0f) 0f else delta / max
            // Only colorful, reasonably bright pixels vote for the accent.
            if (sat < 0.25f || max < 0.25f) continue
            val hue = when (max) {
                r -> ((g - b) / delta).mod(6f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * 60f
            hueWeight[((hue / 10f).toInt()).coerceIn(0, 35)] += (sat * max).toDouble()
        }
        val luma = (lumaSum / pixels.size).toFloat()
        val total = hueWeight.sum()
        if (total < pixels.size * 0.02) return Result(luma, DEFAULT_ACCENT)
        // Smooth over neighboring hue buckets so one noisy bucket doesn't win.
        var best = 0
        var bestScore = -1.0
        for (i in 0 until 36) {
            val score = hueWeight[(i + 35) % 36] * 0.5 + hueWeight[i] + hueWeight[(i + 1) % 36] * 0.5
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return Result(luma, Color.HSVToColor(floatArrayOf(best * 10f + 5f, 0.62f, 0.98f)))
    }
}
