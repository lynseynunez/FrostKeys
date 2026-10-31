# SakuriKey

A highly customizable Android keyboard with a **liquid glass** look: frosted keys over your own
background photo, real-time blur on Android 12+, and full control over size, spacing, shape and color.
Everything stays on your device. There are no analytics. The only features that use the internet are
**GIF search** (off until you turn it on and add your own free Klipy API key), the **translator**
(only the text you type or paste into it) and **AI rewrite** (off until you turn it on and add your own free
Groq or Gemini key; only the text you choose to rewrite).

## Features

**Typing**
- QWERTY, QWERTZ, AZERTY and Colemak letter layouts, plus numbers/symbols, extra symbols and a numpad for number fields
- Shift, double-tap shift (or long-press) for caps lock, auto-capitalization
- Backspace with hold-to-repeat (emoji-aware), enter key that matches the app's action (search, send, go, next, done)
- Long-press popups for accents, digits and alternate symbols, key preview bubbles
- Swipe the space bar to move the cursor, or rest your finger on it for a moment and then drag to select text; long-press space to switch keyboards
- Double-space for ". "
- Swipe left on backspace to select word by word and delete on release; swipe down on a letter to type the symbol in its corner
- Voice typing: the mic button uses your phone's own speech recognizer (no microphone permission needed)
- Undo an autocorrection with backspace and an "Add “word”" chip appears: tap it and the word is never corrected again (it is also added to Settings > Typing > Autocorrect list). Tapping the quoted typed word does the same.
- Sakura petals: a glass animation (petals drift, sway and tumble; tilt blows them, typing hard stirs a gust) and a typing effect (petals fly from each key), and the Sakura look uses both
- Sakura + spotlight: the petal effect with the key spotlight's colored glow, and the petals take the spotlight colors you picked
- Keyboard feel presets (Soft, Clicky, Mechanical, Silky): each kind of key gets its own vibration pattern, no sounds
- Smart paste (a copied email, phone number or link appears in the suggestion bar for a few minutes) and calculator and unit chips (type 12*8 or 5 km and tap the answer), both switchable in Typing
- Phrases can include {date}, {time}, {day}, {month}, {year} and {clipboard}
- Phrases: save text you type often, optionally with a shortcut ("omw" becomes "On my way!") that expands on space, and insert phrases from a panel
- Add your own characters to any key's long-press popup

**Suggestions**
- Word completions and next-word predictions in a glass suggestion strip
- Simple autocorrect (keyboard-proximity edit distance) applied on space or punctuation. Press backspace right after to undo it.
- Your own autocorrect list (Settings > Typing): words to leave alone (like gpu) and fixes that always apply (gpu becomes GPU)
- Knows which words usually follow each other (about 80,000 common pairs), so "away form" becomes "away from" and next-word suggestions are better
- Learns the words and word pairs you type, stored only on your device. Long-press a suggestion to forget a learned word.
- No learning in password fields or in apps that ask for incognito input

**Panels**
- Translator (free MyMemory service, no key): translate what you type, or paste a message someone sent you; 30 languages
- GIF search powered by [Klipy](https://klipy.com) (bring your own free API key): trending, categories, search, and sending GIFs into apps that support them
- Emoji panel with categories, recents, offline emoji search and skin tones (long-press an emoji; your choice is remembered)
- Emoji suggestions while typing ("pizza" offers 🍕)
- Clipboard history with Recent and Pinned tabs, pin, delete and clear, and "pin the text in the field" for things you retype (local only; items password managers mark as sensitive are skipped), optional auto-delete after an hour, day or week
- Verification codes you copy from a message show above the keys for one-tap paste and are forgotten after 10 minutes
- Tap the four dots at the top left for a tools menu; use Edit there to choose which tools show in the top row, and hold and drag to reorder them
- Your own stickers: add images from the gallery and send them as images (apps that can't take images get a copy to paste)
- Kaomoji and symbols tabs in the emoji panel
- Text tools: UPPERCASE, Title Case, Sentence case, tidy spaces, bullets, number lines, sort and more, on the selection or the whole field (nothing leaves the phone)
- Fancy text: 16 styles (bold, italic, script, gothic, circled, small caps, strikethrough...) with a live preview
- AI reply helper, your own rewrite styles and dictation cleanup (all through the AI service you set up)
- AI rewrite (optional, bring your own free Groq or Gemini key): fix, friendlier, professional, shorter, longer, casual, add emoji, rephrase, or your own instruction

**Liquid glass theme**
- Keys and the suggestion bar are frosted glass panels with soft rim light, highlights and press animations
- Real background blur with `RenderEffect` on Android 12+ (API 31), translucent frosted fallback on older versions
- Custom background photo picked with the system document picker, downsampled and copied into app storage; drag and pinch to choose the part that shows, match the accent color to the photo, and let the light/dark tint follow it
- Sliders for photo opacity, glass opacity and blur strength with a live preview
- Auto/light/dark tint and an accent color picker
- Adjustable keyboard height, key spacing, corner radius and font size; haptic style and strength (or the phone's built-in tick, click and heavy effects)
- Backup and restore (settings, learned words, phrases and optionally your photo) and shareable theme codes; API keys are never included
- Check for updates from the app (only when you tap it): finds the newest test build and installs it right there (Android asks you to confirm), or points to a new numbered release
- Two key styles: bare translucent "glass letters" (default) or a glass tile behind each key
- A blue/purple/pink gradient glow across the glass, with its own slider
- Modern fonts (Inter, Manrope) or your system font
- Appearance tool: drag sliders for glass, photo, blur and glow right above the keys and watch the keyboard change live
- Key press color: pressed keys glow white, in your accent, along the blue-purple-pink gradient, or in your photo's color
- Hold the clipboard button to paste your latest copy without opening the panel
- Optional animated glass (off by default, with speed and intensity sliders): aurora (drifting color), light flow, rising bubbles, starlight, rain, fireflies or waves (the water slides with your tilt)
- Reduce motion: one switch (that also follows Android's "Remove animations") turns off tilt effects, glass animation and typing effects
- Optional liquid refraction on Android 13+ (beta): the blurred background bends around every key like light through thick glass
- Optional tilt shimmer: a soft highlight slides across the glass and the lettering drifts a hair as you tilt your phone (off by default, uses the motion sensor only while the keyboard is showing)
- Optional typing animations (off by default, with a strength slider): key spotlight (a soft light that follows your typing, in colors you choose: blue-purple-pink, sunset, ocean, candy, aurora, rainbow or your own), sparkles, ripple, warp-speed streaks when you type fast, glow bloom, ink splash, firecracker and paint splatter
- Key motion: sink, pop, bounce, tilt or float up when a key is pressed
- One-tap theme presets (Frost, Midnight, Aurora, Sunset, Clear, Graphite)
- Optional Gboard-style comma and period keys beside the space bar
- Manage learned words: search, add and remove them

## Tests and diagnostics

Every push runs the unit tests (`gradle testDebugUnitTest`) before building: autocorrect on real typos and on words
that must never change, a speed budget for suggestions, backups, layouts and other logic. In the app,
**Settings > Diagnostics** can record typing timing on the phone and show a report against a performance budget.

## Install the APK (no Android Studio needed)

Every push builds a debug APK on GitHub Actions.

1. Open the repository on GitHub and go to the **Actions** tab.
2. Click the latest successful **Build debug APK** run (green check), ideally one on `main`.
3. Scroll to **Artifacts** and download **SakuriKey-debug-apk**. GitHub delivers it as a `.zip`.
4. Unzip it to get `app-debug.apk`. You need to be signed in to GitHub to download artifacts.
5. Copy the APK to your phone (or download it there directly) and open it.
6. When Android asks, allow your browser or file manager to **install unknown apps**, then tap **Install**.
   Play Protect may warn that the app is from an unknown developer. That's expected for a self-built
   debug APK; choose to install anyway.

Updating works the same way: install the newer APK over the old one. Your settings, learned words
and clipboard history are kept. Every build is signed with the same debug key (`app/debug.keystore`),
so updates install in place.

## Releases and updates

The **Release** workflow publishes an APK as a GitHub Release, which the app's **Check for updates** button
looks at. Start it from the **Actions** tab (Release > Run workflow, then type a version such as `1.0.0`),
or push a tag: `git tag v1.0.0 && git push origin v1.0.0`, or change the version in `release-version.txt` and push.

Out of the box the release APK is signed with the same debug key as the CI builds, so it installs over them and
keeps your settings. That is fine for personal use.

### Using your own private signing key (optional)

For a release signed with a key only you hold:

1. On your computer run `tools/make_release_key.sh`. It creates the key outside the repository (back it up, and
   never commit it) and prints four values.
2. In the GitHub repository open **Settings → Secrets and variables → Actions** and add them as secrets:
   `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
3. Publish a release as above. The workflow now signs with your key.

Each release's notes come from `release-notes/<version>.md` if you wrote one, otherwise they are written
automatically from the commit titles since the last release. The app's **Check for updates** shows them.

Heads up: a release signed with your own key differs from the debug builds, so Android will not install it over
one. Export a backup first (Settings → Backup & sharing), uninstall the debug build, install the release, then
import the backup. After that, every later release updates in place.

## Set it up

Open **SakuriKey** from your app drawer. The first card walks you through it:

1. **Enable SakuriKey**: opens the system keyboard list, where you turn SakuriKey on.
   Android shows a standard warning that keyboards can see what you type. SakuriKey can't send it
   anywhere, because it has no network access.
2. **Switch to SakuriKey**: opens the keyboard picker. Choose SakuriKey.
3. **Try it**: type in the test field.

Settings opens on a short list of topics (Colors & photo, Effects & animation, Keyboard layout, Typing, and more); tap one to open its page, and use the back arrow or Back to return. Only the switch itself toggles a setting, and sliders ignore vertical scrolling, so you can scroll without changing anything by accident.

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
├── phrases/    SnippetStore, PhrasesPanel, CustomKeys
├── stickers/   StickerStore, StickerPanel
├── fancy/      FancyText (Unicode styles), FancyBar
├── ai/         GeminiClient, AiBar
├── voice/      VoiceInputActivity, VoiceBridge
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

- Permissions: `VIBRATE`, and `INTERNET`, which is used only for GIF search, the translator, AI rewrite and the
  update check (GIFs and AI rewrite are off by default, and the update check runs only when you tap it).
  Without them turned on, SakuriKey never makes a network request.
- When you use the translator, the text you type or paste into it, the language pair and (optionally) your email
  go to `api.mymemory.translated.net`. Your normal typing is never sent.
- When you use GIFs, requests go to `api.klipy.com` (search words, your key and a random on-device id) and
  the GIF files are downloaded from Klipy. Nothing you type anywhere else is sent.
- When you use AI rewrite, the text you choose to rewrite and the style instruction go to
  `api.groq.com` or `generativelanguage.googleapis.com` (your choice) with your own key. It is never used in password fields, and the key is
  never included in backups.
- Voice typing hands audio to your phone's own speech recognizer app; SakuriKey itself never records audio.
- No analytics, crash reporting or third-party libraries.
- Learned words (`files/user_words.tsv`), clipboard history (`files/clipboard.json`), phrases (`files/snippets.json`)
  and the background photo (`files/background.jpg`) live in app-internal storage. Android's own backup is disabled;
  use Settings > Backup & sharing to make your own.
- Clear learned words and clipboard history from the settings screen at any time.

## License

SakuriKey is licensed under the GNU GPL v3 (see `LICENSE`).
The bundled word list is derived from [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(OpenSubtitles 2018) by Hermit Dave, licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). It was filtered and extended with
contractions by `tools/make_words.py`.
The misspelling list (`typos.tsv`) is filtered from the [codespell](https://github.com/codespell-project/codespell)
dictionary, a derivative of English Wikipedia's lists of common misspellings, licensed
[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/), by `tools/make_typos.py`.
The word-pair list (`bigrams.txt`) is filtered from SymSpell's English bigram dictionary (MIT License), which is
derived from Google Books Ngram data ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)), by `tools/make_bigrams.py`.
The bundled fonts [Inter](https://github.com/rsms/inter) and [Manrope](https://github.com/googlefonts/manrope)
are licensed under the SIL Open Font License 1.1 (see `app/src/main/assets/fonts`).
Emoji keywords and skin tone data come from [gemoji](https://github.com/github/gemoji)
(MIT License, Copyright (c) 2019 GitHub, Inc.), converted by `tools/make_emoji.py`.
