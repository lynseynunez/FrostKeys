package com.flexkeys.suggest

import com.flexkeys.suggest.SuggestionStripView.Action

/** Which actions sit in the top row, and in what order, stored as comma-separated names. */
object ToolbarItems {

    val defaults = listOf(Action.CLIPBOARD, Action.EMOJI, Action.GIF, Action.TRANSLATE, Action.MIC, Action.SETTINGS)

    /** Unknown names are skipped, repeats dropped; an empty result falls back to [defaults]. */
    fun parse(text: String): List<Action> {
        val list = text.split(',').mapNotNull { name -> Action.values().firstOrNull { it.name == name.trim() } }.distinct()
        return list.ifEmpty { defaults }
    }

    fun format(items: List<Action>): String = items.joinToString(",") { it.name }

    /** Shows [action] in the top row if it isn't there, otherwise removes it (keeping at least one). */
    fun toggle(items: List<Action>, action: Action): List<Action> = when {
        action in items -> if (items.size > 1) items - action else items
        else -> items + action
    }
}
