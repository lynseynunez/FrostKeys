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

    /** Moves [action] to where [target] is, pinning it if it wasn't (a target not in the row means the end). */
    fun move(items: List<Action>, action: Action, target: Action): List<Action> {
        if (action == target) return items
        val rest = items - action
        val at = rest.indexOf(target).let { if (it < 0) rest.size else it }
        // Dragging right moves past the target, dragging left lands before it.
        val from = items.indexOf(action)
        val to = items.indexOf(target)
        val index = if (from in 0 until to) at + 1 else at
        return rest.toMutableList().apply { add(index.coerceAtMost(size), action) }
    }

    /** Shows [action] in the top row if it isn't there, otherwise removes it (keeping at least one). */
    fun toggle(items: List<Action>, action: Action): List<Action> = when {
        action in items -> if (items.size > 1) items - action else items
        else -> items + action
    }
}
