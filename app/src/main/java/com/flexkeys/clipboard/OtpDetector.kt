package com.flexkeys.clipboard

/** Spots one-time verification codes in copied text, like "Your code is 482913". */
object OtpDetector {

    private val keywords = Regex("code|otp|verif|passcode|one-time|one time|security|pin\\b|confirm", RegexOption.IGNORE_CASE)
    private val bareCode = Regex("^\\d{4,8}$")
    private val splitCode = Regex("(?<![\\d-])(\\d{3})[- ](\\d{3})(?![\\d-])")
    private val plainCode = Regex("(?<![\\d.,])\\d{4,8}(?!\\d|[.,]\\d)")

    /** The code found in [text], without any separator, or null if it doesn't look like one. */
    fun find(text: String): String? {
        val t = text.trim()
        if (t.length > MAX_LENGTH || t.isEmpty()) return null
        if (bareCode.matches(t)) return t
        if (!keywords.containsMatchIn(t)) return null
        splitCode.find(t)?.let { return it.groupValues[1] + it.groupValues[2] }
        return plainCode.find(t)?.value
    }

    private const val MAX_LENGTH = 300
}
