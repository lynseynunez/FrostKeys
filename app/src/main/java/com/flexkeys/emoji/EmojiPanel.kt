package com.flexkeys.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.keyboard.Icons
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout

/** Emoji grid with category tabs and recently used emoji. */
class EmojiPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onPanelBackspace()
        fun onPanelClosed()
    }

    private val prefs = Prefs.get(context)
    private val grid = GridView(context)
    private val adapter = EmojiAdapter()
    private val tabs = CategoryTabs(context)
    /** Tab 0 is "recent"; the rest map onto [EmojiData.categories]. */
    private var selectedTab = 0

    init {
        setPadding(dp(6f), dp(6f), dp(6f), dp(4f))
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(44f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.setOnItemClickListener { _, _, position, _ ->
            val emoji = adapter.items[position]
            addRecent(emoji)
            listener.onEmoji(emoji)
        }
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(GlassButton(context, label = "ABC").apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(56f), dp(44f)))
        bar.addView(tabs, LayoutParams(0, dp(44f), 1f))
        bar.addView(GlassButton(context, icon = Icon.BACKSPACE).apply {
            repeatWhileHeld = true
            onClick = listener::onPanelBackspace
        }, LayoutParams(dp(56f), dp(44f)))
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        selectTab(if (recents().isEmpty()) 1 else 0)
    }

    override fun onHostAttached() {
        val t = host?.theme ?: return
        val pressed = GradientDrawable().apply {
            cornerRadius = t.dp(10f)
            setColor(t.pressedOverlay)
        }
        grid.selector = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        grid.setDrawSelectorOnTop(false)
        adapter.notifyDataSetChanged()
        tabs.invalidate()
    }

    private fun recents(): List<String> = prefs.recentEmoji.split(' ').filter { it.isNotEmpty() }

    private fun addRecent(emoji: String) {
        val list = recents().toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        prefs.recentEmoji = list.take(MAX_RECENTS).joinToString(" ")
    }

    private fun selectTab(index: Int) {
        selectedTab = index
        adapter.items = if (index == 0) recents() else supported(index - 1)
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        tabs.invalidate()
    }

    private inner class EmojiAdapter : BaseAdapter() {
        var items: List<String> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f))
                includeFontPadding = false
            }
            tv.text = items[position]
            tv.setTextColor(host?.theme?.textColor ?: Color.BLACK)
            return tv
        }
    }

    /** The row of category icons. */
    private inner class CategoryTabs(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val count get() = EmojiData.categories.size + 1

        override fun onDraw(canvas: Canvas) {
            val t = host?.theme ?: return
            val w = width.toFloat() / count
            val cy = height / 2f
            for (i in 0 until count) {
                val cx = w * i + w / 2f
                if (i == selectedTab) {
                    paint.color = t.accentSoft
                    canvas.drawCircle(cx, cy, minOf(w, height.toFloat()) * 0.42f, paint)
                }
                if (i == 0) {
                    Icons.draw(canvas, Icon.CLOCK, cx, cy, minOf(w, height.toFloat()) * 0.45f, t.textColor)
                } else {
                    paint.color = Color.BLACK
                    paint.alpha = if (i == selectedTab) 255 else 150
                    paint.textSize = minOf(w * 0.55f, height * 0.45f)
                    val fm = paint.fontMetrics
                    canvas.drawText(EmojiData.categories[i - 1].icon, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
                    paint.alpha = 255
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val i = (event.x / (width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                if (i != selectedTab) selectTab(i)
            }
            return true
        }
    }

    companion object {
        private const val MAX_RECENTS = 32
        private val supportedCache = HashMap<Int, List<String>>()

        /** Emoji in category [index] that the device font can actually draw. */
        fun supported(index: Int): List<String> = supportedCache.getOrPut(index) {
            val paint = Paint()
            EmojiData.categories[index].emoji.filter { paint.hasGlyph(it) }
        }
    }
}
