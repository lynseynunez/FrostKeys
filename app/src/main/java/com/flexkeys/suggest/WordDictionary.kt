package com.flexkeys.suggest

import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.Locale

/**
 * The built-in English word list (assets/words.txt, "word<TAB>frequency"). Immutable once
 * loaded; lookups are case-insensitive and return the word's display form (e.g. "I'm").
 */
class WordDictionary private constructor(
    /** Lowercase keys, sorted, for prefix search. */
    private val keys: Array<String>,
    private val display: Array<String>,
    private val freqs: IntArray,
    private val index: HashMap<String, Int>,
) {

    val size: Int get() = keys.size

    fun frequency(word: String): Int = index[word.lowercase(Locale.ROOT)]?.let { freqs[it] } ?: 0

    fun contains(word: String): Boolean = index.containsKey(word.lowercase(Locale.ROOT))

    /** Display form of [word] if known ("i'm" -> "I'm"), else null. */
    fun displayForm(word: String): String? = index[word.lowercase(Locale.ROOT)]?.let { display[it] }

    /** Calls [block] for every word starting with [prefix] (lowercase). */
    inline fun forEachWithPrefix(prefix: String, block: (word: String, freq: Int) -> Unit) {
        forEachIndexWithPrefix(prefix) { i -> block(displayAt(i), frequencyAt(i)) }
    }

    fun displayAt(i: Int): String = display[i]
    fun frequencyAt(i: Int): Int = freqs[i]

    inline fun forEachIndexWithPrefix(prefix: String, block: (Int) -> Unit) {
        var i = lowerBound(prefix)
        while (i < size && keyAt(i).startsWith(prefix)) {
            block(i)
            i++
        }
    }

    fun keyAt(i: Int): String = keys[i]

    fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val TAG = "FlexKeysDictionary"
        const val ASSET = "words.txt"

        const val EXTRA_ASSET = "extra_words.txt"

        val EMPTY = WordDictionary(emptyArray(), emptyArray(), IntArray(0), HashMap())

        /** Reads the bundled word list. Blocking; call from a background thread. */
        fun load(context: Context): WordDictionary = try {
            val main = context.assets.open(ASSET).bufferedReader().useLines { it.toList() }
            // Tech words, slang, brands, names and places the subtitle list lacks. Optional.
            val extra = try {
                context.assets.open(EXTRA_ASSET).bufferedReader().useLines { it.toList() }
            } catch (e: IOException) {
                emptyList()
            }
            parse((main + extra).asSequence())
        } catch (e: IOException) {
            Log.w(TAG, "Word list missing", e)
            EMPTY
        }

        /** Builds a dictionary from "word<TAB>frequency" lines. */
        fun parse(lines: Sequence<String>): WordDictionary {
            // One entry per word. The first one wins, except that a brand spelled with a capital inside
            // (iPhone, YouTube) replaces a plain lowercase entry for the same word.
            val unique = LinkedHashMap<String, Triple<String, String, Int>>(40_000)
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val freq = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                val key = word.lowercase(Locale.ROOT)
                val old = unique[key]
                val innerCapital = word.length > 1 && word.drop(1).any { it.isUpperCase() }
                val oldInnerCapital = old != null && old.second.length > 1 && old.second.drop(1).any { it.isUpperCase() }
                if (old == null || (innerCapital && !oldInnerCapital)) unique[key] = Triple(key, word, maxOf(freq, old?.third ?: 0))
            }
            val entries = ArrayList(unique.values)
            entries.sortBy { it.first }
            val keys = Array(entries.size) { entries[it].first }
            val display = Array(entries.size) { entries[it].second }
            val freqs = IntArray(entries.size) { entries[it].third }
            val index = HashMap<String, Int>(entries.size * 2)
            keys.forEachIndexed { i, k -> index.putIfAbsent(k, i) }
            return WordDictionary(keys, display, freqs, index)
        }
    }
}
