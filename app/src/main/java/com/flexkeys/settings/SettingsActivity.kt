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
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.layout.Layouts
import com.flexkeys.service.Feedback
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView
import kotlin.reflect.KMutableProperty0

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
    private var systemDark = false
    private var photoRequest = 0

    /** Widgets that draw with the accent color and must repaint when it changes. */
    private val accentViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        when (key) {
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW, Prefs.KEY_PUNCTUATION -> previewController.refresh()
            Prefs.KEY_BG_VERSION -> {
                preview.reloadPhoto()
                loadPagePhoto()
            }
            Prefs.KEY_ONE_HANDED -> preview.setOneHanded(prefs.oneHanded)
            Prefs.KEY_RECENT_EMOJI, Prefs.KEY_CLIPBOARD, Prefs.KEY_SUGGESTIONS, Prefs.KEY_AUTOCORRECT,
            Prefs.KEY_LEARN, Prefs.KEY_AUTOCAP, Prefs.KEY_DOUBLE_SPACE, Prefs.KEY_HAPTICS, Prefs.KEY_SOUND,
            Prefs.KEY_SOUND_VOLUME, Prefs.KEY_SKIN_TONES -> Unit
            Prefs.KEY_ACCENT -> {
                applyAccent()
                applyKeyboardTheme()
            }
            else -> applyKeyboardTheme()
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

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val list = LinearLayout(this).apply {
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

        list.addView(header())
        setupCard = card()
        list.addView(setupCard)
        list.addView(appearanceCard())
        list.addView(layoutCard())
        list.addView(typingCard())
        list.addView(feedbackCard())
        list.addView(clipboardCard())
        list.addView(privacyCard())

        column.addView(previewDockView())
        return page
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
        dock.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(16f), dp(6f), dp(16f), dp(8f)) })
        preview = KeyboardRootView(this, GlassTheme.from(this, prefs))
        dock.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewController = PreviewController(preview, output, prefs, Feedback(this, prefs))
        preview.reloadPhoto()
        previewDock = dock
        return dock
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6f), dp(12f), dp(6f), dp(6f))
        addView(TextView(context).apply {
            text = "FlexKeys"
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

    private fun card(title: String, build: LinearLayout.() -> Unit): View = card().apply {
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
        row.setOnClickListener { sw.toggle() }
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
            text = if (enabled && selected) "✓  FlexKeys is ready" else "Get started"
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        when {
            !enabled -> {
                setupCard.text("Step 1 of 2: turn on FlexKeys in your keyboard list.", secondary = true)
                setupCard.buttons(Triple("Enable FlexKeys", true) { openImeSettings() })
            }
            !selected -> {
                setupCard.text("Step 2 of 2: switch to FlexKeys.", secondary = true)
                setupCard.buttons(Triple("Switch keyboard", true) { showImePicker() })
            }
            else -> setupCard.text("Try it here, or tweak the look with the live preview below.", secondary = true)
        }
        if (enabled) {
            val field = EditText(this).apply {
                hint = "Type here with FlexKeys"
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
                setOnFocusChangeListener { _, focused -> previewDock.visibility = if (focused) View.GONE else View.VISIBLE }
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

    /** A one-tap look: tint, accent, glass and blur. */
    private class Preset(val name: String, val mode: Int, val accent: Int, val glass: Int, val blur: Int)

    private val presets = listOf(
        Preset("Frost", Prefs.THEME_LIGHT, 0xFF5B8CFF.toInt(), 30, 60),
        Preset("Midnight", Prefs.THEME_DARK, 0xFF7C5CFF.toInt(), 35, 50),
        Preset("Aurora", Prefs.THEME_DARK, 0xFF20C997.toInt(), 25, 70),
        Preset("Sunset", Prefs.THEME_LIGHT, 0xFFFF6B6B.toInt(), 40, 50),
        Preset("Clear", Prefs.THEME_AUTO, 0xFF22B8CF.toInt(), 8, 30),
        Preset("Graphite", Prefs.THEME_DARK, 0xFF8A93A6.toInt(), 55, 40),
    )

    private fun LinearLayout.presetRow() {
        text("Presets", topPad = 4f)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6f), 0, dp(2f))
        }
        presets.forEachIndexed { i, preset ->
            row.addView(PillButton(context, preset.name, false, palette).apply {
                onClick = {
                    prefs.sp.edit()
                        .putInt(Prefs.KEY_THEME_MODE, preset.mode)
                        .putInt(Prefs.KEY_ACCENT, preset.accent)
                        .putInt(Prefs.KEY_GLASS_OPACITY, preset.glass)
                        .putInt(Prefs.KEY_BLUR, preset.blur)
                        .apply()
                    // Rebuild so every slider and picker shows the new values.
                    recreate()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) marginStart = dp(8f) })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
    }

    private fun appearanceCard() = card("Liquid glass") {
        presetRow()
        segmented("Tint", listOf("Auto", "Light", "Dark"), prefs.themeMode) { prefs.themeMode = it }
        text("Accent", topPad = 14f)
        addView(ColorPickerView(context).apply {
            color = prefs.accentColor
            ringColor = palette.text
            onColorChanged = { prefs.accentColor = it }
        })
        text("Background photo", topPad = 14f)
        text("Shown behind the glass. A private copy stays inside the app.", secondary = true)
        buttons(Triple("Choose photo", true) { pickPhoto() }, Triple("Remove", false) {
            BackgroundImageStore.delete(this@SettingsActivity)
            prefs.backgroundVersion = System.currentTimeMillis()
        })
        slider("Photo opacity", 0, 100, prefs::backgroundOpacity) { "$it%" }
        slider("Glass opacity", 0, 100, prefs::glassOpacity) { "$it%" }
        slider("Blur", 0, 100, prefs::blurStrength) { "$it%" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            text("Live blur needs Android 12. This phone uses a lighter frosted look.", secondary = true)
        }
    }

    private fun layoutCard() = card("Layout & size") {
        val layouts = Layouts.letterLayouts
        segmented("Layout", layouts.map { it.name }, layouts.indexOfFirst { it.id == prefs.layoutId }.coerceAtLeast(0)) {
            prefs.layoutId = layouts[it].id
        }
        segmented("One-handed", listOf("Off", "Left", "Right"), prefs.oneHanded) { prefs.oneHanded = it }
        slider("Keyboard height", 60, 150, prefs::heightPercent) { "$it%" }
        slider("Key spacing", 0, 16, prefs::keySpacingDp) { "$it dp" }
        slider("Corner radius", 0, 30, prefs::cornerRadiusDp) { "$it dp" }
        slider("Font size", 60, 160, prefs::fontPercent) { "$it%" }
        toggle("Comma & period keys", "Show , and . beside the space bar, like Gboard", prefs::punctuationKeys)
        toggle("Number row", "Always show 1–0 above the letters", prefs::numberRow)
        toggle("Key preview", "Pop up a bubble above keys as you type", prefs::keyPreview)
    }

    private fun typingCard() = card("Typing") {
        toggle("Suggestions", "Completions and next-word predictions", prefs::suggestions)
        toggle("Autocorrect", "Fix typos on space. Backspace right after undoes it.", prefs::autocorrect)
        toggle("Learn my words", "Improve suggestions from what you type, on-device only", prefs::learnWords)
        toggle("Auto-capitalization", null, prefs::autoCapitalize)
        toggle("Double-space period", "Tap space twice for \". \"", prefs::doubleSpacePeriod)
        buttons(Triple("Manage learned words", false) {
            startActivity(Intent(this@SettingsActivity, LearnedWordsActivity::class.java))
        })
        buttons(Triple("Clear learned words", false) {
            confirm("Forget every word FlexKeys has learned from your typing?") {
                SuggestionEngine.get(this@SettingsActivity).user.clear()
                toast("Learned words cleared")
            }
        })
    }

    private fun feedbackCard() = card("Sound & vibration") {
        toggle("Vibrate on key press", null, prefs::haptics)
        toggle("Sound on key press", null, prefs::sound)
        slider("Sound volume", 0, 100, prefs::soundVolume) { "$it%" }
    }

    private fun clipboardCard() = card("Clipboard") {
        toggle("Clipboard history", "Paste recent copies from the keyboard", prefs::clipboardHistory)
        text("Items password managers mark as sensitive are never saved.", secondary = true)
        buttons(Triple("Clear history", false) {
            confirm("Delete all clipboard history, including pinned items?") {
                ClipboardStore.get(this@SettingsActivity).clearAll()
                toast("Clipboard history cleared")
            }
        })
    }

    private fun privacyCard() = card("Privacy") {
        text(
            "FlexKeys has no internet permission and no analytics. Learned words, clipboard history " +
                "and your photo never leave this phone.",
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
        if (requestCode != REQUEST_PHOTO || resultCode != RESULT_OK) return
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val REQUEST_PHOTO = 1
    }
}
