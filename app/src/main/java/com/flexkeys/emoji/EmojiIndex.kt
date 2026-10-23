package com.flexkeys.emoji

import android.content.Context
import android.graphics.Paint
import android.util.Log
import com.flexkeys.theme.Prefs
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Emoji keywords from the bundled assets/emoji.tsv (generated from GitHub's open gemoji data),
 * used for emoji search, emoji suggestions while typing and skin tones. Works fully offline.
 */
object EmojiIndex {

    private class Entry(val emoji: String, val skinTones: Boolean, val keywords: List<String>)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var skinCapable: Set<String> = emptySet()
    private var loading = false
    private val glyphPaint = Paint()

    private val SKIN_TONES = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)

    /** Starts loading the index in the background; safe to call repeatedly. */
    @Synchronized
    fun load(context: Context) {
        if (loading) return
        loading = true
        val app = context.applicationContext
        Executors.newSingleThreadExecutor().execute {
            val list = ArrayList<Entry>(2000)
            try {
                app.assets.open("emoji.tsv").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty() || line[0] == '#') continue
                        val p = line.split('\t')
                        if (p.size < 3) continue
                        list.add(Entry(p[0], p[1] == "1", p[2].split(' ').filter { it.isNotEmpty() }))
                    }
                }
            } catch (e: IOException) {
                Log.w("FlexKeysEmoji", "Emoji index missing", e)
            }
            val supported = list.filter { glyphPaint.hasGlyph(it.emoji) }
            skinCapable = supported.filter { it.skinTones }.map { stripVariation(it.emoji) }.toHashSet()
            entries = supported
        }
    }

    /** Emoji whose keywords start with every word of [query], best matches first. */
    fun search(query: String, limit: Int = 60): List<String> {
        val words = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            var score = 0
            for (w in words) {
                val i = e.keywords.indexOfFirst { it.startsWith(w) }
                if (i < 0) return@mapNotNull null
                score += i + (if (e.keywords[i] == w) 0 else 3)
            }
            e.emoji to score
        }.sortedBy { it.second }.take(limit).map { it.first }
    }

    /** Emoji that match a whole typed word exactly ("pizza" -> 🍕), for the suggestion strip. */
    fun forWord(word: String, limit: Int = 1): List<String> {
        val w = word.lowercase(Locale.ROOT).trim('\'', '’')
        if (w.length < 3) return emptyList()
        val singular = if (w.length > 3 && w.endsWith('s')) w.dropLast(1) else w
        return entries.mapNotNull { e ->
            val i = e.keywords.indexOfFirst { it == w || it == singular }
            // Only strong matches: the word must be among the first few keywords.
            if (i in 0..2) e.emoji to i else null
        }.sortedBy { it.second }.take(limit).map { it.first }
    }

    fun supportsSkinTone(emoji: String): Boolean = stripVariation(emoji) in skinCapable

    /** The emoji itself followed by its five skin tone variants. */
    fun skinVariants(emoji: String): List<String> =
        listOf(emoji) + SKIN_TONES.map { withTone(emoji, it) }.filter { glyphPaint.hasGlyph(it) }

    private fun withTone(emoji: String, tone: Int): String {
        val base = stripVariation(emoji)
        val first = base.codePointAt(0)
        val rest = base.substring(Character.charCount(first))
        return String(Character.toChars(first)) + String(Character.toChars(tone)) + rest
    }

    /** Removes the U+FE0F emoji presentation selector right after the first code point. */
    private fun stripVariation(emoji: String): String {
        if (emoji.isEmpty()) return emoji
        val first = Character.charCount(emoji.codePointAt(0))
        return if (emoji.length > first && emoji[first] == '️') emoji.removeRange(first, first + 1) else emoji
    }

    // ---- recents and chosen skin tones (stored in prefs) ----------------------------------------

    private const val MAX_RECENTS = 32

    fun recents(prefs: Prefs): List<String> = prefs.recentEmoji.split(' ').filter { it.isNotEmpty() }

    fun addRecent(prefs: Prefs, emoji: String) {
        val list = recents(prefs).toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        prefs.recentEmoji = list.take(MAX_RECENTS).joinToString(" ")
    }

    /** Remembered skin tone choices: base emoji -> chosen variant. */
    fun chosenTones(prefs: Prefs): Map<String, String> =
        prefs.skinTones.split(' ').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to pair.substring(i + 1)
        }.toMap()

    fun rememberTone(prefs: Prefs, base: String, variant: String) {
        val map = chosenTones(prefs).toMutableMap()
        if (variant == base) map.remove(base) else map[base] = variant
        prefs.skinTones = map.entries.joinToString(" ") { "${it.key}=${it.value}" }
    }
}
