package com.flexkeys.suggest

/**
 * Real words that are very often typed by mistake for another real word, fixed only when the word
 * before them makes the mistake clear ("better then" -> "better than"). The dictionary can't see these
 * slips because both words are valid, so they need the previous word. Deliberately short and cautious.
 */
object ConfusableFixes {

    private val comparatives = setOf(
        "more", "less", "better", "worse", "bigger", "smaller", "faster", "slower", "older", "younger", "higher", "lower",
        "greater", "rather", "other", "easier", "harder", "longer", "shorter", "stronger", "cheaper", "larger", "bigger",
        "taller", "darker", "lighter", "louder", "smarter", "happier", "nicer", "cooler", "hotter", "colder", "later",
        "earlier", "different", "fewer", "further", "farther",
    )

    private val fromSignals = setOf(
        "away", "back", "come", "comes", "came", "coming", "get", "got", "far", "different", "apart", "hear", "heard",
        "tired", "free", "safe", "straight", "separate", "recover", "suffer", "suffering", "learn", "learned",
    )

    private val loseSignals = setOf("to", "not", "never", "will", "would", "could", "might", "gonna", "don't", "can't", "won't")

    /** The word that [typed] probably should be after [previous], or null if it looks right. Both lowercase. */
    fun fix(typed: String, previous: String?): String? {
        if (previous == null) return null
        return when (typed) {
            "then" -> "than".takeIf { previous in comparatives }
            "form" -> "from".takeIf { previous in fromSignals }
            "loose" -> "lose".takeIf { previous in loseSignals }
            else -> null
        }
    }
}
