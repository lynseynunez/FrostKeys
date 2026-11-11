package com.flexkeys.suggest

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * A short, private record of what autocorrect did, kept on the phone only (files/autocorrect_log.tsv): the last changes
 * it made and whether you undid them, and words it did not recognize and left alone. It feeds the review screen
 * (Settings > Typing > Autocorrect review), the self-tuning, and the report you can copy. Only the words themselves
 * are kept, never the sentence around them, and nothing is recorded in password fields.
 */
class AutocorrectLog(private val file: File?) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    enum class Kind { WORD, SPLIT, PERIOD }

    class Entry(val time: Long, val kind: Kind, val typed: String, val result: String, var undone: Boolean = false)

    private val entries = ArrayList<Entry>()
    /** Words the dictionary did not know that were kept as typed, with how often. */
    private val unknown = LinkedHashMap<String, Int>()

    // ---- reading the stored file ----

    class Stored(val entries: List<Entry>, val unknown: Map<String, Int>)

    fun loadBlocking(): Stored {
        val e = ArrayList<Entry>()
        val u = LinkedHashMap<String, Int>()
        val f = file
        if (f != null && f.isFile) {
            try {
                f.forEachLine { line ->
                    val p = line.split('\t')
                    when {
                        p.size == 6 && p[0] == "e" -> {
                            val kind = Kind.values().firstOrNull { it.name == p[2] }
                            val time = p[1].toLongOrNull()
                            if (kind != null && time != null) e.add(Entry(time, kind, p[3], p[4], p[5] == "1"))
                        }
                        p.size == 3 && p[0] == "u" -> p[2].toIntOrNull()?.let { u[p[1]] = it }
                    }
                }
            } catch (ex: IOException) {
                Log.w(TAG, "Could not read the autocorrect log", ex)
            }
        }
        return Stored(e, u)
    }

    fun install(data: Stored) {
        // Anything logged before loading finished goes after what was stored.
        val newer = ArrayList(entries)
        entries.clear()
        entries.addAll(data.entries)
        entries.addAll(newer)
        trim()
        for ((w, n) in data.unknown) unknown[w] = (unknown[w] ?: 0) + n
        trimUnknown()
    }

    // ---- recording ----

    fun recordFix(kind: Kind, typed: String, result: String, now: Long = System.currentTimeMillis()) {
        entries.add(Entry(now, kind, typed.take(MAX_WORD), result.take(MAX_WORD)))
        trim()
        save()
    }

    /** The user undid a change: marks the newest matching one. */
    fun markUndone(typed: String, result: String) {
        val e = entries.lastOrNull { !it.undone && it.typed == typed.take(MAX_WORD) && it.result == result.take(MAX_WORD) } ?: return
        e.undone = true
        save()
    }

    /** Marks every recent change of [typed] as handled, after the user said it was wrong. */
    fun markWrong(typed: String) {
        var changed = false
        for (e in entries) if (!e.undone && e.typed.equals(typed, ignoreCase = true)) { e.undone = true; changed = true }
        if (changed) save()
    }

    /** Takes one entry off the list (the user said it was fine). */
    fun dismiss(entry: Entry) {
        if (entries.remove(entry)) save()
    }

    /** A word the dictionary did not know was kept as typed. */
    fun noteUnknown(word: String) {
        val key = word.lowercase().take(MAX_WORD)
        if (key.length < 3) return
        unknown[key] = (unknown[key] ?: 0) + 1
        trimUnknown()
        save()
    }

    fun forgetUnknown(word: String) {
        if (unknown.remove(word.lowercase()) != null) save()
    }

    fun clear() {
        entries.clear()
        unknown.clear()
        save()
    }

    // ---- reading ----

    /** Newest first. */
    fun recent(limit: Int): List<Entry> = entries.asReversed().take(limit)

    /** The words left alone that came up most, best first. */
    fun unknownWords(limit: Int): List<Pair<String, Int>> =
        unknown.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    val fixedCount: Int get() = entries.size
    val undoneCount: Int get() = entries.count { it.undone }

    /** The share of the last [window] changes that were undone, or null if there are too few to say. */
    fun undoRate(window: Int = RATE_WINDOW): Double? {
        val last = entries.takeLast(window)
        if (last.size < MIN_FOR_RATE) return null
        return last.count { it.undone }.toDouble() / last.size
    }

    /**
     * How much more careful autocorrect should be right now (added to its confidence threshold). Zero while undos are
     * rare; up to 1.5 when many recent changes were undone, which makes it change fewer words until things settle.
     */
    fun carefulness(): Double {
        val rate = undoRate() ?: return 0.0
        return ((rate - 0.10) * 6.0).coerceIn(0.0, MAX_CAREFUL)
    }

    /** A plain-text summary you can copy and share, to see what autocorrect has been doing. */
    fun report(): String {
        val sb = StringBuilder()
        sb.append("Autocorrect report\n")
        val rate = undoRate()
        sb.append("Changes made: ").append(fixedCount).append(", undone by you: ").append(undoneCount)
        if (rate != null) sb.append(" (last ${minOf(entries.size, RATE_WINDOW)}: ${(rate * 100).toInt()}% undone)")
        sb.append('\n')
        val care = carefulness()
        if (care > 0.0) sb.append("Self-tuning: being more careful by %.1f because many recent changes were undone\n".format(care))
        sb.append("\nRecent changes (typed -> became):\n")
        for (e in recent(30)) sb.append("  ").append(e.typed).append(" -> ").append(e.result).append(if (e.undone) "   [undone]" else "").append('\n')
        val u = unknownWords(15)
        if (u.isNotEmpty()) {
            sb.append("\nWords it did not recognize and left alone:\n")
            for ((w, n) in u) sb.append("  ").append(w).append(" (").append(n).append("x)\n")
        }
        return sb.toString()
    }

    // ---- housekeeping ----

    private fun trim() {
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    private fun trimUnknown() {
        while (unknown.size > MAX_UNKNOWN) {
            val lowest = unknown.entries.minByOrNull { it.value } ?: break
            unknown.remove(lowest.key)
        }
    }

    private fun save() {
        val f = file ?: return
        val sb = StringBuilder()
        for (e in entries) sb.append("e\t").append(e.time).append('\t').append(e.kind.name).append('\t').append(clean(e.typed)).append('\t')
            .append(clean(e.result)).append('\t').append(if (e.undone) "1" else "0").append('\n')
        for ((w, n) in unknown) sb.append("u\t").append(clean(w)).append('\t').append(n).append('\n')
        val data = sb.toString()
        writer.execute {
            try {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(data)
                tmp.renameTo(f)
            } catch (ex: IOException) {
                Log.w(TAG, "Could not save the autocorrect log", ex)
            }
        }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    companion object {
        private const val TAG = "FlexKeysAcLog"
        private const val FILE_NAME = "autocorrect_log.tsv"
        private const val MAX_ENTRIES = 200
        private const val MAX_UNKNOWN = 150
        private const val MAX_WORD = 40
        private const val RATE_WINDOW = 40
        private const val MIN_FOR_RATE = 12
        private const val MAX_CAREFUL = 1.5
        private val writer = Executors.newSingleThreadExecutor()
    }
}
