package com.flexkeys.suggest

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.emoji.EmojiIndex
import com.flexkeys.keyboard.Icon
import com.flexkeys.phrases.SnippetStore
import com.flexkeys.suggest.SuggestionStripView.Action
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout
import com.flexkeys.ui.GlassTileDrawable

/**
 * Quick Insert: one place for the things people reach for most. Rows of recent clipboard items, recent emoji and
 * saved phrases to tap straight into the text, and a row of shortcuts that open the other panels.
 */
class QuickInsertPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener {
        fun onQuickText(text: String)
        fun onQuickAction(action: Action)
        fun onQuickClosed()
    }

    private val prefs = Prefs.get(context)
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val chips = ArrayList<TextView>()
    private val labels = ArrayList<TextView>()

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(GlassButton(context, icon = Icon.KEYBOARD).apply { onClick = listener::onQuickClosed }, LayoutParams(dp(48f), dp(44f)))
        header.addView(TextView(context).apply {
            text = "Quick insert"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(8f), 0, 0, 0)
            labels.add(this)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header)
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(body)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onHostAttached() = build()

    private fun build() {
        body.removeAllViews()
        chips.clear()
        val clips = ClipboardStore.get(context).items().take(8).map { it.text }
        val emoji = EmojiIndex.recents(prefs).take(14)
        val phrases = SnippetStore.get(context).all().take(8).map { it.text }
        row("Clipboard", clips) { onTap(it) }
        row("Emoji", emoji) { onTap(it) }
        row("Phrases", phrases) { onTap(it) }
        val shortcuts = listOf(Action.GIF, Action.STICKERS, Action.CLIPBOARD, Action.EMOJI, Action.PHRASES, Action.FANCY, Action.TRANSLATE, Action.AI, Action.MIC)
        label("Open")
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (a in shortcuts) line.addView(chip(a.label) { listener.onQuickAction(a) })
        scroll.addView(line)
        body.addView(scroll)
        applyTheme()
    }

    private fun onTap(text: String) = listener.onQuickText(text)

    private fun label(text: String) {
        val tv = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(6f), dp(8f), 0, dp(2f))
        }
        labels.add(tv)
        body.addView(tv)
    }

    private fun row(title: String, items: List<String>, onTap: (String) -> Unit) {
        if (items.isEmpty()) return
        label(title)
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (text in items) line.addView(chip(text) { onTap(text) })
        scroll.addView(line)
        body.addView(scroll)
    }

    private fun chip(text: String, onClick: () -> Unit): TextView {
        val tv = TextView(context).apply {
            this.text = text.replace('\n', ' ')
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            maxWidth = dp(180f)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { onClick() }
        }
        tv.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40f)).apply { marginEnd = dp(6f) }
        chips.add(tv)
        return tv
    }

    private fun applyTheme() {
        val t: GlassTheme = host?.theme ?: return
        val h = host ?: return
        for (l in labels) l.setTextColor(t.secondaryTextColor.takeIf { l.textSize < 40f && l.text != "Quick insert" } ?: t.textColor)
        for (c in chips) {
            c.setTextColor(t.textColor)
            c.background = GlassTileDrawable(h.painter, GlassTheme.withAlpha(if (t.dark) Color.BLACK else Color.WHITE, 0.22f), dp(18f).toFloat())
        }
    }
}
