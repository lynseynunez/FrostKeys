package com.flexkeys.layout

/** Which characters a letter key offers on long-press, and whether its corner shows a small symbol. */
data class PopupSettings(
    val accents: Boolean = true,
    val accentSets: Set<String> = setOf(AccentSets.WESTERN),
    val symbols: Boolean = true,
    val accentsFirst: Boolean = false,
    val cornerHints: Boolean = true,
) {
    /** Rebuilds every letter key's popup from these settings. Other keys are left alone. */
    fun apply(layout: LayoutDef): LayoutDef {
        if (this == DEFAULT) return layout
        return layout.copy(rows = layout.rows.map { row ->
            RowDef(row.keys.map { key ->
                val c = key.label.singleOrNull()
                if (key.type != KeyType.CHAR || c == null || !c.isLetter()) return@map key
                // Digits and symbols are the entries that are not letters; accents are the letters.
                val symbolPart = if (symbols) key.popup.filter { it.isNotEmpty() && !it[0].isLetter() } else emptyList()
                val accentPart = if (accents) AccentSets.accentsFor(c, accentSets) else emptyList()
                val popup = if (accentsFirst) accentPart + symbolPart else symbolPart + accentPart
                key.copy(popup = popup, hint = if (cornerHints && symbols) key.hint else null)
            })
        })
    }

    companion object {
        val DEFAULT = PopupSettings()
    }
}

/** Accented letters by language group. The first group is what every layout already offers. */
object AccentSets {
    const val WESTERN = "western"
    const val CENTRAL = "central"
    const val NORDIC = "nordic"
    const val TURKISH = "turkish"

    /** Ids and names, in the order shown in Settings. */
    val names: List<Pair<String, String>> = listOf(
        WESTERN to "Western Europe (é, ñ, ü, ç)",
        CENTRAL to "Central & Eastern Europe (ł, ř, ő)",
        NORDIC to "Nordic (å, æ, ø)",
        TURKISH to "Turkish (ı, ş, ğ)",
    )

    private val sets: Map<String, Map<Char, String>> = mapOf(
        WESTERN to mapOf(
            'a' to "àáâäæãåā", 'c' to "çćč", 'd' to "ď", 'e' to "èéêëēėę", 'g' to "ğ", 'i' to "îïíīįì",
            'l' to "ł", 'n' to "ñń", 'o' to "ôöòóœøōõ", 'r' to "ř", 's' to "ßśš", 't' to "ť",
            'u' to "ûüùúū", 'y' to "ÿý", 'z' to "žźż",
        ),
        CENTRAL to mapOf(
            'a' to "ąáä", 'c' to "ćč", 'd' to "ďđ", 'e' to "ęěé", 'i' to "í", 'l' to "łĺľ", 'n' to "ńň",
            'o' to "óöő", 'r' to "řŕ", 's' to "śšș", 't' to "ťț", 'u' to "ůűú", 'y' to "ý", 'z' to "źżž",
        ),
        NORDIC to mapOf('a' to "åäæ", 'o' to "øöœ", 'e' to "é"),
        TURKISH to mapOf('c' to "ç", 'g' to "ğ", 'i' to "ıİ", 's' to "ş", 'u' to "ü", 'o' to "ö"),
    )

    /** The accented forms of [c] for the chosen groups, in group order, without repeats. */
    fun accentsFor(c: Char, groups: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        for ((id, _) in names) {
            if (id !in groups) continue
            sets[id]?.get(c.lowercaseChar())?.forEach { out.add(it.toString()) }
        }
        return out.toList()
    }
}
