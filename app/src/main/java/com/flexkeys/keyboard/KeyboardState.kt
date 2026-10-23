package com.flexkeys.keyboard

import com.flexkeys.layout.Layer
import com.flexkeys.layout.LayoutDef
import com.flexkeys.layout.Layouts

enum class ShiftState { OFF, ONCE, LOCKED }

/** The action the enter key performs, derived from the editor's imeOptions. */
enum class EnterAction(val icon: Icon) {
    NEWLINE(Icon.ENTER),
    GO(Icon.ARROW_RIGHT),
    SEARCH(Icon.SEARCH),
    SEND(Icon.SEND),
    NEXT(Icon.ARROW_RIGHT),
    PREVIOUS(Icon.ARROW_LEFT),
    DONE(Icon.DONE),
}

/** Tracks which layer is visible and the shift state. Has no Android dependencies. */
class KeyboardState {
    var layer: Layer = Layer.LETTERS
        private set
    var shift: ShiftState = ShiftState.OFF
        private set
    var lettersId: String = "qwerty"
    var numberRow: Boolean = false
    var punctuationKeys: Boolean = true

    /** True when the user pressed shift themselves, so auto-capitalization leaves it alone. */
    private var manualShift = false
    private var lastShiftTap = 0L

    val layout: LayoutDef get() = Layouts.forLayer(layer, lettersId, numberRow, punctuationKeys)
    val isShifted: Boolean get() = shift != ShiftState.OFF && layer == Layer.LETTERS

    fun setLayer(newLayer: Layer) {
        layer = newLayer
        if (newLayer != Layer.LETTERS) {
            shift = ShiftState.OFF
            manualShift = false
        }
    }

    fun onShiftTap(now: Long) {
        shift = when {
            shift == ShiftState.OFF -> ShiftState.ONCE
            shift == ShiftState.ONCE && now - lastShiftTap < DOUBLE_TAP_MS -> ShiftState.LOCKED
            else -> ShiftState.OFF
        }
        manualShift = shift != ShiftState.OFF
        lastShiftTap = now
    }

    fun lockCaps() {
        shift = ShiftState.LOCKED
        manualShift = true
    }

    /** Called after a character is typed: a one-shot shift is used up. */
    fun onCharacterTyped() {
        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            manualShift = false
        }
    }

    /** Applies auto-capitalization unless the user set shift manually. */
    fun applyAutoCaps(capsNeeded: Boolean) {
        if (shift == ShiftState.LOCKED || manualShift || layer != Layer.LETTERS) return
        shift = if (capsNeeded) ShiftState.ONCE else ShiftState.OFF
    }

    fun reset() {
        layer = Layer.LETTERS
        shift = ShiftState.OFF
        manualShift = false
    }

    private companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}
