# FlexKeys

A highly customizable Android keyboard with a **liquid glass** look: frosted keys over your own
background photo, real-time blur on Android 12+, and full control over size, spacing, shape and color.
Everything stays on your device. There are no analytics, and the only feature that uses the internet is
**GIF search** (off until you turn it on and add your own free Klipy API key) and the **translator**
(only the text you type or paste into it).

## Features

**Typing**
- QWERTY, QWERTZ, AZERTY and Colemak letter layouts, plus numbers/symbols, extra symbols and a numpad for number fields
- Shift, double-tap shift (or long-press) for caps lock, auto-capitalization
- Backspace with hold-to-repeat (emoji-aware), enter key that matches the app's action (search, send, go, next, done)
- Long-press popups for accents, digits and alternate symbols, key preview bubbles
- Swipe the space bar to move the cursor; long-press space to switch keyboards
- Double-space for ". "

**Suggestions**
- Word completions and next-word predictions in a glass suggestion strip
- Simple autocorrect (keyboard-proximity edit distance) applied on space or punctuation. Press backspace right after to undo it.
- Learns the words and word pairs you type, stored only on your device. Long-press a suggestion to forget a learned word.
- No learning in password fields or in apps that ask for incognito input

**Panels**
- Translator (free MyMemory service, no key): translate what you type, or paste a message someone sent you; 30 languages
- GIF search powered by [Klipy](https://klipy.com) (bring your own free API key): trending, categories, search, and sending GIFs into apps that support them
- Emoji panel with categories, recents, offline emoji search and skin tones (long-press an emoji; your choice is remembered)
- Emoji suggestions while typing ("pizza" offers 🍕)
- Clipboard history with pin, delete and clear (local only; items password managers mark as sensitive are skipped)

**Liquid glass theme**
- Keys and the suggestion bar are frosted glass panels with soft rim light, highlights and press animations
- Real background blur with `RenderEffect` on Android 12+ (API 31), translucent frosted fallback on older versions
- Custom background photo picked with the system document picker, downsampled and copied into app storage
- Sliders for photo opacity, glass opacity and blur strength with a live preview
- Auto/light/dark tint and an accent color picker
- Adjustable keyboard height, key spacing, corner radius and font size; haptics and key sound toggles
- Two key styles: bare translucent "glass letters" (default) or a glass tile behind each key
- A blue/purple/pink gradient glow across the glass, with its own slider
- Modern fonts (Inter, Manrope) or your system font
- One-tap theme presets (Frost, Midnight, Aurora, Sunset, Clear, Graphite)
- Optional Gboard-style comma and period keys beside the space bar
- Manage learned words: search, add and remove them

## Install the APK (no Android Studio needed)

Every push builds a debug APK on GitHub Actions.

1. Open the repository on GitHub and go to the **Actions** tab.
2. Click the latest successful **Build debug APK** run (green check), ideally one on `main`.
3. Scroll to **Artifacts** and download **FlexKeys-debug-apk**. GitHub delivers it as a `.zip`.
4. Unzip it to get `app-debug.apk`. You need to be signed in to GitHub to download artifacts.
5. Copy the APK to your phone (or download it there directly) and open it.
6. When Android asks, allow your browser or file manager to **install unknown apps**, then tap **Install**.
   Play Protect may warn that the app is from an unknown developer. That's expected for a self-built
   debug APK; choose to install anyway.

Updating works the same way: install the newer APK over the old one. Your settings, learned words
and clipboard history are kept. Every build is signed with the same debug key (`app/debug.keystore`),
so updates install in place.

## Set it up

Open **FlexKeys** from your app drawer. The first card walks you through it:

1. **Enable FlexKeys**: opens the system keyboard list, where you turn FlexKeys on.
   Android shows a standard warning that keyboards can see what you type. FlexKeys can't send it
   anywhere, because it has no network access.
2. **Switch to FlexKeys**: opens the keyboard picker. Choose FlexKeys.
3. **Try it**: type in the test field.

The live preview at the top of the settings screen updates as you move any slider, and you can type on it.
To pick a background photo, tap **Choose photo** under *Liquid glass*.

## Build it yourself

The project uses plain Gradle with no wrapper, and no libraries beyond the Android framework and the Kotlin standard library.

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.10.2 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |

```sh
gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

This needs an Android SDK with platform 34 (`ANDROID_HOME` or `local.properties` `sdk.dir`). The
CI workflow in `.github/workflows/build.yml` does exactly this on `ubuntu-latest`, using
`gradle/actions/setup-gradle` with an explicit Gradle version.

## Project structure

```
app/src/main/java/com/flexkeys/
├── service/    FlexKeysService (InputMethodService): composing text, autocorrect, enter actions; Feedback (haptics/sound)
├── keyboard/   KeyboardView (custom drawn, touch, popups, repeat), KeyboardState (layers/shift), Icons
├── layout/     Layout data classes and Layouts (all layouts defined as data)
├── theme/      Prefs, GlassTheme, GlassPainter, GlassBackdrop (blur), BoxBlur, BackgroundImageStore
├── suggest/    WordDictionary, UserDictionary, SuggestionEngine, SuggestionStripView
├── emoji/      EmojiData, EmojiIndex (search, skin tones), EmojiPanel, EmojiSearchBar
├── clipboard/  ClipboardStore, ClipboardPanel
├── gif/        KlipyClient, GifLoader, GifProvider, GifPanel
├── translate/  Languages, TextChunker, MyMemoryClient, TranslateBar
├── ui/         KeyboardRootView (shared backdrop, panels), GlassHost, GlassButton, GlassPanelLayout
└── settings/   SettingsActivity, PreviewController, ColorPickerView
app/src/main/assets/words.txt    English word list with frequencies
app/src/main/assets/emoji.tsv    emoji keywords for search and suggestions
app/src/main/assets/typos.tsv    common misspellings and their fixes
tools/make_words.py              regenerates words.txt
tools/make_emoji.py              regenerates emoji.tsv
tools/make_typos.py              regenerates typos.tsv
```

### Adding a layout

Letter layouts are data. Add one line to `Layouts.letterLayouts`:

```kotlin
letters("workman", "Workman", "qdrwbjfup'", "ashtgyneoi", "zxmcvkl"),
```

Accent popups, digit hints on the top row, shift/backspace widths and the bottom row are filled in
automatically. Each row is laid out on a 10-unit grid, so shorter rows are centered. For a fully custom
layout, build a `LayoutDef` from `RowDef`s and `KeyDef`s (see `Layouts.symbols` or `Layouts.numpad`).

## Privacy

- Permissions: `VIBRATE`, and `INTERNET`, which is used only for GIF search (off by default). Without a Klipy key
  and the GIF switch turned on, FlexKeys never makes a network request.
- When you use the translator, the text you type or paste into it, the language pair and (optionally) your email
  go to `api.mymemory.translated.net`. Your normal typing is never sent.
- When you use GIFs, requests go to `api.klipy.com` (search words, your key and a random on-device id) and
  the GIF files are downloaded from Klipy. Nothing you type anywhere else is sent.
- No analytics, crash reporting or third-party libraries.
- Learned words (`files/user_words.tsv`), clipboard history (`files/clipboard.json`) and the background
  photo (`files/background.jpg`) live in app-internal storage. Backups are disabled.
- Clear learned words and clipboard history from the settings screen at any time.

## License

FlexKeys is licensed under the GNU GPL v3 (see `LICENSE`).
The bundled word list is derived from [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(OpenSubtitles 2018) by Hermit Dave, licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). It was filtered and extended with
contractions by `tools/make_words.py`.
The misspelling list (`typos.tsv`) is filtered from the [codespell](https://github.com/codespell-project/codespell)
dictionary, a derivative of English Wikipedia's lists of common misspellings, licensed
[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/), by `tools/make_typos.py`.
The bundled fonts [Inter](https://github.com/rsms/inter) and [Manrope](https://github.com/googlefonts/manrope)
are licensed under the SIL Open Font License 1.1 (see `app/src/main/assets/fonts`).
Emoji keywords and skin tone data come from [gemoji](https://github.com/github/gemoji)
(MIT License, Copyright (c) 2019 GitHub, Inc.), converted by `tools/make_emoji.py`.
