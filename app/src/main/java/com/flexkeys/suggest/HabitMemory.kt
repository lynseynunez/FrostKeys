package com.flexkeys.suggest

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * What the keyboard has learned about how this person types, kept on the phone only (files/user_habits.tsv):
 * which letters they mix up (so a slip they make often is corrected more readily), and how often a stray period or a
 * missed space was fixed or undone. Nothing here is sent anywhere.
 */
class HabitMemory(private val file: File?) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    /** "typed intended" (two letters) -> times. */
    private val confusions = HashMap<String, Int>()
    private val counters = HashMap<String, Int>()
    /** The letter before a stray period -> times. */
    private val periodAfter = HashMap<Char, Int>()

    /** Score bonus for each (typed, intended) letter pair, rebuilt whenever the counts change. */
    private var table = FloatArray(26 * 26)

    private fun index(typed: Char, intended: Char): Int {
        val a = typed.lowercaseChar() - 'a'
        val b = intended.lowercaseChar() - 'a'
        return if (a in 0..25 && b in 0..25) a * 26 + b else -1
    }

    // ---- reading the stored file ----

    class Stored(val confusions: Map<String, Int>, val counters: Map<String, Int>, val periodAfter: Map<Char, Int>)

    fun loadBlocking(): Stored {
        val c = HashMap<String, Int>()
        val n = HashMap<String, Int>()
        val p = HashMap<Char, Int>()
        val f = file
        if (f != null && f.isFile) {
            try {
                f.forEachLine { line ->
                    val parts = line.split('\t')
                    when {
                        parts.size == 4 && parts[0] == "c" && parts[1].length == 1 && parts[2].length == 1 ->
                            parts[3].toIntOrNull()?.let { c[parts[1] + parts[2]] = it }
                        parts.size == 3 && parts[0] == "n" -> parts[2].toIntOrNull()?.let { n[parts[1]] = it }
                        parts.size == 3 && parts[0] == "p" && parts[1].length == 1 -> parts[2].toIntOrNull()?.let { p[parts[1][0]] = it }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read typing habits", e)
            }
        }
        return Stored(c, n, p)
    }

    fun install(data: Stored) {
        for ((k, v) in data.confusions) confusions[k] = (confusions[k] ?: 0) + v
        for ((k, v) in data.counters) counters[k] = (counters[k] ?: 0) + v
        for ((k, v) in data.periodAfter) periodAfter[k] = (periodAfter[k] ?: 0) + v
        rebuild()
    }

    // ---- letter mix-ups ----

    /** The user typed [typed] meaning [intended]; [weight] is 1 for an accepted autocorrection, 2 for a fix they chose themselves. */
    fun recordConfusion(typed: Char, intended: Char, weight: Int = 1) {
        if (index(typed, intended) < 0 || typed.lowercaseChar() == intended.lowercaseChar()) return
        val key = "${typed.lowercaseChar()}${intended.lowercaseChar()}"
        confusions[key] = ((confusions[key] ?: 0) + weight).coerceAtMost(MAX_COUNT)
        rebuild()
        save()
    }

    /** An autocorrection that implied this mix-up was undone. */
    fun forgetConfusion(typed: Char, intended: Char, weight: Int = 1) {
        val key = "${typed.lowercaseChar()}${intended.lowercaseChar()}"
        val now = confusions[key] ?: return
        if (now - weight <= 0) confusions.remove(key) else confusions[key] = now - weight
        rebuild()
        save()
    }

    fun confusionCount(typed: Char, intended: Char): Int = confusions["${typed.lowercaseChar()}${intended.lowercaseChar()}"] ?: 0

    /**
     * How much likelier a correction from [typed] to [intended] is for this person, as a score bonus (0 for no history).
     * A neighboring key they slip onto often gets up to about 0.75; a pair that is not neighbors needs a few more
     * sightings and gets less.
     */
    fun bonus(typed: Char, intended: Char): Float {
        val i = index(typed, intended)
        return if (i < 0) 0f else table[i]
    }

    private fun rebuild() {
        val t = FloatArray(26 * 26)
        for ((key, count) in confusions) {
            val i = index(key[0], key[1])
            if (i < 0) continue
            val neighbors = neighborKeys(key[0], key[1])
            t[i] = if (neighbors) minOf(0.75f, 0.15f * (count - 1).coerceAtLeast(0)) else minOf(0.5f, 0.12f * (count - 2).coerceAtLeast(0))
        }
        table = t
    }

    /** True if the two letter keys touch on the QWERTY layout (a distance of up to about one and a half keys). */
    private fun neighborKeys(a: Char, b: Char): Boolean {
        val d = KeyGeometry.distance(a, b) ?: return false
        return d <= 1.6f
    }

    // ---- counts of stray periods and run-together words ----

    fun count(name: String): Int = counters[name] ?: 0

    fun add(name: String, delta: Int = 1) {
        counters[name] = ((counters[name] ?: 0) + delta).coerceAtLeast(0)
        save()
    }

    /** A stray period followed [letter]. */
    fun noteStrayPeriodAfter(letter: Char) {
        val c = letter.lowercaseChar()
        if (c !in 'a'..'z') return
        periodAfter[c] = (periodAfter[c] ?: 0) + 1
        save()
    }

    /** True once the stray-period fix has been undone often enough, relative to how often it was applied, that it should pause. */
    fun periodFixPaused(): Boolean {
        val undone = count(PERIOD_UNDONE)
        return undone >= 3 && undone * 2 > count(PERIOD_FIXED)
    }

    // ---- what to show the user ----

    class Insights(
        val periodFixed: Int, val periodUndone: Int, val periodPaused: Boolean, val periodLetters: List<Char>,
        val splitFixed: Int, val splitUndone: Int, val mixUps: List<Triple<Char, Char, Int>>,
    )

    fun insights(): Insights = Insights(
        periodFixed = count(PERIOD_FIXED),
        periodUndone = count(PERIOD_UNDONE),
        periodPaused = periodFixPaused(),
        periodLetters = periodAfter.entries.sortedByDescending { it.value }.take(3).map { it.key },
        splitFixed = count(SPLIT_FIXED),
        splitUndone = count(SPLIT_UNDONE),
        mixUps = confusions.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(4)
            .map { Triple(it.key[0], it.key[1], it.value) },
    )

    /** Forgets everything learned. */
    fun clear() {
        confusions.clear()
        counters.clear()
        periodAfter.clear()
        rebuild()
        save()
    }

    private fun save() {
        val f = file ?: return
        val sb = StringBuilder()
        for ((k, v) in confusions) sb.append("c\t").append(k[0]).append('\t').append(k[1]).append('\t').append(v).append('\n')
        for ((k, v) in counters) sb.append("n\t").append(k).append('\t').append(v).append('\n')
        for ((k, v) in periodAfter) sb.append("p\t").append(k).append('\t').append(v).append('\n')
        val data = sb.toString()
        writer.execute {
            try {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(data)
                tmp.renameTo(f)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save typing habits", e)
            }
        }
    }

    companion object {
        private const val TAG = "FlexKeysHabits"
        private const val FILE_NAME = "user_habits.tsv"
        private const val MAX_COUNT = 40
        const val PERIOD_FIXED = "periodFixed"
        const val PERIOD_UNDONE = "periodUndone"
        const val SPLIT_FIXED = "splitFixed"
        const val SPLIT_UNDONE = "splitUndone"
        private val writer = Executors.newSingleThreadExecutor()
    }
}
