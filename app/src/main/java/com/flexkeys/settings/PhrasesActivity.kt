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
import com.flexkeys.phrases.CustomKeys
import com.flexkeys.phrases.SnippetStore
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/** Add and remove saved phrases (with optional typing shortcuts) and extra long-press characters. */
class PhrasesActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private lateinit var prefs: Prefs
    private val store get() = SnippetStore.get(this)
    private lateinit var phraseList: LinearLayout
    private lateinit var keyList: LinearLayout

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
        column.addView(label("Phrases", 30f, palette.text).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) })
        column.addView(label(
            "Save text you type often. Give a phrase a shortcut and it appears when you type the shortcut and press space; " +
                "press backspace right after to undo. Open all phrases from the bubble button on the keyboard.",
            14f, palette.secondary,
        ).apply { setPadding(0, dp(2f), 0, dp(12f)) })

        val shortcut = field("Shortcut (optional), like omw", singleLine = true)
        val text = field("Phrase, like On my way!", singleLine = false)
        column.addView(shortcut)
        column.addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) })
        column.addView(PillButton(this, "Save phrase", true, palette).apply {
            onClick = {
                if (store.put(shortcut.text.toString(), text.text.toString())) {
                    shortcut.setText("")
                    text.setText("")
                    refresh()
                } else {
                    Toast.makeText(this@PhrasesActivity, "Enter a phrase. A shortcut can't contain spaces.", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); gravity = Gravity.END })
        phraseList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(phraseList)

        column.addView(label("Extra characters on keys", 22f, palette.text).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(28f), 0, 0)
        })
        column.addView(label(
            "Add your own characters to the popup you get when you long-press a letter, like é on e or € on e.",
            14f, palette.secondary,
        ).apply { setPadding(0, dp(2f), 0, dp(12f)) })
        val key = field("Letter, like e", singleLine = true)
        val chars = field("Characters, like é€", singleLine = true)
        column.addView(key)
        column.addView(chars, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) })
        column.addView(PillButton(this, "Set characters", true, palette).apply {
            onClick = {
                val k = key.text.toString().trim().lowercase()
                val options = CustomKeys.options(chars.text.toString())
                if (k.isEmpty() || k.codePointCount(0, k.length) != 1) {
                    Toast.makeText(this@PhrasesActivity, "Enter one letter or symbol for the key.", Toast.LENGTH_SHORT).show()
                } else {
                    val map = LinkedHashMap(CustomKeys.parse(prefs.customKeys))
                    if (options.isEmpty()) map.remove(k) else map[k] = options
                    prefs.customKeys = CustomKeys.format(map)
                    key.setText("")
                    chars.setText("")
                    refresh()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); gravity = Gravity.END })
        keyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(keyList)

        setContentView(page)
        refresh()
    }

    private fun label(text: String, sp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun field(hint: String, singleLine: Boolean) = EditText(this).apply {
        this.hint = hint
        setTextColor(palette.text)
        setHintTextColor(palette.secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            (if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        isSingleLine = singleLine
        setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
    }

    private fun row(title: String, subtitle: String?, onRemove: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18f), dp(8f), dp(8f), dp(8f))
        background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (subtitle != null) texts.addView(label(subtitle, 12.5f, palette.secondary))
        texts.addView(label(title, 16f, palette.text).apply { maxLines = 3 })
        addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(PillButton(context, "Remove", false, palette).apply { onClick = onRemove })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) }
    }

    private fun refresh() {
        phraseList.removeAllViews()
        for (s in store.all()) {
            phraseList.addView(row(s.text, s.shortcut.ifEmpty { null }) {
                store.remove(s)
                refresh()
            })
        }
        keyList.removeAllViews()
        for ((k, v) in CustomKeys.parse(prefs.customKeys)) {
            keyList.addView(row(v.joinToString("  "), k) {
                val map = LinkedHashMap(CustomKeys.parse(prefs.customKeys))
                map.remove(k)
                prefs.customKeys = CustomKeys.format(map)
                refresh()
            })
        }
    }
}
