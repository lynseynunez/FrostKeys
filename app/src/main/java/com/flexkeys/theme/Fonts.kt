package com.flexkeys.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface

/**
 * The keyboard's typefaces. Inter and Manrope are bundled variable fonts (SIL Open Font
 * License, see assets/fonts); "system" uses the phone's own sans-serif.
 */
object Fonts {
    const val INTER = "inter"
    const val MANROPE = "manrope"
    const val SYSTEM = "system"

    /** Ids in the order shown in settings, with their display names. */
    val options: List<Pair<String, String>> = listOf(INTER to "Inter", MANROPE to "Manrope", SYSTEM to "System")

    private val cache = HashMap<String, Typeface>()

    fun typeface(context: Context, id: String): Typeface = cache.getOrPut(id) {
        val asset = when (id) {
            INTER -> "fonts/inter.ttf"
            MANROPE -> "fonts/manrope.ttf"
            else -> null
        }
        try {
            if (asset != null) Typeface.createFromAsset(context.applicationContext.assets, asset)
            else Typeface.create("sans-serif", Typeface.NORMAL)
        } catch (e: RuntimeException) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    /** Sets [paint]'s typeface to [id] at the given weight (100 thin .. 900 black). */
    fun apply(paint: Paint, context: Context, id: String, weight: Int) {
        paint.typeface = typeface(context, id)
        paint.setFontVariationSettings(if (id == SYSTEM) null else "'wght' $weight")
    }
}
