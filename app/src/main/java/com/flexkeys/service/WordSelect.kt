package com.flexkeys.service

/** Finds how far back whole words reach from the cursor, for swipe-to-select on backspace. */
object WordSelect {

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    /** Number of characters before the end of [text] that cover the last [words] words. */
    fun charsBack(text: String, words: Int): Int {
        var i = text.length
        repeat(words) {
            while (i > 0 && !isWordChar(text[i - 1])) i--
            while (i > 0 && isWordChar(text[i - 1])) i--
        }
        return text.length - i
    }
}
