package com.flexkeys.theme

import android.graphics.Bitmap

/** Three-pass box blur, a cheap Gaussian approximation for the pre-Android 12 fallback. */
object BoxBlur {

    fun blur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (radius < 1 || w < 2 || h < 2) return
        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        repeat(3) {
            pass(src, tmp, w, h, radius, horizontal = true)
            pass(tmp, src, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun pass(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            var a = 0; var red = 0; var g = 0; var b = 0
            fun index(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            for (i in -r..r) {
                val p = input[index(i)]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                output[index(i)] = ((a / window) shl 24) or ((red / window) shl 16) or ((g / window) shl 8) or (b / window)
                val outP = input[index(i - r)]
                val inP = input[index(i + r + 1)]
                a += (inP ushr 24) - (outP ushr 24)
                red += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
                g += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
                b += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }
}
