package com.flexkeys.ai

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
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.ToolBar
import com.flexkeys.ui.ToolSource
import com.flexkeys.ui.findGlassHost

/**
 * AI rewrite: shows the selected text (or the whole field), and a row of styles. Tap one and the
 * rewritten text appears; Replace puts it back into the field. Typing on the keys writes a custom
 * instruction, sent with the return key.
 */
class AiBar(
    context: Context,
    private val sourceProvider: () -> ToolSource,
    private val onReplace: (text: String, wholeField: Boolean) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context), ToolBar {

    /** The custom instruction typed on the keys. */
    override var query = ""
        private set

    /** Only while writing a custom instruction does typing on the keys go into the bar. */
    private var instructionMode = false
    override val capturesTyping get() = instructionMode

    private var source = ""
    private var wholeField = true
    private var result = ""
    private var status: String? = null
    private var generation = 0

    private val prefs = Prefs.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private var host: GlassHost? = null

    private val modes = GeminiClient.Mode.values()
    private val chips = modes.map { chip(it.label) } + chip("Custom")
    private val chipScroll = HorizontalScrollView(context)
    private val sourceView = TextView(context)
    private val instructionView = TextView(context)
    private val inputBox = LinearLayout(context)
    private val resultView = TextView(context)
    private val resultScroll = ScrollView(context)
    private val replaceChip = chip("Replace")
    private val copyChip = chip("Copy")
    private var selectedMode = -1

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
            c.setOnClickListener { if (i < modes.size) run(i) else toggleInstruction() }
            row.addView(c, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginEnd = dp(6f) })
        }
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(row)
        header.addView(chipScroll, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f) })
        addView(header)

        inputBox.orientation = VERTICAL
        inputBox.setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
        sourceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        sourceView.maxLines = 1
        sourceView.ellipsize = TextUtils.TruncateAt.END
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        instructionView.maxLines = 1
        instructionView.ellipsize = TextUtils.TruncateAt.START
        inputBox.addView(sourceView)
        inputBox.addView(instructionView)
        addView(inputBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        resultView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        resultScroll.addView(resultView)
        addView(resultScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54f)).apply { topMargin = dp(4f) })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(copyChip, LayoutParams(0, dp(34f), 1f))
        actions.addView(replaceChip, LayoutParams(0, dp(34f), 1f).apply { marginStart = dp(6f) })
        addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4f) })

        replaceChip.setOnClickListener { if (ready()) onReplace(result, wholeField) }
        copyChip.setOnClickListener { copyResult() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        restyle()
    }

    override fun onDetachedFromWindow() {
        generation++
        super.onDetachedFromWindow()
    }

    /** Opens on [text]: the selection, or the whole field when nothing is selected. */
    fun start(text: String, isSelection: Boolean) {
        source = text
        wholeField = !isSelection
        render()
    }

    /** Re-reads the field, so text typed (or selected) after opening is picked up. */
    fun refreshSource() {
        val s = sourceProvider()
        source = s.text
        wholeField = !s.isSelection
        render()
    }

    private fun toggleInstruction() {
        instructionMode = !instructionMode
        if (!instructionMode) query = ""
        selectedMode = if (instructionMode) modes.size else -1
        render()
    }

    override fun restyle() {
        val t = host?.theme ?: return
        fun pill(color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        copyChip.background = pill(t.keyFill, dp(17f).toFloat())
        copyChip.setTextColor(t.textColor)
        replaceChip.background = pill(t.accentFill, dp(17f).toFloat())
        replaceChip.setTextColor(t.onAccent)
        inputBox.background = pill(t.keyFill, dp(20f).toFloat())
        resultScroll.background = pill(t.keyFill, dp(20f).toFloat())
        render()
    }

    override fun reset() {
        generation++
        query = ""
        instructionMode = false
        source = ""
        result = ""
        status = null
        selectedMode = -1
        render()
    }

    override fun setQuery(q: String) {
        query = q.take(200)
        render()
    }

    /** Return key: use the custom instruction, or put the finished text in the field. */
    override fun onEnter() {
        when {
            query.isNotBlank() -> send(query.trim(), -1)
            ready() -> onReplace(result, wholeField)
        }
    }

    private fun ready() = result.isNotEmpty() && status == null

    private fun run(index: Int) {
        instructionMode = false
        query = ""
        send(modes[index].instruction, index)
    }

    private fun send(instruction: String, modeIndex: Int) {
        selectedMode = modeIndex
        refreshSource()
        if (source.isBlank()) {
            status = "Type something in the field first, or select the text to rewrite."
            result = ""
            render()
            return
        }
        if (!prefs.aiEnabled || prefs.geminiKey.isBlank()) {
            status = "Turn on AI rewrite and add your free Gemini key in SakuriKey settings."
            result = ""
            render()
            return
        }
        val gen = ++generation
        status = "Writing…"
        result = ""
        render()
        GeminiClient.rewrite(prefs.geminiKey, prefs.geminiModel, instruction, source) { text, error ->
            if (gen != generation) return@rewrite
            status = error
            result = text ?: ""
            render()
        }
    }

    private fun render() {
        val t = host?.theme
        val secondary = t?.secondaryTextColor ?: 0x99000000.toInt()
        val primary = t?.textColor ?: 0xFF000000.toInt()
        sourceView.text = when {
            source.isBlank() -> "Type in the field, or select text, then pick a style"
            wholeField -> "Whole field: $source"
            else -> "Selected: $source"
        }
        sourceView.setTextColor(secondary)
        instructionView.visibility = if (instructionMode) VISIBLE else GONE
        instructionView.text = if (query.isEmpty()) "Type your instruction on the keys, then return" else query
        instructionView.setTextColor(if (query.isEmpty()) secondary else primary)
        resultView.text = status ?: result.ifEmpty { "The rewrite appears here" }
        resultView.setTextColor(if (status != null || result.isEmpty()) secondary else primary)
        chips.forEachIndexed { i, c ->
            val on = i == selectedMode
            c.background = GradientDrawable().apply {
                cornerRadius = dp(17f).toFloat()
                setColor(if (on) t?.accentFill ?: 0x66FFFFFF else t?.keyFill ?: 0x33FFFFFF)
            }
            c.setTextColor(if (on) t?.onAccent ?: 0xFFFFFFFF.toInt() else primary)
        }
        replaceChip.alpha = if (ready()) 1f else 0.4f
        copyChip.alpha = if (ready()) 1f else 0.4f
    }

    private fun copyResult() {
        if (!ready()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("rewrite", result))
        copyChip.text = "Copied"
        handler.postDelayed({ copyChip.text = "Copy" }, 1200L)
    }
}
