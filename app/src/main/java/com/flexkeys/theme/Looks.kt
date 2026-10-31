package com.flexkeys.theme

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved look: the settings that make the keyboard look and feel the way it does, without the background photo.
 * [values] maps a preference key to an Int.
 */
class Look(val name: String, val values: Map<String, Int>, val builtIn: Boolean = false) {
    val mode: Int get() = values[Prefs.KEY_THEME_MODE] ?: Prefs.THEME_AUTO
    val accent: Int get() = values[Prefs.KEY_ACCENT] ?: Prefs.DEFAULT_ACCENT
    val glass: Int get() = values[Prefs.KEY_GLASS_OPACITY] ?: 35
    val gradient: Int get() = values[Prefs.KEY_GRADIENT] ?: 60
    val corner: Int get() = values[Prefs.KEY_CORNER] ?: 8
}

object Looks {
    /** Every preference a look sets. All are whole numbers. */
    val keys = listOf(
        Prefs.KEY_THEME_MODE, Prefs.KEY_ACCENT, Prefs.KEY_GLASS_OPACITY, Prefs.KEY_BLUR, Prefs.KEY_GRADIENT,
        Prefs.KEY_PRESS_STYLE, Prefs.KEY_KEY_MOTION, Prefs.KEY_TYPING_EFFECT, Prefs.KEY_GLASS_ANIMATION,
        Prefs.KEY_STYLE, Prefs.KEY_CORNER, Prefs.KEY_SPACING, Prefs.KEY_SPACE_STYLE, Prefs.KEY_SPACE_GLASS,
    )

    private fun look(
        name: String, mode: Int, accent: Long, glass: Int, blur: Int, gradient: Int = 40, press: Int = 1,
        typing: Int = 0, animation: Int = 0, corner: Int? = null, spacing: Int? = null, tiles: Boolean = false,
    ) = Look(
        name,
        buildMap {
            put(Prefs.KEY_THEME_MODE, mode)
            put(Prefs.KEY_ACCENT, accent.toInt())
            put(Prefs.KEY_GLASS_OPACITY, glass)
            put(Prefs.KEY_BLUR, blur)
            put(Prefs.KEY_GRADIENT, gradient)
            put(Prefs.KEY_PRESS_STYLE, press)
            put(Prefs.KEY_TYPING_EFFECT, typing)
            put(Prefs.KEY_GLASS_ANIMATION, animation)
            put(Prefs.KEY_STYLE, if (tiles) Prefs.KEY_STYLE_TILES else Prefs.KEY_STYLE_GLASS)
            // Shape settings only change when a look has its own idea of them.
            if (corner != null) put(Prefs.KEY_CORNER, corner)
            if (spacing != null) put(Prefs.KEY_SPACING, spacing)
        },
        builtIn = true,
    )

    /**
     * Typing animation: 0 off, 1 sparkles, 2 ripple, 3 warp, 4 bloom, 5 ink, 6 firecracker, 7 paint, 8 spotlight, 9 sakura petals.
     * Glass animation: 0 off, 1 aurora, 2 light flow, 3 bubbles, 4 starlight, 5 rain, 6 fireflies, 7 waves, 8 sakura petals.
     */
    val builtIn: List<Look> = listOf(
        look("Frost", Prefs.THEME_LIGHT, 0xFF5B8CFF, 30, 60, gradient = 25),
        look("Midnight", Prefs.THEME_DARK, 0xFF7C5CFF, 35, 50, gradient = 30, animation = 4),
        look("Aurora", Prefs.THEME_DARK, 0xFF20C997, 25, 70, gradient = 45, animation = 1),
        look("Sunset", Prefs.THEME_LIGHT, 0xFFFF6B6B, 40, 50, gradient = 35, typing = 1),
        look("Clear", Prefs.THEME_AUTO, 0xFF22B8CF, 8, 30, gradient = 0),
        look("Graphite", Prefs.THEME_DARK, 0xFF8A93A6, 55, 40, tiles = true, corner = 8),
        look("Sakura", Prefs.THEME_DARK, 0xFFFF7EB6, 30, 65, gradient = 40, typing = 9, animation = 8),
        look("Ocean", Prefs.THEME_DARK, 0xFF3AA0FF, 28, 70, gradient = 40, typing = 2, animation = 7),
        look("Mint", Prefs.THEME_LIGHT, 0xFF2BC49A, 35, 55, gradient = 25),
        look("Candy", Prefs.THEME_LIGHT, 0xFFFF5FA2, 40, 45, gradient = 50, typing = 8, corner = 20),
        look("Rainy night", Prefs.THEME_DARK, 0xFF5C7CFA, 30, 70, animation = 5, typing = 4),
        look("Ember", Prefs.THEME_DARK, 0xFFFF8A3D, 32, 60, gradient = 35, typing = 6),
        look("Lavender", Prefs.THEME_LIGHT, 0xFF9B7BFF, 38, 55, gradient = 30, corner = 18),
        look("Slate", Prefs.THEME_DARK, 0xFF6C8AA8, 45, 35, tiles = true, corner = 10, spacing = 3),
    )

    /** The current settings as a look called [name]. */
    fun capture(prefs: Prefs, name: String): Look =
        Look(name, keys.associateWith { prefs.sp.getInt(it, DEFAULTS[it] ?: 0) })

    /** True when the current settings already match [look] exactly. */
    fun isActive(prefs: Prefs, look: Look): Boolean =
        look.values.all { (k, v) -> prefs.sp.getInt(k, DEFAULTS[k] ?: Int.MIN_VALUE) == v }

    fun apply(prefs: Prefs, look: Look) {
        val e = prefs.sp.edit()
        for ((k, v) in look.values) if (k in keys) e.putInt(k, v)
        e.apply()
    }

    // ---- the user's own looks, stored as JSON in one preference ----

    fun parse(json: String): List<Look> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                val values = LinkedHashMap<String, Int>()
                for (k in keys) if (o.has(k)) values[k] = o.optInt(k)
                Look(name, values)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun format(looks: List<Look>): String {
        val arr = JSONArray()
        for (l in looks) {
            val o = JSONObject().put("name", l.name)
            for ((k, v) in l.values) o.put(k, v)
            arr.put(o)
        }
        return arr.toString()
    }

    /** Defaults for preferences a look may set but the user never touched. */
    private val DEFAULTS: Map<String, Int> = mapOf(
        Prefs.KEY_THEME_MODE to Prefs.THEME_AUTO, Prefs.KEY_ACCENT to Prefs.DEFAULT_ACCENT, Prefs.KEY_GLASS_OPACITY to 35,
        Prefs.KEY_BLUR to 50, Prefs.KEY_GRADIENT to 60, Prefs.KEY_PRESS_STYLE to 0, Prefs.KEY_KEY_MOTION to 0,
        Prefs.KEY_TYPING_EFFECT to 0, Prefs.KEY_GLASS_ANIMATION to 0, Prefs.KEY_STYLE to Prefs.KEY_STYLE_GLASS,
        Prefs.KEY_CORNER to 8, Prefs.KEY_SPACING to 5, Prefs.KEY_SPACE_STYLE to 0, Prefs.KEY_SPACE_GLASS to 60,
    )
}
