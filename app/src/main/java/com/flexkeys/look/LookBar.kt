package com.flexkeys.look

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.settings.GlassSlider
import com.flexkeys.settings.SettingsPalette
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.ToolBar
import com.flexkeys.ui.findGlassHost

/**
 * Live look controls above the keys: drag a slider and the keyboard changes right away, so there is
 * no need to go back and forth to settings. Typing on the keys works as usual while it is open.
 */
class LookBar(
    context: Context,
    private val onClose: () -> Unit,
) : LinearLayout(context), ToolBar {

    override val query = ""
    override val capturesTyping get() = false

    private val prefs = Prefs.get(context)
    private var host: GlassHost? = null

    private class Row(val label: TextView, val value: TextView, var slider: GlassSlider?)

    private val rows = ArrayList<Row>()
    private val title = TextView(context)

    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), dp(2f))

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(GlassButton(context, icon = Icon.ARROW_LEFT).apply { onClick = { onClose() } }, LayoutParams(dp(44f), dp(40f)))
        title.text = "Adjust look"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.setPadding(dp(8f), 0, 0, 0)
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header)

        addRow("Glass", 0, 100, prefs.glassOpacity) { prefs.glassOpacity = it }
        addRow("Photo", 0, 100, prefs.backgroundOpacity) { prefs.backgroundOpacity = it }
        addRow("Blur", 0, 100, prefs.blurStrength) { prefs.blurStrength = it }
        addRow("Glow", 0, 100, prefs.gradientStrength) { prefs.gradientStrength = it }
        addRow("Effects", 0, 100, prefs.effectStrength) { prefs.effectStrength = it }
    }

    private fun addRow(name: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
        val label = TextView(context).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val valueText = TextView(context).apply {
            text = "$value%"
            gravity = Gravity.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val row = Row(label, valueText, null)
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(label, LayoutParams(dp(54f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
        val slider = GlassSlider(context, min, max, value, SettingsPalette(true, 0xFF5B8CFF.toInt())) {
            valueText.text = "$it%"
            onChange(it)
        }
        row.slider = slider
        line.addView(slider, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(valueText, LayoutParams(dp(48f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8f) })
        addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36f)))
        rows.add(row)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        restyle()
    }

    override fun restyle() {
        val t = host?.theme ?: return
        title.setTextColor(t.textColor)
        for (r in rows) {
            r.label.setTextColor(t.secondaryTextColor)
            r.value.setTextColor(t.secondaryTextColor)
            r.slider?.palette = SettingsPalette(t.dark, t.accent)
            r.slider?.invalidate()
        }
        background = null
    }

    override fun setQuery(q: String) = Unit
    override fun onEnter() = Unit

    /** Picks up values that changed elsewhere (settings, a preset) when the bar opens. */
    override fun reset() = Unit
}
