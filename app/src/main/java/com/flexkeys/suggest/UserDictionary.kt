package com.flexkeys.suggest

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Words and word pairs learned from what the user types. Stored only in app-internal
 * storage (files/user_words.tsv). Accessed on the main thread; saving happens in the
 * background from a snapshot.
 */
class UserDictionary(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var dirty = 0
    @Volatile private var loaded = false
    /** Set by [clear] so data still being loaded from disk is thrown away. */
    private var discardStored = false

    /** Reads the stored file. Blocking; call from a background thread before use. */
    fun loadBlocking(): Pair<Map<String, Int>, Map<String, Map<String, Int>>> {
        val w = HashMap<String, Int>()
        val b = HashMap<String, HashMap<String, Int>>()
        if (file.isFile) {
            try {
                file.forEachLine { line ->
                    val p = line.split('\t')
                    when {
                        p.size == 3 && p[0] == "w" -> p[2].toIntOrNull()?.let { w[p[1]] = it }
                        p.size == 4 && p[0] == "b" -> p[3].toIntOrNull()?.let { b.getOrPut(p[1]) { HashMap() }[p[2]] = it }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read learned words", e)
            }
        }
        return w to b
    }

    /** Installs data produced by [loadBlocking]. Main thread. */
    fun install(data: Pair<Map<String, Int>, Map<String, Map<String, Int>>>) {
        loaded = true
        if (discardStored) return
        // Anything learned before loading finished is merged on top.
        for ((k, v) in data.first) words[k] = (words[k] ?: 0) + v
        for ((prev, nexts) in data.second) {
            val map = bigrams.getOrPut(prev) { HashMap() }
            for ((k, v) in nexts) map[k] = (map[k] ?: 0) + v
        }
    }

    fun count(word: String): Int = words[key(word)] ?: 0

    fun bigramCount(prev: String?, word: String): Int =
        if (prev == null) 0 else bigrams[key(prev)]?.get(key(word)) ?: 0

    /** The words most often typed after [prev], best first. */
    fun nextWords(prev: String, limit: Int): List<String> =
        bigrams[key(prev)]?.entries?.sortedByDescending { it.value }?.take(limit)?.map { it.key } ?: emptyList()

    /** Learned words starting with [prefix] (lowercase). */
    fun withPrefix(prefix: String): List<Pair<String, Int>> =
        words.entries.filter { it.key.startsWith(prefix) }.map { it.key to it.value }

    fun learn(word: String, prev: String?, weight: Int = 1) {
        val k = key(word)
        if (k.isEmpty() || k.length > MAX_WORD) return
        words[k] = (words[k] ?: 0) + weight
        if (prev != null) {
            val map = bigrams.getOrPut(key(prev)) { HashMap() }
            map[k] = (map[k] ?: 0) + 1
            if (map.size > MAX_NEXT) map.entries.minByOrNull { it.value }?.let { map.remove(it.key) }
        }
        if (words.size > MAX_WORDS) prune()
        if (++dirty >= SAVE_EVERY) save()
    }

    val isLoaded: Boolean get() = loaded

    /** Every learned word with how often it was typed, most used first. */
    fun allWords(): List<Pair<String, Int>> = words.entries.sortedByDescending { it.value }.map { it.key to it.value }

    /** Adds a word by hand so it is suggested and never autocorrected. */
    fun addWord(word: String) {
        val k = key(word.trim())
        if (k.isEmpty() || k.length > MAX_WORD || k.contains(' ')) return
        words[k] = maxOf(words[k] ?: 0, 2)
        dirty++
        save()
    }

    fun forget(word: String) {
        val k = key(word)
        words.remove(k)
        bigrams.remove(k)
        bigrams.values.forEach { it.remove(k) }
        dirty++
        save()
    }

    /** Forgets several words with a single save. */
    fun forgetMany(list: Collection<String>) {
        if (list.isEmpty()) return
        for (word in list) {
            val k = key(word)
            words.remove(k)
            bigrams.remove(k)
            bigrams.values.forEach { it.remove(k) }
        }
        dirty++
        save()
    }

    fun clear() {
        words.clear()
        bigrams.clear()
        discardStored = true
        dirty = 0
        writer.execute { file.delete() }
    }

    private fun prune() {
        val drop = words.entries.sortedBy { it.value }.take(words.size - MAX_WORDS * 9 / 10).map { it.key }
        drop.forEach { words.remove(it); bigrams.remove(it) }
    }

    /** All learned words and word pairs in the file format, for backups. */
    fun exportText(): String = buildString {
        for ((k, v) in words) append("w\t").append(k).append('\t').append(v).append('\n')
        for ((prev, nexts) in bigrams) for ((k, v) in nexts) {
            append("b\t").append(prev).append('\t').append(k).append('\t').append(v).append('\n')
        }
    }

    /** Adds the words from [exportText] output to the ones already learned. */
    fun importText(text: String) {
        for (line in text.lineSequence()) {
            val p = line.split('\t')
            when {
                p.size == 3 && p[0] == "w" -> p[2].toIntOrNull()?.let { words[p[1]] = maxOf(words[p[1]] ?: 0, it) }
                p.size == 4 && p[0] == "b" -> p[3].toIntOrNull()?.let {
                    val map = bigrams.getOrPut(p[1]) { HashMap() }
                    map[p[2]] = maxOf(map[p[2]] ?: 0, it)
                }
            }
        }
        dirty++
        save()
    }

    /** Writes pending changes in the background. */
    fun save() {
        if (dirty == 0 || !loaded) return
        dirty = 0
        val snapshot = exportText()
        writer.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(snapshot)
                tmp.renameTo(file)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save learned words", e)
            }
        }
    }

    private fun key(word: String) = word.lowercase(Locale.ROOT).replace('\t', ' ').replace('\n', ' ')

    companion object {
        private const val TAG = "FlexKeysUserDict"
        const val FILE_NAME = "user_words.tsv"
        private const val MAX_WORD = 40
        private const val MAX_WORDS = 20_000
        private const val MAX_NEXT = 40
        private const val SAVE_EVERY = 15
        private val writer = Executors.newSingleThreadExecutor()
    }
}
