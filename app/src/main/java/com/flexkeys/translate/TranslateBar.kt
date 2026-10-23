package com.flexkeys.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.keyboard.Icon
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.findGlassHost

/** Receives the finished translation to put into the text field. */
interface TranslateListener {
    fun onTranslateInsert(text: String)
}

/**
 * The translator, shown above the keys while it is open. Type on the keys (or tap Paste to bring in
 * a message you copied) and the translation appears; Insert puts it into the text field.
 */
class TranslateBar(
    context: Context,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    var query = ""
        private set
    private var result = ""
    /** "Translating…" or an error, shown in place of the result. */
    private var status: String? = null
    private var generation = 0
    private var picking: Boolean? = null // true: choosing the source language, false: the target

    private val prefs = Prefs.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private val debounce = Runnable { translateNow() }
    private var host: GlassHost? = null

    private val fromChip = chip()
    private val toChip = chip()
    private val pasteChip = chip()
    private val insertChip = chip()
    private val copyChip = chip()
    private val inputView = TextView(context)
    private val resultView = TextView(context)
    private val resultScroll = ScrollView(context)
    private val body = LinearLayout(context)
    private val picker = ScrollView(context)
    private val pickerList = LinearLayout(context)

    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun chip() = TextView(context).apply {
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
        header.addView(fromChip, LayoutParams(0, dp(36f), 1f).apply { marginStart = dp(4f) })
        header.addView(GlassButton(context, icon = Icon.SWAP).apply { onClick = { swap() } }, LayoutParams(dp(40f), dp(40f)))
        header.addView(toChip, LayoutParams(0, dp(36f), 1f))
        header.addView(pasteChip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginStart = dp(6f) })
        addView(header)

        body.orientation = VERTICAL
        inputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        inputView.maxLines = 2
        inputView.ellipsize = android.text.TextUtils.TruncateAt.START
        body.addView(inputView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        resultView.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
        resultScroll.addView(resultView)
        body.addView(resultScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54f)).apply { topMargin = dp(4f) })
        // Copy and Insert side by side under the result.
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(copyChip, LayoutParams(0, dp(34f), 1f))
        actions.addView(insertChip, LayoutParams(0, dp(34f), 1f).apply { marginStart = dp(6f) })
        body.addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4f) })
        addView(body)

        pickerList.orientation = VERTICAL
        picker.addView(pickerList)
        picker.visibility = GONE
        addView(picker, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176f)).apply { topMargin = dp(6f) })

        fromChip.setOnClickListener { togglePicker(true) }
        toChip.setOnClickListener { togglePicker(false) }
        pasteChip.text = "Paste"
        pasteChip.setOnClickListener { pasteFromClipboard() }
        insertChip.text = "Insert"
        insertChip.setOnClickListener { if (result.isNotEmpty() && status == null) onInsert(result) }
        copyChip.text = "Copy"
        copyChip.setOnClickListener { copyResult() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        restyle()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(debounce)
        super.onDetachedFromWindow()
    }

    /** Colors follow the glass theme; call after the theme changed. */
    fun restyle() {
        val t = host?.theme ?: return
        fun pill(color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        for (c in listOf(fromChip, toChip, pasteChip, copyChip)) {
            c.background = pill(t.keyFill, dp(17f).toFloat())
            c.setTextColor(t.textColor)
        }
        insertChip.background = pill(t.accentFill, dp(17f).toFloat())
        insertChip.setTextColor(t.onAccent)
        inputView.background = pill(t.keyFill, dp(20f).toFloat())
        resultScroll.background = pill(t.keyFill, dp(20f).toFloat())
        render()
    }

    /** Clears everything for a fresh start. */
    fun reset() {
        handler.removeCallbacks(debounce)
        generation++
        query = ""
        result = ""
        status = null
        hidePicker()
        render()
    }

    fun setQuery(q: String) {
        query = q.take(2000)
        handler.removeCallbacks(debounce)
        if (query.isBlank()) {
            generation++
            result = ""
            status = null
            render()
        } else {
            render()
            handler.postDelayed(debounce, 700L)
        }
    }

    /** Return key: insert the translation if there is one, otherwise translate now. */
    fun onEnter() {
        if (result.isNotEmpty() && status == null) onInsert(result) else translateNow()
    }

    private fun translateNow() {
        handler.removeCallbacks(debounce)
        val text = query.trim()
        if (text.isEmpty()) {
            result = ""
            status = null
            render()
            return
        }
        if (!prefs.translatorEnabled) {
            result = ""
            status = "The translator is turned off in FlexKeys settings."
            render()
            return
        }
        val gen = ++generation
        status = "Translating…"
        render()
        MyMemoryClient.translate(text, prefs.translateFrom, prefs.translateTo, prefs.translateEmail) { translated, error ->
            if (gen != generation) return@translate
            status = error
            result = translated ?: ""
            render()
        }
    }

    private fun render() {
        val t = host?.theme
        val secondary = t?.secondaryTextColor ?: 0x99000000.toInt()
        val primary = t?.textColor ?: 0xFF000000.toInt()
        inputView.text = if (query.isEmpty()) "Type on the keys, or tap Paste" else query
        inputView.setTextColor(if (query.isEmpty()) secondary else primary)
        fromChip.text = Languages.nameOf(prefs.translateFrom)
        toChip.text = Languages.nameOf(prefs.translateTo)
        resultView.text = status ?: result.ifEmpty { "Translation appears here" }
        resultView.setTextColor(if (status != null || result.isEmpty()) secondary else primary)
        val ready = result.isNotEmpty() && status == null
        insertChip.alpha = if (ready) 1f else 0.4f
        copyChip.alpha = if (ready) 1f else 0.4f
    }

    // ---- actions ----------------------------------------------------------------------------------

    private fun swap() {
        val from = prefs.translateFrom
        val to = prefs.translateTo
        if (from == Languages.AUTO) {
            prefs.translateFrom = to
            prefs.translateTo = if (to == "en") "es" else "en"
        } else {
            prefs.translateFrom = to
            prefs.translateTo = from
        }
        render()
        if (query.isNotBlank()) translateNow()
    }

    private fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = try {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        } catch (e: SecurityException) {
            null
        }
        val text = clip?.takeIf { it.isNotBlank() } ?: ClipboardStore.get(context).items().firstOrNull()?.text
        if (text.isNullOrBlank()) {
            status = "Nothing to paste. Copy a message first."
            render()
            return
        }
        query = text.take(2000)
        translateNow()
    }

    private fun copyResult() {
        if (result.isEmpty() || status != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", result))
        copyChip.text = "Copied"
        handler.postDelayed({ copyChip.text = "Copy" }, 1200L)
    }

    // ---- language picker ------------------------------------------------------------------------------

    private fun togglePicker(source: Boolean) {
        if (picking == source) hidePicker() else showPicker(source)
    }

    private fun showPicker(source: Boolean) {
        picking = source
        val t = host?.theme
        val current = if (source) prefs.translateFrom else prefs.translateTo
        pickerList.removeAllViews()
        for (language in if (source) Languages.sources else Languages.all) {
            val selected = language.code == current
            pickerList.addView(TextView(context).apply {
                text = language.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (selected) t?.onAccent ?: 0xFFFFFFFF.toInt() else t?.textColor ?: 0xFF000000.toInt())
                setPadding(dp(16f), 0, dp(16f), 0)
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(16f).toFloat()
                    setColor(if (selected) t?.accentFill ?: 0x66FFFFFF else 0x00000000)
                }
                setOnClickListener {
                    if (source) prefs.translateFrom = language.code else prefs.translateTo = language.code
                    hidePicker()
                    render()
                    if (query.isNotBlank()) translateNow()
                }
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)))
        }
        picker.scrollTo(0, 0)
        picker.visibility = VISIBLE
        body.visibility = GONE
    }

    private fun hidePicker() {
        picking = null
        picker.visibility = GONE
        body.visibility = VISIBLE
    }
}
