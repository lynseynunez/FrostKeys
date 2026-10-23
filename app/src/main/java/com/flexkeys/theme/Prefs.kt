package com.flexkeys.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed access to every SakuriKey setting. Backed by a single SharedPreferences file so
 * the settings app and the keyboard service see each other's changes immediately.
 */
class Prefs private constructor(context: Context) {

    val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- layout ----
    var layoutId by string(KEY_LAYOUT, "qwerty")
    var numberRow by bool(KEY_NUMBER_ROW, false)
    /** Keyboard height in percent of the default. */
    var heightPercent by int(KEY_HEIGHT, 100)
    var keySpacingDp by int(KEY_SPACING, 5)
    /** [KEY_STYLE_GLASS]: bare glass letters on the sheet; [KEY_STYLE_TILES]: a glass tile behind each key. */
    var keyStyle by int(KEY_STYLE, KEY_STYLE_GLASS)
    var fontFamily by string(KEY_FONT_FAMILY, Fonts.INTER)
    /** Emoji shown in the middle of the space bar; empty for none. */
    var spaceEmblem by string(KEY_SPACE_EMBLEM, SPACE_EMBLEM_ICON)
    /** How bright and visible the frosted space bar is, 0..100. */
    var spaceGlass by int(KEY_SPACE_GLASS, 60)
    /** Size of the space bar emblem in percent. */
    var emblemSize by int(KEY_EMBLEM_SIZE, 100)
    var cornerRadiusDp by int(KEY_CORNER, 8)
    /** Label size in percent of the default. */
    var fontPercent by int(KEY_FONT, 100)

    // ---- feedback ----
    var haptics by bool(KEY_HAPTICS, true)
    var sound by bool(KEY_SOUND, false)
    var soundVolume by int(KEY_SOUND_VOLUME, 40)
    /** 0 custom strength, 1 tick, 2 click, 3 heavy (the phone's built-in effects where available). */
    var hapticStyle by int(KEY_HAPTIC_STYLE, 0)
    /** Strength of the custom vibration, 0..100. */
    var hapticStrength by int(KEY_HAPTIC_STRENGTH, 50)
    var keyPreview by bool(KEY_PREVIEW, false)

    // ---- theme ----
    var themeMode by int(KEY_THEME_MODE, THEME_AUTO)
    var accentColor by int(KEY_ACCENT, DEFAULT_ACCENT)
    var backgroundOpacity by int(KEY_BG_OPACITY, 100)
    var glassOpacity by int(KEY_GLASS_OPACITY, 35)
    var blurStrength by int(KEY_BLUR, 50)
    /** Strength of the blue/purple/pink glow across the glass; 0 turns it off. */
    var gradientStrength by int(KEY_GRADIENT, 60)
    /** Photo zoom in percent (100 = just covering the keyboard) and pan, -100..100 (0 = centered). */
    var photoZoom by int(KEY_PHOTO_ZOOM, 100)
    var photoPanX by int(KEY_PHOTO_PAN_X, 0)
    var photoPanY by int(KEY_PHOTO_PAN_Y, 0)
    /** When on, light or dark tint follows how bright the photo is. */
    var adaptTint by bool(KEY_ADAPT_TINT, false)
    /** Average brightness of the photo in percent, or -1 when there is no photo. */
    var photoLuma by int(KEY_PHOTO_LUMA, -1)
    /** Most prominent color of the photo, found when it was picked. */
    var photoAccent by int(KEY_PHOTO_ACCENT, DEFAULT_ACCENT)
    /** Bumped every time the background photo changes so views know to reload it. */
    var backgroundVersion by long(KEY_BG_VERSION, 0L)

    // ---- typing ----
    var suggestions by bool(KEY_SUGGESTIONS, true)
    var autocorrect by bool(KEY_AUTOCORRECT, true)
    var autoCapitalize by bool(KEY_AUTOCAP, true)
    var doubleSpacePeriod by bool(KEY_DOUBLE_SPACE, true)
    var learnWords by bool(KEY_LEARN, true)
    var clipboardHistory by bool(KEY_CLIPBOARD, true)
    /** Delete unpinned clipboard history after: 0 never, 1 an hour, 2 a day, 3 a week. */
    var clipboardExpiry by int(KEY_CLIPBOARD_EXPIRY, 0)

    // ---- emoji ----
    var recentEmoji by string(KEY_RECENT_EMOJI, "")
    var skinTones by string(KEY_SKIN_TONES, "")
    /** Default skin tone for people and hand emoji: 0 none (yellow), 1..5 light to dark. */
    var skinToneDefault by int(KEY_SKIN_DEFAULT, 0)

    // ---- translator (sends only the text typed or pasted into the translator panel) ----
    var translatorEnabled by bool(KEY_TRANSLATOR, true)
    var translateFrom by string(KEY_TRANSLATE_FROM, "en")
    var translateTo by string(KEY_TRANSLATE_TO, "es")
    /** Optional: raises MyMemory's free daily limit. Only sent with translations. */
    var translateEmail by string(KEY_TRANSLATE_EMAIL, "")

    // ---- GIFs (the only feature that uses the internet; off until the user turns it on) ----
    var gifsEnabled by bool(KEY_GIFS, false)
    /** The user's own free Klipy API key. Stored only on this device. */
    var klipyKey by string(KEY_KLIPY_KEY, "")
    /** Random id Klipy asks for; generated on the device, not linked to anything. */
    var klipyCustomerId by string(KEY_KLIPY_CUSTOMER, "")
    /** AI rewrite is off until the user turns it on and adds their own Gemini key. */
    var aiEnabled by bool(KEY_AI_ENABLED, false)
    /** The user's own free Gemini API key. Stored only on this device and never backed up. */
    var geminiKey by string(KEY_GEMINI_KEY, "")
    var geminiModel by string(KEY_GEMINI_MODEL, "gemini-flash-latest")
    /** Actions shown in the top row, in order (see ToolbarItems). */
    var toolbarItems by string(KEY_TOOLBAR_ITEMS, "")
    /** The last fancy text style picked (index into FancyText.Style). */
    var fancyStyle by int(KEY_FANCY_STYLE, 0)
    /** Extra long-press characters per key, one "a:äå" line per key (see CustomKeys). */
    var customKeys by string(KEY_CUSTOM_KEYS, "")
    /** Comma and period keys next to the space bar, as on Gboard. */
    var punctuationKeys by bool(KEY_PUNCTUATION, true)

    private fun string(key: String, def: String) = Pref({ sp.getString(key, def) ?: def }, { sp.edit().putString(key, it).apply() })
    private fun bool(key: String, def: Boolean) = Pref({ sp.getBoolean(key, def) }, { sp.edit().putBoolean(key, it).apply() })
    private fun int(key: String, def: Int) = Pref({ sp.getInt(key, def) }, { sp.edit().putInt(key, it).apply() })
    private fun long(key: String, def: Long) = Pref({ sp.getLong(key, def) }, { sp.edit().putLong(key, it).apply() })

    class Pref<T>(private val read: () -> T, private val write: (T) -> Unit) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = read()
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) = write(value)
    }

    companion object {
        const val FILE = "flexkeys_prefs"

        const val KEY_LAYOUT = "layout"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_HEIGHT = "height_percent"
        const val KEY_SPACING = "key_spacing"
        const val KEY_CORNER = "corner_radius"
        const val KEY_FONT = "font_percent"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val KEY_HAPTIC_STYLE = "haptic_style"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_PREVIEW = "key_preview"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_BG_OPACITY = "bg_opacity"
        const val KEY_GLASS_OPACITY = "glass_opacity"
        const val KEY_BLUR = "blur"
        const val KEY_GRADIENT = "gradient"
        const val KEY_STYLE = "key_style"
        const val KEY_FONT_FAMILY = "font_family"
        const val KEY_SPACE_EMBLEM = "space_emblem"
        const val KEY_SPACE_GLASS = "space_glass"
        const val KEY_EMBLEM_SIZE = "emblem_size"
        const val KEY_STYLE_GLASS = 0
        const val KEY_STYLE_TILES = 1
        const val KEY_BG_VERSION = "bg_version"
        const val KEY_PHOTO_ZOOM = "photo_zoom"
        const val KEY_PHOTO_PAN_X = "photo_pan_x"
        const val KEY_PHOTO_PAN_Y = "photo_pan_y"
        const val KEY_ADAPT_TINT = "adapt_tint"
        const val KEY_PHOTO_LUMA = "photo_luma"
        const val KEY_PHOTO_ACCENT = "photo_accent"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_AUTOCORRECT = "autocorrect"
        const val KEY_AUTOCAP = "autocap"
        const val KEY_DOUBLE_SPACE = "double_space_period"
        const val KEY_LEARN = "learn_words"
        const val KEY_CLIPBOARD = "clipboard_history"
        const val KEY_CLIPBOARD_EXPIRY = "clipboard_expiry"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        const val KEY_SKIN_TONES = "skin_tones"
        const val KEY_SKIN_DEFAULT = "skin_tone_default"
        const val KEY_TRANSLATOR = "translator_enabled"
        const val KEY_TRANSLATE_FROM = "translate_from"
        const val KEY_TRANSLATE_TO = "translate_to"
        const val KEY_TRANSLATE_EMAIL = "translate_email"
        /** Space bar emblem token for the bundled icon; other values are emoji, empty is none. */
        const val SPACE_EMBLEM_ICON = "icon"
        const val KEY_GIFS = "gifs_enabled"
        const val KEY_KLIPY_KEY = "klipy_key"
        const val KEY_KLIPY_CUSTOMER = "klipy_customer_id"
        const val KEY_PUNCTUATION = "punctuation_keys"
        const val KEY_CUSTOM_KEYS = "custom_keys"
        const val KEY_FANCY_STYLE = "fancy_style"
        const val KEY_TOOLBAR_ITEMS = "toolbar_items"
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_GEMINI_KEY = "gemini_key"
        const val KEY_GEMINI_MODEL = "gemini_model"

        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val DEFAULT_ACCENT = 0xFF5B8CFF.toInt()

        /**
         * Settings that change how the keyboard looks. Changing one of these rebuilds the theme;
         * every other setting is simply read the next time it is used.
         */
        val THEME_KEYS: Set<String> = setOf(
            KEY_HEIGHT, KEY_SPACING, KEY_CORNER, KEY_FONT, KEY_THEME_MODE, KEY_ACCENT, KEY_BG_OPACITY,
            KEY_GLASS_OPACITY, KEY_BLUR, KEY_GRADIENT, KEY_STYLE, KEY_FONT_FAMILY, KEY_SPACE_EMBLEM,
            KEY_SPACE_GLASS, KEY_EMBLEM_SIZE, KEY_PHOTO_ZOOM, KEY_PHOTO_PAN_X, KEY_PHOTO_PAN_Y,
            KEY_ADAPT_TINT, KEY_PHOTO_LUMA,
        )

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
