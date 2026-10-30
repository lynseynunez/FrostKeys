package com.flexkeys.settings

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.flexkeys.ai.AiPresets
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/** Your own rewrite styles for the AI bar, like "Make it sound like me" or "Translate to Spanish". */
class AiStylesActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = SettingsPalette(dark, prefs.accentColor)

        val page = FrameLayout(this)
        page.addView(BackdropView(this).apply {
            val t = GlassTheme.from(context, prefs)
            backdrop.setTheme(GlassTheme(dark, t.accent, t.glassOpacity, 0f, 48f * t.density, t.cornerRadiusPx,
                t.keySpacingPx, t.fontScale, t.heightScale, t.density))
            scrim = palette.scrim
        })
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(24f))
        }
        page.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
        })
        column.addView(label("‹  Settings", 15f, palette.secondary).apply {
            setPadding(0, dp(4f), 0, dp(8f))
            setOnClickListener { finish() }
        })
        column.addView(label("Rewrite styles", 30f, palette.text).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) })
        column.addView(label("Add your own styles to the AI bar. Each one has a short name for the button and an instruction " +
            "that tells the AI how to rewrite your text.", 14f, palette.secondary).apply { setPadding(0, dp(2f), 0, dp(12f)) })
        val name = field("Name, like My voice")
        val instruction = field("Instruction, like Rewrite this the way I write: short, lowercase, a bit funny")
        column.addView(name)
        column.addView(instruction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) })
        column.addView(PillButton(this, "Save style", true, palette).apply {
            onClick = {
                val updated = AiPresets.add(prefs.aiPresets, name.text.toString(), instruction.text.toString())
                if (updated == null) {
                    Toast.makeText(this@AiStylesActivity, "Enter a name and an instruction.", Toast.LENGTH_SHORT).show()
                } else if (AiPresets.parse(updated).size > AiPresets.MAX) {
                    Toast.makeText(this@AiStylesActivity, "You can keep up to ${AiPresets.MAX} styles.", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.aiPresets = updated
                    name.setText("")
                    instruction.setText("")
                    refresh()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); gravity = Gravity.END })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list)
        setContentView(page)
        refresh()
    }

    private fun label(text: String, sp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(palette.text)
        setHintTextColor(palette.secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSingleLine = true
        setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
    }

    private fun refresh() {
        list.removeAllViews()
        for (p in AiPresets.parse(prefs.aiPresets)) {
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18f), dp(8f), dp(8f), dp(8f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18f).toFloat()
                    setColor(palette.card)
                    setStroke(dp(1f), palette.cardBorder)
                }
                val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(label(p.name, 16f, palette.text))
                texts.addView(label(p.instruction, 12.5f, palette.secondary).apply { maxLines = 2 })
                addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(PillButton(context, "Remove", false, palette).apply {
                    onClick = {
                        prefs.aiPresets = AiPresets.remove(prefs.aiPresets, p.name)
                        refresh()
                    }
                })
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8f) }
            })
        }
    }
}
