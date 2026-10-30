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
import com.flexkeys.phrases.PhrasesPanel
import com.flexkeys.stickers.StickerPanel
import com.flexkeys.suggest.MenuPanel
import com.flexkeys.gif.GifPanel
import com.flexkeys.ai.AiBar
import com.flexkeys.fancy.FancyBar
import com.flexkeys.look.LookBar
import com.flexkeys.translate.TranslateBar
import com.flexkeys.translate.TranslateListener
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
    interface PanelListener : EmojiPanel.Listener, ClipboardPanel.Listener, MenuPanel.Listener, PhrasesPanel.Listener, StickerPanel.Listener, GifPanel.Listener, TranslateListener, ToolListener

    enum class Panel { NONE, EMOJI, CLIPBOARD, GIF, PHRASES, MENU, STICKERS }

    /** The GIF search being shown or typed (kept while the GIF panel is closed and reopened). */
    private var gifQuery = ""
    var gifSearching = false
        private set

    /** The translator, shown in place of the suggestion strip while it is open. */
    private val translateBar = TranslateBar(
        context,
        onInsert = { text ->
            panelListener?.onTranslateInsert(text)
            endTranslate()
        },
        onClose = { endTranslate() },
    )
    private val fancyBar = FancyBar(
        context,
        onInsert = { text ->
            panelListener?.onToolInsert(text)
            endTool()
        },
        onClose = { endTool() },
    )

    private val lookBar = LookBar(context) { endTool() }

    private val aiBar = AiBar(
        context,
        sourceProvider = { panelListener?.toolSource(wholeField = true) ?: ToolSource("", false) },
        onReplace = { text, whole ->
            panelListener?.onToolReplace(text, whole)
            endTool()
        },
        onClose = { endTool() },
    )

    /** The tool bar (translator, fancy text...) currently replacing the suggestion strip, if any. */
    private var activeTool: ToolBar? = null
    val translating get() = activeTool != null

    /** Replaces the suggestion strip while searching emoji. */
    private val emojiSearchBar = EmojiSearchBar(
        context,
        onPick = { emoji ->
            EmojiIndex.addRecent(Prefs.get(context), emoji)
            panelListener?.onEmoji(emoji)
        },
        onClose = { if (gifSearching) endGifSearch(submit = false) else setEmojiSearch(false) },
    )
    var emojiSearching = false
        private set

    /** Set by the input method, whose glass continues behind the navigation bar. */
    var extendBehindNavigationBar = false

    var panelListener: PanelListener? = null
    var panel: Panel = Panel.NONE
        private set
    private var panelView: View? = null

    private var gradient: Shader? = null

    private fun rebuildGradient() {
        gradient = if (width <= 0 || height <= 0 || theme.gradientStrength <= 0f) null else {
            LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), theme.gradientColors,
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun glassGradient(): Shader? = gradient

    // ---- tilt shimmer: a soft highlight that slides across the glass as the phone moves ----
    private val shimmerPrefs = Prefs.get(context)
    /** True for a moment after each key press: heavy redraws wait so typing stays smooth. */
    private val typingBusy get() = android.os.SystemClock.uptimeMillis() - keyboardView.lastKeyDownAt < 450L

    private var lastTiltDraw = 0L

    private val tilt = TiltSensor(context) {
        // While typing, effects keep moving but redraw about half as often, so keys stay responsive.
        val now = android.os.SystemClock.uptimeMillis()
        if (!typingBusy || now - lastTiltDraw >= 66L) {
            lastTiltDraw = now
            invalidate()
            keyboardView.invalidate()
        }
    }
    private val glassAnimation = GlassAnimation()

    private val shimmerMatrix = android.graphics.Matrix()
    private val shimmerShader = LinearGradient(-0.5f, 0f, 0.5f, 0f,
        intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** True when animations should rest to save battery. */
    private val saving get() = shimmerPrefs.batterySaver && PowerState.isLow(context)

    private val shimmerOn get() = shimmerPrefs.shimmer && !saving
    private val shimmerAmount get() = shimmerPrefs.shimmerStrength.coerceIn(0, 100) / 100f

    override val shimmerX: Float get() = if (shimmerOn) tilt.x * shimmerAmount else 0f
    override val shimmerY: Float get() = if (shimmerOn) tilt.y * shimmerAmount else 0f

    /** The sensor only runs while the keyboard is on screen and the setting is on. */
    private fun syncShimmer() {
        val animationFollowsTilt = shimmerPrefs.glassAnimation != GlassAnimation.OFF && shimmerPrefs.animationTilt && !saving
        if ((shimmerOn || animationFollowsTilt) && isShown) tilt.start() else tilt.stop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncShimmer()
    }

    override fun onDetachedFromWindow() {
        tilt.stop()
        super.onDetachedFromWindow()
    }

    private val sheetPath = Path()
    private val sheetRect = RectF()
    private val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        addView(translateBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        translateBar.visibility = View.GONE
        addView(fancyBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fancyBar.visibility = View.GONE
        addView(lookBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        lookBar.visibility = View.GONE
        addView(aiBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        aiBar.visibility = View.GONE
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
        private companion object {
            const val GIF_HEIGHT_FACTOR = 1.45f
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val keys = getChildAt(0)
            if (keys == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            measureChild(keys, widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val width = MeasureSpec.getSize(widthMeasureSpec)
            var height = keys.measuredHeight
            for (i in 1 until childCount) {
                val child = getChildAt(i)
                // The GIF grid gets extra height: there is a lot to browse and scroll through.
                val h = if (child is GifPanel) (keys.measuredHeight * GIF_HEIGHT_FACTOR).toInt() else keys.measuredHeight
                child.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
                )
                height = maxOf(height, h)
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
                Panel.GIF -> GifPanel(context, l, gifQuery)
                Panel.PHRASES -> PhrasesPanel(context, l)
                Panel.STICKERS -> StickerPanel(context, l)
                Panel.MENU -> MenuPanel(context, l) { strip.invalidate() }
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
            Panel.GIF -> SuggestionStripView.Action.GIF
            Panel.PHRASES -> SuggestionStripView.Action.PHRASES
            Panel.STICKERS -> SuggestionStripView.Action.STICKERS
            Panel.MENU -> null
            Panel.NONE -> null
        }
        strip.menuOpen = panel == Panel.MENU
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

    /** GIF search: type on the keys, press return, and the GIF panel reopens with the results. */
    fun startGifSearch() {
        if (gifSearching) return
        gifSearching = true
        emojiSearchBar.setGifMode(true)
        emojiSearchBar.setQuery(gifQuery)
        emojiSearchBar.visibility = View.VISIBLE
        strip.visibility = View.GONE
        showPanel(Panel.NONE)
    }

    private fun endGifSearch(submit: Boolean) {
        if (!gifSearching) return
        if (submit) gifQuery = emojiSearchBar.query.trim()
        gifSearching = false
        emojiSearchBar.setGifMode(false)
        emojiSearchBar.visibility = View.GONE
        strip.visibility = View.VISIBLE
        showPanel(Panel.GIF)
    }

    /** Opens the translator: type on the keys (or paste a copied message) to translate. */
    fun startTranslate() = startTool(translateBar)

    fun endTranslate() {
        if (activeTool === translateBar) endTool()
    }

    /** Opens fancy text, starting from [text] (the user's selection, possibly empty). */
    fun startFancy(text: String) {
        startTool(fancyBar)
        fancyBar.start(text)
    }

    /** Opens the live look controls above the keys. */
    fun startLook() = startTool(lookBar)

    /** Called after the text field changed, so AI rewrite can pick up what was typed. */
    fun refreshTools() {
        if (activeTool === aiBar) aiBar.refreshSource()
    }

    /** Opens AI rewrite on [source]: the selection, or the whole field when nothing is selected. */
    fun startAi(source: ToolSource) {
        startTool(aiBar)
        aiBar.start(source.text, source.isSelection)
    }

    private fun startTool(bar: ToolBar) {
        if (activeTool === bar) return
        if (activeTool != null) endTool()
        cancelEmojiSearch()
        showPanel(Panel.NONE)
        activeTool = bar
        bar.reset()
        (bar as View).visibility = View.VISIBLE
        strip.visibility = View.GONE
    }

    fun endTool() {
        val bar = activeTool ?: return
        activeTool = null
        bar.reset()
        (bar as View).visibility = View.GONE
        strip.visibility = View.VISIBLE
    }

    private fun routeKeyToTool(bar: ToolBar, key: KeyDef, shifted: Boolean): Boolean {
        val q = bar.query
        when (key.type) {
            KeyType.CHAR -> bar.setQuery(q + if (key.isLetter && shifted) key.output.uppercase() else key.output)
            KeyType.SPACE -> bar.setQuery("$q ")
            KeyType.BACKSPACE -> if (q.isNotEmpty()) bar.setQuery(q.dropLast(1))
            KeyType.ENTER -> bar.onEnter()
            KeyType.EMOJI -> endTool()
            else -> return false
        }
        return true
    }

    /** Leaves any emoji or GIF search without opening a panel (e.g. a new text field). */
    fun cancelEmojiSearch() {
        endTool()
        if (gifSearching) {
            gifSearching = false
            emojiSearchBar.setGifMode(false)
        }
        if (!emojiSearching && emojiSearchBar.visibility != View.VISIBLE) return
        emojiSearching = false
        emojiSearchBar.visibility = View.GONE
        strip.visibility = View.VISIBLE
    }

    /** While searching emoji, typed keys edit the query. Returns true if the key was used. */
    fun routeKeyToSearch(key: KeyDef, shifted: Boolean): Boolean {
        activeTool?.let {
            if (it.capturesTyping) return routeKeyToTool(it, key, shifted)
            if (key.type == KeyType.EMOJI) endTool()
            return false
        }
        if (!emojiSearching && !gifSearching) return false
        val q = emojiSearchBar.query
        when (key.type) {
            KeyType.CHAR -> emojiSearchBar.setQuery(q + if (key.isLetter && shifted) key.output.uppercase() else key.output)
            KeyType.SPACE -> emojiSearchBar.setQuery("$q ")
            KeyType.BACKSPACE -> if (q.isNotEmpty()) emojiSearchBar.setQuery(q.dropLast(1))
            KeyType.ENTER -> if (gifSearching) {
                endGifSearch(submit = true)
            } else {
                val first = emojiSearchBar.firstResult()
                if (first != null) emojiSearchBar.setQuery("").also {
                    EmojiIndex.addRecent(Prefs.get(context), first)
                    panelListener?.onEmoji(first)
                } else {
                    setEmojiSearch(false)
                }
            }
            KeyType.EMOJI -> if (gifSearching) endGifSearch(submit = false) else setEmojiSearch(false)
            else -> return false
        }
        return true
    }

    /** Popup characters while searching emoji. */
    fun routeTextToSearch(text: String): Boolean {
        activeTool?.let {
            if (!it.capturesTyping) return false
            it.setQuery(it.query + text)
            return true
        }
        if (!emojiSearching && !gifSearching) return false
        emojiSearchBar.setQuery(emojiSearchBar.query + text)
        return true
    }

    fun applyTheme(newTheme: GlassTheme) {
        theme = newTheme
        painter.theme = newTheme
        backdrop.setTheme(newTheme)
        rebuildGradient()
        translateBar.restyle()
        fancyBar.restyle()
        aiBar.restyle()
        lookBar.restyle()
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
        val animStyle = shimmerPrefs.glassAnimation
        if (animStyle != GlassAnimation.OFF && isShown && !saving) {
            glassAnimation.tick(android.os.SystemClock.uptimeMillis(), shimmerPrefs.animationSpeed.coerceIn(0, 100) / 100f)
            glassAnimation.draw(canvas, animStyle, width.toFloat(), height.toFloat(),
                shimmerPrefs.animationIntensity.coerceIn(0, 100) / 100f, theme,
                if (shimmerPrefs.animationTilt) tilt.x else 0f, if (shimmerPrefs.animationTilt) tilt.y else 0f)
            // About 30 frames a second is smooth enough for slow motion and easy on the battery; about 15 while typing.
            postInvalidateDelayed(if (typingBusy) 66L else 33L)
        } else {
            glassAnimation.pause()
        }
        syncShimmer()
        if (shimmerOn && tilt.running) {
            // A wide, soft band of light whose position follows the tilt.
            val bandWidth = width * 0.8f
            val centerX = width * (0.5f + 0.6f * tilt.x * shimmerAmount.coerceAtLeast(0.2f)) - tilt.y * width * 0.1f
            shimmerMatrix.setScale(bandWidth, bandWidth)
            shimmerMatrix.postRotate(24f)
            shimmerMatrix.postTranslate(centerX, height * 0.4f)
            shimmerShader.setLocalMatrix(shimmerMatrix)
            shimmerPaint.shader = shimmerShader
            shimmerPaint.alpha = (255f * (if (theme.dark) 0.16f else 0.30f) * shimmerAmount).toInt().coerceIn(0, 255)
            canvas.drawRect(sheetRect, shimmerPaint)
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
        const val SHEET_RADIUS_DP = 26f
        val loader: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
