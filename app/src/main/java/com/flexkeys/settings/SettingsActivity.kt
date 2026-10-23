package com.flexkeys.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.flexkeys.layout.Layouts
import com.flexkeys.service.Feedback
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView
import kotlin.reflect.KMutableProperty0

/**
 * The launcher screen: setup steps, a live keyboard preview pinned at the top and every
 * customization option below it. Built in code to keep the project free of extra libraries.
 */
class SettingsActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var preview: KeyboardRootView
    private lateinit var previewController: PreviewController
    private lateinit var palette: Palette

    private lateinit var stepEnableStatus: TextView
    private lateinit var stepSwitchStatus: TextView
    private val accentTinted = ArrayList<View>()

    private class Palette(dark: Boolean) {
        val background = if (dark) 0xFF101116.toInt() else 0xFFF2F3F8.toInt()
        val card = if (dark) 0xFF1B1D24.toInt() else 0xFFFFFFFF.toInt()
        val text = if (dark) 0xFFEEF0F6.toInt() else 0xFF15171C.toInt()
        val secondary = if (dark) 0xFF9AA0AE.toInt() else 0xFF5E6472.toInt()
        val outline = if (dark) 0x22FFFFFF else 0x14000000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = Palette(dark)
        setContentView(buildContent())
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        preview.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a separate window; re-check when it closes.
        if (hasFocus) updateSetupStatus()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW -> previewController.refresh()
            Prefs.KEY_BG_VERSION -> preview.reloadPhoto()
            Prefs.KEY_ACCENT -> {
                applyAccent()
                preview.applyTheme(GlassTheme.from(this, prefs))
            }
            else -> preview.applyTheme(GlassTheme.from(this, prefs))
        }
    }

    // ---- layout -----------------------------------------------------------------------------------

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildContent(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        page.addView(TextView(this).apply {
            text = "FlexKeys"
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(20f), dp(16f), dp(20f), dp(4f))
        })

        // Live preview, pinned above the scrolling settings.
        val previewOutput = TextView(this).apply {
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setPadding(dp(20f), dp(6f), dp(20f), dp(8f))
        }
        page.addView(previewOutput)
        preview = KeyboardRootView(this, GlassTheme.from(this, prefs))
        val previewFrame = FrameLayout(this).apply {
            clipChildren = false
            addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        page.clipChildren = false
        page.addView(previewFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewController = PreviewController(preview, previewOutput, prefs, Feedback(this, prefs))
        preview.reloadPhoto()

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(28f))
        }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        list.addView(setupCard())
        list.addView(appearanceCard())
        list.addView(layoutCard())
        list.addView(typingCard())
        list.addView(feedbackCard())
        list.addView(privacyCard())
        applyAccent()
        return page
    }

    private fun card(title: String, build: LinearLayout.() -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(palette.card)
                setStroke(dp(1f), palette.outline)
            }
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(6f))
        })
        card.build()
        card.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12f) }
        return card
    }

    private fun LinearLayout.label(text: String, secondary: Boolean = false, sizeSp: Float = 15f): TextView {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(if (secondary) palette.secondary else palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (secondary) 13f else sizeSp)
            setPadding(0, dp(if (secondary) 2f else 8f), 0, dp(2f))
        }
        addView(tv)
        return tv
    }

    private fun LinearLayout.slider(
        title: String, min: Int, max: Int, prop: KMutableProperty0<Int>, format: (Int) -> String,
    ) {
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val name = TextView(context).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val value = TextView(context).apply {
            setTextColor(palette.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.END
        }
        header.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(value)
        header.setPadding(0, dp(10f), 0, 0)
        addView(header)
        val seek = SeekBar(context).apply {
            this.max = max - min
            progress = (prop.get() - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = format(p + min)
                    if (fromUser) prop.set(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        value.text = format(prop.get())
        accentTinted.add(seek)
        addView(seek)
    }

    private fun LinearLayout.toggle(title: String, summary: String?, prop: KMutableProperty0<Boolean>): Switch {
        val sw = Switch(context).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            isChecked = prop.get()
            setOnCheckedChangeListener { _, checked -> prop.set(checked) }
            setPadding(0, dp(8f), 0, if (summary == null) dp(8f) else 0)
        }
        accentTinted.add(sw)
        addView(sw)
        if (summary != null) label(summary, secondary = true)
        return sw
    }

    private fun LinearLayout.choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val group = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        options.forEachIndexed { i, label ->
            val rb = RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                setTextColor(palette.text)
                isChecked = i == selected
                setPadding(dp(2f), 0, dp(12f), 0)
            }
            accentTinted.add(rb)
            group.addView(rb)
            rb.setOnCheckedChangeListener { _, checked -> if (checked) onSelect(i) }
        }
        addView(group)
    }

    private fun LinearLayout.buttons(vararg actions: Pair<String, () -> Unit>) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6f), 0, dp(2f))
        }
        actions.forEachIndexed { i, (label, action) ->
            val b = Button(context).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }
            accentTinted.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(8f)
            })
        }
        addView(row)
    }

    private fun applyAccent() {
        val accent = prefs.accentColor or 0xFF000000.toInt()
        val tint = ColorStateList.valueOf(accent)
        val onAccent = if (GlassTheme.luminance(accent) > 0.6f) 0xFF15171C.toInt() else 0xFFFFFFFF.toInt()
        for (v in accentTinted) when (v) {
            is SeekBar -> {
                v.progressTintList = tint
                v.thumbTintList = tint
            }
            is Switch -> {
                v.thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, 0xFFBDC1CC.toInt())
                )
                v.trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(GlassTheme.withAlpha(accent, 0.5f), 0x66888C99)
                )
            }
            is RadioButton -> v.buttonTintList = tint
            is Button -> {
                v.backgroundTintList = tint
                v.setTextColor(onAccent)
            }
        }
    }

    // ---- cards ---------------------------------------------------------------------------------------

    private fun setupCard() = card("Get started") {
        label("1. Enable FlexKeys in your system keyboard list.")
        stepEnableStatus = label("", secondary = true)
        buttons("Open keyboard settings" to {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                toast("Keyboard settings are not available on this device")
            }
        })
        label("2. Switch to FlexKeys as your current keyboard.")
        stepSwitchStatus = label("", secondary = true)
        buttons("Choose keyboard" to {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        label("3. Try it out:")
        addView(EditText(context).apply {
            hint = "Type here with FlexKeys"
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        })
    }

    private fun updateSetupStatus() {
        if (!::stepEnableStatus.isInitialized) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current?.substringBefore('/') == packageName
        stepEnableStatus.text = if (enabled) "✓ FlexKeys is enabled" else "Not enabled yet"
        stepSwitchStatus.text = when {
            selected -> "✓ FlexKeys is your current keyboard"
            enabled -> "Not selected yet"
            else -> "Enable FlexKeys first"
        }
    }

    private fun appearanceCard() = card("Liquid glass") {
        label("Tint")
        choice(listOf("Auto", "Light", "Dark"), prefs.themeMode) { prefs.themeMode = it }
        label("Accent color")
        addView(ColorPickerView(context).apply {
            color = prefs.accentColor
            ringColor = palette.text
            onColorChanged = { prefs.accentColor = it }
        })
        label("Background photo")
        label("Pick a photo to show behind the glass. A private copy is kept inside the app.", secondary = true)
        buttons("Choose photo" to { pickPhoto() }, "Remove" to {
            BackgroundImageStore.delete(this@SettingsActivity)
            prefs.backgroundVersion = System.currentTimeMillis()
        })
        slider("Photo opacity", 0, 100, prefs::backgroundOpacity) { "$it%" }
        slider("Glass opacity", 0, 100, prefs::glassOpacity) { "$it%" }
        slider("Blur strength", 0, 100, prefs::blurStrength) { "$it%" }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            label("Real-time blur needs Android 12. This device uses a lighter frosted fallback.", secondary = true)
        }
    }

    private fun layoutCard() = card("Layout & size") {
        label("Keyboard layout")
        val layouts = Layouts.letterLayouts
        addView(Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, layouts.map { it.name })
            setSelection(layouts.indexOfFirst { it.id == prefs.layoutId }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(palette.text)
                    if (layouts[position].id != prefs.layoutId) prefs.layoutId = layouts[position].id
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        toggle("Number row", "Always show 1-0 above the letters", prefs::numberRow)
        toggle("Key preview", "Show a bubble above keys as you type", prefs::keyPreview)
        slider("Keyboard height", 60, 150, prefs::heightPercent) { "$it%" }
        slider("Key spacing", 0, 16, prefs::keySpacingDp) { "$it dp" }
        slider("Corner radius", 0, 30, prefs::cornerRadiusDp) { "$it dp" }
        slider("Font size", 60, 160, prefs::fontPercent) { "$it%" }
        label("One-handed mode")
        choice(listOf("Off", "Left", "Right"), prefs.oneHanded) { prefs.oneHanded = it }
    }

    private fun typingCard() = card("Typing") {
        toggle("Auto-capitalization", null, prefs::autoCapitalize)
        toggle("Double-space period", "Tap space twice to insert \". \"", prefs::doubleSpacePeriod)
    }

    private fun feedbackCard() = card("Sound & vibration") {
        toggle("Vibrate on key press", null, prefs::haptics)
        toggle("Sound on key press", null, prefs::sound)
        slider("Sound volume", 0, 100, prefs::soundVolume) { "$it%" }
    }

    private fun privacyCard() = card("Privacy") {
        label(
            "FlexKeys has no internet permission and no analytics. Learned words, clipboard " +
                "history and your background photo are stored only on this device.",
            secondary = true,
        )
        buttons("Reset all settings" to {
            AlertDialog.Builder(this@SettingsActivity)
                .setMessage("Reset every setting to its default? Learned words and clipboard history are kept.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.sp.edit().clear().apply()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
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
        toast("Applying photo…")
        val app = applicationContext
        Thread {
            val ok = BackgroundImageStore.import(app, uri)
            runOnUiThread {
                if (ok) {
                    prefs.backgroundVersion = System.currentTimeMillis()
                    if (prefs.backgroundOpacity == 0) prefs.backgroundOpacity = 100
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
