package com.flexkeys.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed access to every FlexKeys setting. Backed by a single SharedPreferences file so
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
    var cornerRadiusDp by int(KEY_CORNER, 8)
    /** Label size in percent of the default. */
    var fontPercent by int(KEY_FONT, 100)

    // ---- feedback ----
    var haptics by bool(KEY_HAPTICS, true)
    var sound by bool(KEY_SOUND, false)
    var soundVolume by int(KEY_SOUND_VOLUME, 40)
    var keyPreview by bool(KEY_PREVIEW, false)

    // ---- theme ----
    var themeMode by int(KEY_THEME_MODE, THEME_AUTO)
    var accentColor by int(KEY_ACCENT, DEFAULT_ACCENT)
    var backgroundOpacity by int(KEY_BG_OPACITY, 100)
    var glassOpacity by int(KEY_GLASS_OPACITY, 35)
    var blurStrength by int(KEY_BLUR, 50)
    /** Strength of the blue/purple/pink glow across the glass; 0 turns it off. */
    var gradientStrength by int(KEY_GRADIENT, 60)
    /** Bumped every time the background photo changes so views know to reload it. */
    var backgroundVersion by long(KEY_BG_VERSION, 0L)

    // ---- typing ----
    var suggestions by bool(KEY_SUGGESTIONS, true)
    var autocorrect by bool(KEY_AUTOCORRECT, true)
    var autoCapitalize by bool(KEY_AUTOCAP, true)
    var doubleSpacePeriod by bool(KEY_DOUBLE_SPACE, true)
    var learnWords by bool(KEY_LEARN, true)
    var clipboardHistory by bool(KEY_CLIPBOARD, true)

    // ---- emoji ----
    var recentEmoji by string(KEY_RECENT_EMOJI, "")
    var skinTones by string(KEY_SKIN_TONES, "")

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
        const val KEY_STYLE_GLASS = 0
        const val KEY_STYLE_TILES = 1
        const val KEY_BG_VERSION = "bg_version"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_AUTOCORRECT = "autocorrect"
        const val KEY_AUTOCAP = "autocap"
        const val KEY_DOUBLE_SPACE = "double_space_period"
        const val KEY_LEARN = "learn_words"
        const val KEY_CLIPBOARD = "clipboard_history"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        const val KEY_SKIN_TONES = "skin_tones"
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

        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val DEFAULT_ACCENT = 0xFF5B8CFF.toInt()

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
