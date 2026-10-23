package com.flexkeys.clipboard

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
import com.flexkeys.keyboard.Icon
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout

/** Clipboard history: tap to paste, pin to keep, delete to forget. */
class ClipboardPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener {
        fun onPaste(text: String)
        fun onPanelClosed()
    }

    private val store = ClipboardStore.get(context)
    private val prefs = Prefs.get(context)
    private val list = ListView(context)
    private val empty = TextView(context)
    private val title = TextView(context)
    private val adapter = ClipAdapter()
    private val onStoreChanged: () -> Unit = { refresh() }

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(GlassButton(context, icon = Icon.KEYBOARD).apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(48f), dp(44f)))
        title.text = "Clipboard"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GlassButton(context, icon = Icon.TRASH).apply { onClick = { store.clearUnpinned() } },
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
        adapter.items = store.items()
        adapter.notifyDataSetChanged()
        empty.text = if (prefs.clipboardHistory) {
            "Text you copy shows up here.\nPin items to keep them."
        } else {
            "Clipboard history is off.\nTurn it on in FlexKeys settings."
        }
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class ClipAdapter : BaseAdapter() {
        var items: List<ClipboardStore.Item> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val t = host?.theme
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(4f), dp(4f), dp(4f))
                background = GradientDrawable().apply {
                    cornerRadius = t?.cornerRadiusPx ?: dp(12f).toFloat()
                    setColor(t?.keyFill ?: 0x55FFFFFF)
                    setStroke(dp(1f), t?.borderBottom ?: 0x22FFFFFF)
                }
            }
            val text = TextView(context).apply {
                this.text = item.text
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(t?.textColor ?: Color.BLACK)
                setPadding(0, dp(8f), dp(8f), dp(8f))
                setOnClickListener { listener.onPaste(item.text) }
            }
            row.addView(text, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(GlassButton(context, icon = if (item.pinned) Icon.PIN_ON else Icon.PIN).apply {
                selectedState = item.pinned
                onClick = { store.togglePin(item.id) }
            }, LayoutParams(dp(40f), dp(40f)))
            row.addView(GlassButton(context, icon = Icon.TRASH).apply { onClick = { store.delete(item.id) } },
                LayoutParams(dp(40f), dp(40f)))
            // Spacing between rows without a divider drawable.
            return FrameLayout(context).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(3f), 0, dp(3f))
                addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }
}
