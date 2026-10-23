package com.flexkeys.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.flexkeys.keyboard.Icon
import com.flexkeys.theme.Prefs

/** The empty side of the keyboard in one-handed mode: move to the other side, or go back to full width. */
class OneHandedGutter(context: Context) : LinearLayout(context) {

    private val prefs = Prefs.get(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val size = (48 * resources.displayMetrics.density).toInt()
        addView(GlassButton(context, icon = Icon.SWAP).apply {
            filled = true
            onClick = {
                prefs.oneHanded = if (prefs.oneHanded == Prefs.ONE_HANDED_LEFT) Prefs.ONE_HANDED_RIGHT else Prefs.ONE_HANDED_LEFT
            }
        }, LayoutParams(size, size))
        addView(GlassButton(context, icon = Icon.EXPAND).apply {
            filled = true
            onClick = { prefs.oneHanded = Prefs.ONE_HANDED_OFF }
        }, LayoutParams(size, size).apply { topMargin = size / 3 })
    }
}
