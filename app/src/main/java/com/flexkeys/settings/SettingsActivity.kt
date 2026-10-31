package com.flexkeys.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.flexkeys.ai.AiClient
import com.flexkeys.ai.GeminiClient
import com.flexkeys.ai.GroqClient
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.keyboard.SpotlightPalettes
import com.flexkeys.diagnostics.Diagnostics
import com.flexkeys.gif.KlipyClient
import com.flexkeys.layout.Layouts
import com.flexkeys.service.Feedback
import com.flexkeys.service.KeyFeel
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.Fonts
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Look
import com.flexkeys.theme.Looks
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView
import kotlin.reflect.KMutableProperty0

private const val RECENT_KEY = "recent_topics"

/**
 * The launcher screen: setup, every customization option on frosted cards over a blurred
 * backdrop, and a live keyboard preview docked at the bottom where the real keyboard sits.
 */
class SettingsActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var palette: SettingsPalette
    private lateinit var backdropView: BackdropView
    private lateinit var preview: KeyboardRootView
    private lateinit var previewController: PreviewController
    private lateinit var previewDock: View
    private lateinit var setupCard: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    /** The settings page being shown, or null for the home list. */
    private var currentPage: String? = null
    private var systemDark = false
    private var photoRequest = 0

    /** Widgets that draw with the accent color and must repaint when it changes. */
    private val accentViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.installCrashHandler(this)
        prefs = Prefs.get(this)
        systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = SettingsPalette(systemDark, prefs.accentColor)
        drawBehindStatusBar()
        setContentView(buildPage())
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        preview.release()
        backdropView.backdrop.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        buildSetupCard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a separate window; re-check when it closes.
        if (hasFocus) buildSetupCard()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != RECENT_KEY) currentPage?.let { noteRecent(it) }
        when (key) {
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW, Prefs.KEY_PUNCTUATION, Prefs.KEY_CUSTOM_KEYS, in Prefs.POPUP_KEYS -> previewController.refresh()
            Prefs.KEY_BG_VERSION -> {
                preview.reloadPhoto()
                loadPagePhoto()
            }
            Prefs.KEY_ACCENT -> {
                applyAccent()
                applyKeyboardTheme()
            }
            in Prefs.THEME_KEYS -> applyKeyboardTheme()
            else -> Unit
        }
    }

    private fun applyKeyboardTheme() {
        preview.applyTheme(GlassTheme.from(this, prefs))
        backdropView.backdrop.setTheme(pageTheme())
        backdropView.invalidate()
    }

    // ---- page ------------------------------------------------------------------------------------

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    @Suppress("DEPRECATION")
    private fun drawBehindStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            (if (systemDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }

    /** The page backdrop uses the keyboard's colors and photo but always a strong blur. */
    private fun pageTheme(): GlassTheme {
        val t = GlassTheme.from(this, prefs)
        return GlassTheme(
            dark = systemDark, accent = t.accent, glassOpacity = t.glassOpacity,
            backgroundOpacity = t.backgroundOpacity, blurRadiusPx = 48f * t.density,
            cornerRadiusPx = t.cornerRadiusPx, keySpacingPx = t.keySpacingPx, fontScale = t.fontScale,
            heightScale = t.heightScale, density = t.density,
        )
    }

    private fun loadPagePhoto() {
        val request = ++photoRequest
        val app = applicationContext
        Thread {
            val bitmap = BackgroundImageStore.load(app, 720)
            runOnUiThread {
                if (request != photoRequest || isDestroyed) return@runOnUiThread
                backdropView.backdrop.setPhoto(bitmap)
                backdropView.invalidate()
            }
        }.start()
    }

    private fun buildPage(): View {
        val page = FrameLayout(this)
        backdropView = BackdropView(this).apply {
            backdrop.setTheme(pageTheme())
            scrim = palette.scrim
        }
        page.addView(backdropView)
        loadPagePhoto()

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(column)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(32f), dp(16f), dp(24f))
        }
        scroll.addView(list)
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Leave room for the status bar we draw behind.
        page.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            list.setPadding(dp(16f), insets.systemWindowInsetTop + dp(8f), dp(16f), dp(24f))
            insets
        }

        // The dock must exist before the home list is built, because showing a page shows or hides it.
        column.addView(previewDockView())
        showHome()
        return page
    }

    // ---- navigation: a short home list, and one page per topic ----

    private class Topic(
        val id: String,
        val icon: String,
        val title: String,
        val summary: () -> String,
        val cards: () -> List<View>,
        val advancedHint: String = "",
        val advanced: () -> List<View> = { emptyList() },
        val showsPreview: Boolean = false,
    )

    private val typingEffectNames = listOf("Off", "Sparkles", "Ripple", "Warp speed", "Glow bloom", "Ink splash", "Firecracker", "Paint splatter", "Key spotlight", "Sakura petals", "Sakura + spotlight")
    private val glassAnimationNames = listOf("Off", "Aurora", "Light flow", "Bubbles", "Starlight", "Rain", "Fireflies", "Waves", "Sakura petals")

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    private fun topics(): List<Topic> = listOf(
        Topic("looks", "🖼️", "Looks",
            summary = {
                val active = (Looks.builtIn + Looks.parse(prefs.myLooks)).firstOrNull { Looks.isActive(prefs, it) }
                if (active != null) "${active.name} · ${Looks.builtIn.size} built in, ${Looks.parse(prefs.myLooks).size} of yours" else "Custom · ${Looks.builtIn.size} built in"
            },
            cards = { listOf(builtInLooksCard(), myLooksCard()) }, showsPreview = true),
        Topic("colors", "🎨", "Colors & photo",
            summary = {
                val tint = listOf("Auto", "Light", "Dark").getOrElse(prefs.themeMode) { "Auto" }
                val photo = if (BackgroundImageStore.exists(this)) "photo on" else "no photo"
                "$tint tint · $photo · glass ${prefs.glassOpacity}%"
            },
            cards = { listOf(colorsCard(), photoCard(), glassCard()) }, showsPreview = true),
        Topic("effects", "✨", "Effects & animation",
            summary = { "${typingEffectNames[prefs.typingEffect.coerceIn(0, typingEffectNames.size - 1)]} · glass ${glassAnimationNames[prefs.glassAnimation.coerceIn(0, glassAnimationNames.size - 1)].lowercase()}" },
            cards = { listOf(effectLooksCard(), typingEffectsCard(), glassAnimationCard()) },
            advancedHint = "Tilt, reduce motion, battery", advanced = { listOf(motionCard()) }, showsPreview = true),
        Topic("layout", "⌨️", "Keyboard layout",
            summary = {
                val layouts = Layouts.letterLayouts
                "${layouts.firstOrNull { it.id == prefs.layoutId }?.name ?: "QWERTY"} · height ${prefs.heightPercent}%"
            },
            cards = { listOf(layoutStyleCard(), layoutSizeCard(), layoutKeysCard(), layoutPopupsCard()) },
            advancedHint = "Width, position, split keys", advanced = { listOf(widthCard()) }, showsPreview = true),
        Topic("typing", "✍️", "Typing",
            summary = { "Autocorrect ${onOff(prefs.autocorrect)} · suggestions ${onOff(prefs.suggestions)}" },
            cards = { listOf(typingCard(), typingToolsCard()) },
            advancedHint = "Clean up or clear learned words", advanced = { listOf(typingCleanupCard()) }, showsPreview = true),
        Topic("feedback", "📳", "Sound & vibration",
            summary = { "Vibration ${onOff(prefs.haptics)} · ${KeyFeel.names.getOrElse(prefs.keyFeel) { "Classic" }.substringBefore(" (")} feel · sound ${onOff(prefs.sound)}" },
            cards = { listOf(feedbackCard()) }, showsPreview = true),
        Topic("extras", "🌐", "GIFs & translator",
            summary = { "GIFs ${onOff(prefs.gifsEnabled)} · translator ${onOff(prefs.translatorEnabled)}" },
            cards = { listOf(gifCard(), translatorCard()) }),
        Topic("ai", "🤖", "AI rewrite",
            summary = { if (prefs.aiEnabled) "On · ${AiClient.providerNames.getOrElse(prefs.aiProvider) { "Groq" }}" else "Off" },
            cards = { listOf(aiCard()) }),
        Topic("clipboard", "📋", "Clipboard",
            summary = { "History ${onOff(prefs.clipboardHistory)}" },
            cards = { listOf(clipboardCard()) }),
        Topic("backup", "💾", "Backup & sharing",
            summary = { "Save and restore settings, share your look" },
            cards = { listOf(backupCard()) }),
        Topic("about", "ℹ️", "About & privacy",
            summary = { "Privacy, updates, version" },
            cards = { listOf(privacyCard(), aboutCard()) },
            advancedHint = "Diagnostics", advanced = { listOf(diagnosticsCard()) }),
    )

    /** One entry per setting people look for. [card] is the title of the card that holds it. */
    private class Hit(val label: String, val topic: String, val card: String, val words: String = "")

    private val searchIndex = listOf(
        Hit("Looks gallery", "looks", "Built-in looks", "themes presets frost midnight aurora sunset sakura ocean mint candy"),
        Hit("Save current look", "looks", "My looks", "theme save custom"),
        Hit("Accent languages", "layout", "Long-press characters", "accents é ñ ü popups"),
        Hit("Menu background", "layout", "Long-press characters", "popup transparency opacity glass"),
        Hit("Menu blur", "layout", "Long-press characters", "popup frosted glass"),
        Hit("Accents", "layout", "Long-press characters", "popup long press"),
        Hit("Symbols and digits", "layout", "Long-press characters", "popup long press"),
        Hit("Small symbols on keys", "layout", "Long-press characters", "corner hints"),
        Hit("Height when sideways", "layout", "Size & shape", "landscape rotate wide"),
        Hit("Tint", "colors", "Colors", "light dark auto theme mode"),
        Hit("Accent color", "colors", "Colors", "colour hue"),
        Hit("Background photo", "colors", "Background photo", "wallpaper image picture"),
        Hit("Adjust photo position", "colors", "Background photo", "crop zoom"),
        Hit("Adapt tint to photo", "colors", "Background photo"),
        Hit("Photo opacity", "colors", "Background photo", "transparency"),
        Hit("Glass opacity", "colors", "Glass", "transparency see through"),
        Hit("Blur", "colors", "Glass", "frosted"),
        Hit("Gradient glow", "colors", "Glass"),
        Hit("Liquid refraction", "colors", "Glass", "bend beta lens"),
        Hit("Quick looks", "effects", "Quick looks", "preset combo"),
        Hit("Key press color", "effects", "Typing effects", "highlight"),
        Hit("Key motion", "effects", "Typing effects", "sink pop bounce tilt float"),
        Hit("Typing animation", "effects", "Typing effects", "sparkles ripple warp bloom ink firecracker paint spotlight"),
        Hit("Animation strength", "effects", "Typing effects"),
        Hit("Key spotlight colors", "effects", "Typing effects", "palette custom"),
        Hit("Glass animation", "effects", "Glass animation", "aurora light flow bubbles starlight rain fireflies waves background"),
        Hit("Animation speed", "effects", "Glass animation"),
        Hit("Animation intensity", "effects", "Glass animation"),
        Hit("Tilt shimmer", "effects", "Tilt & motion", "sensor shine"),
        Hit("Reduce motion", "effects", "Tilt & motion", "accessibility animations off"),
        Hit("Pause on low battery", "effects", "Tilt & motion", "battery saver"),
        Hit("Keyboard layout", "layout", "Layout & style", "qwerty azerty dvorak colemak"),
        Hit("Key style", "layout", "Layout & style", "glass tiles"),
        Hit("Font", "layout", "Layout & style", "typeface"),
        Hit("Space bar emblem", "layout", "Layout & style", "logo flower"),
        Hit("Space bar style", "layout", "Layout & style", "card outline underline clear"),
        Hit("Space bar glass", "layout", "Layout & style"),
        Hit("Keyboard height", "layout", "Size & shape", "taller shorter"),
        Hit("Key spacing", "layout", "Size & shape", "gap"),
        Hit("Corner radius", "layout", "Size & shape", "round"),
        Hit("Font size", "layout", "Size & shape", "text bigger"),
        Hit("Suggestion word size", "layout", "Size & shape", "strip"),
        Hit("Comma & period keys", "layout", "Keys", "punctuation"),
        Hit("Number row", "layout", "Keys", "digits"),
        Hit("Key preview", "layout", "Keys", "popup bubble"),
        Hit("Keyboard width", "layout", "Width & position", "tablet compact wide"),
        Hit("Keyboard position", "layout", "Width & position", "left right center one hand"),
        Hit("Split keys", "layout", "Width & position", "tablet thumbs"),
        Hit("Skin tone", "typing", "Typing", "emoji"),
        Hit("Suggestions", "typing", "Typing", "predictions completions"),
        Hit("Autocorrect", "typing", "Typing", "typo fix"),
        Hit("Learn my words", "typing", "Typing", "dictionary"),
        Hit("Auto-capitalization", "typing", "Typing", "capital"),
        Hit("Double-space period", "typing", "Typing"),
        Hit("Smart paste", "typing", "Typing", "email phone link clipboard suggestion"),
        Hit("Calculator and unit chips", "typing", "Typing", "math convert km miles kg lb temperature"),
        Hit("Autocorrect list", "typing", "My words & tools", "protected always change"),
        Hit("Learned words", "typing", "My words & tools", "manage"),
        Hit("Phrases & extra keys", "typing", "My words & tools", "snippets shortcuts"),
        Hit("Stickers", "typing", "My words & tools"),
        Hit("Clear learned words", "typing", "Cleanup", "forget reset"),
        Hit("Vibrate on key press", "feedback", "Sound & vibration", "haptics buzz"),
        Hit("Haptic style", "feedback", "Sound & vibration", "tick click heavy strength"),
        Hit("Keyboard feel", "feedback", "Sound & vibration", "soft clicky mechanical silky thock haptic vibration"),
        Hit("Sound on key press", "feedback", "Sound & vibration", "volume click"),
        Hit("GIF search", "extras", "GIFs", "klipy api key"),
        Hit("Translator", "extras", "Translator", "mymemory language"),
        Hit("AI rewrite", "ai", "AI rewrite", "groq gemini key fix reword"),
        Hit("Rewrite styles", "ai", "AI rewrite", "presets tone"),
        Hit("Clean up dictation", "ai", "AI rewrite", "voice"),
        Hit("Clipboard history", "clipboard", "Clipboard", "paste copy"),
        Hit("Delete clipboard after", "clipboard", "Clipboard", "expiry"),
        Hit("Export backup", "backup", "Backup & sharing", "save restore import"),
        Hit("Share my theme", "backup", "Backup & sharing", "code"),
        Hit("Privacy", "about", "Privacy", "reset all settings data"),
        Hit("Check for updates", "about", "About", "version"),
        Hit("Diagnostics", "about", "Diagnostics", "timing report lag"),
    )

    private fun noteRecent(topicId: String) {
        val current = (prefs.sp.getString(RECENT_KEY, "") ?: "").split(',').filter { it.isNotEmpty() }
        if (current.firstOrNull() == topicId) return
        val next = (listOf(topicId) + current.filter { it != topicId }).take(3)
        prefs.sp.edit().putString(RECENT_KEY, next.joinToString(",")).apply()
    }

    private fun dockVisibility(): Int =
        if (topics().firstOrNull { it.id == currentPage }?.showsPreview == true) View.VISIBLE else View.GONE

    /** For the smoke test: the id of every settings page. */
    internal fun topicIds(): List<String> = topics().map { it.id }

    /** For the smoke test: opens a page as a tap on its home row would. */
    internal fun openTopicForTest(id: String) {
        topics().firstOrNull { it.id == id }?.let { showTopic(it) }
    }

    /** For the smoke test: back to the home list. */
    internal fun openHomeForTest() = showHome()

    private fun showHome() {
        currentPage = null
        accentViews.clear()
        list.removeAllViews()
        previewDock.visibility = View.GONE
        list.addView(header())

        val all = topics()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        list.addView(searchField { query ->
            val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
            results.removeAllViews()
            body.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
            results.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
            if (words.isEmpty()) return@searchField
            val hits = searchIndex.filter { hit ->
                val haystack = "${hit.label} ${hit.words} ${hit.card}".lowercase()
                words.all { haystack.contains(it) }
            }
            if (hits.isEmpty()) {
                results.addView(card().apply { text("Nothing matches \"$query\".", secondary = true) })
            }
            for (hit in hits.take(12)) {
                val topic = all.first { it.id == hit.topic }
                results.addView(card().apply {
                    setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
                    addView(TextView(context).apply {
                        text = hit.label
                        setTextColor(palette.text)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    })
                    addView(TextView(context).apply {
                        text = "${topic.title}  ›  ${hit.card}"
                        setTextColor(palette.secondary)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    })
                    isClickable = true
                    setOnClickListener { showTopic(topic, hit.card) }
                })
            }
        })
        list.addView(results)
        list.addView(body)

        setupCard = card()
        body.addView(setupCard)
        buildSetupCard()

        val recent = (prefs.sp.getString(RECENT_KEY, "") ?: "").split(',').mapNotNull { id -> all.firstOrNull { it.id == id } }
        if (recent.isNotEmpty()) {
            body.addView(sectionLabel("Recently changed"))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            recent.forEachIndexed { i, topic ->
                row.addView(PillButton(this, topic.title, false, palette).apply { onClick = { showTopic(topic) } },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { if (i > 0) marginStart = dp(8f) })
            }
            body.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(4f), 0, 0)
                addView(row)
            })
        }
        body.addView(sectionLabel("All settings"))
        for (topic in all) body.addView(topicRow(topic))
        scroll.scrollTo(0, 0)
    }

    private fun sectionLabel(label: String): View = TextView(this).apply {
        text = label.uppercase()
        setTextColor(palette.secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        setPadding(dp(8f), dp(20f), 0, dp(2f))
    }

    private fun searchField(onQuery: (String) -> Unit): View = EditText(this).apply {
        hint = "Search settings"
        setTextColor(palette.text)
        setHintTextColor(palette.secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
        background = GradientDrawable().apply {
            cornerRadius = dp(26f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10f) }
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = onQuery(s?.toString()?.trim() ?: "")
        })
    }

    /** Opens a topic. [focus] is the title of a card to scroll to (and unfold if it is under Advanced). */
    private fun showTopic(topic: Topic, focus: String? = null) {
        currentPage = topic.id
        accentViews.clear()
        list.removeAllViews()
        previewDock.visibility = dockVisibility()
        list.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(12f), dp(6f), dp(4f))
            addView(TextView(context).apply {
                text = "‹  All settings"
                setTextColor(palette.secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(6f), dp(24f), dp(10f))
                setOnClickListener { showHome() }
            })
            addView(TextView(context).apply {
                text = topic.title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            })
        })
        for (c in topic.cards()) list.addView(c)

        val advancedCards = topic.advanced()
        var advancedBox: LinearLayout? = null
        if (advancedCards.isNotEmpty()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            advancedCards.forEach { box.addView(it) }
            advancedBox = box
            val arrow = TextView(this).apply {
                text = "⌄"
                setTextColor(palette.secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            }
            list.addView(card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(TextView(context).apply {
                    text = "Advanced"
                    setTextColor(palette.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                })
                texts.addView(TextView(context).apply {
                    text = topic.advancedHint
                    setTextColor(palette.secondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
                addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(arrow)
                isClickable = true
                setOnClickListener {
                    val open = box.visibility != View.VISIBLE
                    box.visibility = if (open) View.VISIBLE else View.GONE
                    arrow.text = if (open) "⌃" else "⌄"
                }
            })
            list.addView(box)
        }

        scroll.scrollTo(0, 0)
        if (focus != null) {
            val target = list.findViewWithTag<View>(focus)
            if (target != null) {
                if (advancedBox != null && target.parent === advancedBox) advancedBox.visibility = View.VISIBLE
                list.postDelayed({
                    var y = 0
                    var v: View = target
                    while (v !== list) {
                        y += v.top
                        v = v.parent as? View ?: break
                    }
                    scroll.smoothScrollTo(0, (y - dp(24f)).coerceAtLeast(0))
                }, 80)
            }
        }
    }

    /** Rebuilds the open page after values changed in bulk (a preset), keeping the scroll position. */
    private fun refreshPage() {
        val topic = topics().firstOrNull { it.id == currentPage } ?: return
        val y = scroll.scrollY
        showTopic(topic)
        scroll.post { scroll.scrollTo(0, y) }
    }

    /** A big tappable row on the home list. Only a tap on the row opens the page, so scrolling past is safe. */
    private fun topicRow(topic: Topic): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        addView(TextView(context).apply {
            text = topic.icon
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(palette.tonal)
            }
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(14f) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = topic.title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        texts.addView(TextView(context).apply {
            text = topic.summary()
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2f), 0, 0)
        })
        addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "›"
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        })
        isClickable = true
        setOnClickListener { showTopic(topic) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentPage != null) showHome() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun previewDockView(): View {
        val dock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val output = TextView(this).apply {
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setColor(palette.card)
                setStroke(dp(1f), palette.cardBorder)
            }
        }
        // The keyboard preview takes a lot of room, so a slim handle folds it away.
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.previewOpen) View.VISIBLE else View.GONE
        }
        val handle = TextView(this).apply {
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(6f))
            fun refreshLabel() {
                text = if (prefs.previewOpen) "Keyboard preview   ⌄  tap to hide" else "Keyboard preview   ⌃  tap to show"
            }
            refreshLabel()
            setOnClickListener {
                prefs.previewOpen = !prefs.previewOpen
                body.visibility = if (prefs.previewOpen) View.VISIBLE else View.GONE
                refreshLabel()
            }
        }
        dock.addView(handle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dock.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(16f), dp(2f), dp(16f), dp(8f)) })
        preview = KeyboardRootView(this, GlassTheme.from(this, prefs))
        body.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewController = PreviewController(preview, output, prefs, Feedback(this, prefs))
        preview.reloadPhoto()
        previewDock = dock
        return dock
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6f), dp(12f), dp(6f), dp(6f))
        addView(TextView(context).apply {
            text = "SakuriKey"
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = -0.02f
        })
        addView(TextView(context).apply {
            text = "Your keyboard, your glass."
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
    }

    // ---- building blocks -------------------------------------------------------------------------

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(28f).toFloat()
            setColor(palette.card)
            setStroke(dp(1f), palette.cardBorder)
        }
        setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(14f) }
    }

    /** A card with a title. Each topic page shows its cards open, so nothing needs unfolding. */
    private fun card(title: String, build: LinearLayout.() -> Unit): View = card().apply {
        tag = title
        addView(TextView(context).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(4f))
        })
        build()
    }

    private fun LinearLayout.text(value: String, secondary: Boolean = false, topPad: Float = 10f): TextView {
        val tv = TextView(context).apply {
            text = value
            setTextColor(if (secondary) palette.secondary else palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (secondary) 13.5f else 16f)
            setPadding(0, dp(if (secondary) 2f else topPad), 0, dp(2f))
        }
        addView(tv)
        return tv
    }

    private fun LinearLayout.toggle(title: String, summary: String?, prop: KMutableProperty0<Boolean>) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, dp(6f))
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })
        if (summary != null) texts.addView(TextView(context).apply {
            text = summary
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(0, dp(2f), dp(12f), 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = PillSwitch(context, prop.get(), palette) { prop.set(it) }
        accentViews.add(sw)
        row.addView(sw)
        // Only the switch itself changes the setting, so scrolling past a row can't flip it by accident.
        addView(row)
    }

    private fun LinearLayout.slider(title: String, min: Int, max: Int, prop: KMutableProperty0<Int>, format: (Int) -> String) {
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(2f))
        }
        head.addView(TextView(context).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(context).apply {
            text = format(prop.get())
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        head.addView(value)
        addView(head)
        val slider = GlassSlider(context, min, max, prop.get(), palette) {
            value.text = format(it)
            prop.set(it)
        }
        accentViews.add(slider)
        addView(slider)
    }

    private fun LinearLayout.segmented(title: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        if (title != null) text(title, topPad = 12f)
        val control = SegmentedControl(context, options, selected, palette, onSelect)
        accentViews.add(control)
        addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
    }

    /** A row showing the current choice; tapping it opens a list of every option. */
    private fun LinearLayout.picker(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        text(title, topPad = 12f)
        var current = selected.coerceIn(0, options.size - 1)
        val button = PillButton(context, options[current], false, palette)
        accentViews.add(button)
        button.onClick = {
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), current) { dialog, which ->
                    current = which
                    button.label = options[which]
                    onSelect(which)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6f) })
    }

    private fun LinearLayout.buttons(vararg actions: Triple<String, Boolean, () -> Unit>) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f), 0, dp(2f))
        }
        actions.forEachIndexed { i, (label, primary, action) ->
            val b = PillButton(context, label, primary, palette).apply { onClick = action }
            accentViews.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(10f)
            })
        }
        addView(row)
    }

    private fun applyAccent() {
        palette = SettingsPalette(systemDark, prefs.accentColor)
        for (v in accentViews) {
            when (v) {
                is PillButton -> v.palette = palette
                is PillSwitch -> v.palette = palette
                is GlassSlider -> v.palette = palette
                is SegmentedControl -> v.palette = palette
            }
            v.invalidate()
        }
    }

    // ---- cards ---------------------------------------------------------------------------------------

    private fun buildSetupCard() {
        if (!::setupCard.isInitialized) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current?.substringBefore('/') == packageName
        // Keep the test field (and its focus) if the state did not change.
        val state = (if (enabled) 1 else 0) + (if (selected) 2 else 0)
        if (setupCard.tag == state) return
        setupCard.tag = state
        setupCard.removeAllViews()
        setupCard.addView(TextView(this).apply {
            text = if (enabled && selected) "✓  SakuriKey is ready" else "Get started"
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        when {
            !enabled -> {
                setupCard.text("Step 1 of 2: turn on SakuriKey in your keyboard list.", secondary = true)
                setupCard.buttons(Triple("Enable SakuriKey", true) { openImeSettings() })
            }
            !selected -> {
                setupCard.text("Step 2 of 2: switch to SakuriKey.", secondary = true)
                setupCard.buttons(Triple("Switch keyboard", true) { showImePicker() })
            }
            else -> setupCard.text("Try it here, or tweak the look with the live preview below.", secondary = true)
        }
        if (enabled) {
            val field = EditText(this).apply {
                hint = "Type here with SakuriKey"
                setTextColor(palette.text)
                setHintTextColor(palette.secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18f).toFloat()
                    setColor(palette.tonal)
                }
                // The real keyboard takes the preview's place while typing here.
                setOnFocusChangeListener { _, focused -> previewDock.visibility = if (focused) View.GONE else dockVisibility() }
            }
            setupCard.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12f) })
        }
    }

    private fun openImeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast("Keyboard settings are not available on this device")
        }
    }

    private fun showImePicker() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

    // ---- looks gallery ----

    /** A grid of look thumbnails, two per row. Tap applies a look; long-press runs [onLongPress] if given. */
    private fun LinearLayout.lookGrid(looks: List<Look>, onLongPress: ((Look) -> Unit)?) {
        looks.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10f), 0, 0)
            }
            pair.forEachIndexed { i, look ->
                row.addView(LookTile(context, look, Looks.isActive(prefs, look), palette).apply {
                    onClick = {
                        Looks.apply(prefs, look)
                        refreshPage()
                    }
                    if (onLongPress != null) this.onLongClick = { onLongPress(look) }
                }, LinearLayout.LayoutParams(0, dp(112f), 1f).apply { if (i > 0) marginStart = dp(10f) })
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, dp(112f), 1f).apply { marginStart = dp(10f) })
            addView(row)
        }
    }

    private fun builtInLooksCard() = card("Built-in looks") {
        text("Tap a look to try it. Your background photo stays as it is.", secondary = true)
        lookGrid(Looks.builtIn, null)
    }

    private fun myLooksCard() = card("My looks") {
        val mine = Looks.parse(prefs.myLooks)
        if (mine.isEmpty()) {
            text("Set the keyboard up the way you like it, then save it here to come back to it in one tap.", secondary = true)
        } else {
            lookGrid(mine) { look ->
                confirm("Delete the look \"${look.name}\"?") {
                    prefs.myLooks = Looks.format(Looks.parse(prefs.myLooks).filter { it.name != look.name })
                    refreshPage()
                }
            }
            text("Long-press one of your looks to delete it.", secondary = true)
        }
        buttons(Triple("Save current look", true) { askLookName() })
    }

    private fun askLookName() {
        val input = EditText(this).apply {
            hint = "Name"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(24))
            setPadding(dp(24f), dp(16f), dp(24f), dp(8f))
        }
        AlertDialog.Builder(this)
            .setTitle("Name this look")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    toast("Give it a name first")
                    return@setPositiveButton
                }
                val others = Looks.parse(prefs.myLooks).filter { it.name != name }
                prefs.myLooks = Looks.format(others + Looks.capture(prefs, name))
                refreshPage()
                toast("Saved \"$name\"")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun colorsCard() = card("Colors") {
        buttons(Triple("Browse looks", false) { topics().firstOrNull { it.id == "looks" }?.let { showTopic(it) } })
        segmented("Tint", listOf("Auto", "Light", "Dark"), prefs.themeMode) { prefs.themeMode = it }
        text("Accent", topPad = 14f)
        addView(ColorPickerView(context).apply {
            color = prefs.accentColor
            ringColor = palette.text
            onColorChanged = { prefs.accentColor = it }
        })
    }

    private fun photoCard() = card("Background photo") {
        text("Shown behind the glass. A private copy stays inside the app.", secondary = true)
        buttons(Triple("Choose photo", true) { pickPhoto() }, Triple("Remove", false) {
            BackgroundImageStore.delete(this@SettingsActivity)
            prefs.backgroundVersion = System.currentTimeMillis()
        })
        buttons(Triple("Adjust position", false) {
            if (BackgroundImageStore.exists(this@SettingsActivity)) {
                startActivity(Intent(this@SettingsActivity, PhotoAdjustActivity::class.java))
            } else {
                toast("Choose a photo first")
            }
        }, Triple("Use photo's colors", false) {
            if (BackgroundImageStore.exists(this@SettingsActivity) && prefs.photoLuma >= 0) {
                prefs.accentColor = prefs.photoAccent
                toast("Accent matched to your photo")
            } else {
                toast("Choose a photo first")
            }
        })
        toggle("Adapt tint to photo", "Light or dark follows how bright the photo is.", prefs::adaptTint)
        slider("Photo opacity", 0, 100, prefs::backgroundOpacity) { "$it%" }
    }

    private fun glassCard() = card("Glass") {
        slider("Glass opacity", 0, 100, prefs::glassOpacity) { "$it%" }
        slider("Blur", 0, 100, prefs::blurStrength) { "$it%" }
        slider("Gradient glow", 0, 100, prefs::gradientStrength) { if (it == 0) "Off" else "$it%" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toggle("Liquid refraction (beta)", "Bends the blurred background around every key, like light through thick glass", prefs::refraction)
            slider("Refraction strength", 0, 100, prefs::refractionStrength) { "$it%" }
        } else {
            text("Liquid refraction needs Android 13 or newer.", secondary = true)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            text("Live blur needs Android 12. This phone uses a lighter frosted look.", secondary = true)
        }
    }

    private fun typingEffectsCard() = card("Typing effects") {
        picker("Key press color", listOf("White", "Accent", "Gradient", "Photo", "Rainbow"), prefs.pressStyle) { prefs.pressStyle = it }
        picker("Key motion", listOf("Sink", "Pop", "Bounce", "Tilt", "Float up", "None"), prefs.keyMotion) { prefs.keyMotion = it }
        picker("Typing animation", typingEffectNames, prefs.typingEffect) { prefs.typingEffect = it }
        slider("Animation strength", 0, 100, prefs::effectStrength) { "$it%" }
        picker("Spotlight colors (also tint the Sakura + spotlight petals)", SpotlightPalettes.names, prefs.spotlightPalette) {
            prefs.spotlightPalette = it
            if (it == SpotlightPalettes.CUSTOM) showSpotlightColors()
        }
        toggle("Change color every press", "Otherwise the spotlight's color follows where the key is across the keyboard", prefs::spotlightCycle)
        buttons(Triple("Edit custom spotlight colors", false) { showSpotlightColors() })
        text("Warp speed shows streaks when you type fast.", secondary = true)
    }

    private fun glassAnimationCard() = card("Glass animation") {
        picker("Glass animation", glassAnimationNames, prefs.glassAnimation) { prefs.glassAnimation = it }
        slider("Animation speed", 0, 100, prefs::animationSpeed) { "$it%" }
        slider("Animation intensity", 0, 100, prefs::animationIntensity) { "$it%" }
        toggle("Animation follows tilt", "The animation slides and sways as you tilt your phone", prefs::animationTilt)
    }

    private fun motionCard() = card("Tilt & motion") {
        toggle("Tilt shimmer", "Glass highlights shift slightly as you tilt your phone", prefs::shimmer)
        slider("Shimmer strength", 0, 100, prefs::shimmerStrength) { "$it%" }
        toggle("Reduce motion", "Turns off tilt effects, glass animation and typing effects together", prefs::reduceMotion)
        toggle("Follow Android's \"Remove animations\"", "Also rest the effects when that accessibility setting is on", prefs::followSystemMotion)
        toggle("Pause animations on low battery", "Stops the glass animation and tilt effects at 20% or in battery saver", prefs::batterySaver)
    }

    private fun widthCard() = card("Width & position") {
        picker("Keyboard width", listOf("Auto (full on phones, narrower on tablets)", "Full", "Wide (85%)", "Medium (70%)", "Compact (55%)"), prefs.keyboardWidth + 1) { prefs.keyboardWidth = it - 1 }
        picker("Keyboard position", listOf("Center", "Left", "Right"), prefs.keyboardPosition) { prefs.keyboardPosition = it }
        toggle("Split keys", "Leaves a gap down the middle of the letter rows for thumb typing on a tablet", prefs::splitKeys)
        slider("Split gap", 8, 30, prefs::splitGap) { "$it%" }
    }

    private fun layoutStyleCard() = card("Layout & style") {
        val layouts = Layouts.letterLayouts
        segmented("Layout", layouts.map { it.name }, layouts.indexOfFirst { it.id == prefs.layoutId }.coerceAtLeast(0)) {
            prefs.layoutId = layouts[it].id
        }
        segmented("Key style", listOf("Glass letters", "Tiles"), prefs.keyStyle) { prefs.keyStyle = it }
        segmented("Font", Fonts.options.map { it.second }, Fonts.options.indexOfFirst { it.first == prefs.fontFamily }.coerceAtLeast(0)) {
            prefs.fontFamily = Fonts.options[it].first
        }
        val emblems = listOf(Prefs.SPACE_EMBLEM_ICON, "🌸", "❤️", "")
        segmented("Space bar emblem", listOf("Icon", "🌸", "❤️", "None"), emblems.indexOf(prefs.spaceEmblem).coerceAtLeast(0)) {
            prefs.spaceEmblem = emblems[it]
        }
        slider("Emblem size", 50, 160, prefs::emblemSize) { "$it%" }
        slider("Space bar glass", 0, 100, prefs::spaceGlass) { "$it%" }
        picker("Space bar style", listOf("Frosted card", "Outline only", "Clear glass", "Underline", "Accent tint"), prefs.spaceStyle) { prefs.spaceStyle = it }
    }

    private fun layoutSizeCard() = card("Size & shape") {
        slider("Keyboard height", 60, 150, prefs::heightPercent) { "$it%" }
        segmented("Height when sideways", listOf("Auto", "Short", "Medium", "Normal"), prefs.landscapeHeight) { prefs.landscapeHeight = it }
        slider("Key spacing", 0, 16, prefs::keySpacingDp) { "$it dp" }
        slider("Corner radius", 0, 30, prefs::cornerRadiusDp) { "$it dp" }
        slider("Font size", 60, 160, prefs::fontPercent) { "$it%" }
        slider("Suggestion word size", 50, 130, prefs::suggestionFontPercent) { "$it%" }
    }

    private fun layoutKeysCard() = card("Keys") {
        toggle("Comma & period keys", "Show , and . beside the space bar, like Gboard", prefs::punctuationKeys)
        toggle("Number row", "Always show 1–0 above the letters", prefs::numberRow)
        toggle("Key preview", "Pop up a bubble above keys as you type", prefs::keyPreview)
    }

    private fun layoutPopupsCard() = card("Long-press characters") {
        text("Hold a letter to pick an accent, a symbol or a digit.", secondary = true)
        toggle("Accents", "é, ñ, ü and friends", prefs::accentPopups)
        buttons(Triple("Accent languages", false) { showAccentLanguages() })
        toggle("Symbols and digits", "The extra symbol under each letter, like Gboard", prefs::symbolPopups)
        toggle("Accents before symbols", "Put accents first in the menu", prefs::accentsFirst)
        toggle("Small symbols on keys", "The tiny corner symbol; swipe down on a key to type it", prefs::cornerHints)
        slider("Menu background", 0, 100, prefs::popupOpacity) { "$it%" }
        slider("Menu blur", 0, 100, prefs::popupBlur) { if (it == 0) "Off" else "$it%" }
        text("The menu's frosted glass: lower the background to see more of the keys behind it, raise the blur to soften them.", secondary = true)
        text("Add your own characters to any key under Typing > Phrases & extra keys.", secondary = true)
    }

    private fun showAccentLanguages() {
        val groups = com.flexkeys.layout.AccentSets.names
        val chosen = prefs.accentSets.split(',').map { it.trim() }.toMutableSet()
        AlertDialog.Builder(this)
            .setTitle("Accent languages")
            .setMultiChoiceItems(groups.map { it.second }.toTypedArray(), BooleanArray(groups.size) { groups[it].first in chosen }) { _, i, on ->
                if (on) chosen.add(groups[i].first) else chosen.remove(groups[i].first)
            }
            .setPositiveButton("Done") { _, _ ->
                prefs.accentSets = groups.map { it.first }.filter { it in chosen }.joinToString(",")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class EffectLook(val name: String, val typing: Int, val glass: Int)

    private val effectLooks = listOf(
        EffectLook("Calm", 0, 0),
        EffectLook("Sparkle", 1, 4),
        EffectLook("Dreamy", 2, 1),
        EffectLook("Rainy night", 4, 5),
        EffectLook("Fireflies", 8, 6),
        EffectLook("Sakura", 9, 8),
        EffectLook("Sakura glow", 10, 8),
        EffectLook("Ocean", 2, 7),
    )

    private fun effectLooksCard() = card("Quick looks") {
        text("One tap sets the typing animation and glass animation together. Fine-tune below.", secondary = true)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, dp(2f))
        }
        effectLooks.forEachIndexed { i, look ->
            row.addView(PillButton(context, look.name, false, palette).apply {
                onClick = {
                    prefs.typingEffect = look.typing
                    prefs.glassAnimation = look.glass
                    refreshPage()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) marginStart = dp(8f) })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
    }

    private fun typingCard() = card("Typing") {
        segmented("Default skin tone", listOf("Off", "🏻", "🏼", "🏽", "🏾", "🏿"), prefs.skinToneDefault) { prefs.skinToneDefault = it }
        text("Used for people and hand emoji. In the emoji panel, long-press one (marked with a small corner triangle) to pick a different tone for just that emoji.", secondary = true)
        toggle("Suggestions", "Completions and next-word predictions", prefs::suggestions)
        toggle("Autocorrect", "Fix typos on space. Backspace right after undoes it.", prefs::autocorrect)
        toggle("Learn my words", "Improve suggestions from what you type, on-device only", prefs::learnWords)
        toggle("Auto-capitalization", null, prefs::autoCapitalize)
        toggle("Double-space period", "Tap space twice for \". \"", prefs::doubleSpacePeriod)
        toggle("Smart paste", "Offer a copied email, phone number or link as a one-tap paste", prefs::smartClips)
        toggle("Calculator and unit chips", "Type 12*8 or 5 km and tap the answer in the suggestion bar", prefs::calculator)
    }

    private fun typingToolsCard() = card("My words & tools") {
        buttons(Triple("Autocorrect list (leave alone / always fix)", true) {
            startActivity(Intent(this@SettingsActivity, CorrectionsActivity::class.java))
        })
        buttons(Triple("Manage learned words", false) {
            startActivity(Intent(this@SettingsActivity, LearnedWordsActivity::class.java))
        }, Triple("Phrases & extra keys", false) {
            startActivity(Intent(this@SettingsActivity, PhrasesActivity::class.java))
        })
        buttons(Triple("Stickers", false) {
            startActivity(Intent(this@SettingsActivity, StickersActivity::class.java))
        })
    }

    private fun typingCleanupCard() = card("Cleanup") {
        buttons(Triple("Clean up typos in learned words", false) {
            val removed = SuggestionEngine.get(this@SettingsActivity).cleanLearned()
            toast(if (removed == 0) "No typos found in your learned words" else "Removed $removed learned typo${if (removed == 1) "" else "s"}")
        })
        buttons(Triple("Clear learned words", false) {
            confirm("Forget every word SakuriKey has learned from your typing?") {
                SuggestionEngine.get(this@SettingsActivity).user.clear()
                toast("Learned words cleared")
            }
        })
    }

    private fun feedbackCard() = card("Sound & vibration") {
        toggle("Vibrate on key press", null, prefs::haptics)
        picker("Keyboard feel", KeyFeel.names, prefs.keyFeel) { prefs.keyFeel = it }
        text("Soft, Clicky, Mechanical and Silky give each kind of key its own feel. Try them on the keyboard below.", secondary = true)
        segmented("Haptic style", listOf("Custom", "Tick", "Click", "Heavy"), prefs.hapticStyle) { prefs.hapticStyle = it }
        slider("Custom strength", 0, 100, prefs::hapticStrength) { "$it%" }
        text("Tick, Click and Heavy use your phone's built-in vibration effects (Android 10+). Strength applies to Custom.", secondary = true)
        toggle("Sound on key press", null, prefs::sound)
        slider("Sound volume", 0, 100, prefs::soundVolume) { "$it%" }
    }

    private fun gifCard() = card("GIFs") {
        toggle("GIF search", "Uses the internet, only when you open the GIF panel", prefs::gifsEnabled)
        text("To use GIFs you need a free API key from Klipy:", secondary = true)
        text("1. Open the Klipy developer page below and sign up.", secondary = true)
        text("2. Create an app or platform, then create an API key.", secondary = true)
        text("3. Copy the key and paste it here.", secondary = true)
        buttons(Triple("Get a free Klipy key", true) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://klipy.com/api-overview")))
            } catch (e: ActivityNotFoundException) {
                toast("No browser found. Go to klipy.com/api-overview")
            }
        })
        val field = EditText(context).apply {
            hint = "Paste your Klipy API key"
            setText(prefs.klipyKey)
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(palette.tonal)
            }
            // The real keyboard takes the preview's place while typing here.
            setOnFocusChangeListener { _, focused -> previewDock.visibility = if (focused) View.GONE else dockVisibility() }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    prefs.klipyKey = s?.toString()?.trim() ?: ""
                }
            })
        }
        addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12f) })
        buttons(Triple("Test my key", false) {
            val key = prefs.klipyKey.trim()
            if (key.isEmpty()) {
                toast("Paste your key first")
            } else {
                toast("Testing…")
                var id = prefs.klipyCustomerId
                if (id.isEmpty()) {
                    id = java.util.UUID.randomUUID().toString()
                    prefs.klipyCustomerId = id
                }
                KlipyClient.fetch(this@SettingsActivity, key, id, "", 1) { page, error ->
                    toast(if (page != null) "Key works. ${page.items.size} GIFs loaded." else error ?: "That didn't work.")
                }
            }
        })
        text("The key stays on this phone and is only sent to Klipy when you use GIFs.", secondary = true)
    }

    private fun translatorCard() = card("Translator") {
        toggle("Translator", "Translate what you type, or a message you copied", prefs::translatorEnabled)
        text("Open it from the toolbar (the four dots), then the translate icon. Type on the keys, or tap Paste to translate a message someone sent you.", secondary = true)
        text("Uses the free MyMemory service. Only the text you type or paste into the translator is sent, never your normal typing.", secondary = true)
        val field = EditText(context).apply {
            hint = "Optional: your email for a higher daily limit"
            setText(prefs.translateEmail)
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(palette.tonal)
            }
            // The real keyboard takes the preview's place while typing here.
            setOnFocusChangeListener { _, focused -> previewDock.visibility = if (focused) View.GONE else dockVisibility() }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    prefs.translateEmail = s?.toString()?.trim() ?: ""
                }
            })
        }
        addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12f) })
        text("Without an email the free limit is about 5,000 characters a day; with one it is higher. The email is only sent along with translations.", secondary = true)
    }

    private fun LinearLayout.inputField(hint: String, initial: String, password: Boolean, onChange: (String) -> Unit) {
        val field = EditText(context).apply {
            this.hint = hint
            setText(initial)
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or
                if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(palette.tonal)
            }
            // The real keyboard takes the preview's place while typing here.
            setOnFocusChangeListener { _, focused -> previewDock.visibility = if (focused) View.GONE else dockVisibility() }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = onChange(s?.toString()?.trim() ?: "")
            })
        }
        addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12f) })
    }

    /** Pick up to three colors for the spotlight. */
    private fun showSpotlightColors() {
        val current = SpotlightPalettes.parseCustom(prefs.spotlightColors).let { if (it.isEmpty()) SpotlightPalettes.defaultCustom else it }
        val colors = IntArray(3) { current.getOrElse(it) { SpotlightPalettes.defaultCustom[it] } }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(12f), dp(20f), dp(4f))
        }
        for (i in 0 until 3) {
            box.addView(TextView(this).apply { text = "Color ${i + 1}"; setPadding(0, dp(8f), 0, 0) })
            box.addView(ColorPickerView(this).apply {
                color = colors[i]
                ringColor = 0xFF888888.toInt()
                onColorChanged = { colors[i] = it or 0xFF000000.toInt() }
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Spotlight colors")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                prefs.spotlightColors = SpotlightPalettes.formatCustom(colors)
                prefs.spotlightPalette = SpotlightPalettes.CUSTOM
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun aiCard() = card("AI rewrite") {
        toggle("AI rewrite", "Fix, reword or shorten text from the keyboard", prefs::aiEnabled)
        text("Open it from the toolbar (the sparkle icon). It works on the text you select, or the whole field if nothing is selected, and is turned off in password fields.", secondary = true)
        text("Uses a free AI service with your own key. Groq is fast and reliable; Gemini is the alternative. Only the text you choose to rewrite is sent, and only when you tap a style. Your key stays on this phone and is never included in backups.", secondary = true)
        segmented("Service", AiClient.providerNames, if (prefs.aiProvider == AiClient.GEMINI) 1 else 0) {
            prefs.aiProvider = if (it == 1) AiClient.GEMINI else AiClient.GROQ
        }
        text("Groq", topPad = 14f)
        buttons(Triple("Get a free Groq key", false) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.groq.com/keys")))
            } catch (e: ActivityNotFoundException) {
                toast("No browser found. Go to console.groq.com/keys")
            }
        })
        inputField("Paste your Groq API key", prefs.groqKey, password = true) { prefs.groqKey = it }
        inputField("Groq model", prefs.groqModel, password = false) { prefs.groqModel = it.ifEmpty { GroqClient.DEFAULT_MODEL } }
        text("Gemini", topPad = 14f)
        buttons(Triple("Get a free Gemini key", false) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey")))
            } catch (e: ActivityNotFoundException) {
                toast("No browser found. Go to aistudio.google.com/apikey")
            }
        })
        inputField("Paste your Gemini API key", prefs.geminiKey, password = true) { prefs.geminiKey = it }
        inputField("Gemini model", prefs.geminiModel, password = false) { prefs.geminiModel = it.ifEmpty { GeminiClient.DEFAULT_MODEL } }
        buttons(Triple("My rewrite styles", false) {
            startActivity(Intent(this@SettingsActivity, AiStylesActivity::class.java))
        })
        toggle("Clean up dictation", "After voice typing, add punctuation and drop filler words (uses the service above)", prefs::voiceCleanup)
        buttons(Triple("Test the selected service", false) {
            if (!AiClient.hasKey(prefs)) {
                toast("Paste the key for the selected service first")
            } else {
                toast("Testing…")
                AiClient.rewrite(prefs, GeminiClient.Mode.FIX.instruction, "helo wrld, this is a tset") { text, error ->
                    toast(if (text != null) "It works: “$text”" else error ?: "That didn't work.")
                }
            }
        })
    }

    private fun clipboardCard() = card("Clipboard") {
        toggle("Clipboard history", "Paste recent copies from the keyboard", prefs::clipboardHistory)
        text("Items password managers mark as sensitive are never saved. Verification codes you copy appear above the keys for one tap and are forgotten after 10 minutes.", secondary = true)
        segmented("Delete history after", listOf("Never", "1 hour", "1 day", "1 week"), prefs.clipboardExpiry) { prefs.clipboardExpiry = it }
        buttons(Triple("Clear history", false) {
            confirm("Delete all clipboard history, including pinned items?") {
                ClipboardStore.get(this@SettingsActivity).clearAll()
                toast("Clipboard history cleared")
            }
        })
    }

    private var pendingBackupWithPhoto = false

    private fun backupCard() = card("Backup & sharing") {
        text("Save your settings, learned words, phrases, pinned clipboard items and favorite GIFs to a file, and restore them after reinstalling or on another phone. API keys are never included, and only what you pinned from the clipboard (never the rest of its history).", secondary = true)
        buttons(Triple("Export backup", true) {
            AlertDialog.Builder(this@SettingsActivity)
                .setMessage("Include your background photo and stickers in the backup? This can make the file large.")
                .setPositiveButton("With images") { _, _ -> startExport(true) }
                .setNegativeButton("Without") { _, _ -> startExport(false) }
                .setNeutralButton("Cancel", null)
                .show()
        }, Triple("Import backup", false) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try {
                startActivityForResult(intent, REQUEST_IMPORT)
            } catch (e: ActivityNotFoundException) {
                toast("No file picker found")
            }
        })
        text("Share your look as a short code anyone can apply. It covers colors, glass, fonts and sizes, not your photo.", secondary = true)
        buttons(Triple("Share my theme", true) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, ThemeShare.encode(prefs))
            }
            startActivity(Intent.createChooser(send, "Share theme code"))
        }, Triple("Apply a theme code", false) { showApplyThemeDialog() })
    }

    private fun startExport(withPhoto: Boolean) {
        pendingBackupWithPhoto = withPhoto
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "SakuriKey-backup.json")
        }
        try {
            startActivityForResult(intent, REQUEST_EXPORT)
        } catch (e: ActivityNotFoundException) {
            toast("No file picker found")
        }
    }

    private fun showApplyThemeDialog() {
        val clip = try {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: SecurityException) {
            null
        }
        val input = EditText(this).apply {
            hint = "Paste a theme code"
            if (ThemeShare.isThemeCode(clip)) setText(clip)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Apply a theme code")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                if (ThemeShare.apply(prefs, input.text.toString())) {
                    toast("Theme applied")
                    recreate()
                } else {
                    toast("That isn't a valid theme code")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun diagnosticsCard() = card("Diagnostics") {
        toggle("Record typing timing", "Measures how fast keys are handled, on this phone only", prefs::diagnostics)
        text("Turn this on, type for a while, then open the report to see if lag comes from the keyboard or the phone. " +
            "Crashes are always saved on this phone too. Nothing is ever sent anywhere.", secondary = true)
        buttons(Triple("Show report", false) {
            val report = Diagnostics.report(this@SettingsActivity)
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Typing report")
                .setMessage(report)
                .setPositiveButton("Copy") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("SakuriKey report", report))
                    toast("Report copied")
                }
                .setNegativeButton("Close", null)
                .show()
        }, Triple("Clear", false) {
            Diagnostics.clear(this@SettingsActivity)
            toast("Cleared")
        })
    }

    private fun aboutCard() = card("About") {
        text("SakuriKey ${UpdateChecker.currentVersion(this@SettingsActivity)}", secondary = true)
        // CI writes the commit into assets/build_id.txt, so you can tell which build is installed.
        val build = try { assets.open("build_id.txt").bufferedReader().use { it.readText().trim().take(7) } } catch (e: Exception) { "local" }
        text("Build $build", secondary = true)
        buttons(Triple("Check for updates", true) { checkForUpdates(build) })
    }

    /** Looks for a newer test build first (installable right here), then for a newer numbered release. */
    private fun checkForUpdates(installedBuild: String) {
        toast("Checking…")
        UpdateChecker.checkTestBuild { test ->
            val commit = test.commit?.take(7)
            val newTest = test.error == null && test.apkUrl != null && commit != null &&
                installedBuild != "local" && !commit.equals(installedBuild, ignoreCase = true)
            if (newTest) {
                val mb = if (test.sizeBytes > 0) " (${"%.1f".format(test.sizeBytes / 1_048_576.0)} MB)" else ""
                AlertDialog.Builder(this)
                    .setTitle("A newer build is available")
                    .setMessage("You have build $installedBuild. The newest is $commit$mb.\n\nSakuriKey will download it, then Android asks you to confirm the install.")
                    .setPositiveButton("Download and install") { _, _ ->
                        ApkInstaller.downloadAndInstall(this, test.apkUrl!!, { toast(it) }, { toast(it) })
                    }
                    .setNegativeButton("Later", null)
                    .show()
                return@checkTestBuild
            }
            UpdateChecker.check(this) { r ->
                when {
                    r.error != null && test.error == null && test.commit != null -> toast("You're up to date")
                    r.error != null -> toast(r.error)
                    r.newer -> AlertDialog.Builder(this)
                        .setMessage("Version ${r.latest} is available." + if (r.notes.isNotEmpty()) "\n\n${r.notes}" else "")
                        .setPositiveButton("Open download page") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.url)))
                        }
                        .setNegativeButton("Later", null)
                        .show()
                    else -> toast("You're up to date")
                }
            }
        }
    }

    private fun privacyCard() = card("Privacy") {
        text(
            "SakuriKey has no analytics. The only things that use the internet are GIF search and the " +
                "translator (only for the text you search or translate), and Check for updates when you tap it. Learned words, clipboard " +
                "history and your photo never leave this phone.",
            secondary = true,
        )
        buttons(Triple("Reset all settings", false) {
            confirm("Reset every setting to its default? Learned words and clipboard history are kept.") {
                prefs.sp.edit().clear().apply()
                recreate()
            }
        })
    }

    private fun confirm(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- background photo -----------------------------------------------------------------------

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQUEST_PHOTO)
        } catch (e: ActivityNotFoundException) {
            toast("No photo picker found")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQUEST_EXPORT || requestCode == REQUEST_IMPORT) {
            handleBackupResult(requestCode, data?.data ?: return)
            return
        }
        if (requestCode != REQUEST_PHOTO) return
        val uri = data?.data ?: return
        val app = applicationContext
        Thread {
            val ok = BackgroundImageStore.import(app, uri)
            runOnUiThread {
                if (ok) {
                    if (prefs.backgroundOpacity == 0) prefs.backgroundOpacity = 100
                    prefs.backgroundVersion = System.currentTimeMillis()
                } else {
                    toast("Couldn't load that image")
                }
            }
        }.start()
    }

    private fun handleBackupResult(requestCode: Int, uri: android.net.Uri) {
        val app = applicationContext
        val withPhoto = pendingBackupWithPhoto
        Thread {
            val message = try {
                if (requestCode == REQUEST_EXPORT) {
                    val json = BackupManager.export(app, withPhoto)
                    app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    "Backup saved"
                } else {
                    val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    BackupManager.import(app, text).message
                }
            } catch (e: java.io.IOException) {
                "Couldn't read or write that file"
            } catch (e: OutOfMemoryError) {
                "That backup is too big to build. Try again without images."
            }
            runOnUiThread {
                toast(message)
                if (requestCode == REQUEST_IMPORT) recreate()
            }
        }.start()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val REQUEST_PHOTO = 1
        const val REQUEST_EXPORT = 2
        const val REQUEST_IMPORT = 3
    }
}
