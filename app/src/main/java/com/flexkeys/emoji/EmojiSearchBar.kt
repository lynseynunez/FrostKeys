package com.flexkeys.emoji

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.keyboard.Icon
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassHost
import com.flexkeys.ui.findGlassHost

/**
 * Shown above the keys while searching emoji: the query being typed and a scrollable row of
 * matching emoji. Typed keys are routed here by [com.flexkeys.ui.KeyboardRootView].
 */
class EmojiSearchBar(
    context: Context,
    private val onPick: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    var query = ""
        private set
    /** GIF mode: only the query box (results appear in the GIF panel after pressing return). */
    private var gifMode = false
    private val prefs = Prefs.get(context)
    private val queryView = TextView(context)
    private val results = LinearLayout(context)
    private val scroll = HorizontalScrollView(context)
    private var host: GlassHost? = null

    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), 0)
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(GlassButton(context, icon = Icon.ARROW_LEFT).apply { onClick = { onClose() } },
            LayoutParams(dp(44f), dp(40f)))
        queryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        queryView.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        queryView.maxLines = 1
        queryView.ellipsize = TextUtils.TruncateAt.START
        top.addView(queryView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) })
        addView(top)
        results.orientation = HORIZONTAL
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(results)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50f)))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host = findGlassHost()
        render()
    }

    fun setQuery(q: String) {
        query = q
        render()
    }

    fun setGifMode(on: Boolean) {
        gifMode = on
        scroll.visibility = if (on) GONE else VISIBLE
        render()
    }

    private fun render() {
        val t = host?.theme
        queryView.background = GradientDrawable().apply {
            cornerRadius = dp(20f).toFloat()
            setColor(t?.keyFill ?: 0x33FFFFFF)
        }
        queryView.setTextColor(if (query.isEmpty()) t?.secondaryTextColor ?: 0x99000000.toInt() else t?.textColor ?: 0xFF000000.toInt())
        queryView.text = if (query.isEmpty()) (if (gifMode) "Search GIFs, then press return" else "Search emoji") else query
        results.removeAllViews()
        if (gifMode) return
        val found = if (query.isBlank()) EmojiIndex.recents(prefs) else EmojiIndex.search(query)
        for (emoji in found) {
            results.addView(TextView(context).apply {
                text = emoji
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setOnClickListener { onPick(emoji) }
            }, LayoutParams(dp(50f), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        if (found.isEmpty()) {
            results.addView(TextView(context).apply {
                text = if (query.isBlank()) "Type to search" else "No emoji found"
                setTextColor(t?.secondaryTextColor ?: 0x99000000.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), 0, 0, 0)
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        scroll.scrollTo(0, 0)
    }

    /** The best match, used when return is pressed. */
    fun firstResult(): String? = if (query.isBlank()) null else EmojiIndex.search(query, 1).firstOrNull()
}
