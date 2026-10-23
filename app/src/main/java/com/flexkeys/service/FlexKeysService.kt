package com.flexkeys.service

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.keyboard.EnterAction
import com.flexkeys.keyboard.KeyboardState
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.layout.Layer
import com.flexkeys.settings.SettingsActivity
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.suggest.SuggestionStripView
import com.flexkeys.suggest.Suggestions
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView

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

    private val composing = StringBuilder()
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
        feedback = Feedback(this, prefs)
        engine = SuggestionEngine.get(this)
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
        view.setOneHanded(prefs.oneHanded)
        root = view
        refreshKeyboard()
        view.reloadPhoto()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) state.reset()
        resetComposingState()

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        val password = cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS
        val literal = cls == InputType.TYPE_CLASS_TEXT && variation in LITERAL_VARIATIONS
        val noSuggestions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        predictionOn = prefs.suggestions && cls == InputType.TYPE_CLASS_TEXT && !password && !noSuggestions
        correctionOn = predictionOn && prefs.autocorrect && !literal
        learningOn = predictionOn && prefs.learnWords && !literal &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

        state.setLayer(if (numeric) Layer.NUMPAD else Layer.LETTERS)
        root?.showPanel(KeyboardRootView.Panel.NONE)
        refreshKeyboard()
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

    private class WordContext(val previous: String?, val sentenceStart: Boolean, val afterSpace: Boolean)

    /** Looks at the text before the composing word to find the previous word. */
    private fun readContext(ic: InputConnection): WordContext {
        val raw = ic.getTextBeforeCursor(80, 0)?.toString() ?: return WordContext(null, true, false)
        val text = raw.dropLast(composing.length.coerceAtMost(raw.length))
        val trimmed = text.trimEnd()
        val afterSpace = text.isNotEmpty() && text.last().isWhitespace()
        if (trimmed.isEmpty()) return WordContext(null, true, false)
        val sentenceStart = trimmed.last() in ".!?" || text.substring(trimmed.length).contains('\n')
        val previous = if (sentenceStart) null else WORD_AT_END.find(trimmed)?.value
        return WordContext(previous, sentenceStart, afterSpace)
    }

    private fun updateSuggestions() {
        val r = root ?: return
        val strip = r.strip
        val ic = currentInputConnection
        suggestions = if (!predictionOn || ic == null || r.panel != KeyboardRootView.Panel.NONE) {
            Suggestions.EMPTY
        } else {
            val ctx = readContext(ic)
            when {
                composing.isNotEmpty() -> engine.suggest(composing.toString(), ctx.previous, correctionOn)
                ctx.afterSpace -> engine.predict(ctx.previous, ctx.sentenceStart)
                else -> Suggestions.EMPTY
            }
        }
        strip.suggestions = suggestions
    }

    override fun onSuggestionPicked(index: Int, text: String) {
        val ic = currentInputConnection ?: return
        feedback.keyPress(KeyType.CHAR)
        val previous = readContext(ic).previous
        ic.beginBatchEdit()
        ic.commitText(text, 1) // Replaces the composing word, if any.
        ic.commitText(" ", 1)
        ic.endBatchEdit()
        if (learningOn) {
            // Choosing the literal typed word over a correction marks it as a real word.
            val chosenTyped = index == suggestions.typedIndex && suggestions.correction != null
            if (chosenTyped) engine.user.learn(text, previous, weight = 2) else engine.learn(text, previous)
        }
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

    override fun onAction(action: SuggestionStripView.Action) {
        feedback.keyPress(KeyType.CHAR)
        when (action) {
            SuggestionStripView.Action.SETTINGS -> {
                val intent = Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                requestHideSelf(0)
                startActivity(intent)
            }
            SuggestionStripView.Action.HIDE -> requestHideSelf(0)
            SuggestionStripView.Action.SWITCH_KEYBOARD -> {
                val switched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToNextInputMethod(false)
                if (!switched) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
            SuggestionStripView.Action.CLIPBOARD -> togglePanel(KeyboardRootView.Panel.CLIPBOARD)
            SuggestionStripView.Action.EMOJI -> togglePanel(KeyboardRootView.Panel.EMOJI)
            SuggestionStripView.Action.ONE_HANDED -> {
                prefs.oneHanded = if (prefs.oneHanded == Prefs.ONE_HANDED_OFF) Prefs.ONE_HANDED_RIGHT else Prefs.ONE_HANDED_OFF
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
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        ClipboardStore.get(this).add(text)
    }

    override fun onStripTouched() {
        root?.keyboardView?.cancelAll()
    }

    // ---- KeyboardView.Listener -------------------------------------------------------------------

    override fun onKeyDown(key: KeyDef) = feedback.keyPress(key.type)

    override fun onKey(key: KeyDef) {
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
    private fun commitComposing(ic: InputConnection, autocorrect: Boolean) {
        val typed = composing.toString()
        val previous = if (learningOn || autocorrect) readContext(ic).previous else null
        var final = engine.displayForm(typed)
        if (autocorrect) {
            val shownForThisWord = suggestions.typedIndex >= 0 &&
                suggestions.slots.getOrNull(suggestions.typedIndex) == typed
            val correction = if (shownForThisWord) suggestions.correction else engine.suggest(typed, previous, true).correction
            if (correction != null && correction != typed) {
                final = correction
                lastCorrection = Correction(typed, correction)
            }
        }
        ic.commitText(final, 1)
        composing.clear()
        if (learningOn) engine.learn(final, previous)
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        lastCorrection = null
        if (!composingIntact(ic)) composing.clear()
        var doubleSpaced = false
        if (composing.isNotEmpty()) {
            ic.beginBatchEdit()
            commitComposing(ic, autocorrect = correctionOn)
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
                if (learningOn) engine.user.learn(correction.original, null, weight = 2)
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
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW -> refreshKeyboard()
            Prefs.KEY_BG_VERSION -> r.reloadPhoto()
            Prefs.KEY_ONE_HANDED -> r.setOneHanded(prefs.oneHanded)
            null -> { // All settings were reset.
                r.applyTheme(GlassTheme.from(this, prefs))
                r.setOneHanded(prefs.oneHanded)
                r.reloadPhoto()
                refreshKeyboard()
            }
            in BEHAVIOR_KEYS -> Unit // Read when next used.
            else -> r.applyTheme(GlassTheme.from(this, prefs))
        }
    }

    companion object {
        private const val DOUBLE_SPACE_MS = 600L
        private const val MAX_RESTART = 32
        /** ClipDescription.EXTRA_IS_SENSITIVE, also set by some apps before Android 13. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        private val WORD_AT_END = Regex("[\\p{L}\\p{N}'’]+$")
        private val CORRECTING_PUNCTUATION = setOf(".", ",", "!", "?", ":", ";", ")")
        private val ATTACHING_PUNCTUATION = setOf(".", ",", "!", "?", ":", ";")

        /** Settings that change behavior but not how the keyboard looks. */
        private val BEHAVIOR_KEYS = setOf(
            Prefs.KEY_SUGGESTIONS, Prefs.KEY_AUTOCORRECT, Prefs.KEY_LEARN, Prefs.KEY_AUTOCAP,
            Prefs.KEY_DOUBLE_SPACE, Prefs.KEY_HAPTICS, Prefs.KEY_SOUND, Prefs.KEY_SOUND_VOLUME,
            Prefs.KEY_RECENT_EMOJI, Prefs.KEY_CLIPBOARD,
        )

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
