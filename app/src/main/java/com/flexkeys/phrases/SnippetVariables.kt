package com.flexkeys.phrases

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Placeholders a saved phrase can contain, filled in when it is inserted:
 * {date} {time} {day} {month} {year} {clipboard}.
 */
object SnippetVariables {

    fun expand(text: String, now: Date = Date(), clipboard: String = "", locale: Locale = Locale.getDefault()): String {
        if (!text.contains('{')) return text
        val cal = Calendar.getInstance(locale).apply { time = now }
        val values = mapOf(
            "date" to DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(now),
            "time" to DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now),
            "day" to cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale).orEmpty(),
            "month" to cal.getDisplayName(Calendar.MONTH, Calendar.LONG, locale).orEmpty(),
            "year" to cal.get(Calendar.YEAR).toString(),
            "clipboard" to clipboard,
        )
        return Regex("\\{(date|time|day|month|year|clipboard)\\}", RegexOption.IGNORE_CASE)
            .replace(text) { values.getValue(it.groupValues[1].lowercase(Locale.ROOT)) }
    }
}
