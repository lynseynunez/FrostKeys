package com.flexkeys.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.flexkeys.suggest.AutocorrectLog
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/**
 * What autocorrect has been doing, so it can be corrected as you go instead of by typing word lists: the changes it made
 * (tap "Wrong" on one it should not have made), and the words it did not recognize (add them, or say what they should be).
 */
class AutocorrectReviewActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private lateinit var prefs: Prefs
    private lateinit var engine: SuggestionEngine
    private lateinit var content: LinearLayout

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        engine = SuggestionEngine.get(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = SettingsPalette(dark, prefs.accentColor)

        val page = FrameLayout(this)
        page.addView(BackdropView(this).apply {
            val t = GlassTheme.from(context, prefs)
            backdrop.setTheme(GlassTheme(dark, t.accent, t.glassOpacity, 0f, 48f * t.density, t.cornerRadiusPx,
                t.keySpacingPx, t.fontScale, t.heightScale, t.density))
            scrim = palette.scrim
        })
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(24f))
        }
        page.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        })
        setContentView(page)
        refresh()
    }

    private fun label(text: String, sp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun heading(text: String) = label(text, 22f, palette.text).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(0, dp(22f), 0, dp(2f))
    }

    private fun card(build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18f), dp(12f), dp(12f), dp(12f))
        background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8f) }
        build()
    }

    private fun buttons(vararg actions: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8f), 0, 0)
        actions.forEachIndexed { i, (name, action) ->
            addView(PillButton(context, name, i == 0, palette).apply { onClick = action },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) marginStart = dp(8f) })
        }
    }

    private fun refresh() {
        content.removeAllViews()
        val log = engine.log
        content.addView(label("‹  Settings", 15f, palette.secondary).apply {
            setPadding(0, dp(4f), 0, dp(8f))
            setOnClickListener { finish() }
        })
        content.addView(label("Autocorrect review", 30f, palette.text).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) })

        val rate = log.undoRate()
        val care = log.carefulness()
        val summary = buildString {
            append("${log.fixedCount} changes made, ${log.undoneCount} undone by you.")
            if (rate != null) append(" Of the last ${minOf(log.fixedCount, 40)}, ${(rate * 100).toInt()}% were undone.")
            if (care > 0.0 && prefs.autoTune) append("\nAutocorrect is being extra careful for now because many recent changes were undone.")
        }
        content.addView(label(summary, 14f, palette.secondary).apply { setPadding(0, dp(6f), 0, 0) })
        if (!prefs.autocorrectLog) {
            content.addView(label("The autocorrect log is turned off in Settings > Typing, so nothing new is recorded.", 14f, palette.secondary)
                .apply { setPadding(0, dp(6f), 0, 0) })
        }

        // ---- changes it made ----
        content.addView(heading("Changes it made"))
        content.addView(label("Tap Wrong if autocorrect should have left the word alone. It is remembered, and added to your leave-alone list.", 14f, palette.secondary))
        val recent = log.recent(25)
        if (recent.isEmpty()) content.addView(label("Nothing yet. Keep typing and it fills in.", 15f, palette.secondary).apply { setPadding(0, dp(10f), 0, 0) })
        for (e in recent) {
            content.addView(card {
                addView(label("${e.typed}  →  ${e.result}", 17f, palette.text))
                if (e.undone) {
                    addView(label("You undid this one.", 13f, palette.secondary))
                    addView(buttons("Never change it again" to { neverChange(e.typed) }))
                } else {
                    addView(buttons("Wrong" to { neverChange(e.typed) }, "Fine" to { forgetEntry(e) }))
                }
            })
        }

        // ---- words it did not recognize ----
        content.addView(heading("Words it did not recognize"))
        content.addView(label("Words left as you typed them. If one is a real word, add it. If it is a typo, say what it should be and it is fixed from then on.", 14f, palette.secondary))
        val unknown = log.unknownWords(25)
        if (unknown.isEmpty()) content.addView(label("None so far.", 15f, palette.secondary).apply { setPadding(0, dp(10f), 0, 0) })
        for ((word, count) in unknown) {
            content.addView(card {
                addView(label(if (count > 1) "$word   (${count}×)" else word, 17f, palette.text))
                addView(buttons("It's a word" to { addWord(word) }, "Fix it…" to { chooseFix(word) }, "Ignore" to { log.forgetUnknown(word); refresh() }))
            })
        }

        // ---- sharing and clearing ----
        content.addView(heading("Share or clear"))
        content.addView(buttons(
            "Copy report" to {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Autocorrect report", log.report()))
                Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
            },
            "Clear log" to { log.clear(); refresh() },
        ))
    }

    /** The word must never be changed again. */
    private fun neverChange(typed: String) {
        prefs.protectedWords = engine.addToVocabulary(typed, prefs.protectedWords)
        engine.fixes.reject(typed, typed)
        engine.log.markWrong(typed)
        engine.log.forgetUnknown(typed)
        Toast.makeText(this, "\"$typed\" will be left alone", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun forgetEntry(e: AutocorrectLog.Entry) {
        // "Fine": it was right, so it stops being listed.
        engine.log.dismiss(e)
        refresh()
    }

    private fun addWord(word: String) {
        engine.log.forgetUnknown(word)
        prefs.protectedWords = engine.addToVocabulary(word, prefs.protectedWords)
        Toast.makeText(this, "\"$word\" added", Toast.LENGTH_SHORT).show()
        refresh()
    }

    /** Offers what it might have meant; picking one makes that fix permanent. */
    private fun chooseFix(word: String) {
        val options = engine.suggest(word, null, true, false).slots
            .filter { it.isNotEmpty() && !it.equals(word, ignoreCase = true) }
            .distinct()
        if (options.isEmpty()) {
            Toast.makeText(this, "No suggestions for \"$word\". Add it as a word, or ignore it.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("\"$word\" should be")
            .setItems(options.toTypedArray()) { _, which ->
                val target = options[which]
                val rest = prefs.customFixes.lines().filter { it.isNotBlank() && !it.startsWith("${word.lowercase()}\t") }
                prefs.customFixes = (rest + "${word.lowercase()}\t$target").joinToString("\n")
                engine.log.forgetUnknown(word)
                Toast.makeText(this, "\"$word\" will become \"$target\"", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
