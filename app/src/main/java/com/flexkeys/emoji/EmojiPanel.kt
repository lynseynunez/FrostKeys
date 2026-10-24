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
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.keyboard.Icons
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout

/** Emoji grid with category tabs, recents, search and skin tones (long-press). */
class EmojiPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onPanelBackspace()
        fun onPanelClosed()
        /** The search button was tapped: type on the keyboard to find emoji. */
        fun onEmojiSearch()
    }

    private val prefs = Prefs.get(context)
    private val grid = GridView(context)
    private val adapter = EmojiAdapter()
    private val tabs = CategoryTabs(context)
    private val tabScroll = android.widget.HorizontalScrollView(context)
    /** Shown over the grid while picking a skin tone. */
    private val toneRow = LinearLayout(context)
    private var tones: Map<String, String> = EmojiIndex.chosenTones(prefs)
    /** Tab 0 is "recent", then [EmojiData.categories], then text faces and symbols. */
    private var selectedTab = 0
    private val kaomojiTab get() = EmojiData.categories.size + 1
    private val symbolsTab get() = EmojiData.categories.size + 2

    init {
        EmojiIndex.load(context)
        setPadding(dp(6f), dp(6f), dp(6f), dp(4f))
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(44f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.setOnItemClickListener { _, _, position, _ -> pick(display(adapter.items[position])) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            val base = adapter.items[position]
            if (EmojiIndex.supportsSkinTone(base)) {
                showTones(base)
                true
            } else {
                false
            }
        }
        val body = FrameLayout(context)
        body.addView(grid)
        toneRow.orientation = HORIZONTAL
        toneRow.gravity = Gravity.CENTER
        toneRow.visibility = View.GONE
        body.addView(toneRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60f), Gravity.TOP))
        addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(GlassButton(context, label = "ABC").apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(52f), dp(44f)))
        bar.addView(GlassButton(context, icon = Icon.SEARCH).apply { onClick = listener::onEmojiSearch },
            LayoutParams(dp(44f), dp(44f)))
        // Roomy, easy-to-hit tabs in a strip that scrolls sideways.
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.overScrollMode = View.OVER_SCROLL_NEVER
        tabScroll.isHorizontalFadingEdgeEnabled = true
        tabScroll.setFadingEdgeLength(dp(18f))
        tabScroll.addView(tabs, FrameLayout.LayoutParams(tabs.totalCount * dp(TAB_DP), ViewGroup.LayoutParams.MATCH_PARENT))
        bar.addView(tabScroll, LayoutParams(0, dp(48f), 1f))
        bar.addView(GlassButton(context, icon = Icon.BACKSPACE).apply {
            repeatWhileHeld = true
            onClick = listener::onPanelBackspace
        }, LayoutParams(dp(52f), dp(44f)))
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        selectTab(if (EmojiIndex.recents(prefs).isEmpty()) 1 else 0)
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
        toneRow.background = GradientDrawable().apply {
            cornerRadius = t.dp(18f)
            setColor(if (t.dark) 0xF02A2C36.toInt() else 0xF5FFFFFF.toInt())
        }
        adapter.notifyDataSetChanged()
        tabs.invalidate()
    }

    /** The emoji as shown: with the user's remembered skin tone applied. */
    private fun display(base: String): String = EmojiIndex.displayFor(prefs, base, tones)

    private fun pick(emoji: String) {
        // Text faces are too wide for the recents grid, so they aren't kept there.
        if (selectedTab != kaomojiTab) EmojiIndex.addRecent(prefs, emoji)
        listener.onEmoji(emoji)
    }

    private fun showTones(base: String) {
        toneRow.removeAllViews()
        for (variant in EmojiIndex.skinVariants(base)) {
            toneRow.addView(TextView(context).apply {
                text = variant
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setOnClickListener {
                    EmojiIndex.rememberTone(prefs, base, variant)
                    tones = EmojiIndex.chosenTones(prefs)
                    toneRow.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                    pick(variant)
                }
            }, LayoutParams(dp(48f), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        toneRow.visibility = View.VISIBLE
    }

    private fun selectTab(index: Int) {
        selectedTab = index
        toneRow.visibility = View.GONE
        adapter.items = when (index) {
            0 -> EmojiIndex.recents(prefs)
            kaomojiTab -> EmojiData.kaomoji
            symbolsTab -> supportedSymbols
            else -> supported(index - 1)
        }
        grid.columnWidth = if (index == kaomojiTab) dp(116f) else dp(44f)
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        tabs.invalidate()
        // Keep the chosen tab in view.
        tabScroll.post { tabScroll.smoothScrollTo(index * dp(TAB_DP) - (tabScroll.width - dp(TAB_DP)) / 2, 0) }
    }

    private inner class EmojiAdapter : BaseAdapter() {
        var items: List<String> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? EmojiCell) ?: EmojiCell(context).apply {
                gravity = Gravity.CENTER
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f))
                includeFontPadding = false
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, when (selectedTab) { kaomojiTab -> 14f; symbolsTab -> 22f; else -> 26f })
            tv.maxLines = 1
            tv.text = display(items[position])
            tv.setTextColor(host?.theme?.textColor ?: Color.BLACK)
            // A small corner mark shows which emoji have skin tones (long-press them).
            tv.hasTones = EmojiIndex.supportsSkinTone(items[position])
            tv.markColor = host?.theme?.secondaryTextColor ?: Color.GRAY
            return tv
        }
    }

    /** An emoji cell that draws a tiny triangle in its corner when the emoji has skin tone variants. */
    private class EmojiCell(context: Context) : TextView(context) {
        var hasTones = false
        var markColor = Color.GRAY
        private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mark = android.graphics.Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!hasTones) return
            val d = resources.displayMetrics.density
            val right = width - 5f * d
            val bottom = height - 5f * d
            val size = 5f * d
            mark.reset()
            mark.moveTo(right, bottom - size)
            mark.lineTo(right, bottom)
            mark.lineTo(right - size, bottom)
            mark.close()
            markPaint.color = markColor
            canvas.drawPath(mark, markPaint)
        }
    }

    /** The row of category icons. */
    private inner class CategoryTabs(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val count get() = EmojiData.categories.size + 3
        val totalCount get() = count

        override fun onDraw(canvas: Canvas) {
            val t = host?.theme ?: return
            val w = width.toFloat() / count
            val cy = height / 2f
            for (i in 0 until count) {
                val cx = w * i + w / 2f
                if (i == selectedTab) {
                    paint.color = t.accentSoft
                    canvas.drawCircle(cx, cy, minOf(w, height.toFloat()) * 0.46f, paint)
                }
                if (i == 0) {
                    Icons.draw(canvas, Icon.CLOCK, cx, cy, minOf(w, height.toFloat()) * 0.5f, t.textColor)
                } else if (i > EmojiData.categories.size) {
                    paint.color = t.textColor
                    paint.alpha = if (i == selectedTab) 255 else 150
                    paint.textSize = minOf(w * 0.62f, height * 0.5f)
                    val fm = paint.fontMetrics
                    canvas.drawText(if (i == kaomojiTab) "ツ" else "★", cx, cy - (fm.ascent + fm.descent) / 2f, paint)
                    paint.alpha = 255
                } else {
                    paint.color = Color.BLACK
                    paint.alpha = if (i == selectedTab) 255 else 150
                    paint.textSize = minOf(w * 0.62f, height * 0.5f)
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
        /** Width of each category tab in dp. */
        private const val TAB_DP = 48f

        private val supportedCache = HashMap<Int, List<String>>()

        /** Symbols the device font can draw. */
        private val supportedSymbols: List<String> by lazy {
            val paint = Paint()
            EmojiData.symbols.filter { paint.hasGlyph(it) }
        }

        /** Emoji in category [index] that the device font can actually draw. */
        fun supported(index: Int): List<String> = supportedCache.getOrPut(index) {
            val paint = Paint()
            EmojiData.categories[index].emoji.filter { paint.hasGlyph(it) }
        }
    }
}
