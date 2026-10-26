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
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/**
 * Your own autocorrect rules: words that must never be changed (like "gpu"), and typed text that is
 * always replaced with something specific.
 */
class CorrectionsActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private lateinit var prefs: Prefs
    private lateinit var protectedList: LinearLayout
    private lateinit var fixList: LinearLayout

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun protectedWords(): List<String> = prefs.protectedWords.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun fixes(): List<Pair<String, String>> = prefs.customFixes.lines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
    }

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
        column.addView(label("Autocorrect list", 30f, palette.text).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) })

        // ---- words to leave alone ----
        column.addView(label("Leave these words alone", 22f, palette.text).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(18f), 0, 0)
        })
        column.addView(label("Words you never want changed, like gpu, names or slang. They are also suggested as you type.", 14f, palette.secondary)
            .apply { setPadding(0, dp(2f), 0, dp(10f)) })
        val wordField = field("Word, like gpu")
        column.addView(wordField)
        column.addView(PillButton(this, "Leave it alone", true, palette).apply {
            onClick = {
                val word = wordField.text.toString().trim().lowercase()
                if (word.isEmpty() || word.any { it.isWhitespace() }) {
                    Toast.makeText(this@CorrectionsActivity, "Enter a single word.", Toast.LENGTH_SHORT).show()
                } else {
                    if (word !in protectedWords()) prefs.protectedWords = (protectedWords() + word).joinToString("\n")
                    SuggestionEngine.get(this@CorrectionsActivity).user.addWord(word)
                    wordField.setText("")
                    refresh()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); gravity = Gravity.END })
        protectedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(protectedList)

        // ---- your own fixes ----
        column.addView(label("Always change", 22f, palette.text).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(28f), 0, 0)
        })
        column.addView(label("Whenever you type the first word and press space, it becomes the second. For example gpu becomes GPU.", 14f, palette.secondary)
            .apply { setPadding(0, dp(2f), 0, dp(10f)) })
        val typedField = field("When I type, like gpu")
        val fixField = field("Change it to, like GPU")
        column.addView(typedField)
        column.addView(fixField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) })
        column.addView(PillButton(this, "Save fix", true, palette).apply {
            onClick = {
                val typed = typedField.text.toString().trim().lowercase()
                val fix = fixField.text.toString().trim()
                if (typed.isEmpty() || typed.any { it.isWhitespace() } || fix.isEmpty()) {
                    Toast.makeText(this@CorrectionsActivity, "Enter what you type (one word) and what it should become.", Toast.LENGTH_SHORT).show()
                } else {
                    val rest = fixes().filter { it.first != typed }
                    prefs.customFixes = (rest + (typed to fix)).joinToString("\n") { "${it.first}\t${it.second}" }
                    typedField.setText("")
                    fixField.setText("")
                    refresh()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); gravity = Gravity.END })
        fixList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(fixList)

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

    private fun row(title: String, onRemove: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18f), dp(8f), dp(8f), dp(8f))
        background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
        addView(label(title, 16f, palette.text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(PillButton(context, "Remove", false, palette).apply { onClick = onRemove })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) }
    }

    private fun refresh() {
        protectedList.removeAllViews()
        for (word in protectedWords()) {
            protectedList.addView(row(word) {
                prefs.protectedWords = (protectedWords() - word).joinToString("\n")
                SuggestionEngine.get(this).user.forget(word)
                refresh()
            })
        }
        fixList.removeAllViews()
        for ((typed, fix) in fixes()) {
            fixList.addView(row("$typed  →  $fix") {
                prefs.customFixes = fixes().filter { it.first != typed }.joinToString("\n") { "${it.first}\t${it.second}" }
                refresh()
            })
        }
    }
}
