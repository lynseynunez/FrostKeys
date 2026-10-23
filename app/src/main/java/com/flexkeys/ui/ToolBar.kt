package com.flexkeys.ui

/**
 * A bar that replaces the suggestion strip while open and takes over typing from the keys
 * (the translator, fancy text, AI rewrite). The keyboard view feeds it characters through [setQuery].
 */
interface ToolBar {
    val query: String
    fun setQuery(q: String)
    /** The return key was pressed. */
    fun onEnter()
    fun restyle()
    fun reset()
}

/** Text a tool works on, and where its result goes. */
class ToolSource(val text: String, val isSelection: Boolean)

interface ToolListener {
    /** The text the tool should start from: the selection, or for [wholeField] the whole field. */
    fun toolSource(wholeField: Boolean): ToolSource

    /** Types [text] at the cursor, replacing any selection. */
    fun onToolInsert(text: String)

    /** Replaces the selection (or, if [wholeField], the entire field) with [text]. */
    fun onToolReplace(text: String, wholeField: Boolean)
}
