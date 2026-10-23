package com.flexkeys.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.view.WindowInsets
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.flexkeys.clipboard.ClipboardPanel
import com.flexkeys.edit.EditPanel
import com.flexkeys.emoji.EmojiIndex
import com.flexkeys.emoji.EmojiPanel
import com.flexkeys.emoji.EmojiSearchBar
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.suggest.SuggestionStripView
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.GlassBackdrop
import com.flexkeys.theme.GlassPainter
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/**
 * Top-level keyboard view, used both by the input method and by the live preview in the
 * settings app. Draws the shared backdrop, hosts the keyboard and draws its popups on top.
 */
class KeyboardRootView(context: Context, initialTheme: GlassTheme) : LinearLayout(context), GlassHost {

    override var theme: GlassTheme = initialTheme
        private set
    override val backdrop = GlassBackdrop().apply { setTheme(initialTheme) }
    override val painter = GlassPainter(initialTheme)

    val strip = SuggestionStripView(context)
    val keyboardView = KeyboardView(context)

    /** Holds the keyboard row and, on top of it, the emoji and clipboard panels. */
    private val content = PanelFrame(context).apply { clipChildren = false; clipToPadding = false }
    private val keyboardRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    /** Receives emoji, clipboard pastes and panel navigation from the panels. */
    interface PanelListener : EmojiPanel.Listener, ClipboardPanel.Listener, EditPanel.Listener

    enum class Panel { NONE, EMOJI, CLIPBOARD, EDIT }

    /** Replaces the suggestion strip while searching emoji. */
    private val emojiSearchBar = EmojiSearchBar(
        context,
        onPick = { emoji ->
            EmojiIndex.addRecent(Prefs.get(context), emoji)
            panelListener?.onEmoji(emoji)
        },
        onClose = { setEmojiSearch(false) },
    )
    var emojiSearching = false
        private set

    /** Set by the input method, whose glass continues behind the navigation bar. */
    var extendBehindNavigationBar = false

    var panelListener: PanelListener? = null
    var panel: Panel = Panel.NONE
        private set
    private var panelView: View? = null
    private val gutter = OneHandedGutter(context)

    private var gradient: Shader? = null

    private fun rebuildGradient() {
        gradient = if (width <= 0 || height <= 0 || theme.gradientStrength <= 0f) null else {
            LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), theme.gradientColors,
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun glassGradient(): Shader? = gradient

    private val sheetPath = Path()
    private val sheetRect = RectF()
    private val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var oneHandedMode = Prefs.ONE_HANDED_OFF

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        addView(emojiSearchBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        emojiSearchBar.visibility = View.GONE
        addView(strip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(keyboardRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        keyboardRow.addView(keyboardView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // In the input method the glass extends behind the navigation bar; keep the keys above it.
        setOnApplyWindowInsetsListener { _, insets ->
            if (!extendBehindNavigationBar) return@setOnApplyWindowInsetsListener insets
            val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            setPadding(0, 0, 0, bottom)
            insets
        }
    }

    /**
     * Sizes every panel to exactly the height of the keys, so the emoji grid or clipboard list
     * never makes the keyboard taller.
     */
    private class PanelFrame(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val keys = getChildAt(0)
            if (keys == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            measureChild(keys, widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = keys.measuredHeight
            for (i in 1 until childCount) {
                getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
                )
            }
            setMeasuredDimension(width, height)
        }
    }

    /** Shows the emoji or clipboard panel in place of the keys, or goes back to the keys. */
    fun showPanel(newPanel: Panel) {
        if (newPanel == panel) return
        keyboardView.cancelAll()
        panelView?.let { content.removeView(it) }
        panelView = null
        val l = panelListener
        if (newPanel != Panel.NONE && l != null) {
            val view = when (newPanel) {
                Panel.EMOJI -> EmojiPanel(context, l)
                Panel.EDIT -> EditPanel(context, l)
                else -> ClipboardPanel(context, l)
            }
            content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            panelView = view
            panel = newPanel
        } else {
            panel = Panel.NONE
        }
        keyboardRow.visibility = if (panel == Panel.NONE) View.VISIBLE else View.INVISIBLE
        strip.activeAction = when (panel) {
            Panel.EMOJI -> SuggestionStripView.Action.EMOJI
            Panel.CLIPBOARD -> SuggestionStripView.Action.CLIPBOARD
            Panel.EDIT -> SuggestionStripView.Action.EDIT
            Panel.NONE -> null
        }
        invalidate()
    }

    /**
     * Emoji search: the keys stay visible and type into the search bar. Closing the search
     * returns to the emoji panel.
     */
    fun setEmojiSearch(on: Boolean) {
        if (on == emojiSearching) return
        emojiSearching = on
        emojiSearchBar.setQuery("")
        emojiSearchBar.visibility = if (on) View.VISIBLE else View.GONE
        strip.visibility = if (on) View.GONE else View.VISIBLE
        showPanel(if (on) Panel.NONE else Panel.EMOJI)
    }

    /** Leaves emoji search without reopening the emoji panel (e.g. a new text field). */
    fun cancelEmojiSearch() {
        if (!emojiSearching) return
        emojiSearching = false
        emojiSearchBar.visibility = View.GONE
        strip.visibility = View.VISIBLE
    }

    /** While searching emoji, typed keys edit the query. Returns true if the key was used. */
    fun routeKeyToSearch(key: KeyDef, shifted: Boolean): Boolean {
        if (!emojiSearching) return false
        val q = emojiSearchBar.query
        when (key.type) {
            KeyType.CHAR -> emojiSearchBar.setQuery(q + if (key.isLetter && shifted) key.output.uppercase() else key.output)
            KeyType.SPACE -> emojiSearchBar.setQuery("$q ")
            KeyType.BACKSPACE -> if (q.isNotEmpty()) emojiSearchBar.setQuery(q.dropLast(1))
            KeyType.ENTER -> {
                val first = emojiSearchBar.firstResult()
                if (first != null) emojiSearchBar.setQuery("").also {
                    EmojiIndex.addRecent(Prefs.get(context), first)
                    panelListener?.onEmoji(first)
                } else {
                    setEmojiSearch(false)
                }
            }
            KeyType.EMOJI -> setEmojiSearch(false)
            else -> return false
        }
        return true
    }

    /** Popup characters while searching emoji. */
    fun routeTextToSearch(text: String): Boolean {
        if (!emojiSearching) return false
        emojiSearchBar.setQuery(emojiSearchBar.query + text)
        return true
    }

    /** [mode] is one of the Prefs.ONE_HANDED_* constants. */
    fun setOneHanded(mode: Int) {
        if (mode == oneHandedMode) return
        oneHandedMode = mode
        keyboardRow.removeAllViews()
        val keys = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, ONE_HANDED_WEIGHT)
        val side = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - ONE_HANDED_WEIGHT)
        when (mode) {
            Prefs.ONE_HANDED_LEFT -> {
                keyboardRow.addView(keyboardView, keys)
                keyboardRow.addView(gutter, side)
            }
            Prefs.ONE_HANDED_RIGHT -> {
                keyboardRow.addView(gutter, side)
                keyboardRow.addView(keyboardView, keys)
            }
            else -> keyboardRow.addView(keyboardView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        invalidate()
    }

    fun applyTheme(newTheme: GlassTheme) {
        theme = newTheme
        painter.theme = newTheme
        backdrop.setTheme(newTheme)
        rebuildGradient()
        keyboardView.onThemeChanged()
        strip.requestLayout()
        invalidateTree(this)
    }

    private var photoRequest = 0

    /** Loads the background photo (if any) off the main thread and redraws when ready. */
    fun reloadPhoto() {
        val request = ++photoRequest
        val app = context.applicationContext
        val maxDim = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        loader.execute {
            val bitmap = BackgroundImageStore.load(app, maxDim)
            post {
                if (request != photoRequest) {
                    bitmap?.recycle()
                    return@post
                }
                // The previous bitmap is not recycled: an already recorded display list may
                // still reference it. It is garbage collected once nothing draws it.
                backdrop.setPhoto(bitmap)
                invalidateTree(this)
            }
        }
    }

    private fun invalidateTree(view: View) {
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdrop.setSize(w, h)
        rebuildGradient()
        invalidateTree(this)
    }

    /** The whole keyboard is one sheet of frosted glass with rounded top corners. */
    override fun onDraw(canvas: Canvas) {
        val radius = theme.dp(SHEET_RADIUS_DP)
        sheetRect.set(0f, 0f, width.toFloat(), height + radius)
        sheetPath.reset()
        sheetPath.addRoundRect(sheetRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(sheetPath)
        backdrop.drawBlurred(canvas, 0f, 0f)
        sheetPaint.style = Paint.Style.FILL
        sheetPaint.color = theme.sheetTint
        canvas.drawRect(sheetRect, sheetPaint)
        gradient?.let {
            sheetPaint.shader = it
            sheetPaint.alpha = (theme.sheetGradientAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(sheetRect, sheetPaint)
            sheetPaint.shader = null
        }
        canvas.restore()
    }

    private val overlayOffset = PointF()

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (keyboardView.isShown && panel == Panel.NONE) {
            backdropOffset(keyboardView, overlayOffset)
            canvas.save()
            canvas.translate(overlayOffset.x, overlayOffset.y)
            keyboardView.drawOverlay(canvas)
            canvas.restore()
        }
    }

    override fun backdropOffset(view: View, out: PointF) {
        var x = 0f
        var y = 0f
        var v: View? = view
        while (v != null && v !== this) {
            x += v.left + v.translationX
            y += v.top + v.translationY
            val parent = v.parent as? View ?: break
            x -= parent.scrollX
            y -= parent.scrollY
            v = parent
        }
        out.set(x, y)
    }

    override fun invalidateOverlay() = invalidate()

    fun release() {
        photoRequest++
        backdrop.setPhoto(null)
        backdrop.release()
    }

    private companion object {
        const val ONE_HANDED_WEIGHT = 0.8f
        const val SHEET_RADIUS_DP = 26f
        val loader: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
