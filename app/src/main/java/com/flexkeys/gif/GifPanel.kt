package com.flexkeys.gif

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassTiles
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout
import java.util.UUID

/**
 * GIF search: a search box, category chips and a two-column grid of Klipy GIFs that loads more
 * as you scroll. Tapping a GIF sends it to the app.
 */
class GifPanel(
    context: Context,
    private val listener: Listener,
    initialQuery: String,
) : GlassPanelLayout(context) {

    interface Listener {
        fun onGifPicked(item: GifItem)
        /** The search box was tapped: type on the keyboard keys, then press return. */
        fun onGifSearchRequested()
        fun onPanelClosed()
        fun onOpenSettings()
    }

    private val prefs = Prefs.get(context)
    private var query = initialQuery.trim()
    private var page = 1
    private var hasMore = false
    private var loading = false
    private var generation = 0
    /** Showing the starred GIFs instead of a search. */
    private var showingFavorites = false

    private val searchBox = TextView(context)
    private val chips = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val columns = Array(2) { LinearLayout(context).apply { orientation = VERTICAL } }
    private val columnWeight = FloatArray(2)
    private val status = TextView(context)
    private val settingsButton = TextView(context)

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(2f))

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(GlassButton(context, label = "ABC").apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(52f), dp(40f)))
        searchBox.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            maxLines = 1
            setOnClickListener {
                showingFavorites = false
                listener.onGifSearchRequested()
            }
        }
        header.addView(searchBox, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6f) })
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        chips.orientation = HORIZONTAL
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38f)))

        val body = FrameLayout(context)
        val grid = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(2f), 0, dp(2f))
        }
        columns.forEachIndexed { i, col ->
            grid.addView(col, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i == 0) marginEnd = dp(3f) else marginStart = dp(3f)
            })
        }
        scroll.addView(grid)
        scroll.isVerticalScrollBarEnabled = false
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val content = scroll.getChildAt(0) ?: return@setOnScrollChangeListener
            if (hasMore && !loading && y + scroll.height >= content.height - dp(160f)) load(reset = false)
        }
        body.addView(scroll)
        status.gravity = Gravity.CENTER
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        status.setPadding(dp(24f), 0, dp(24f), dp(40f))
        status.visibility = View.GONE
        body.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        settingsButton.apply {
            text = "Open SakuriKey settings"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            visibility = View.GONE
            setOnClickListener { listener.onOpenSettings() }
        }
        body.addView(settingsButton, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomBottom() })
        addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Klipy asks apps to credit them.
        addView(TextView(context).apply {
            text = "Powered by KLIPY"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            alpha = 0.6f
            tag = ATTRIBUTION
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16f)))
    }

    private fun FrameLayout.LayoutParams.bottomBottom() {
        bottomMargin = dp(24f)
    }

    override fun onHostAttached() {
        val t = host?.theme ?: return
        searchBox.background = GlassTiles.tile(host, GlassTiles.neutral(t), dp(20f).toFloat())
        status.setTextColor(t.secondaryTextColor)
        settingsButton.setTextColor(t.onAccent)
        settingsButton.background = GlassTiles.tile(host, GlassTiles.accent(t), dp(20f).toFloat())
        (getChildAt(childCount - 1) as? TextView)?.setTextColor(t.secondaryTextColor)
        updateSearchBox()
        buildChips()
        GifLoader.prune(context)
        load(reset = true)
    }

    private fun updateSearchBox() {
        val t = host?.theme
        val empty = query.isEmpty() && !showingFavorites
        searchBox.text = if (showingFavorites) "Favorites" else if (query.isEmpty()) "Search GIFs" else query
        searchBox.setTextColor(if (empty) t?.secondaryTextColor ?: 0x99000000.toInt() else t?.textColor ?: 0xFF000000.toInt())
    }

    private fun buildChips() {
        val t = host?.theme ?: return
        chips.removeAllViews()
        for (label in listOf(FAVORITES) + CATEGORIES) {
            val isFavorites = label == FAVORITES
            val value = if (label == CATEGORIES[0]) "" else label.lowercase()
            val selected = if (isFavorites) showingFavorites else !showingFavorites && query.equals(value, ignoreCase = true)
            chips.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (selected) t.onAccent else t.textColor)
                gravity = Gravity.CENTER
                setPadding(dp(14f), 0, dp(14f), 0)
                background = GlassTiles.tile(host, if (selected) GlassTiles.accent(t) else GlassTiles.neutral(t), dp(17f).toFloat())
                setOnClickListener {
                    showingFavorites = isFavorites
                    if (!isFavorites) query = value
                    updateSearchBox()
                    buildChips()
                    load(reset = true)
                }
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f); topMargin = dp(2f) })
        }
    }

    private fun showStatus(message: String?, needsSettings: Boolean = false) {
        status.text = message ?: ""
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        settingsButton.visibility = if (needsSettings) View.VISIBLE else View.GONE
    }

    private fun customerId(): String {
        var id = prefs.klipyCustomerId
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString()
            prefs.klipyCustomerId = id
        }
        return id
    }

    private fun load(reset: Boolean) {
        if (!prefs.gifsEnabled) {
            showStatus("GIF search is turned off.\nTurn it on in SakuriKey settings.", needsSettings = true)
            return
        }
        if (showingFavorites) {
            generation++
            hasMore = false
            loading = false
            columns.forEach { it.removeAllViews() }
            columnWeight.fill(0f)
            scroll.scrollTo(0, 0)
            val saved = GifFavorites.all(prefs)
            if (saved.isEmpty()) {
                showStatus("No favorites yet.\nTap the star on a GIF, or long-press it, to keep it here.")
            } else {
                showStatus(null)
                saved.forEach(::addTile)
            }
            return
        }
        val key = prefs.klipyKey.trim()
        if (key.isEmpty()) {
            showStatus("Add your free Klipy API key\nin SakuriKey settings to use GIFs.", needsSettings = true)
            return
        }
        if (reset) {
            generation++
            page = 1
            hasMore = false
            loading = false
            columns.forEach { it.removeAllViews() }
            columnWeight.fill(0f)
            scroll.scrollTo(0, 0)
        }
        if (loading) return
        loading = true
        if (page == 1) showStatus("Loading…")
        val gen = generation
        KlipyClient.fetch(context, key, customerId(), query, page) { result, error ->
            if (gen != generation || !isAttachedToWindow) return@fetch
            loading = false
            if (result == null) {
                showStatus(error ?: "Couldn't load GIFs.")
                return@fetch
            }
            hasMore = result.hasMore
            if (result.items.isEmpty() && page == 1) {
                showStatus("No GIFs found")
                return@fetch
            }
            showStatus(null)
            result.items.forEach(::addTile)
            page++
        }
    }

    private fun addTile(item: GifItem) {
        val aspect = (item.height.toFloat() / item.width.coerceAtLeast(1)).coerceIn(0.5f, 1.8f)
        val col = if (columnWeight[0] <= columnWeight[1]) 0 else 1
        columnWeight[col] += aspect
        val t = host?.theme
        val tile = GifTile(context, aspect, dp(12f).toFloat()).apply {
            setBackgroundColor(t?.keyFill ?: 0x33FFFFFF)
            favorite = GifFavorites.contains(prefs, item.id)
            val toggleStar = {
                val now = GifFavorites.toggle(prefs, item)
                favorite = now
                if (showingFavorites && !now) {
                    // Gone from the favorites view: rebuild the grid without it.
                    load(reset = true)
                }
            }
            setOnClickListener { if (starHit) toggleStar() else listener.onGifPicked(item) }
            setOnLongClickListener {
                toggleStar()
                true
            }
        }
        columns[col].addView(tile, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(6f) })
        val maxDim = dp(220f)
        GifLoader.loadPreview(context, item.previewUrl, maxDim) { drawable ->
            if (drawable == null || !tile.isAttachedToWindow) return@loadPreview
            tile.setImageDrawable(drawable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()
            }
        }
    }

    /** A rounded image whose height follows the GIF's aspect ratio, with a star in the corner for favorites. */
    private class GifTile(context: Context, private val aspect: Float, radius: Float) : ImageView(context) {
        var favorite = false
            set(value) {
                field = value
                invalidate()
            }

        /** True when the last touch started on the star, so a tap there stars the GIF instead of sending it. */
        var starHit = false
            private set

        private val density = resources.displayMetrics.density
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val starSize get() = 30f * density

        init {
            scaleType = ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, (w * aspect).toInt())
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                starHit = event.x >= width - starSize * 1.4f && event.y <= starSize * 1.4f
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val r = starSize / 2f
            val cx = width - r - 5f * density
            val cy = r + 5f * density
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = 0x66000000
            canvas.drawCircle(cx, cy, r, paint)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = starSize * 0.62f
            paint.color = if (favorite) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt()
            val fm = paint.fontMetrics
            canvas.drawText(if (favorite) "★" else "☆", cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        }
    }

    private companion object {
        const val ATTRIBUTION = "klipy_attribution"
        const val FAVORITES = "★ Favorites"
        val CATEGORIES = listOf("Trending", "Happy", "Sad", "Love", "LOL", "Thanks", "Yes", "No", "OMG", "Hug", "Bye", "Wow")
    }
}
