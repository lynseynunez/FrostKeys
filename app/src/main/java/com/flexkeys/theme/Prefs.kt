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
    /** Look of the space bar: 0 frosted card, 1 outline, 2 clear glass, 3 underline, 4 accent tint. */
    var spaceStyle by int(KEY_SPACE_STYLE, 0)
    /** Size of the space bar emblem in percent. */
    var emblemSize by int(KEY_EMBLEM_SIZE, 100)
    var cornerRadiusDp by int(KEY_CORNER, 8)
    /** Size of the suggestion words above the keys, in percent of the default. */
    var suggestionFontPercent by int(KEY_SUGGESTION_FONT, 80)
    /** Label size in percent of the default. */
    var fontPercent by int(KEY_FONT, 100)

    // ---- feedback ----
    var haptics by bool(KEY_HAPTICS, true)
    var sound by bool(KEY_SOUND, false)
    var soundVolume by int(KEY_SOUND_VOLUME, 40)
    /** 0 custom strength, 1 tick, 2 click, 3 heavy (the phone's built-in effects where available). */
    var hapticStyle by int(KEY_HAPTIC_STYLE, 0)
    /** Keyboard feel preset (see KeyFeel): 0 keeps the older haptic style settings. */
    var keyFeel by int(KEY_KEY_FEEL, 0)
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
    /** Offer a copied email, phone number or link as a one-tap paste, and answers for sums and unit conversions. */
    var smartClips by bool(KEY_SMART_CLIPS, true)
    var calculator by bool(KEY_CALCULATOR, true)
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
    /** The user's starred GIFs (see GifFavorites). */
    var gifFavorites by string(KEY_GIF_FAVORITES, "")
    /** AI rewrite is off until the user turns it on and adds their own Gemini key. */
    var aiEnabled by bool(KEY_AI_ENABLED, false)
    /** The user's own rewrite styles, one "name<TAB>instruction" per line (see AiPresets). */
    var aiPresets by string(KEY_AI_PRESETS, "")
    /** After dictating, tidy the text with the AI service (needs AI rewrite set up). */
    var voiceCleanup by bool(KEY_VOICE_CLEANUP, false)
    /** The user's own free Gemini API key. Stored only on this device and never backed up. */
    var geminiKey by string(KEY_GEMINI_KEY, "")
    var geminiModel by string(KEY_GEMINI_MODEL, "gemini-flash-latest")
    /** Which AI service rewrites text: 0 Gemini, 1 Groq (see AiClient). */
    var aiProvider by int(KEY_AI_PROVIDER, 1)
    /** The user's own free Groq API key. Stored only on this device and never backed up. */
    var groqKey by string(KEY_GROQ_KEY, "")
    var groqModel by string(KEY_GROQ_MODEL, "openai/gpt-oss-20b")
    /** Actions shown in the top row, in order (see ToolbarItems). */
    var toolbarItems by string(KEY_TOOLBAR_ITEMS, "")
    /** Animation while typing: 0 off, 1 sparkles, 2 ripple, 3 warp speed (see TypingEffects). */
    var typingEffect by int(KEY_TYPING_EFFECT, 0)
    /** How keys move when pressed: 0 sink, 1 pop, 2 bounce, 3 tilt, 4 float, 5 none (see KeyMotion). */
    var keyMotion by int(KEY_KEY_MOTION, 0)
    /** How strong the typing animation is, 0..100. */
    var effectStrength by int(KEY_EFFECT_STRENGTH, 40)
    /** Pause the glass animation and tilt effects when the battery is low or battery saver is on. */
    var batterySaver by bool(KEY_BATTERY_SAVER, true)
    /** Animation living in the glass: 0 off, 1 aurora, 2 light flow, 3 bubbles (see GlassAnimation). */
    var glassAnimation by int(KEY_GLASS_ANIMATION, 0)
    var animationSpeed by int(KEY_ANIMATION_SPEED, 40)
    /** The glass animation follows the phone's tilt. */
    var animationTilt by bool(KEY_ANIMATION_TILT, true)
    var animationIntensity by int(KEY_ANIMATION_INTENSITY, 50)
    /** Record typing timing on this phone (see Diagnostics). Off by default. */
    var diagnostics by bool(KEY_DIAGNOSTICS, false)
    /** Words autocorrect must never change, one per line (lowercase). */
    var protectedWords by string(KEY_PROTECTED_WORDS, "")
    /** The user's own fixes, one "typed<TAB>replacement" per line: typed text is always changed to the replacement. */
    var customFixes by string(KEY_CUSTOM_FIXES, "")
    /** Highlights on the glass shift slightly as the phone is tilted. */
    var shimmer by bool(KEY_SHIMMER, false)
    /** Liquid refraction around every key (Android 13+). Off by default. */
    var refraction by bool(KEY_REFRACTION, false)
    var refractionStrength by int(KEY_REFRACTION_STRENGTH, 55)
    /** One switch that turns off sensor-driven shifts, looping glass animations and typing effects. */
    var reduceMotion by bool(KEY_REDUCE_MOTION, false)
    /** Also rest the effects when Android's "Remove animations" setting is on. */
    var followSystemMotion by bool(KEY_FOLLOW_SYSTEM_MOTION, true)
    /** Colors of the key spotlight: an index into SpotlightPalettes (1 is blue, purple, pink). */
    var spotlightPalette by int(KEY_SPOTLIGHT_PALETTE, 1)
    /** The user's own spotlight colors as comma-separated hex. */
    var spotlightColors by string(KEY_SPOTLIGHT_COLORS, "")
    /** Change spotlight color on every press instead of by position across the keyboard. */
    var spotlightCycle by bool(KEY_SPOTLIGHT_CYCLE, false)
    /** How strong the tilt shimmer is, 0..100. */
    var shimmerStrength by int(KEY_SHIMMER_STRENGTH, 50)
    /** The live keyboard preview docked at the bottom of settings is expanded. */
    var previewOpen by bool(KEY_PREVIEW_OPEN, true)
    /** How wide the keyboard is: -1 auto (narrower on tablets), 0 full, 1 wide 85%, 2 medium 70%, 3 compact 55%. */
    var keyboardWidth by int(KEY_KEYBOARD_WIDTH, -1)
    /** Where a narrower keyboard sits: 0 center, 1 left, 2 right. */
    var keyboardPosition by int(KEY_KEYBOARD_POSITION, 0)
    /** Split the letter rows into two halves with a gap, for thumbs on a tablet. */
    var splitKeys by bool(KEY_SPLIT_KEYS, false)
    /** Width of the gap in a split keyboard, in percent of the keyboard width. */
    var splitGap by int(KEY_SPLIT_GAP, 18)
    /** Color a pressed key lights up in: 0 white, 1 accent, 2 gradient, 3 photo color. */
    var pressStyle by int(KEY_PRESS_STYLE, 0)
    /** The last fancy text style picked (index into FancyText.Style). */
    var fancyStyle by int(KEY_FANCY_STYLE, 0)
    /** Extra long-press characters per key, one "a:äå" line per key (see CustomKeys). */
    var customKeys by string(KEY_CUSTOM_KEYS, "")
    /** Long-press accents on letter keys, the language groups to draw them from (comma separated), and symbols/digits. */
    var accentPopups by bool(KEY_ACCENT_POPUPS, true)
    var accentSets by string(KEY_ACCENT_SETS, "western")
    var symbolPopups by bool(KEY_SYMBOL_POPUPS, true)
    var accentsFirst by bool(KEY_ACCENTS_FIRST, false)
    var cornerHints by bool(KEY_CORNER_HINTS, true)
    /** The user's saved looks (see Looks.parse). */
    var myLooks by string(KEY_MY_LOOKS, "")
    /** Keyboard height when the phone is sideways: 0 auto, 1 short, 2 medium, 3 normal. */
    var landscapeHeight by int(KEY_LANDSCAPE_HEIGHT, 0)
    /** The long-press menu's tint strength and how strongly it blurs what is behind it, both 0..100. */
    var popupOpacity by int(KEY_POPUP_OPACITY, 15)
    var popupBlur by int(KEY_POPUP_BLUR, 60)

    fun popupSettings() = com.flexkeys.layout.PopupSettings(
        accents = accentPopups,
        accentSets = accentSets.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        symbols = symbolPopups,
        accentsFirst = accentsFirst,
        cornerHints = cornerHints,
    )
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
        const val KEY_SUGGESTION_FONT = "suggestion_font_percent"
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
        const val KEY_SPACE_STYLE = "space_style"
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
        const val KEY_GIF_FAVORITES = "gif_favorites"
        const val KEY_PUNCTUATION = "punctuation_keys"
        const val KEY_SMART_CLIPS = "smart_clips"
        const val KEY_CALCULATOR = "calculator"
        const val KEY_KEY_FEEL = "key_feel"
        const val KEY_MY_LOOKS = "my_looks"
        const val KEY_ACCENT_POPUPS = "accent_popups"
        const val KEY_ACCENT_SETS = "accent_sets"
        const val KEY_SYMBOL_POPUPS = "symbol_popups"
        const val KEY_ACCENTS_FIRST = "accents_first"
        const val KEY_CORNER_HINTS = "corner_hints"
        const val KEY_LANDSCAPE_HEIGHT = "landscape_height"
        const val KEY_POPUP_OPACITY = "popup_opacity"
        const val KEY_POPUP_BLUR = "popup_blur"
        /** Settings that change what the letter keys offer on long-press. */
        val POPUP_KEYS: Set<String> = setOf(KEY_ACCENT_POPUPS, KEY_ACCENT_SETS, KEY_SYMBOL_POPUPS, KEY_ACCENTS_FIRST, KEY_CORNER_HINTS)
        const val KEY_CUSTOM_KEYS = "custom_keys"
        const val KEY_FANCY_STYLE = "fancy_style"
        const val KEY_KEYBOARD_WIDTH = "keyboard_width"
        const val KEY_KEYBOARD_POSITION = "keyboard_position"
        const val KEY_SPLIT_KEYS = "split_keys"
        const val KEY_SPLIT_GAP = "split_gap"
        const val KEY_PRESS_STYLE = "press_style"
        const val KEY_PREVIEW_OPEN = "settings_preview_open"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_PROTECTED_WORDS = "protected_words"
        const val KEY_CUSTOM_FIXES = "custom_fixes"
        const val KEY_BATTERY_SAVER = "battery_saver"
        const val KEY_GLASS_ANIMATION = "glass_animation"
        const val KEY_ANIMATION_SPEED = "animation_speed"
        const val KEY_ANIMATION_TILT = "animation_tilt"
        const val KEY_ANIMATION_INTENSITY = "animation_intensity"
        const val KEY_SPOTLIGHT_PALETTE = "spotlight_palette"
        const val KEY_SPOTLIGHT_COLORS = "spotlight_colors"
        const val KEY_SPOTLIGHT_CYCLE = "spotlight_cycle"
        const val KEY_REFRACTION = "refraction"
        const val KEY_REFRACTION_STRENGTH = "refraction_strength"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_FOLLOW_SYSTEM_MOTION = "follow_system_motion"
        const val KEY_SHIMMER = "shimmer"
        const val KEY_SHIMMER_STRENGTH = "shimmer_strength"
        const val KEY_EFFECT_STRENGTH = "effect_strength"
        const val KEY_KEY_MOTION = "key_motion"
        const val KEY_TYPING_EFFECT = "typing_effect"
        const val KEY_TOOLBAR_ITEMS = "toolbar_items"
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_AI_PRESETS = "ai_presets"
        const val KEY_VOICE_CLEANUP = "voice_cleanup"
        const val KEY_GEMINI_KEY = "gemini_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_GROQ_KEY = "groq_key"
        const val KEY_GROQ_MODEL = "groq_model"

        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val DEFAULT_ACCENT = 0xFF5B8CFF.toInt()

        /**
         * Settings that change how the keyboard looks. Changing one of these rebuilds the theme;
         * every other setting is simply read the next time it is used.
         */
        val THEME_KEYS: Set<String> = setOf(
            KEY_HEIGHT, KEY_LANDSCAPE_HEIGHT, KEY_SPACING, KEY_CORNER, KEY_FONT, KEY_SUGGESTION_FONT, KEY_KEYBOARD_WIDTH, KEY_KEYBOARD_POSITION,
            KEY_SPLIT_KEYS, KEY_SPLIT_GAP, KEY_THEME_MODE, KEY_ACCENT, KEY_BG_OPACITY,
            KEY_GLASS_OPACITY, KEY_BLUR, KEY_GRADIENT, KEY_STYLE, KEY_FONT_FAMILY, KEY_SPACE_EMBLEM,
            KEY_SPACE_GLASS, KEY_SPACE_STYLE, KEY_EMBLEM_SIZE, KEY_PHOTO_ZOOM, KEY_PHOTO_PAN_X, KEY_PHOTO_PAN_Y,
            KEY_ADAPT_TINT, KEY_PHOTO_LUMA, KEY_REFRACTION, KEY_REFRACTION_STRENGTH, KEY_PRESS_STYLE, KEY_PHOTO_ACCENT,
        )

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
