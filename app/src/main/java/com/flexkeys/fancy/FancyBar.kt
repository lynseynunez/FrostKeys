package com.flexkeys.fancy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.ToolBar
import com.flexkeys.ui.findGlassHost

/**
 * Fancy text: pick a style, type on the keys (or start from selected text) and see the restyled
 * text live. Insert puts it into the field.
 */
class FancyBar(
    context: Context,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context), ToolBar {

    override var query = ""
        private set

    private val prefs = Prefs.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private var host: GlassHost? = null

    private val styles = FancyText.Style.values()
    private val chips = styles.map { chip(FancyText.sample(it)) }
    private val chipRow = LinearLayout(context)
    private val chipScroll = HorizontalScrollView(context)
    private val inputView = TextView(context)
    private val resultView = TextView(context)
    private val resultScroll = ScrollView(context)
    private val insertChip = chip("Insert")
    private val copyChip = chip("Copy")

    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun chip(label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(14f), 0, dp(14f), 0)
        maxLines = 1
    }

    private val style get() = styles[prefs.fancyStyle.coerceIn(0, styles.size - 1)]

    init {
        orientation = VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), dp(2f))

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(GlassButton(context, icon = Icon.ARROW_LEFT).apply { onClick = { onClose() } }, LayoutParams(dp(44f), dp(40f)))
        chipRow.orientation = HORIZONTAL
        chips.forEachIndexed { i, c ->
            c.setOnClickListener {
                prefs.fancyStyle = i
                render()
            }
            chipRow.addView(c, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginEnd = dp(6f) })
        }
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chipRow)
        header.addView(chipScroll, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f) })
        addView(header)

        inputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        inputView.maxLines = 2
        inputView.ellipsize = android.text.TextUtils.TruncateAt.START
        addView(inputView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        resultView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        resultScroll.addView(resultView)
        addView(resultScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54f)).apply { topMargin = dp(4f) })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(copyChip, LayoutParams(0, dp(34f), 1f))
        actions.addView(insertChip, LayoutParams(0, dp(34f), 1f).apply { marginStart = dp(6f) })
        addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4f) })

        insertChip.setOnClickListener { onEnter() }
        copyChip.setOnClickListener { copyResult() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        restyle()
    }

    override fun restyle() {
        val t = host?.theme ?: return
        fun pill(color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        copyChip.background = pill(t.keyFill, dp(17f).toFloat())
        copyChip.setTextColor(t.textColor)
        insertChip.background = pill(t.accentFill, dp(17f).toFloat())
        insertChip.setTextColor(t.onAccent)
        inputView.background = pill(t.keyFill, dp(20f).toFloat())
        resultScroll.background = pill(t.keyFill, dp(20f).toFloat())
        render()
    }

    override fun reset() {
        query = ""
        render()
    }

    /** Starts from existing text, such as the user's selection. */
    fun start(text: String) {
        query = text.take(MAX_LENGTH)
        render()
        chipScroll.post { chipScroll.scrollTo(chips[prefs.fancyStyle.coerceIn(0, chips.size - 1)].left - dp(8f), 0) }
    }

    override fun setQuery(q: String) {
        query = q.take(MAX_LENGTH)
        render()
    }

    override fun onEnter() {
        if (query.isNotEmpty()) onInsert(FancyText.apply(style, query))
    }

    private fun render() {
        val t = host?.theme
        val secondary = t?.secondaryTextColor ?: 0x99000000.toInt()
        val primary = t?.textColor ?: 0xFF000000.toInt()
        inputView.text = if (query.isEmpty()) "Type on the keys" else query
        inputView.setTextColor(if (query.isEmpty()) secondary else primary)
        resultView.text = if (query.isEmpty()) "Styled text appears here" else FancyText.apply(style, query)
        resultView.setTextColor(if (query.isEmpty()) secondary else primary)
        val selected = prefs.fancyStyle.coerceIn(0, chips.size - 1)
        chips.forEachIndexed { i, c ->
            c.background = GradientDrawable().apply {
                cornerRadius = dp(17f).toFloat()
                setColor(if (i == selected) t?.accentFill ?: 0x66FFFFFF else t?.keyFill ?: 0x33FFFFFF)
            }
            c.setTextColor(if (i == selected) t?.onAccent ?: 0xFFFFFFFF.toInt() else primary)
        }
        val ready = query.isNotEmpty()
        insertChip.alpha = if (ready) 1f else 0.4f
        copyChip.alpha = if (ready) 1f else 0.4f
    }

    private fun copyResult() {
        if (query.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("fancy text", FancyText.apply(style, query)))
        copyChip.text = "Copied"
        handler.postDelayed({ copyChip.text = "Copy" }, 1200L)
    }

    private companion object {
        const val MAX_LENGTH = 2000
    }
}
