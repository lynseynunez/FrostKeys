package com.flexkeys.suggest

import java.util.Locale

/**
 * How often one word follows another in everyday English (assets/bigrams.txt, about 80,000 common pairs).
 * Used to tell which word fits after the previous one, and to predict the next word.
 */
class BigramTable private constructor(private val after: Map<String, Entry>) {

    /** The words that follow one word, best first, with their counts. */
    private class Entry(val words: Array<String>, val counts: IntArray)

    val size: Int get() = after.size

    /** The count for [prev] followed by [word] (both lowercase), or 0. Counts are scaled to fit in an Int. */
    fun count(prev: String?, word: String): Int {
        if (prev == null) return 0
        val e = after[prev.lowercase(Locale.ROOT)] ?: return 0
        val i = e.words.indexOf(word.lowercase(Locale.ROOT))
        return if (i < 0) 0 else e.counts[i]
    }

    /** The most likely words after [prev], best first. */
    fun next(prev: String, limit: Int): List<String> =
        after[prev.lowercase(Locale.ROOT)]?.words?.asList()?.take(limit) ?: emptyList()

    companion object {
        val EMPTY = BigramTable(emptyMap())

        /** Counts in the file go up to the billions; dividing keeps them inside an Int and the logarithm barely changes. */
        private const val SCALE = 64

        /** Reads "first second count" lines. */
        fun parse(lines: Sequence<String>): BigramTable {
            val building = HashMap<String, MutableList<Pair<String, Int>>>()
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val p = line.split(' ')
                if (p.size != 3) continue
                val count = (p[2].toLongOrNull() ?: continue) / SCALE
                building.getOrPut(p[0]) { ArrayList(4) }.add(p[1] to count.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
            }
            val result = HashMap<String, Entry>(building.size * 2)
            for ((prev, list) in building) {
                list.sortByDescending { it.second }
                result[prev] = Entry(Array(list.size) { list[it].first }, IntArray(list.size) { list[it].second })
            }
            return BigramTable(result)
        }
    }
}
