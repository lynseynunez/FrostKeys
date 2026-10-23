package com.flexkeys.settings

import android.util.Base64
import com.flexkeys.theme.Prefs
import org.json.JSONException
import org.json.JSONObject

/**
 * Turns the look of the keyboard (colors, glass, fonts, sizes; not your photo or typing data) into a
 * short code you can send in any chat, and applies a code someone sends you.
 */
object ThemeShare {

    private const val PREFIX = "SAKURITHEME1:"
    private val INT_KEYS = listOf(
        Prefs.KEY_THEME_MODE, Prefs.KEY_ACCENT, Prefs.KEY_BG_OPACITY, Prefs.KEY_GLASS_OPACITY, Prefs.KEY_BLUR,
        Prefs.KEY_GRADIENT, Prefs.KEY_STYLE, Prefs.KEY_CORNER, Prefs.KEY_SPACING, Prefs.KEY_FONT, Prefs.KEY_HEIGHT,
        Prefs.KEY_SPACE_GLASS, Prefs.KEY_EMBLEM_SIZE,
    )
    private val STRING_KEYS = listOf(Prefs.KEY_FONT_FAMILY, Prefs.KEY_SPACE_EMBLEM)

    fun isThemeCode(text: String?): Boolean = text?.trim()?.startsWith(PREFIX) == true

    fun encode(prefs: Prefs): String {
        val o = JSONObject()
        for (k in INT_KEYS) if (prefs.sp.contains(k)) o.put(k, prefs.sp.getInt(k, 0))
        for (k in STRING_KEYS) if (prefs.sp.contains(k)) o.put(k, prefs.sp.getString(k, ""))
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(), flags)
    }

    /** Applies the theme in [code]. Returns false if it isn't a valid theme code. */
    fun apply(prefs: Prefs, code: String): Boolean {
        val text = code.trim()
        if (!text.startsWith(PREFIX)) return false
        return try {
            val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            val o = JSONObject(String(Base64.decode(text.removePrefix(PREFIX), flags)))
            val editor = prefs.sp.edit()
            for (k in INT_KEYS) if (o.has(k)) editor.putInt(k, o.getInt(k))
            for (k in STRING_KEYS) if (o.has(k)) editor.putString(k, o.getString(k))
            editor.apply()
            true
        } catch (e: JSONException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
