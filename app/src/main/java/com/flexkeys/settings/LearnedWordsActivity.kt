package com.flexkeys.settings

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/** View, search, add and remove the words SakuriKey has learned. */
class LearnedWordsActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private val user get() = SuggestionEngine.get(this).user
    private val adapter = WordAdapter()
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private var filter = ""

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)
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
            setPadding(dp(16f), dp(16f), dp(16f), 0)
        }
        page.addView(column)

        column.addView(TextView(this).apply {
            text = "‹  Settings"
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(4f), 0, dp(8f))
            setOnClickListener { finish() }
        })
        column.addView(TextView(this).apply {
            text = "Learned words"
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        count = TextView(this).apply {
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(12f))
        }
        column.addView(count)

        // Add a word by hand.
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val addField = field("Add a word")
        addRow.addView(addField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val add = {
            val word = addField.text.toString().trim()
            if (word.isNotEmpty()) {
                user.addWord(word)
                addField.setText("")
                refresh()
            }
        }
        addField.imeOptions = EditorInfo.IME_ACTION_DONE
        addField.setOnEditorActionListener { _, _, _ -> add(); true }
        addRow.addView(PillButton(this, "Add", true, palette).apply { onClick = add },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(10f) })
        column.addView(addRow)

        val search = field("Search")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString()?.trim()?.lowercase() ?: ""
                refresh()
            }
        })
        column.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f); bottomMargin = dp(8f) })

        val body = FrameLayout(this)
        val list = ListView(this).apply {
            adapter = this@LearnedWordsActivity.adapter
            divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
        }
        body.addView(list)
        empty = TextView(this).apply {
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
        }
        body.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // The word list loads in the background the first time; show it once it's ready.
        if (!user.isLoaded) Handler(Looper.getMainLooper()).postDelayed({ if (!isDestroyed) refresh() }, 800)
    }

    override fun onPause() {
        user.save()
        super.onPause()
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
        val all = user.allWords()
        adapter.items = if (filter.isEmpty()) all else all.filter { it.first.contains(filter) }
        adapter.notifyDataSetChanged()
        count.text = when (all.size) {
            0 -> "Words you type will appear here"
            1 -> "1 word, stored only on this phone"
            else -> "${all.size} words, stored only on this phone"
        }
        empty.text = if (all.isEmpty()) "No learned words yet." else "No matches."
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class WordAdapter : BaseAdapter() {
        var items: List<Pair<String, Int>> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val (word, uses) = items[position]
            val row = LinearLayout(this@LearnedWordsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18f), dp(6f), dp(8f), dp(6f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18f).toFloat()
                    setColor(palette.card)
                    setStroke(dp(1f), palette.cardBorder)
                }
            }
            val texts = LinearLayout(this@LearnedWordsActivity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this@LearnedWordsActivity).apply {
                text = word
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            texts.addView(TextView(this@LearnedWordsActivity).apply {
                text = if (uses == 1) "typed once" else "typed $uses times"
                setTextColor(palette.secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(PillButton(this@LearnedWordsActivity, "Remove", false, palette).apply {
                onClick = {
                    user.forget(word)
                    refresh()
                }
            })
            return FrameLayout(this@LearnedWordsActivity).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(4f), 0, dp(4f))
                addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }
}
