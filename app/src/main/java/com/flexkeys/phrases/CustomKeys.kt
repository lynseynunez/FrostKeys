package com.flexkeys.phrases

import com.flexkeys.layout.KeyType
import com.flexkeys.layout.LayoutDef
import com.flexkeys.layout.RowDef

/**
 * Extra characters the user adds to a key's long-press popup. Stored as one line per key,
 * "a:äå", where every character after the colon becomes a popup option.
 */
object CustomKeys {

    fun parse(text: String): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        for (line in text.lines()) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            if (key.isEmpty() || key.codePointCount(0, key.length) != 1) continue
            val chars = options(line.substring(colon + 1))
            if (chars.isNotEmpty()) result[key] = chars
        }
        return result
    }

    fun format(map: Map<String, List<String>>): String =
        map.entries.joinToString("\n") { (k, v) -> "$k:${v.joinToString("")}" }

    /** Splits typed text into one option per character, dropping spaces and repeats. */
    fun options(text: String): List<String> {
        val out = LinkedHashSet<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp)) continue
            out.add(String(Character.toChars(cp)))
        }
        return out.take(MAX_OPTIONS)
    }

    /** Adds the user's characters to the front of each matching key's popup. */
    fun apply(layout: LayoutDef, extras: Map<String, List<String>>): LayoutDef {
        if (extras.isEmpty()) return layout
        return layout.copy(rows = layout.rows.map { row ->
            RowDef(row.keys.map { key ->
                val more = if (key.type == KeyType.CHAR) extras[key.output.lowercase()] else null
                if (more == null) key else key.copy(popup = (more + key.popup).distinct())
            })
        })
    }

    const val MAX_OPTIONS = 12
}
