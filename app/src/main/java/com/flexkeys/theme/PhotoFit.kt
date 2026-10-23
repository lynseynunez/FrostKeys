package com.flexkeys.theme

/** How the background photo is scaled and positioned inside the area it covers. */
object PhotoFit {

    class Fit(val scale: Float, val dx: Float, val dy: Float)

    /**
     * Fits a [bw] x [bh] photo to cover a [w] x [h] area. [zoom] is at least 1 (just covering);
     * [panX] and [panY] run from -1 (show the left/top edge) to 1 (right/bottom), 0 is centered.
     */
    fun compute(bw: Int, bh: Int, w: Float, h: Float, zoom: Float, panX: Float, panY: Float): Fit {
        val scale = maxOf(w / bw, h / bh) * zoom.coerceAtLeast(1f)
        val overflowX = bw * scale - w
        val overflowY = bh * scale - h
        val dx = -overflowX * (panX.coerceIn(-1f, 1f) + 1f) / 2f
        val dy = -overflowY * (panY.coerceIn(-1f, 1f) + 1f) / 2f
        return Fit(scale, dx, dy)
    }
}
