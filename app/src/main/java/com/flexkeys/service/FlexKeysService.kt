package com.flexkeys.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.flexkeys.ai.AiClient
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.diagnostics.Diagnostics
import com.flexkeys.emoji.EmojiIndex
import com.flexkeys.gif.GifItem
import com.flexkeys.gif.GifLoader
import com.flexkeys.gif.GifProvider
import com.flexkeys.keyboard.EnterAction
import com.flexkeys.keyboard.KeyboardState
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.layout.Layer
import com.flexkeys.phrases.CustomKeys
import com.flexkeys.phrases.SnippetVariables
import com.flexkeys.phrases.SnippetStore
import com.flexkeys.settings.PhrasesActivity
import com.flexkeys.settings.SettingsActivity
import com.flexkeys.settings.StickersActivity
import com.flexkeys.suggest.CalcHit
import com.flexkeys.suggest.SmartClip
import com.flexkeys.suggest.SmartText
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.suggest.SuggestionStripView
import com.flexkeys.suggest.Suggestions
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView
import com.flexkeys.ui.ToolSource
import com.flexkeys.voice.VoiceBridge
import com.flexkeys.voice.VoiceInputActivity
import java.io.File

/**
 * The input method. Owns the views and turns key events into edits on the text field.
 *
 * The word being typed is kept as composing text so suggestions and autocorrect can replace
 * it; everything else is committed directly.
 */
class FlexKeysService : InputMethodService(), KeyboardView.Listener, SuggestionStripView.Listener,
    KeyboardRootView.PanelListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var feedback: Feedback
    private lateinit var engine: SuggestionEngine
    private val state = KeyboardState()
    private var root: KeyboardRootView? = null

    // Per-field behavior.
    private var predictionOn = false
    private var correctionOn = false
    private var learningOn = false

    private var passwordField = false
    private val snippets by lazy { SnippetStore.get(this) }
    private val composing = StringBuilder()
    private var selectionEnd = -1
    private var swipeAnchor = -1
    private var swipeText = ""
    private var suggestions = Suggestions.EMPTY
    private var lastSpaceTime = 0L
    /** The last autocorrection, so an immediate backspace can undo it. */
    private var lastCorrection: Correction? = null
    /** A suggestion was just committed together with an automatic space. */
    private var pickedSuggestion = false

    private data class Correction(val original: String, val corrected: String, var separator: String = "")

    private lateinit var clipboard: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        Diagnostics.installCrashHandler(this)
        java.io.File(filesDir, "background_subject.png").delete() // left over from a removed feature
        feedback = Feedback(this, prefs)
        engine = SuggestionEngine.get(this)
        EmojiIndex.load(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        engine.user.save()
        root?.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        root?.release()
        val view = KeyboardRootView(this, GlassTheme.from(this, prefs))
        view.keyboardView.listener = this
        view.strip.listener = this
        view.panelListener = this
        view.extendBehindNavigationBar = true
        root = view
        refreshKeyboard()
        view.reloadPhoto()
        drawBehindNavigationBar()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Turning the phone changes the keyboard's height and width, so rebuild the look for the new orientation. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        root?.let {
            it.applyTheme(GlassTheme.from(this, prefs))
            it.requestLayout()
        }
    }

    /**
     * Lets the keyboard's glass continue under the navigation bar, as Gboard does. The root view
     * pads itself by the navigation bar height so keys stay above the system buttons.
     */
    private fun drawBehindNavigationBar() {
        val w = window?.window ?: return
        w.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        // If the system leaves a sliver below our view (seen after a restart), let the window's own
        // background match the glass instead of showing the app underneath.
        // Only a strip along the bottom is painted, never the area around the sheet's rounded top corners.
        root?.let { w.setBackgroundDrawable(BottomStripDrawable(it.theme.baseBottom, it.theme.dp(48f))) }
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root?.requestApplyInsets()
    }

    /** The system can change the window setup between showings (for example after a restart), so set it up each time. */
    override fun onWindowShown() {
        super.onWindowShown()
        drawBehindNavigationBar()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) state.reset()
        resetComposingState()

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        val password = cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS ||
            cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        passwordField = password
        val literal = cls == InputType.TYPE_CLASS_TEXT && variation in LITERAL_VARIATIONS
        val noSuggestions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 && !prefs.ignoreNoSuggestions
        predictionOn = prefs.suggestions && cls == InputType.TYPE_CLASS_TEXT && !password && !noSuggestions
        correctionOn = predictionOn && prefs.autocorrect && !literal
        learningOn = predictionOn && prefs.learnWords && !literal &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

        Diagnostics.noteField(
            "type class $cls, variation $variation, flags ${info.inputType and InputType.TYPE_MASK_FLAGS}" +
                (if ((info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) " (app asks for no suggestions)" else "") +
                (if (password) " (password)" else "") + (if (literal) " (literal: email, web address or similar)" else "") +
                "\nsuggestions ${if (predictionOn) "on" else "off"}, autocorrect ${if (correctionOn) "on" else "off"}" +
                " (settings: suggestions ${if (prefs.suggestions) "on" else "off"}, autocorrect ${if (prefs.autocorrect) "on" else "off"})" +
                "\ndictionary: ${engine.dictionary.size} words loaded",
        )
        state.setLayer(if (numeric) Layer.NUMPAD else Layer.LETTERS)
        root?.cancelEmojiSearch()
        root?.showPanel(KeyboardRootView.Panel.NONE)
        refreshKeyboard()
        updateShiftState()
        updateSuggestions()
        VoiceBridge.take()?.let { insertSpoken(it) }
    }

    /** Types what the speech recognizer heard, with a space in front when it follows other text. */
    private fun insertSpoken(spoken: String) {
        if (prefs.voiceCleanup && prefs.aiEnabled && AiClient.hasKey(prefs) && !passwordField) {
            Toast.makeText(this, "Cleaning up…", Toast.LENGTH_SHORT).show()
            // If the AI service fails, the plain dictation is typed instead.
            AiClient.rewrite(prefs, VOICE_CLEANUP_INSTRUCTION, spoken) { cleaned, _ -> commitSpoken(cleaned ?: spoken) }
        } else {
            commitSpoken(spoken)
        }
    }

    private fun commitSpoken(spoken: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        ic.commitText(lead + spoken, 1)
        lastCorrection = null
        updateShiftState()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        root?.keyboardView?.cancelAll()
        if (composing.isNotEmpty()) currentInputConnection?.finishComposingText()
        resetComposingState()
        engine.user.save()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        resetComposingState()
        super.onFinishInput()
    }

    private fun resetComposingState() {
        composing.clear()
        lastCorrection = null
        pickedSuggestion = false
        lastSpaceTime = 0L
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionEnd = newSelEnd
        // The cursor left the word being composed (e.g. the user tapped elsewhere in the text).
        if (composing.isNotEmpty() && candidatesStart >= 0 &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            currentInputConnection?.finishComposingText()
            composing.clear()
            updateSuggestions()
        }
        updateShiftState()
    }

    // ---- keyboard state -> view ----------------------------------------------------------------

    private fun refreshKeyboard() {
        val kv = root?.keyboardView ?: return
        state.lettersId = prefs.layoutId
        state.numberRow = prefs.numberRow
        state.punctuationKeys = prefs.punctuationKeys
        state.customKeys = CustomKeys.parse(prefs.customKeys)
        state.popupSettings = prefs.popupSettings()
        kv.layer = state.layer
        kv.layout = state.layout
        kv.shiftState = state.shift
        kv.enterAction = enterActionFor(currentInputEditorInfo)
        kv.showKeyPreview = prefs.keyPreview
    }

    private fun updateShiftState() {
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val caps = prefs.autoCapitalize && info != null && ic != null && info.inputType != 0 &&
            ic.getCursorCapsMode(info.inputType) != 0
        state.applyAutoCaps(caps)
        root?.keyboardView?.shiftState = state.shift
    }

    private fun enterActionFor(info: EditorInfo?): EnterAction {
        if (info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return EnterAction.NEWLINE
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            else -> EnterAction.NEWLINE
        }
    }

    // ---- suggestions -------------------------------------------------------------------------------

    private class WordContext(
        val previous: String?,
        val sentenceStart: Boolean,
        val afterSpace: Boolean,
        /** The last few words typed, even across punctuation, for emoji suggestions about the whole sentence. */
        val recent: List<String> = emptyList(),
    )

    /** Looks at the text before the composing word to find the previous word. */
    private fun readContext(ic: InputConnection): WordContext {
        val raw = ic.getTextBeforeCursor(80, 0)?.toString() ?: return WordContext(null, true, false)
        val text = raw.dropLast(composing.length.coerceAtMost(raw.length))
        val trimmed = text.trimEnd()
        val afterSpace = text.isNotEmpty() && text.last().isWhitespace()
        if (trimmed.isEmpty()) return WordContext(null, true, false)
        val sentenceStart = trimmed.last() in ".!?" || text.substring(trimmed.length).contains('\n')
        val previous = if (sentenceStart) null else WORD_AT_END.find(trimmed)?.value
        val recent = RECENT_WORDS.findAll(trimmed.takeLast(48)).map { it.value.lowercase() }.toList().takeLast(3)
        return WordContext(previous, sentenceStart, afterSpace, recent)
    }

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val suggestionRunnable = Runnable { updateSuggestionsNow() }
    private var calcHit: CalcHit? = null
    /** The word just restored by undoing an autocorrection, offered in the strip so it can be added for good. */
    private var learnOffer: String? = null

    /**
     * Looks up suggestions a moment after the last key, not on every key, so fast typing isn't slowed
     * down by the dictionary work and the extra calls into the text field.
     */
    private fun updateSuggestions() {
        uiHandler.removeCallbacks(suggestionRunnable)
        uiHandler.postDelayed(suggestionRunnable, SUGGESTION_DELAY_MS)
    }

    private fun updateSuggestionsNow() {
        val r = root ?: return
        r.refreshTools()
        val strip = r.strip
        val ic = currentInputConnection
        suggestions = if (ic == null || r.panel != KeyboardRootView.Panel.NONE) {
            Suggestions.EMPTY
        } else if (!predictionOn) {
            withOtp(Suggestions.EMPTY)
        } else {
            val ctx = readContext(ic)
            // Gboard-style emoji suggestions for the word being typed or just typed.
            when {
                composing.isNotEmpty() -> engine.suggest(composing.toString(), ctx.previous, correctionOn, ctx.sentenceStart).let { s ->
                    EmojiIndex.forWord(composing.toString()).firstOrNull()?.let { s.withEmoji(EmojiIndex.displayFor(prefs, it)) } ?: s
                }
                ctx.afterSpace -> engine.predict(ctx.previous, ctx.sentenceStart).let { s ->
                    ctx.recent.asReversed().firstNotNullOfOrNull { EmojiIndex.forWord(it).firstOrNull() }?.let { s.withEmoji(EmojiIndex.displayFor(prefs, it)) } ?: s
                }
                else -> Suggestions.EMPTY
            }.let { withOtp(it) }
        }
        suggestions = withLearn(withCalc(suggestions))
        strip.suggestions = suggestions
    }

    /**
     * A fresh verification code (never in password fields), or a copied email, phone number or link when you are
     * at the start of a field or after a space, offers itself as a one-tap paste.
     */
    private fun withOtp(s: Suggestions): Suggestions {
        if (composing.isNotEmpty() || passwordField || !prefs.clipboardHistory) return s
        val (_, found) = ClipboardStore.get(this).freshSmart() ?: return s
        if (found.kind != SmartClip.Kind.CODE) {
            if (!prefs.smartClips) return s
            val last = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (last.isNotEmpty() && !last.last().isWhitespace()) return s
        }
        return s.withCode(found.value)
    }

    /** After undoing an autocorrection, an "Add “word”" chip. */
    private fun withLearn(s: Suggestions): Suggestions {
        val word = learnOffer ?: return s
        if (composing.isNotEmpty()) return s
        return s.withLearn(word)
    }

    /** The answer to a sum or unit just typed, as a chip in the middle of the strip. */
    private fun withCalc(s: Suggestions): Suggestions {
        calcHit = null
        if (!prefs.calculator || composing.isNotEmpty() || passwordField || root?.panel != KeyboardRootView.Panel.NONE) return s
        val before = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: return s
        val hit = SmartText.findCalc(before) ?: return s
        calcHit = hit
        return s.withCalc(hit.label)
    }

    /** Adds [word] to what the keyboard knows, and to the autocorrect "leave alone" list in Settings. */
    private fun addWordForGood(word: String) {
        prefs.protectedWords = engine.addToVocabulary(word, prefs.protectedWords)
    }

    override fun onSuggestionPicked(index: Int, text: String) {
        val ic = currentInputConnection ?: return
        feedback.keyPress(KeyType.CHAR)
        if (text.isEmpty()) return
        if (index == suggestions.learnIndex) {
            learnOffer?.let { addWordForGood(it) }
            learnOffer = null
            updateSuggestions()
            return
        }
        if (index == suggestions.calcIndex) {
            val hit = calcHit ?: return
            commitPending(ic)
            if (hit.replaceLength > 0) ic.deleteSurroundingText(hit.replaceLength, 0)
            ic.commitText(hit.insert, 1)
            updateShiftState()
            updateSuggestions()
            return
        }
        if (index == suggestions.clipIndex) {
            // Paste it, then stop offering it (a code is forgotten altogether).
            commitPending(ic)
            ic.commitText(text, 1)
            val store = ClipboardStore.get(this)
            store.freshSmart()?.let { (item, found) ->
                if (found.kind == SmartClip.Kind.CODE) store.delete(item.id) else store.dismissOffer(item.id)
            }
            updateShiftState()
            updateSuggestions()
            return
        }
        if (index == suggestions.emojiIndex) {
            // Keep the word and add the emoji after it: "pizza" -> "pizza 🍕 ".
            ic.beginBatchEdit()
            val hadWord = composing.isNotEmpty() && composingIntact(ic)
            if (hadWord) commitComposing(ic, autocorrect = false) else composing.clear()
            ic.commitText(if (hadWord) " $text " else "$text ", 1)
            ic.endBatchEdit()
            EmojiIndex.addRecent(prefs, text)
            lastCorrection = null
            pickedSuggestion = false
            updateShiftState()
            updateSuggestions()
            return
        }
        val previous = readContext(ic).previous
        ic.beginBatchEdit()
        ic.commitText(text, 1) // Replaces the composing word, if any.
        ic.commitText(" ", 1)
        ic.endBatchEdit()
        // Choosing the literal typed word over a correction adds it for good (even with learning off: you asked for it).
        val chosenTyped = index == suggestions.typedIndex && suggestions.correction != null
        if (chosenTyped) addWordForGood(text) else if (learningOn) engine.learn(text, previous)
        composing.clear()
        lastCorrection = null
        pickedSuggestion = true
        lastSpaceTime = 0L
        state.onCharacterTyped()
        updateShiftState()
        updateSuggestions()
    }

    override fun onSuggestionLongPressed(text: String) {
        if (engine.user.count(text) > 0) {
            engine.user.forget(text)
            Toast.makeText(this, "Removed “$text” from learned words", Toast.LENGTH_SHORT).show()
            updateSuggestions()
        }
    }

    /** Holding the clipboard button pastes the most recent copy without opening the panel. */
    override fun onActionLongPressed(action: SuggestionStripView.Action) {
        if (action != SuggestionStripView.Action.CLIPBOARD) return
        val ic = currentInputConnection ?: return
        feedback.longPress()
        val latest = ClipboardStore.get(this).newest()
        if (latest == null) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        commitPending(ic)
        ic.commitText(latest.text, 1)
        updateShiftState()
        updateSuggestions()
    }

    override fun onMenuTapped() {
        feedback.keyPress(KeyType.CHAR)
        togglePanel(KeyboardRootView.Panel.MENU)
    }

    override fun onMenuAction(action: SuggestionStripView.Action) {
        root?.showPanel(KeyboardRootView.Panel.NONE)
        onAction(action)
    }

    override fun onAction(action: SuggestionStripView.Action) {
        feedback.keyPress(KeyType.CHAR)
        when (action) {
            SuggestionStripView.Action.SETTINGS -> openSettings()
            SuggestionStripView.Action.HIDE -> requestHideSelf(0)
            SuggestionStripView.Action.CLIPBOARD -> togglePanel(KeyboardRootView.Panel.CLIPBOARD)
            SuggestionStripView.Action.EMOJI -> togglePanel(KeyboardRootView.Panel.EMOJI)
            SuggestionStripView.Action.GIF -> togglePanel(KeyboardRootView.Panel.GIF)
            SuggestionStripView.Action.PHRASES -> togglePanel(KeyboardRootView.Panel.PHRASES)
            SuggestionStripView.Action.STICKERS -> togglePanel(KeyboardRootView.Panel.STICKERS)
            SuggestionStripView.Action.AI -> when {
                !prefs.aiEnabled || !AiClient.hasKey(prefs) ->
                    Toast.makeText(this, "Turn on AI rewrite and add your API key in SakuriKey settings", Toast.LENGTH_LONG).show()
                passwordField -> Toast.makeText(this, "AI rewrite is off in password fields", Toast.LENGTH_SHORT).show()
                else -> {
                    root?.startAi(toolSource(wholeField = true))
                    updateShiftState()
                }
            }
            SuggestionStripView.Action.TEXT_TOOLS -> {
                if (passwordField) {
                    Toast.makeText(this, "Text tools are off in password fields", Toast.LENGTH_SHORT).show()
                } else {
                    root?.startTextTools(toolSource(wholeField = true))
                    updateShiftState()
                }
            }
            SuggestionStripView.Action.LOOK -> {
                root?.startLook()
                updateShiftState()
            }
            SuggestionStripView.Action.FANCY -> {
                root?.startFancy(toolSource(wholeField = false).text)
                updateShiftState()
            }
            SuggestionStripView.Action.MIC -> {
                requestHideSelf(0)
                startActivity(Intent(this, VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SuggestionStripView.Action.TRANSLATE -> {
                root?.startTranslate()
                updateShiftState()
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        requestHideSelf(0)
        startActivity(intent)
    }

    override fun onOpenSettings() = openSettings()

    override fun onEditStickers() {
        requestHideSelf(0)
        startActivity(Intent(this, StickersActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Sends the sticker as an image; if the app can't take images, copies it so it can be pasted. */
    override fun onStickerPicked(file: File) {
        feedback.keyPress(KeyType.CHAR)
        val ic = currentInputConnection ?: return
        val app = applicationContext
        val sendDir = File(app.cacheDir, GifLoader.SEND_DIR).apply { mkdirs() }
        val copy = File(sendDir, file.name)
        try {
            if (!copy.isFile) file.copyTo(copy, overwrite = true)
        } catch (e: java.io.IOException) {
            Toast.makeText(app, "Couldn't open that sticker", Toast.LENGTH_SHORT).show()
            return
        }
        commitPending(ic)
        val uri = GifProvider.uriFor(app, copy)
        val info = InputContentInfo(uri, ClipDescription("Sticker", arrayOf("image/png")))
        if (!ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)) {
            clipboard.setPrimaryClip(ClipData.newUri(app.contentResolver, "Sticker", uri))
            Toast.makeText(app, "This app can't receive stickers. Copied, so you can paste it.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onEditPhrases() {
        requestHideSelf(0)
        startActivity(Intent(this, PhrasesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Puts the finished translation into the text field. */
    override fun onTranslateInsert(text: String) {
        val ic = currentInputConnection ?: return
        commitPending(ic)
        ic.commitText(text, 1)
        updateShiftState()
        updateSuggestions()
    }

    override fun toolSource(wholeField: Boolean): ToolSource {
        val ic = currentInputConnection ?: return ToolSource("", false)
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotEmpty() || !wholeField) return ToolSource(selected, selected.isNotEmpty())
        val before = ic.getTextBeforeCursor(TOOL_TEXT_LIMIT, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(TOOL_TEXT_LIMIT, 0)?.toString().orEmpty()
        return ToolSource(before + after, false)
    }

    // Never read a password field into the pinned list.
    override fun fieldTextForPin(): String = if (passwordField) "" else toolSource(true).text

    override fun onToolInsert(text: String) = onTranslateInsert(text)

    override fun onToolReplace(text: String, wholeField: Boolean) {
        val ic = currentInputConnection ?: return
        commitPending(ic)
        if (wholeField) {
            val before = ic.getTextBeforeCursor(TOOL_TEXT_LIMIT, 0)?.length ?: 0
            val after = ic.getTextAfterCursor(TOOL_TEXT_LIMIT, 0)?.length ?: 0
            ic.deleteSurroundingText(before, after)
        }
        ic.commitText(text, 1)
        updateShiftState()
        updateSuggestions()
    }

    override fun onGifSearchRequested() {
        feedback.keyPress(KeyType.CHAR)
        root?.startGifSearch()
        updateShiftState()
    }

    /** Downloads the GIF and hands it to the app through the keyboard image API; falls back to a link. */
    override fun onGifPicked(item: GifItem) {
        feedback.keyPress(KeyType.CHAR)
        val app = applicationContext
        GifLoader.downloadForSending(app, item.sendUrl) { file ->
            val ic = currentInputConnection
            if (file == null || ic == null) {
                Toast.makeText(app, "Couldn't download that GIF", Toast.LENGTH_SHORT).show()
                return@downloadForSending
            }
            commitPending(ic)
            val info = InputContentInfo(
                GifProvider.uriFor(app, file),
                ClipDescription(item.title ?: "GIF", arrayOf("image/gif")),
                Uri.parse(item.sendUrl),
            )
            val sent = ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            if (!sent) {
                ic.commitText(item.sendUrl + " ", 1)
                Toast.makeText(app, "This app can't receive GIFs, so a link was inserted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePanel(panel: KeyboardRootView.Panel) {
        val r = root ?: return
        r.showPanel(if (r.panel == panel) KeyboardRootView.Panel.NONE else panel)
        if (r.panel == KeyboardRootView.Panel.NONE) updateSuggestions()
    }

    // ---- panels ------------------------------------------------------------------------------------

    /** Commits the word being typed as-is before inserting text from a panel. */
    private fun commitPending(ic: InputConnection) {
        if (composing.isNotEmpty()) {
            if (composingIntact(ic)) commitComposing(ic, autocorrect = false) else composing.clear()
        }
        lastCorrection = null
        pickedSuggestion = false
        lastSpaceTime = 0L
    }

    override fun onEmoji(emoji: String) {
        val ic = currentInputConnection ?: return
        feedback.keyPress(KeyType.CHAR)
        commitPending(ic)
        ic.commitText(emoji, 1)
    }

    override fun onPaste(text: String) {
        val ic = currentInputConnection ?: return
        feedback.keyPress(KeyType.CHAR)
        commitPending(ic)
        ic.commitText(text, 1)
        updateShiftState()
    }

    override fun onEmojiSearch() {
        feedback.keyPress(KeyType.CHAR)
        root?.setEmojiSearch(true)
        updateShiftState()
    }

    override fun onPanelBackspace() {
        feedback.keyPress(KeyType.BACKSPACE)
        onBackspace()
    }

    override fun onPanelClosed() {
        feedback.keyPress(KeyType.CHAR)
        root?.showPanel(KeyboardRootView.Panel.NONE)
        updateShiftState()
        updateSuggestions()
    }

    private fun captureClip() {
        if (!prefs.clipboardHistory) return
        val clip = try {
            clipboard.primaryClip
        } catch (e: SecurityException) {
            null
        } ?: return
        // Password managers mark copied secrets as sensitive; never store those.
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return
        if (clip.itemCount == 0) return
        // A sticker we copied ourselves for pasting isn't something to keep in the history.
        if (clip.description?.label == "Sticker") return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        ClipboardStore.get(this).add(text)
        updateSuggestions()
    }

    override fun onStripTouched() {
        root?.keyboardView?.cancelAll()
    }

    // ---- KeyboardView.Listener -------------------------------------------------------------------

    override fun onKeyDown(key: KeyDef) = feedback.keyPress(key.type)

    override fun onKey(key: KeyDef) {
        if (!prefs.diagnostics) {
            handleKey(key)
            return
        }
        val start = System.nanoTime()
        try {
            handleKey(key)
        } finally {
            Diagnostics.recordKey(System.nanoTime() - start)
        }
    }

    private fun handleKey(key: KeyDef) {
        // The "add this word" offer only lasts until the next key.
        learnOffer = null
        if (root?.routeKeyToSearch(key, state.isShifted) == true) {
            if (key.type == KeyType.CHAR) {
                state.onCharacterTyped()
                root?.keyboardView?.shiftState = state.shift
            }
            return
        }
        when (key.type) {
            KeyType.CHAR -> handleText(if (key.isLetter && state.isShifted) key.output.uppercase() else key.output)
            KeyType.SPACE -> onSpace()
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> {
                state.onShiftTap(SystemClock.uptimeMillis())
                root?.keyboardView?.shiftState = state.shift
            }
            KeyType.LAYER -> {
                state.setLayer(key.target ?: Layer.LETTERS)
                refreshKeyboard()
                updateShiftState()
            }
            KeyType.EMOJI -> root?.showPanel(KeyboardRootView.Panel.EMOJI)
            KeyType.SPACER -> Unit
        }
        if (key.type != KeyType.SPACE) lastSpaceTime = 0L
    }

    override fun onText(text: String) {
        if (root?.routeTextToSearch(text) == true) return
        handleText(text)
        lastSpaceTime = 0L
    }

    override fun onLongPress(key: KeyDef): Boolean {
        return when (key.type) {
            KeyType.SPACE -> {
                feedback.longPress()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                true
            }
            KeyType.SHIFT -> {
                feedback.longPress()
                state.lockCaps()
                root?.keyboardView?.shiftState = state.shift
                true
            }
            KeyType.CHAR -> {
                if (key.popup.isNotEmpty()) feedback.longPress()
                false
            }
            else -> false
        }
    }

    override fun onBackspaceSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        if (swipeAnchor < 0) {
            if (composing.isNotEmpty()) {
                ic.finishComposingText()
                composing.clear()
            }
            if (selectionEnd < 0) return
            swipeAnchor = selectionEnd
            swipeText = ic.getTextBeforeCursor(SWIPE_CONTEXT, 0)?.toString() ?: ""
        }
        val back = WordSelect.charsBack(swipeText, words)
        ic.setSelection(swipeAnchor - back, swipeAnchor)
    }

    override fun onBackspaceSwipeEnd() {
        val ic = currentInputConnection
        if (ic != null && swipeAnchor >= 0 && ic.getSelectedText(0)?.isNotEmpty() == true) {
            ic.commitText("", 1)
            lastCorrection = null
        }
        swipeAnchor = -1
        swipeText = ""
        updateSuggestions()
    }

    private var selectAnchor = -1
    private var selectHead = -1

    override fun onSelectStart() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.clear()
        }
        selectAnchor = selectionEnd
        selectHead = selectionEnd
        feedback.longPress()
    }

    override fun onCursorSelect(steps: Int) {
        val ic = currentInputConnection ?: return
        if (selectAnchor < 0) return
        selectHead = (selectHead + steps).coerceAtLeast(0)
        ic.setSelection(minOf(selectAnchor, selectHead), maxOf(selectAnchor, selectHead))
    }

    override fun onCursorMove(steps: Int) {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.clear()
        }
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
        updateSuggestions()
    }

    // ---- editing -----------------------------------------------------------------------------------

    private fun isWordChar(text: String): Boolean {
        if (text.codePointCount(0, text.length) != 1) return false
        val cp = text.codePointAt(0)
        return Character.isLetter(cp) ||
            (composing.isNotEmpty() && (cp == '\''.code || cp == '’'.code || Character.isDigit(cp)))
    }

    /** True if the editor still holds our composing word right before the cursor. */
    private fun composingIntact(ic: InputConnection): Boolean {
        if (composing.isEmpty()) return true
        return ic.getTextBeforeCursor(composing.length, 0)?.toString() == composing.toString()
    }

    private fun handleText(text: String) {
        val ic = currentInputConnection ?: return
        lastCorrection = null
        if (!composingIntact(ic)) composing.clear()
        if (predictionOn && isWordChar(text)) {
            composing.append(text)
            ic.setComposingText(composing, 1)
            pickedSuggestion = false
        } else {
            ic.beginBatchEdit()
            if (composing.isNotEmpty()) commitComposing(ic, autocorrect = correctionOn && text in CORRECTING_PUNCTUATION)
            val swapSpace = pickedSuggestion && text in ATTACHING_PUNCTUATION &&
                ic.getTextBeforeCursor(1, 0)?.toString() == " "
            if (swapSpace) {
                // "word |" + "." becomes "word. |"
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$text ", 1)
            } else {
                ic.commitText(text, 1)
            }
            ic.endBatchEdit()
            lastCorrection?.separator = text
            pickedSuggestion = false
        }
        state.onCharacterTyped()
        root?.keyboardView?.shiftState = state.shift
        updateShiftState()
        updateSuggestions()
    }

    /** Commits the composing word, autocorrecting it if asked, and learns it. */
    private fun commitComposing(ic: InputConnection, autocorrect: Boolean, expand: Boolean = false) {
        val typed = composing.toString()
        val phrase = if (expand) snippets.expansion(typed)?.let { SnippetVariables.expand(it, clipboard = latestClipText()) } else null
        if (phrase != null) {
            ic.commitText(phrase, 1)
            composing.clear()
            lastCorrection = Correction(typed, phrase)
            return
        }
        val context = if (learningOn || autocorrect) readContext(ic) else null
        val previous = context?.previous
        var final = engine.displayForm(typed)
        if (autocorrect) {
            val shownForThisWord = suggestions.typedIndex >= 0 &&
                suggestions.slots.getOrNull(suggestions.typedIndex) == typed
            val correction = if (shownForThisWord) suggestions.correction else engine.suggest(typed, previous, true, context?.sentenceStart ?: true).correction
            if (correction != null && correction != typed) {
                final = correction
                lastCorrection = Correction(typed, correction)
            }
        }
        ic.commitText(final, 1)
        composing.clear()
        if (learningOn) engine.learn(final, previous)
    }

    /** The newest clipboard text, for the {clipboard} placeholder in saved phrases. */
    private fun latestClipText(): String = ClipboardStore.get(this).newest()?.text.orEmpty()

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        lastCorrection = null
        if (!composingIntact(ic)) composing.clear()
        var doubleSpaced = false
        if (composing.isNotEmpty()) {
            ic.beginBatchEdit()
            commitComposing(ic, autocorrect = correctionOn, expand = true)
            ic.commitText(" ", 1)
            ic.endBatchEdit()
            lastCorrection?.separator = " "
        } else {
            if (prefs.doubleSpacePeriod && now - lastSpaceTime < DOUBLE_SPACE_MS) {
                val before = ic.getTextBeforeCursor(2, 0)
                if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    ic.endBatchEdit()
                    doubleSpaced = true
                }
            }
            if (!doubleSpaced) ic.commitText(" ", 1)
        }
        lastSpaceTime = if (doubleSpaced) 0L else now
        pickedSuggestion = false
        updateShiftState()
        updateSuggestions()
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        pickedSuggestion = false
        if (!composingIntact(ic)) composing.clear()
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - lastGraphemeLength(composing))
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            updateShiftState()
            updateSuggestions()
            return
        }

        val correction = lastCorrection
        lastCorrection = null
        if (correction != null) {
            val committed = correction.corrected + correction.separator
            if (ic.getTextBeforeCursor(committed.length, 0)?.toString() == committed) {
                // Undo the autocorrection and remember the word so it isn't corrected again.
                ic.beginBatchEdit()
                ic.deleteSurroundingText(committed.length, 0)
                ic.commitText(correction.original + correction.separator, 1)
                ic.endBatchEdit()
                if (learningOn) engine.user.learn(correction.original, null, weight = 3)
                // Gboard-style: offer to add the word for good, so it never needs adding in Settings.
                if (!passwordField && correction.original.any { it.isLetter() } && !engine.isKnown(correction.original)) {
                    learnOffer = correction.original
                }
                updateShiftState()
                updateSuggestions()
                return
            }
        }

        deleteBackward(ic)
        if (predictionOn) restartComposing(ic)
        updateShiftState()
        updateSuggestions()
    }

    private fun deleteBackward(ic: InputConnection) {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(32, 0)
        if (before.isNullOrEmpty()) {
            // Nothing visible to us; let the app handle a raw delete key (e.g. removing chips).
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        ic.deleteSurroundingText(lastGraphemeLength(before), 0)
    }

    /** After deleting back into a word, make that word editable by suggestions again. */
    private fun restartComposing(ic: InputConnection) {
        val after = ic.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && (Character.isLetterOrDigit(after[0]) || after[0] == '\'')) return
        val before = ic.getTextBeforeCursor(MAX_RESTART, 0)?.toString() ?: return
        val word = WORD_AT_END.find(before)?.value ?: return
        if (word.length >= MAX_RESTART || !Character.isLetter(word.codePointAt(0))) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.setComposingText(word, 1)
        ic.endBatchEdit()
        composing.append(word)
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            if (composingIntact(ic)) commitComposing(ic, autocorrect = false) else composing.clear()
        }
        lastCorrection = null
        pickedSuggestion = false
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) sendKeyChar('\n') else ic.performEditorAction(action)
        updateSuggestions()
    }

    // ---- prefs -----------------------------------------------------------------------------------------

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        val r = root ?: return
        when (key) {
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW, Prefs.KEY_PUNCTUATION, Prefs.KEY_CUSTOM_KEYS, in Prefs.POPUP_KEYS -> refreshKeyboard()
            Prefs.KEY_BG_VERSION -> r.reloadPhoto()
            null -> { // All settings were reset.
                r.applyTheme(GlassTheme.from(this, prefs))
                r.reloadPhoto()
                refreshKeyboard()
            }
            in Prefs.THEME_KEYS -> r.applyTheme(GlassTheme.from(this, prefs))
            else -> Unit // Behavior settings are read when next used.
        }
    }

    companion object {
        private const val VOICE_CLEANUP_INSTRUCTION = "Clean up this dictated text: add punctuation and capitalization, " +
            "remove filler words like um and uh and false starts, and fix obvious mishearings. Do not change the meaning or add anything."

        /** Wait this long after a key before refreshing suggestions. */
        private const val SUGGESTION_DELAY_MS = 40L

        /** How much text before the cursor a backspace swipe can select back through. */
        private const val SWIPE_CONTEXT = 500
        /** Most text a tool (AI rewrite) reads from the field. */
        private const val TOOL_TEXT_LIMIT = 4000

        private const val DOUBLE_SPACE_MS = 600L
        private const val MAX_RESTART = 32
        /** ClipDescription.EXTRA_IS_SENSITIVE, also set by some apps before Android 13. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        private val RECENT_WORDS = Regex("[\\p{L}']+")
        private val WORD_AT_END = Regex("[\\p{L}\\p{N}'’]+$")
        private val CORRECTING_PUNCTUATION = setOf(".", ",", "!", "?", ":", ";", ")")
        private val ATTACHING_PUNCTUATION = setOf(".", ",", "!", "?", ":", ";")

        private val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        private val LITERAL_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )

        /** Length in chars of the last user-perceived character (handles emoji sequences). */
        fun lastGraphemeLength(text: CharSequence): Int {
            val s = text.toString()
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val start = it.preceding(s.length)
            return if (start == BreakIterator.DONE) 1 else (s.length - start).coerceAtLeast(1)
        }
    }
}

/** A window background that paints only the bottom [heightPx], so the top corners stay see-through. */
private class BottomStripDrawable(color: Int, private val heightPx: Float) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint().apply { this.color = color }

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        canvas.drawRect(b.left.toFloat(), maxOf(b.top.toFloat(), b.bottom - heightPx), b.right.toFloat(), b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
