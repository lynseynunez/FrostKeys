package com.flexkeys.phrases

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.flexkeys.clipboard.ClipboardPanel
import com.flexkeys.keyboard.Icon
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout

/** Saved phrases: tap one to insert it. */
class PhrasesPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener : ClipboardPanel.Listener {
        /** Opens the screen where phrases are added and edited. */
        fun onEditPhrases()
    }

    private val store = SnippetStore.get(context)
    private val list = ListView(context)
    private val empty = TextView(context)
    private val title = TextView(context)
    private val adapter = PhraseAdapter()
    private val onStoreChanged: () -> Unit = { refresh() }

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(GlassButton(context, icon = Icon.KEYBOARD).apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(48f), dp(44f)))
        title.text = "Phrases"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GlassButton(context, icon = Icon.SETTINGS).apply { onClick = listener::onEditPhrases },
            LayoutParams(dp(48f), dp(44f)))
        addView(header)

        val body = FrameLayout(context)
        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        list.selector = ColorDrawable(Color.TRANSPARENT)
        list.isVerticalScrollBarEnabled = false
        body.addView(list)
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(24f), 0, dp(24f), 0)
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        empty.text = "Save things you type often, like an email address or a reply.\nTap the gear to add some."
        body.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onHostAttached() {
        store.addListener(onStoreChanged)
        refresh()
    }

    override fun onDetachedFromWindow() {
        store.removeListener(onStoreChanged)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        val t = host?.theme
        if (t != null) {
            title.setTextColor(t.textColor)
            empty.setTextColor(t.secondaryTextColor)
        }
        adapter.items = store.all()
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class PhraseAdapter : BaseAdapter() {
        var items: List<SnippetStore.Snippet> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val t = host?.theme
            val row = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(14f), dp(6f), dp(10f), dp(6f))
                background = GradientDrawable().apply {
                    cornerRadius = t?.cornerRadiusPx ?: dp(12f).toFloat()
                    setColor(t?.keyFill ?: 0x55FFFFFF)
                    setStroke(dp(1f), t?.borderBottom ?: 0x22FFFFFF)
                }
                setOnClickListener { listener.onPaste(item.text) }
            }
            if (item.shortcut.isNotEmpty()) {
                row.addView(TextView(context).apply {
                    text = item.shortcut
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(t?.secondaryTextColor ?: Color.GRAY)
                })
            }
            row.addView(TextView(context).apply {
                text = item.text
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(t?.textColor ?: Color.BLACK)
            })
            return FrameLayout(context).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(3f), 0, dp(3f))
                addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }
}
