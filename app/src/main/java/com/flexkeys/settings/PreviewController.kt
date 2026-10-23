package com.flexkeys.settings

import android.os.SystemClock
import android.widget.TextView
import com.flexkeys.gif.GifItem
import com.flexkeys.keyboard.KeyboardState
import com.flexkeys.keyboard.KeyboardView
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.KeyType
import com.flexkeys.ui.ToolSource
import com.flexkeys.phrases.CustomKeys
import com.flexkeys.layout.Layer
import com.flexkeys.service.Feedback
import com.flexkeys.service.FlexKeysService
import com.flexkeys.suggest.SuggestionStripView
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.KeyboardRootView

/**
 * Drives the keyboard preview in the settings screen. Typing goes into a small demo line so
 * every setting (layout, sizes, feedback, glass) can be tried without leaving the app.
 */
class PreviewController(
    private val root: KeyboardRootView,
    private val output: TextView,
    private val prefs: Prefs,
    private val feedback: Feedback,
) : KeyboardView.Listener, KeyboardRootView.PanelListener, SuggestionStripView.Listener {

    private val state = KeyboardState()
    private val text = StringBuilder()

    init {
        root.keyboardView.listener = this
        root.panelListener = this
        root.strip.listener = this
        refresh()
        render()
    }

    fun refresh() {
        state.lettersId = prefs.layoutId
        state.numberRow = prefs.numberRow
        state.punctuationKeys = prefs.punctuationKeys
        state.customKeys = CustomKeys.parse(prefs.customKeys)
        val kv = root.keyboardView
        kv.layer = state.layer
        kv.layout = state.layout
        kv.shiftState = state.shift
        kv.showKeyPreview = prefs.keyPreview
    }

    private fun render() {
        output.text = if (text.isEmpty()) "Type on the preview to try your settings" else text
        output.alpha = if (text.isEmpty()) 0.6f else 1f
    }

    private fun autoCaps() {
        val trimmed = text.trimEnd()
        val caps = prefs.autoCapitalize && (trimmed.isEmpty() || (text.endsWith(" ") && trimmed.last() in ".!?"))
        state.applyAutoCaps(caps)
        root.keyboardView.shiftState = state.shift
    }

    override fun onKeyDown(key: KeyDef) = feedback.keyPress(key.type)

    override fun onKey(key: KeyDef) {
        if (root.routeKeyToSearch(key, state.isShifted)) {
            if (key.type == KeyType.CHAR) {
                state.onCharacterTyped()
                root.keyboardView.shiftState = state.shift
            }
            return
        }
        when (key.type) {
            KeyType.CHAR -> type(if (key.isLetter && state.isShifted) key.output.uppercase() else key.output)
            KeyType.SPACE -> type(" ")
            KeyType.ENTER -> {
                text.clear()
                render()
                autoCaps()
            }
            KeyType.BACKSPACE -> if (text.isNotEmpty()) {
                text.setLength(text.length - FlexKeysService.lastGraphemeLength(text))
                render()
                autoCaps()
            }
            KeyType.SHIFT -> {
                state.onShiftTap(SystemClock.uptimeMillis())
                root.keyboardView.shiftState = state.shift
            }
            KeyType.LAYER -> {
                state.setLayer(key.target ?: Layer.LETTERS)
                refresh()
                autoCaps()
            }
            KeyType.EMOJI -> root.showPanel(KeyboardRootView.Panel.EMOJI)
            KeyType.SPACER -> Unit
        }
    }

    private fun type(s: String) {
        if (text.length > 80) text.delete(0, text.length - 60)
        text.append(s)
        state.onCharacterTyped()
        render()
        autoCaps()
    }

    override fun onText(text: String) {
        if (!root.routeTextToSearch(text)) type(text)
    }

    override fun onLongPress(key: KeyDef): Boolean {
        if (key.type == KeyType.SHIFT) {
            feedback.longPress()
            state.lockCaps()
            root.keyboardView.shiftState = state.shift
            return true
        }
        if (key.popup.isNotEmpty()) feedback.longPress()
        return false
    }

    override fun onCursorMove(steps: Int) = Unit

    // Panels and toolbar work in the preview too.
    override fun onEmoji(emoji: String) = type(emoji)
    override fun onPaste(text: String) = type(text)
    override fun onPanelBackspace() = onKey(KeyDef("backspace", KeyType.BACKSPACE))
    override fun onPanelClosed() = root.showPanel(KeyboardRootView.Panel.NONE)
    override fun onEmojiSearch() = root.setEmojiSearch(true)

    // GIFs need a real text field to send into, so the preview does not offer them.
    override fun onGifPicked(item: GifItem) = Unit
    override fun onGifSearchRequested() = Unit
    override fun onOpenSettings() = Unit
    override fun onTranslateInsert(text: String) = Unit
    override fun onEditPhrases() = Unit
    override fun toolSource(wholeField: Boolean) = ToolSource("", false)
    override fun onToolInsert(text: String) = Unit
    override fun onToolReplace(text: String, wholeField: Boolean) = Unit

    override fun onSuggestionPicked(index: Int, text: String) = Unit
    override fun onSuggestionLongPressed(text: String) = Unit
    override fun onStripTouched() = root.keyboardView.cancelAll()

    override fun onMenuTapped() {
        feedback.keyPress(KeyType.CHAR)
        root.showPanel(if (root.panel == KeyboardRootView.Panel.MENU) KeyboardRootView.Panel.NONE else KeyboardRootView.Panel.MENU)
    }

    override fun onMenuAction(action: SuggestionStripView.Action) {
        root.showPanel(KeyboardRootView.Panel.NONE)
        onAction(action)
    }

    override fun onAction(action: SuggestionStripView.Action) {
        feedback.keyPress(KeyType.CHAR)
        val target = when (action) {
            SuggestionStripView.Action.EMOJI -> KeyboardRootView.Panel.EMOJI
            SuggestionStripView.Action.CLIPBOARD -> KeyboardRootView.Panel.CLIPBOARD
            else -> return
        }
        root.showPanel(if (root.panel == target) KeyboardRootView.Panel.NONE else target)
    }
}
