package com.flexkeys.service

import android.content.SharedPreferences
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.flexkeys.keyboard.EnterAction
import com.flexkeys.keyboard.KeyboardState
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.layout.Layer
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView

/** The input method. Owns the views and turns key events into edits on the text field. */
class FlexKeysService : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var feedback: Feedback
    private val state = KeyboardState()
    private var root: KeyboardRootView? = null
    private var lastSpaceTime = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        feedback = Feedback(this, prefs)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        root?.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        root?.release()
        val view = KeyboardRootView(this, GlassTheme.from(this, prefs))
        view.keyboardView.listener = this
        root = view
        refreshKeyboard()
        view.reloadPhoto()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) state.reset()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        state.setLayer(if (numeric) Layer.NUMPAD else Layer.LETTERS)
        refreshKeyboard()
        updateShiftState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        root?.keyboardView?.cancelAll()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
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

    // ---- KeyboardView.Listener -------------------------------------------------------------------

    override fun onKeyDown(key: KeyDef) = feedback.keyPress(key.type)

    override fun onKey(key: KeyDef) {
        when (key.type) {
            KeyType.CHAR -> commitCharacter(
                if (key.isLetter && state.isShifted) key.output.uppercase() else key.output
            )
            KeyType.SPACE -> onSpace()
            KeyType.BACKSPACE -> deleteBackward()
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
            KeyType.EMOJI, KeyType.SPACER -> Unit
        }
        if (key.type != KeyType.SPACE) lastSpaceTime = 0L
    }

    override fun onText(text: String) {
        commitCharacter(text)
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
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    // ---- editing -----------------------------------------------------------------------------------

    private fun commitCharacter(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        state.onCharacterTyped()
        root?.keyboardView?.shiftState = state.shift
        updateShiftState()
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (prefs.doubleSpacePeriod && now - lastSpaceTime < DOUBLE_SPACE_MS) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceTime = 0L
                updateShiftState()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceTime = now
        updateShiftState()
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
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

    private fun onEnter() {
        val info = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) sendKeyChar('\n') else ic.performEditorAction(action)
    }

    // ---- prefs -----------------------------------------------------------------------------------------

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        val r = root ?: return
        when (key) {
            Prefs.KEY_LAYOUT, Prefs.KEY_NUMBER_ROW, Prefs.KEY_PREVIEW -> refreshKeyboard()
            Prefs.KEY_BG_VERSION -> r.reloadPhoto()
            else -> r.applyTheme(GlassTheme.from(this, prefs))
        }
    }

    companion object {
        private const val DOUBLE_SPACE_MS = 600L

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
