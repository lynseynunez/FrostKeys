package com.flexkeys.ai

/** The user's own rewrite styles, stored one per line as "name<TAB>instruction". */
object AiPresets {

    class Preset(val name: String, val instruction: String)

    const val MAX = 12

    fun parse(text: String): List<Preset> = text.lines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) return@mapNotNull null
        val name = line.substring(0, tab).trim()
        val instruction = line.substring(tab + 1).trim()
        if (name.isEmpty() || instruction.isEmpty()) null else Preset(name.take(20), instruction.take(300))
    }.take(MAX)

    fun format(list: List<Preset>): String = list.joinToString("\n") { "${it.name}\t${it.instruction}" }

    /** Adds a preset, replacing one with the same name (ignoring case). Returns null if it is not valid. */
    fun add(text: String, name: String, instruction: String): String? {
        val n = name.trim().replace('\t', ' ').replace('\n', ' ')
        val i = instruction.trim().replace('\t', ' ').replace('\n', ' ')
        if (n.isEmpty() || i.isEmpty()) return null
        val rest = parse(text).filter { !it.name.equals(n, ignoreCase = true) }
        return format(rest + Preset(n.take(20), i.take(300)))
    }

    fun remove(text: String, name: String): String = format(parse(text).filter { it.name != name })
}
