package com.flexkeys.voice

/** Removes spoken filler sounds ("um", "uh", "erm") from dictated text, without touching real words. Works offline. */
object Fillers {
    private val filler = Regex("""(?i)(?<![\p{L}'])(?:u+h+m*|u+m+|e+r+m+|e+h+m+|hm+m*|mm+h*m*)(?![\p{L}'])[,.…]?\s*""")

    fun clean(text: String): String {
        var out = filler.replace(text, "")
        out = out.replace(Regex("""\s{2,}"""), " ").replace(Regex("""\s+([,.!?])"""), "$1").trim()
        if (out.isEmpty()) return text // never type nothing
        // The word that follows a removed filler at the start of the text keeps its capital letter.
        val startedWithFiller = filler.find(text)?.range?.first == 0
        if (startedWithFiller) out = out.replaceFirstChar { it.uppercaseChar() }
        return out
    }
}
