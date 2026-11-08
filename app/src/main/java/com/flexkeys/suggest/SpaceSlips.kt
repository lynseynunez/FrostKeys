package com.flexkeys.suggest

/**
 * Spots a period typed where a space was meant ("bro.what"). The period key sits right next to the space bar,
 * so it is an easy slip. Pure logic, no Android.
 */
object SpaceSlips {

    class PeriodSlip(val previousWord: String)

    /** Endings that are really web addresses or file names, never a missed space. */
    private val ADDRESS_ENDINGS = setOf(
        "com", "org", "net", "io", "co", "me", "app", "dev", "edu", "gov", "us", "uk", "ca", "de", "fr", "tv", "gg", "ly",
        "ai", "xyz", "info", "biz", "ru", "jp", "in", "au", "html", "php", "png", "jpg", "pdf", "txt", "zip", "mp3", "mp4",
    )

    /** Words that normally end in a period: "Dr.", "etc.", "vs.". */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "vs", "etc", "inc", "ltd", "jr", "sr", "st", "approx", "dept", "est", "fig", "mt", "prof",
        "gen", "sgt", "ave", "blvd", "no", "eg", "ie", "cf", "al",
    )

    private val WORD_AT_END = Regex("[A-Za-z']+$")

    /**
     * [before] is the text right before the word being finished ([typed]); it must end in a period that follows a word,
     * with no space between. Returns the slip, or null when this looks like a real period (an abbreviation, a web address,
     * a number, a lone letter) or one of the words is not a known word.
     */
    fun periodSlip(before: String, typed: String, isKnown: (String) -> Boolean): PeriodSlip? {
        if (!before.endsWith('.')) return null
        val body = before.dropLast(1)
        val previous = WORD_AT_END.find(body)?.value ?: return null
        if (previous.length < 2) return null
        // "a.m.", "U.S.", "e.g." and "www.site" have more periods or an address character around them.
        val beforePrevious = body.dropLast(previous.length)
        if (beforePrevious.endsWith(".") || beforePrevious.endsWith("@") || beforePrevious.endsWith("/")) return null
        if (typed.length < 2 || !typed.all { it.isLetter() || it == '\'' }) return null
        val t = typed.lowercase()
        val p = previous.lowercase()
        if (t in ADDRESS_ENDINGS || p in ABBREVIATIONS || p == "www") return null
        if (!isKnown(p) || !isKnown(t)) return null
        return PeriodSlip(previous)
    }
}
