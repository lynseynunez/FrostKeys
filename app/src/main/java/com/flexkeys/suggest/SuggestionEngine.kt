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
    /** Slot index holding a one-time code copied from a message, or -1. */
    val clipIndex: Int = -1,
) {
    /** Puts a copied one-time [code] in the left-hand slot. */
    fun withCode(code: String): Suggestions {
        val list = slots.toMutableList()
        if (list.size >= 3) list[0] = code else list.add(0, code)
        return copy(slots = list, clipIndex = 0, highlighted = -1, typedIndex = -1, emojiIndex = -1)
    }

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

    /** Common misspellings with their fix ("teh" -> "the"), from assets/typos.tsv. */
    @Volatile
    private var typos: Map<String, String> = emptyMap()

    init {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Executors.newSingleThreadExecutor().execute {
            val dict = WordDictionary.load(app)
            val typoMap = loadTypos(app)
            val learned = user.loadBlocking()
            main.post {
                dictionary = dict
                typos = typoMap + PERSONAL_TYPOS
                user.install(learned)
                cleanLearned()
            }
        }
    }

    private fun loadTypos(app: Context): Map<String, String> {
        val map = HashMap<String, String>(50_000)
        try {
            app.assets.open("typos.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == '#') continue
                    val tab = line.indexOf('\t')
                    if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        } catch (e: java.io.IOException) {
            // Without the list the keyboard still corrects with its dictionary.
        }
        return map
    }

    private class Candidate(val display: String, val score: Double, val cost: Double)

    fun isKnown(word: String): Boolean = dictionary.contains(word) || user.count(word) >= learnedThreshold(word)

    /**
     * A word that isn't in the dictionary but is one slip away from a common word is probably a typo
     * rather than a real new word ("thsi", "recieve"), so it must be typed many more times before it
     * counts as learned.
     */
    private val typoLikeCache = HashMap<String, Boolean>()

    private fun looksLikeTypo(word: String): Boolean {
        val w = word.lowercase(Locale.ROOT)
        if (dictionary.contains(w) || w.length < 3) return false
        return typoLikeCache.getOrPut(w) {
            var found = false
            forEachEdit(w) { edit, _ ->
                if (!found && dictionary.frequency(edit) >= TYPO_NEIGHBOR_FREQ) found = true
            }
            found
        }
    }

    private fun learnedThreshold(word: String): Int = if (looksLikeTypo(word)) TYPO_LEARNED_THRESHOLD else LEARNED_THRESHOLD

    /**
     * Forgets learned words that look like typos and were typed only a few times. Returns how many
     * were removed. Runs by itself after the dictionary loads, and from settings.
     */
    fun cleanLearned(): Int {
        if (dictionary === WordDictionary.EMPTY) return 0
        val doomed = user.allWords().filter { (word, count) ->
            count < TYPO_LEARNED_THRESHOLD && !dictionary.contains(word) && looksLikeTypo(word)
        }.map { it.first }
        user.forgetMany(doomed)
        return doomed.size
    }

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

    /**
     * @param sentenceStart true when [typed] begins a sentence; a capitalized word elsewhere is
     * probably a name and is left alone.
     */
    fun suggest(typed: String, prev: String?, allowCorrection: Boolean, sentenceStart: Boolean = true): Suggestions {
        if (typed.isEmpty()) return Suggestions.EMPTY
        val lower = typed.lowercase(Locale.ROOT)
        val candidates = HashMap<String, Candidate>()
        fun add(display: String, s: Double, cost: Double) {
            val key = display.lowercase(Locale.ROOT)
            val old = candidates[key]
            if (old == null || old.score < s) candidates[key] = Candidate(display, s, cost)
        }

        // Completions, including the typed word itself.
        var hasCompletions = false
        dictionary.forEachWithPrefix(lower) { word, freq ->
            if (word.length > lower.length) hasCompletions = true
            val extra = word.length - lower.length
            add(word, score(word, freq, prev) - if (extra > 0) COMPLETION_PENALTY + 0.12 * extra else 0.0, COMPLETION_COST)
        }
        for ((word, _) in user.withPrefix(lower)) {
            if (user.count(word) < learnedThreshold(word) && word != lower) continue
            val extra = word.length - lower.length
            add(word, score(word, dictionary.frequency(word), prev) - if (extra > 0) COMPLETION_PENALTY + 0.12 * extra else 0.0, COMPLETION_COST)
        }

        val capitalized = typed.length > 1 && typed[0].isUpperCase()
        val correctable = allowCorrection && lower.length >= 2 && lower.all { it.isLetter() || it == '\'' } &&
            !hasInnerCapital(typed) && !(capitalized && !sentenceStart)
        if (correctable) {
            var bestEdit = Double.NEGATIVE_INFINITY
            forEachEdit(lower) { edit, cost ->
                val freq = dictionary.frequency(edit)
                if (freq > 0 || user.count(edit) >= learnedThreshold(edit)) {
                    val s = score(edit, freq, prev) - EDIT_WEIGHT * cost
                    if (s > bestEdit) bestEdit = s
                    add(dictionary.displayForm(edit) ?: edit, s, cost)
                }
            }
            // Two slips in one word ("tgungs" -> "things"). Only when one edit found nothing good
            // and the word isn't the start of a real word, to keep typing fast.
            if (bestEdit < CORRECTION_THRESHOLD && !hasCompletions && lower.length >= 4) {
                forEachEdit(lower) { first, cost1 ->
                    forEachLikelySlip(first) { edit, cost2 ->
                        if (edit != lower) {
                            val freq = dictionary.frequency(edit)
                            if (freq > 0) {
                                val cost = cost1 + cost2
                                add(dictionary.displayForm(edit) ?: edit, score(edit, freq, prev) - EDIT_WEIGHT * cost - 0.5, cost)
                            }
                        }
                    }
                }
            }
        }

        val ranked = candidates.entries
            .filter { it.key != lower }
            .sortedByDescending { it.value.score }
            .map { it.value }

        var correction: String? = null
        // A known misspelling ("teh", "definately") is fixed straight away, unless the user taught us the word.
        val typoFix = if (correctable && user.count(lower) < learnedThreshold(lower)) PHRASE_FIXES[lower] ?: typos[lower] else null
        if (typoFix != null) {
            correction = applyCase(typoFix, typed)
        } else if (correctable) {
            // Capitalized words are often names, so they need a clearer win to be changed.
            var threshold = if (capitalized) CORRECTION_THRESHOLD + 2.0 else CORRECTION_THRESHOLD
            // A long misspelled word with one clearly best match can be fixed on a lower score.
            val lead = if (ranked.size > 1) ranked[0].score - ranked[1].score else Double.MAX_VALUE
            if (lower.length >= LONG_WORD_LENGTH && lead >= LONG_WORD_LEAD) threshold -= LONG_WORD_THRESHOLD_DISCOUNT
            val best = if (isKnown(lower)) {
                // A valid but rare word is only corrected through the most typo-like edits
                // (missing apostrophe or doubled letter) to a far more likely word: "helo" -> "hello".
                val typedScore = score(lower, dictionary.frequency(lower), prev)
                // Swapped letters and a neighboring key (cost up to 0.7) count too, but need a bigger win.
                ranked.firstOrNull { it.cost <= KNOWN_WORD_MAX_COST }?.takeIf { it.score >= typedScore + KNOWN_WORD_MARGIN }
                    ?: ranked.firstOrNull { it.cost <= KNOWN_WORD_LIKELY_SLIP_COST }
                        ?.takeIf { it.score >= typedScore + KNOWN_WORD_MARGIN + 2.0 }
            } else {
                ranked.firstOrNull()?.takeIf { it.score >= threshold }
            }
            if (best != null) correction = applyCase(best.display, typed)
            // Nothing close enough: maybe two words ran together.
            if (correction == null && !capitalized) {
                if (!isKnown(lower)) {
                    correction = splitFix(lower)?.let { applyCase(it, typed) }
                } else if (user.count(lower) == 0 && dictionary.frequency(lower) < RARE_WORD_FREQ) {
                    // The subtitle list contains run-together junk like "thankyou"; two common words win over it.
                    correction = splitFix(lower, STRICT_SPLIT_FREQ)?.let { applyCase(it, typed) }
                }
            }
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

    /**
     * Two words typed with no space ("stillmissing"), or with a stray letter from a key next to the
     * space bar where the space should be ("testingvit"). Returns the two words, or null.
     */
    private fun splitFix(lower: String, minFreq: Int = SPLIT_MIN_FREQ): String? {
        if (lower.length < 5 || lower.length > 26 || !lower.all { it.isLetter() }) return null
        var best: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        fun consider(a: String, b: String, penalty: Double) {
            val okA = a.length >= 2 || a in ONE_LETTER_WORDS
            if (!okA || b.length < 2) return
            val fa = dictionary.frequency(a)
            val fb = dictionary.frequency(b)
            if (fa < minFreq || fb < minFreq) return
            // Very short parts only count if they are very common words.
            if ((a.length <= 2 && fa < 2_000_000) || (b.length <= 2 && fb < 2_000_000)) return
            val s = ln(fa + 1.0) + ln(fb + 1.0) - penalty
            if (s > bestScore) {
                bestScore = s
                best = "$a $b"
            }
        }
        for (i in 1 until lower.length) {
            consider(lower.substring(0, i), lower.substring(i), 0.0)
            if (lower[i] in SPACE_NEIGHBORS && i + 1 < lower.length) consider(lower.substring(0, i), lower.substring(i + 1), 1.0)
        }
        return best
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
                block(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), SWAP_COST)
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

    /** The cheapest, most common slips only: a neighboring key, swapped letters, a doubled letter. */
    private inline fun forEachLikelySlip(w: String, block: (String, Double) -> Unit) {
        val n = w.length
        for (i in 0 until n) {
            NEIGHBORS[w[i]]?.forEach { c -> block(w.substring(0, i) + c + w.substring(i + 1), 0.6) }
            if (i < n - 1 && w[i] != w[i + 1]) block(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), SWAP_COST)
            if (i > 0 && w[i] == w[i - 1]) block(w.removeRange(i, i + 1), 0.5)
        }
    }

    private fun isNeighbor(a: Char, b: Char): Boolean = NEIGHBORS[a]?.indexOf(b)?.let { it >= 0 } ?: false

    companion object {
        private const val LEARNED_THRESHOLD = 2
        private const val COMPLETION_PENALTY = 0.6
        private const val EDIT_WEIGHT = 3.0
        private const val CORRECTION_THRESHOLD = 5.0
        private const val KNOWN_WORD_MAX_COST = 0.5
        private const val KNOWN_WORD_LIKELY_SLIP_COST = 0.7
        /** Swapped neighboring letters are the most common phone typo. */
        private const val SWAP_COST = 0.6
        /** Long words have few look-alikes, so a clear single best match can be accepted on a lower score. */
        private const val LONG_WORD_LENGTH = 7
        private const val LONG_WORD_THRESHOLD_DISCOUNT = 1.8
        private const val LONG_WORD_LEAD = 2.0

        /** Slips seen in the user's own messages that are too far from the word for the general rules. */
        private val PERSONAL_TYPOS = mapOf(
            "inspirtabion" to "inspiration", "boackgrojnd" to "background", "spellchdck" to "spellcheck",
            "ankmatioh" to "animation", "cinfurmation" to "confirmation", "adjustablw" to "adjustable",
            "everyrhkng" to "everything", "soacebar" to "spacebar", "translocator" to "translator",
            "seemlessly" to "seamlessly", "ahha" to "haha", "wamt" to "want", "thos" to "this", "stillcmissing" to "still missing",
            "testingvit" to "testing it",
        )

        /** Keys right next to the space bar: a slip there is often a stray letter where the space should be. */
        private const val SPACE_NEIGHBORS = "xcvbnm"
        private val ONE_LETTER_WORDS = setOf("i", "a")

        /** Words run together by mistake, fixed into two words. */
        private val PHRASE_FIXES = mapOf(
            "alot" to "a lot", "atleast" to "at least", "incase" to "in case", "infact" to "in fact",
            "aswell" to "as well", "eachother" to "each other", "alittle" to "a little", "alright" to "all right",
            "inbetween" to "in between", "noone" to "no one", "everytime" to "every time", "ofcourse" to "of course",
        )
        /** A word typed this many times that looks like a typo is finally accepted as learned. */
        private const val TYPO_LEARNED_THRESHOLD = 5
        /** How common a dictionary word must be for a near miss to make a typed word look like a typo. */
        private const val TYPO_NEIGHBOR_FREQ = 20_000
        /** Both halves of a split word must be at least this common. */
        private const val SPLIT_MIN_FREQ = 20_000
        /** A dictionary word rarer than this might be two words stuck together. */
        private const val RARE_WORD_FREQ = 10_000
        private const val STRICT_SPLIT_FREQ = 200_000
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
