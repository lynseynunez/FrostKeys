package com.flexkeys.voice

/**
 * Hands recognized speech from [VoiceInputActivity] to the keyboard service, which runs in the
 * same process. The text waits here until the keyboard is shown again over the text field.
 */
object VoiceBridge {

    @Volatile private var pending: String? = null

    fun deliver(text: String) {
        pending = text
    }

    /** Returns the waiting text once, or null if there is none. */
    fun take(): String? {
        val text = pending
        pending = null
        return text
    }
}
