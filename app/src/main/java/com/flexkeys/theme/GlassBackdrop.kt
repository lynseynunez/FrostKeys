package com.flexkeys.theme

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build

/**
 * What sits behind the keys: a soft gradient with color blobs, optionally covered by the
 * user's photo. Views draw it sharp as the keyboard background and blurred inside every
 * glass panel.
 *
 * On Android 12+ the blur is a real GPU blur ([RenderEffect]) on a [RenderNode]. On older
 * versions, or on software canvases, a small software-blurred copy is scaled up instead.
 */
class GlassBackdrop {

    private var width = 0
    private var height = 0
    private var theme: GlassTheme? = null
    private var photo: Bitmap? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scaledPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val photoMatrix = Matrix()
    private var baseShader: Shader? = null
    private var blobShaders: List<Pair<Shader, FloatArray>> = emptyList()

    private var node: Any? = null // RenderNode, typed as Any to keep the class loadable on API 26
    private var nodeDirty = true
    private var softBlur: Bitmap? = null
    private var softDirty = true

    fun setSize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        rebuildShaders()
        invalidate()
    }

    fun setTheme(theme: GlassTheme) {
        this.theme = theme
        rebuildShaders()
        invalidate()
    }

    fun setPhoto(bitmap: Bitmap?) {
        photo = bitmap
        invalidate()
    }

    private fun invalidate() {
        nodeDirty = true
        softDirty = true
    }

    private fun rebuildShaders() {
        val t = theme ?: return
        if (width <= 0 || height <= 0) return
        baseShader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.baseTop, t.baseBottom, Shader.TileMode.CLAMP)
        val w = width.toFloat()
        val h = height.toFloat()
        val spots = listOf(
            floatArrayOf(0.12f * w, 0.18f * h, 0.62f * w),
            floatArrayOf(0.88f * w, 0.32f * h, 0.55f * w),
            floatArrayOf(0.50f * w, 1.05f * h, 0.70f * w),
        )
        blobShaders = spots.mapIndexed { i, s ->
            val color = t.blobColors[i % t.blobColors.size]
            RadialGradient(s[0], s[1], s[2], color, color and 0x00FFFFFF, Shader.TileMode.CLAMP) as Shader to s
        }
    }

    /** Draws the sharp backdrop filling (0, 0, width, height). */
    fun drawBase(canvas: Canvas) {
        val t = theme ?: return
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        basePaint.shader = baseShader
        canvas.drawRect(0f, 0f, w, h, basePaint)
        for ((shader, s) in blobShaders) {
            basePaint.shader = shader
            canvas.drawCircle(s[0], s[1], s[2], basePaint)
        }
        basePaint.shader = null
        val bmp = photo
        if (bmp != null && !bmp.isRecycled && t.backgroundOpacity > 0f) {
            // Center-crop the photo into the backdrop.
            val scale = maxOf(w / bmp.width, h / bmp.height)
            photoMatrix.setScale(scale, scale)
            photoMatrix.postTranslate((w - bmp.width * scale) / 2f, (h - bmp.height * scale) / 2f)
            photoPaint.alpha = (t.backgroundOpacity * 255).toInt()
            canvas.drawBitmap(bmp, photoMatrix, photoPaint)
            basePaint.color = GlassTheme.withAlpha(t.photoScrim, (t.photoScrim ushr 24) / 255f * t.backgroundOpacity)
            canvas.drawRect(0f, 0f, w, h, basePaint)
        }
    }

    /**
     * Draws the blurred backdrop so that backdrop point (ox, oy) lands on the canvas origin.
     * The caller is expected to have clipped the canvas to the glass shape.
     */
    fun drawBlurred(canvas: Canvas, ox: Float, oy: Float) {
        if (width <= 0 || height <= 0 || theme == null) return
        canvas.save()
        canvas.translate(-ox, -oy)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            drawWithRenderNode(canvas)
        } else {
            drawSoftware(canvas)
        }
        canvas.restore()
    }

    @TargetApi(Build.VERSION_CODES.S)
    private fun drawWithRenderNode(canvas: Canvas) {
        val t = theme ?: return
        val rn = (node as? RenderNode) ?: RenderNode("FlexKeysBackdrop").also { node = it }
        if (nodeDirty) {
            rn.setPosition(0, 0, width, height)
            val recording = rn.beginRecording(width, height)
            try {
                drawBase(recording)
            } finally {
                rn.endRecording()
            }
            val radius = t.blurRadiusPx
            rn.setRenderEffect(
                if (radius >= 0.5f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
            )
            nodeDirty = false
        }
        canvas.drawRenderNode(rn)
    }

    private fun drawSoftware(canvas: Canvas) {
        val t = theme ?: return
        if (t.blurRadiusPx < 0.5f) {
            drawBase(canvas)
            return
        }
        if (softDirty || softBlur == null) {
            val sw = (width / SOFT_SCALE).coerceAtLeast(1)
            val sh = (height / SOFT_SCALE).coerceAtLeast(1)
            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val c = Canvas(small)
            c.scale(sw.toFloat() / width, sh.toFloat() / height)
            drawBase(c)
            BoxBlur.blur(small, (t.blurRadiusPx / SOFT_SCALE).toInt().coerceAtLeast(1))
            softBlur = small
            softDirty = false
        }
        val bmp = softBlur ?: return
        canvas.save()
        canvas.scale(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        canvas.drawBitmap(bmp, 0f, 0f, scaledPaint)
        canvas.restore()
    }

    fun release() {
        softBlur = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (node as? RenderNode)?.discardDisplayList()
        node = null
    }

    private companion object {
        const val SOFT_SCALE = 6
    }
}
