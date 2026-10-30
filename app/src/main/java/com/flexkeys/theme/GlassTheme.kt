package com.flexkeys.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Resolved look of the keyboard for one moment in time. Rebuilt from [Prefs] whenever a
 * setting changes; views read colors and sizes from here and never from prefs directly.
 */
class GlassTheme(
    val dark: Boolean,
    val accent: Int,
    /** 0..1, how opaque the frosted glass on keys and panels is. */
    val glassOpacity: Float,
    /** 0..1, opacity of the background photo over the base gradient. */
    val backgroundOpacity: Float,
    val blurRadiusPx: Float,
    val cornerRadiusPx: Float,
    val keySpacingPx: Float,
    val fontScale: Float,
    val heightScale: Float,
    val density: Float,
    /** 0..1, strength of the blue/purple/pink gradient across the glass. */
    val gradientStrength: Float = 0f,
    /** True for the transparent style: bare glass letters instead of a tile behind each key. */
    val glassKeys: Boolean = false,
    val fontId: String = Fonts.INTER,
    val spaceEmblem: String = "",
    /** 0..1, brightness of the frosted space bar. */
    val spaceGlass: Float = 0.6f,
    /** Multiplier for the space bar emblem size. */
    val emblemScale: Float = 1f,
    /** 1 = the photo just covers the keyboard; larger zooms in. */
    val photoZoom: Float = 1f,
    /** -1..1, where the zoomed photo is positioned (0 is centered). */
    val photoPanX: Float = 0f,
    val photoPanY: Float = 0f,
    /** See [Prefs.pressStyle]. */
    val pressStyle: Int = 0,
    /** See [Prefs.spaceStyle]. */
    val spaceStyle: Int = 0,
    val photoAccent: Int = Prefs.DEFAULT_ACCENT,
) {
    // Glass lettering: translucent fill, a bright thin edge, a soft shadow and the gradient shining through.
    val glassTextFill: Int = if (dark) withAlpha(Color.WHITE, 0.80f) else withAlpha(0xFF15171C.toInt(), 0.80f)
    val glassTextRim: Int = withAlpha(Color.WHITE, if (dark) 0.50f else 0.75f)
    val glassTextShadow: Int = withAlpha(Color.BLACK, if (dark) 0.40f else 0.18f)
    val glassTextGradientAlpha: Float = gradientStrength * (if (dark) 0.60f else 0.45f)
    /** Iridescent glow colors, top-left to bottom-right: blue, purple, pink. */
    val gradientColors: IntArray = intArrayOf(0xFF4D7CFF.toInt(), 0xFF9B5CFF.toInt(), 0xFFFF5DB1.toInt())
    val sheetGradientAlpha: Float = gradientStrength * (if (dark) 0.50f else 0.38f)
    val keyGradientAlpha: Float = gradientStrength * (if (dark) 0.26f else 0.22f)

    val textColor: Int = if (dark) 0xFFF4F6FB.toInt() else 0xFF15171C.toInt()
    val secondaryTextColor: Int = withAlpha(textColor, if (dark) 0.62f else 0.55f)

    // Keys are flat translucent tiles on one frosted sheet, in the style of iOS 26 "Liquid Glass".
    val keyFill: Int =
        if (dark) withAlpha(Color.WHITE, 0.12f + 0.34f * glassOpacity) else withAlpha(Color.WHITE, 0.40f + 0.5f * glassOpacity)
    /** Shift, 123, return, backspace: a darker glass than the letter keys, as in modern glass keyboards. */
    val specialKeyFill: Int =
        if (dark) withAlpha(Color.BLACK, 0.16f + 0.22f * glassOpacity) else withAlpha(0xFF7D869A.toInt(), 0.18f + 0.22f * glassOpacity)
    val panelFill: Int = keyFill
    val pressedOverlay: Int = if (dark) withAlpha(Color.WHITE, 0.22f) else withAlpha(Color.WHITE, 0.6f)
    /** Tint over the blurred backdrop for the whole keyboard sheet. */
    val sheetTint: Int = if (dark) withAlpha(Color.BLACK, 0.18f + 0.3f * glassOpacity) else withAlpha(Color.WHITE, 0.12f + 0.3f * glassOpacity)
    /** Shift when active: a solid white key, like iOS. */
    val activeFill: Int = withAlpha(Color.WHITE, 0.95f)
    val onActive: Int = 0xFF15171C.toInt()

    val accentFill: Int = withAlpha(accent, 0.62f + 0.33f * glassOpacity)
    val accentSoft: Int = withAlpha(accent, 0.30f)
    val onAccent: Int = if (luminance(accent) > 0.6f) 0xFF15171C.toInt() else Color.WHITE

    val borderTop: Int = withAlpha(Color.WHITE, if (dark) 0.30f else 0.85f)
    val borderBottom: Int = withAlpha(Color.WHITE, if (dark) 0.03f else 0.15f)
    val highlight: Int = withAlpha(Color.WHITE, if (dark) 0.12f else 0.30f)
    val shadow: Int = withAlpha(Color.BLACK, if (dark) 0.28f else 0.10f)
    val sheetRim: Int = withAlpha(Color.WHITE, if (dark) 0.22f else 0.7f)
    val divider: Int = withAlpha(textColor, 0.14f)

    val baseTop: Int = if (dark) 0xFF1A1B22.toInt() else 0xFFE9ECF4.toInt()
    val baseBottom: Int = if (dark) 0xFF0C0D11.toInt() else 0xFFCDD4E4.toInt()
    /** Soft color blobs behind the glass so it has something to refract without a photo. */
    val blobColors: IntArray = intArrayOf(
        withAlpha(accent, if (dark) 0.55f else 0.42f),
        withAlpha(rotateHue(accent, 55f), if (dark) 0.42f else 0.34f),
        withAlpha(rotateHue(accent, -60f), if (dark) 0.38f else 0.30f),
    )
    /** Laid over the photo to keep labels readable. */
    val photoScrim: Int = if (dark) withAlpha(Color.BLACK, 0.22f) else withAlpha(Color.WHITE, 0.14f)

    fun dp(value: Float): Float = value * density

    /** The glow a pressed key gets. [x] is the key's horizontal position across the keyboard, 0..1. */
    fun pressColor(x: Float, seed: Long = 0L): Int = when (pressStyle) {
        4 -> {
            val hsv = floatArrayOf(((seed * 47L) % 360L).toFloat(), 0.55f, 1f)
            withAlpha(Color.HSVToColor(hsv), 0.6f)
        }
        1 -> withAlpha(accent, 0.55f)
        2 -> {
            val f = x.coerceIn(0f, 1f)
            val c = gradientColors
            withAlpha(if (f < 0.5f) lerp(c[0], c[1], f * 2f) else lerp(c[1], c[2], (f - 0.5f) * 2f), 0.6f)
        }
        3 -> withAlpha(photoAccent, 0.6f)
        else -> pressedOverlay
    }

    companion object {
        fun from(context: Context, prefs: Prefs): GlassTheme {
            val dm = context.resources.displayMetrics
            val adaptive = prefs.adaptTint && prefs.photoLuma >= 0 && prefs.backgroundOpacity > 0
            val dark = when {
                adaptive -> prefs.photoLuma < 55
                prefs.themeMode == Prefs.THEME_LIGHT -> false
                prefs.themeMode == Prefs.THEME_DARK -> true
                else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            return GlassTheme(
                dark = dark,
                accent = prefs.accentColor or 0xFF000000.toInt(),
                glassOpacity = prefs.glassOpacity.coerceIn(0, 100) / 100f,
                backgroundOpacity = prefs.backgroundOpacity.coerceIn(0, 100) / 100f,
                // Blur slider 0..100 maps to 0..32dp.
                blurRadiusPx = prefs.blurStrength.coerceIn(0, 100) / 100f * 32f * dm.density,
                cornerRadiusPx = prefs.cornerRadiusDp.coerceIn(0, 30) * dm.density,
                keySpacingPx = prefs.keySpacingDp.coerceIn(0, 16) * dm.density,
                fontScale = prefs.fontPercent.coerceIn(60, 160) / 100f,
                heightScale = prefs.heightPercent.coerceIn(60, 150) / 100f,
                density = dm.density,
                gradientStrength = prefs.gradientStrength.coerceIn(0, 100) / 100f,
                glassKeys = prefs.keyStyle == Prefs.KEY_STYLE_GLASS,
                fontId = prefs.fontFamily,
                spaceEmblem = prefs.spaceEmblem,
                spaceGlass = prefs.spaceGlass.coerceIn(0, 100) / 100f,
                emblemScale = prefs.emblemSize.coerceIn(50, 160) / 100f,
                photoZoom = prefs.photoZoom.coerceIn(100, 400) / 100f,
                photoPanX = prefs.photoPanX.coerceIn(-100, 100) / 100f,
                photoPanY = prefs.photoPanY.coerceIn(-100, 100) / 100f,
                pressStyle = prefs.pressStyle,
                spaceStyle = prefs.spaceStyle,
                photoAccent = prefs.photoAccent,
            )
        }

        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24)

        fun lerp(a: Int, b: Int, f: Float): Int {
            fun ch(shift: Int) = (((a ushr shift) and 0xFF) + ((((b ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) * f)).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun luminance(color: Int): Float =
            (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f

        fun rotateHue(color: Int, degrees: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
            return Color.HSVToColor(Color.alpha(color), hsv)
        }
    }
}
