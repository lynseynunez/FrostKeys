package com.flexkeys.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView

class FlexKeysService : InputMethodService() {
    override fun onCreateInputView(): View = TextView(this).apply { text = "FlexKeys" }
}
