package com.flexkeys.suggest

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ln

/** What the suggestion strip shows. [slots] are left-to-right; the best guess is in the middle. */
data class Suggestions(
    val slots: List<String>,
    /** Index of the slot that space will commit as an autocorrection, or -1. */
    val highlighted: Int = -1,
    /** The word space would replace the typed word with, if any. */
    val correction: String? = null,
    /** Slot index holding the literally typed word, or -1. */
    val typedIndex: Int = -1,
    /** Slot index holding an emoji suggestion ("pizza" -> 🍕), or -1. */
    val emojiIndex: Int = -1,
) {
    /** Puts [emoji] in the right-hand slot. */
    fun withEmoji(emoji: String): Suggestions {
        val list = slots.toMutableList()
        val index = if (list.size >= 3) 2 else list.size
        if (index == 2 && list.size >= 3) list[2] = emoji else list.add(emoji)
        return copy(slots = list, emojiIndex = index, highlighted = if (highlighted == index) -1 else highlighted)
    }

    companion object {
        val EMPTY = Suggestions(emptyList())
    }
}

/**
 * Completions, simple autocorrect and next-word prediction from the built-in word list plus
 * the words this user has typed. One instance per process, shared by the keyboard service
 * and the settings screen.
 */
class SuggestionEngine private constructor(context: Context) {

    @Volatile
    var dictionary: WordDictionary = WordDictionary.EMPTY
        private set
    val user = UserDictionary(context)

    init {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Executors.newSingleThreadExecutor().execute {
            val dict = WordDictionary.load(app)
            val learned = user.loadBlocking()
            main.post {
                dictionary = dict
                user.install(learned)
            }
        }
    }

    private class Candidate(val display: String, val score: Double, val cost: Double)

    fun isKnown(word: String): Boolean = dictionary.contains(word) || user.count(word) >= LEARNED_THRESHOLD

    /** Proper display form of a known word typed in lowercase ("i" -> "I", "im" stays "im"). */
    fun displayForm(word: String): String {
        val d = dictionary.displayForm(word) ?: return word
        return if (word == word.lowercase(Locale.ROOT) && d.lowercase(Locale.ROOT) == word) d else word
    }

    private fun score(word: String, freq: Int, prev: String?): Double {
        val userCount = user.count(word)
        return ln(freq + 1.0) +
            1.6 * ln(1.0 + userCount) +
            2.2 * ln(1.0 + user.bigramCount(prev, word))
    }

    fun suggest(typed: String, prev: String?, allowCorrection: Boolean): Suggestions {
        if (typed.isEmpty()) return Suggestions.EMPTY
        val lower = typed.lowercase(Locale.ROOT)
        val candidates = HashMap<String, Candidate>()
        fun add(display: String, s: Double, cost: Double) {
            val key = display.lowercase(Locale.ROOT)
            val old = candidates[key]
            if (old == null || old.score < s) candidates[key] = Candidate(display, s, cost)
        }

        // Completions, including the typed word itself.
        dictionary.forEachWithPrefix(lower) { word, freq ->
            val extra = word.length - lower.length
            add(word, score(word, freq, prev) - if (extra > 0) COMPLETION_PENALTY + 0.12 * extra else 0.0, COMPLETION_COST)
        }
        for ((word, _) in user.withPrefix(lower)) {
            if (user.count(word) < LEARNED_THRESHOLD && word != lower) continue
            val extra = word.length - lower.length
            add(word, score(word, dictionary.frequency(word), prev) - if (extra > 0) COMPLETION_PENALTY + 0.12 * extra else 0.0, COMPLETION_COST)
        }

        val correctable = allowCorrection && lower.length >= 2 && lower.all { it.isLetter() || it == '\'' } &&
            !hasInnerCapital(typed)
        if (correctable) {
            forEachEdit(lower) { edit, cost ->
                val freq = dictionary.frequency(edit)
                if (freq > 0 || user.count(edit) >= LEARNED_THRESHOLD) {
                    add(dictionary.displayForm(edit) ?: edit, score(edit, freq, prev) - EDIT_WEIGHT * cost, cost)
                }
            }
        }

        val ranked = candidates.entries
            .filter { it.key != lower }
            .sortedByDescending { it.value.score }
            .map { it.value }

        var correction: String? = null
        if (correctable) {
            val best = if (isKnown(lower)) {
                // A valid but rare word is only corrected through the most typo-like edits
                // (missing apostrophe or doubled letter) to a far more likely word: "helo" -> "hello".
                val typedScore = score(lower, dictionary.frequency(lower), prev)
                ranked.firstOrNull { it.cost <= KNOWN_WORD_MAX_COST }?.takeIf { it.score >= typedScore + KNOWN_WORD_MARGIN }
            } else {
                ranked.firstOrNull()?.takeIf { it.score >= CORRECTION_THRESHOLD }
            }
            if (best != null) correction = applyCase(best.display, typed)
        }

        val others = ranked.map { applyCase(it.display, typed) }.filter { it != correction }
        val center = correction ?: others.getOrNull(0)
        val right = if (correction != null) others.getOrNull(0) else others.getOrNull(1)
        val slots = listOfNotNull(typed, center, right).distinct()
        return Suggestions(
            slots = slots,
            highlighted = if (correction != null) 1 else -1,
            correction = correction,
            typedIndex = 0,
        )
    }

    /** Next-word predictions after [prev]; [sentenceStart] capitalizes them. */
    fun predict(prev: String?, sentenceStart: Boolean): Suggestions {
        val words = LinkedHashSet<String>()
        if (prev != null) user.nextWords(prev, 3).forEach { words.add(dictionary.displayForm(it) ?: it) }
        val fallback = if (sentenceStart) START_WORDS else COMMON_WORDS
        for (w in fallback) {
            if (words.size >= 3) break
            words.add(w)
        }
        val list = words.map { if (sentenceStart) it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } else it }.distinct()
        // Best guess goes in the middle.
        val slots = when (list.size) {
            0 -> emptyList()
            1 -> list
            else -> listOfNotNull(list[1], list[0], list.getOrNull(2))
        }
        return Suggestions(slots)
    }

    fun learn(word: String, prev: String?) {
        if (word.length > 1 || word.equals("a", true) || word == "I") user.learn(word, prev)
    }

    private fun hasInnerCapital(s: String) = s.drop(1).any { it.isUpperCase() } && !s.all { !it.isLetter() || it.isUpperCase() }

    private fun applyCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(Locale.ROOT)
        typed.first().isUpperCase() -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> word
    }

    /** Enumerates single edits of [w] with a cost that favors likely typos on a phone keyboard. */
    private inline fun forEachEdit(w: String, block: (String, Double) -> Unit) {
        val n = w.length
        for (i in 0..n) {
            if (i < n) {
                // Extra character; doubled letters are especially common.
                block(w.removeRange(i, i + 1), if (i > 0 && w[i] == w[i - 1]) 0.5 else 0.9)
                for (c in ALPHABET) {
                    if (c == w[i]) continue
                    val cost = if (isNeighbor(w[i], c)) 0.6 else 1.0
                    block(w.substring(0, i) + c + w.substring(i + 1), cost)
                }
            }
            if (i < n - 1 && w[i] != w[i + 1]) {
                block(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), 0.7)
            }
            for (c in ALPHABET) {
                // A missed apostrophe or a missed double letter are the most common slips.
                val doubled = (i > 0 && w[i - 1] == c) || (i < n && w[i] == c)
                val cost = when {
                    c == '\'' -> 0.15
                    doubled -> 0.35
                    else -> 0.95
                }
                block(w.substring(0, i) + c + w.substring(i), cost)
            }
        }
    }

    private fun isNeighbor(a: Char, b: Char): Boolean = NEIGHBORS[a]?.indexOf(b)?.let { it >= 0 } ?: false

    companion object {
        private const val LEARNED_THRESHOLD = 2
        private const val COMPLETION_PENALTY = 0.6
        private const val EDIT_WEIGHT = 3.0
        private const val CORRECTION_THRESHOLD = 5.0
        private const val KNOWN_WORD_MAX_COST = 0.5
        private const val KNOWN_WORD_MARGIN = 4.0
        private const val COMPLETION_COST = 10.0
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"

        private val START_WORDS = listOf("I", "The", "Thanks", "Hi", "It")
        private val COMMON_WORDS = listOf("the", "to", "and", "a", "I")

        private val NEIGHBORS: Map<Char, String> = run {
            val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            val map = HashMap<Char, String>()
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, c ->
                    val sb = StringBuilder()
                    row.getOrNull(i - 1)?.let { sb.append(it) }
                    row.getOrNull(i + 1)?.let { sb.append(it) }
                    // Rows are staggered by about half a key.
                    rows.getOrNull(r - 1)?.let { up -> listOf(i, i + 1).forEach { j -> up.getOrNull(j)?.let { sb.append(it) } } }
                    rows.getOrNull(r + 1)?.let { down -> listOf(i - 1, i).forEach { j -> down.getOrNull(j)?.let { sb.append(it) } } }
                    map[c] = sb.toString()
                }
            }
            map
        }

        @Volatile private var instance: SuggestionEngine? = null

        fun get(context: Context): SuggestionEngine =
            instance ?: synchronized(this) {
                instance ?: SuggestionEngine(context.applicationContext).also { instance = it }
            }
    }
}
