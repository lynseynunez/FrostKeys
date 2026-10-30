package com.flexkeys.texttools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.ToolBar
import com.flexkeys.ui.ToolSource
import com.flexkeys.ui.findGlassHost

/**
 * Text tools: pick a fix (UPPERCASE, Title Case, bullets, tidy spaces...) and see the result for the
 * selected text, or the whole field if nothing is selected. Replace puts it back. Nothing leaves the phone.
 */
class TextToolsBar(
    context: Context,
    private val sourceProvider: () -> ToolSource,
    private val onReplace: (text: String, wholeField: Boolean) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context), ToolBar {

    override val query = ""
    override val capturesTyping get() = false

    private var source = ""
    private var wholeField = true
    private var result = ""
    private var selected = -1
    private val handler = Handler(Looper.getMainLooper())
    private var host: GlassHost? = null

    private val tools = TextTools.Tool.values()
    private val chips = tools.map { chip(it.label) }
    private val sourceView = TextView(context)
    private val resultView = TextView(context)
    private val resultScroll = ScrollView(context)
    private val replaceChip = chip("Replace")
    private val copyChip = chip("Copy")

    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun chip(label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(14f), 0, dp(14f), 0)
        maxLines = 1
    }

    init {
        orientation = VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), dp(2f))
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(GlassButton(context, icon = Icon.ARROW_LEFT).apply { onClick = { onClose() } }, LayoutParams(dp(44f), dp(40f)))
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        chips.forEachIndexed { i, c ->
            c.setOnClickListener { run(i) }
            row.addView(c, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginEnd = dp(6f) })
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        header.addView(scroll, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f) })
        addView(header)

        sourceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        sourceView.maxLines = 1
        sourceView.ellipsize = TextUtils.TruncateAt.END
        sourceView.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        addView(sourceView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        resultView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        resultScroll.addView(resultView)
        addView(resultScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72f)).apply { topMargin = dp(4f) })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(copyChip, LayoutParams(0, dp(34f), 1f))
        actions.addView(replaceChip, LayoutParams(0, dp(34f), 1f).apply { marginStart = dp(6f) })
        addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4f) })
        replaceChip.setOnClickListener { if (result.isNotEmpty()) onReplace(result, wholeField) }
        copyChip.setOnClickListener { copyResult() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        restyle()
    }

    /** Opens on [text]: the selection, or the whole field when nothing is selected. */
    fun start(text: String, isSelection: Boolean) {
        source = text
        wholeField = !isSelection
        selected = -1
        result = ""
        render()
    }

    /** Re-reads the field after it changed, so the tool works on what is there now. */
    fun refreshSource() {
        val s = sourceProvider()
        source = s.text
        wholeField = !s.isSelection
        if (selected >= 0) result = TextTools.apply(tools[selected], source)
        render()
    }

    private fun run(index: Int) {
        refreshSource()
        selected = index
        result = if (source.isBlank()) "" else TextTools.apply(tools[index], source)
        render()
    }

    override fun setQuery(q: String) = Unit
    override fun onEnter() = Unit
    override fun reset() {
        source = ""
        result = ""
        selected = -1
        render()
    }

    override fun restyle() {
        val t = host?.theme ?: return
        fun pill(color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        copyChip.background = pill(t.keyFill, dp(17f).toFloat())
        copyChip.setTextColor(t.textColor)
        replaceChip.background = pill(t.accentFill, dp(17f).toFloat())
        replaceChip.setTextColor(t.onAccent)
        sourceView.background = pill(t.keyFill, dp(18f).toFloat())
        resultScroll.background = pill(t.keyFill, dp(20f).toFloat())
        render()
    }

    private fun render() {
        val t = host?.theme
        val secondary = t?.secondaryTextColor ?: 0x99000000.toInt()
        val primary = t?.textColor ?: 0xFF000000.toInt()
        sourceView.text = when {
            source.isBlank() -> "Type in the field, or select text, then pick a tool"
            wholeField -> "Whole field: $source"
            else -> "Selected: $source"
        }
        sourceView.setTextColor(secondary)
        resultView.text = result.ifEmpty { "The result appears here" }
        resultView.setTextColor(if (result.isEmpty()) secondary else primary)
        chips.forEachIndexed { i, c ->
            val on = i == selected
            c.background = GradientDrawable().apply {
                cornerRadius = dp(17f).toFloat()
                setColor(if (on) t?.accentFill ?: 0x66FFFFFF else t?.keyFill ?: 0x33FFFFFF)
            }
            c.setTextColor(if (on) t?.onAccent ?: 0xFFFFFFFF.toInt() else primary)
        }
        replaceChip.alpha = if (result.isNotEmpty()) 1f else 0.4f
        copyChip.alpha = if (result.isNotEmpty()) 1f else 0.4f
    }

    private fun copyResult() {
        if (result.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text tools", result))
        copyChip.text = "Copied"
        handler.postDelayed({ copyChip.text = "Copy" }, 1200L)
    }
}
