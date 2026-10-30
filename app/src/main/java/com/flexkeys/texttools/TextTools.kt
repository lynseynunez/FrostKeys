package com.flexkeys.texttools

import java.util.Locale

/** Quick fixes to a block of text: case changes, tidying and turning lines into lists. All pure functions. */
object TextTools {

    enum class Tool(val label: String) {
        UPPER("UPPERCASE"), LOWER("lowercase"), TITLE("Title Case"), SENTENCE("Sentence case"),
        TIDY("Tidy spaces"), BULLETS("Bullets"), UNBULLET("No bullets"), COMMAS("Lines to commas"),
        LINES("Commas to lines"), SORT("Sort lines"), NUMBER("Number lines"), TRIM_LINES("Remove empty lines"),
    }

    fun apply(tool: Tool, text: String): String = when (tool) {
        Tool.UPPER -> text.uppercase(Locale.getDefault())
        Tool.LOWER -> text.lowercase(Locale.getDefault())
        Tool.TITLE -> titleCase(text)
        Tool.SENTENCE -> sentenceCase(text)
        Tool.TIDY -> tidy(text)
        Tool.BULLETS -> mapLines(text) { if (it.isBlank()) it else "• ${it.trim()}" }
        Tool.UNBULLET -> mapLines(text) { it.replace(Regex("^\\s*(?:[•\\-*]|\\d+[.)])\\s+"), "") }
        Tool.COMMAS -> text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        Tool.LINES -> text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        Tool.SORT -> text.lines().sortedBy { it.lowercase(Locale.getDefault()) }.joinToString("\n")
        Tool.NUMBER -> {
            var n = 0
            mapLines(text) { if (it.isBlank()) it else "${++n}. ${it.trim()}" }
        }
        Tool.TRIM_LINES -> text.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun mapLines(text: String, f: (String) -> String) = text.lines().joinToString("\n") { f(it) }

    /** Capitalizes each word, leaving the small joining words lowercase unless they start the text. */
    private fun titleCase(text: String): String {
        val small = setOf("a", "an", "the", "and", "but", "or", "for", "nor", "on", "at", "to", "by", "of", "in", "as")
        var first = true
        return Regex("\\p{L}[\\p{L}'’]*").replace(text) { m ->
            val w = m.value.lowercase(Locale.getDefault())
            val out = if (!first && w in small) w else w.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            first = false
            out
        }
    }

    /** Lowercases everything, then capitalizes the first letter of each sentence and the word "I". */
    private fun sentenceCase(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        val sb = StringBuilder(lower.length)
        var capitalizeNext = true
        for (c in lower) {
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.titlecaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') capitalizeNext = true
                else if (c.isLetterOrDigit()) capitalizeNext = false
            }
        }
        return sb.toString().replace(Regex("\\bi\\b"), "I").replace(Regex("\\bi(?=['’])"), "I")
    }

    /** Collapses runs of spaces and tabs, trims each line and the whole text, and limits blank lines to one. */
    private fun tidy(text: String): String =
        text.lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
            .replace(Regex("\\n{3,}"), "\n\n").trim()
}
