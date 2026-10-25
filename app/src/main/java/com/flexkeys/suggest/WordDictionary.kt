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

        /** Names, apps and texting words the subtitle-based list lacks, so they aren't treated as mistakes. */
        val EXTRA_WORDS: List<String> = listOf(
            "sakurikey", "klipy", "groq", "gboard", "gemini", "samsung", "tiktok", "whatsapp", "instagram", "youtube",
            "snapchat", "spotify", "netflix", "wifi", "bluetooth", "okies", "hubby", "wifey", "lol", "omg", "yup", "nope",
            "aww", "haha", "hahaha", "autocorrect", "autocomplete", "spellcheck", "translator", "keyboard", "emoji", "emojis",
            "settings", "screenshot", "screenshots", "download", "uploads", "app", "apps", "online", "offline", "login", "email",
            "texting", "unfollow", "podcast", "playlist", "wallpaper", "widget", "widgets", "notification", "notifications",
            "customizable", "adjustable", "background", "animation", "animations", "tilt", "shimmer", "gradient",
        ).map { "$it\t3000" }

        val EMPTY = WordDictionary(emptyArray(), emptyArray(), IntArray(0), HashMap())

        /** Reads the bundled word list. Blocking; call from a background thread. */
        fun load(context: Context): WordDictionary = try {
            context.assets.open(ASSET).bufferedReader().useLines { parse(it + EXTRA_WORDS.asSequence()) }
        } catch (e: IOException) {
            Log.w(TAG, "Word list missing", e)
            EMPTY
        }

        /** Builds a dictionary from "word<TAB>frequency" lines. */
        fun parse(lines: Sequence<String>): WordDictionary {
            val entries = ArrayList<Triple<String, String, Int>>(40_000)
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val freq = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                entries.add(Triple(word.lowercase(Locale.ROOT), word, freq))
            }
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
