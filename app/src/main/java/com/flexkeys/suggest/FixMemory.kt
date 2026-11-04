package com.flexkeys.suggest

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Fixes the user chose themselves: they typed X and tapped the suggestion Y. Remembered on the phone only
 * (files/user_fixes.tsv), so the keyboard prefers Y the next time, and after a second time fixes X to Y on its own.
 * Undoing an autocorrection forgets that fix.
 */
class FixMemory(private val file: File?) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val fixes = HashMap<String, HashMap<String, Int>>()

    private fun key(s: String) = s.lowercase(Locale.ROOT)

    /** Reads the stored file. Blocking; call from a background thread. */
    fun loadBlocking(): Map<String, Map<String, Int>> {
        val result = HashMap<String, HashMap<String, Int>>()
        val f = file ?: return result
        if (!f.isFile) return result
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 3) p[2].toIntOrNull()?.let { result.getOrPut(p[0]) { HashMap() }[p[1]] = it }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read your fixes", e)
        }
        return result
    }

    /** Installs data from [loadBlocking]; fixes made while loading are kept. */
    fun install(data: Map<String, Map<String, Int>>) {
        for ((typed, targets) in data) {
            val map = fixes.getOrPut(typed) { HashMap() }
            for ((target, n) in targets) map[target] = (map[target] ?: 0) + n
        }
    }

    fun count(typed: String, target: String): Int = fixes[key(typed)]?.get(key(target)) ?: 0

    /** The fix chosen most often for [typed], with how many times, or null. */
    fun preferred(typed: String): Pair<String, Int>? =
        fixes[key(typed)]?.entries?.maxByOrNull { it.value }?.let { it.key to it.value }

    /** The user fixed [typed] to [target] by hand. */
    fun record(typed: String, target: String) {
        val t = key(typed)
        val g = key(target)
        if (t.length < 2 || g.isEmpty() || t == g) return
        val map = fixes.getOrPut(t) { HashMap() }
        map[g] = (map[g] ?: 0) + 1
        // Nothing grows without limit: keep the targets that were chosen most.
        if (map.size > MAX_TARGETS) map.entries.minByOrNull { it.value }?.let { map.remove(it.key) }
        if (fixes.size > MAX_WORDS) fixes.keys.firstOrNull { it != t }?.let { fixes.remove(it) }
        save()
    }

    /** The user undid [target] for [typed]: forget it. */
    fun reject(typed: String, target: String) {
        val map = fixes[key(typed)] ?: return
        if (map.remove(key(target)) != null) {
            if (map.isEmpty()) fixes.remove(key(typed))
            save()
        }
    }

    fun clear() {
        fixes.clear()
        save()
    }

    val size: Int get() = fixes.size

    private fun save() {
        val f = file ?: return
        val lines = StringBuilder()
        for ((typed, targets) in fixes) for ((target, n) in targets) lines.append(typed).append('\t').append(target).append('\t').append(n).append('\n')
        val data = lines.toString()
        writer.execute {
            try {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(data)
                tmp.renameTo(f)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save your fixes", e)
            }
        }
    }

    companion object {
        private const val TAG = "FlexKeysFixes"
        private const val FILE_NAME = "user_fixes.tsv"
        private const val MAX_TARGETS = 4
        private const val MAX_WORDS = 600
        private val writer = Executors.newSingleThreadExecutor()
    }
}
