package com.flexkeys

import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.suggest.UserDictionary
import com.flexkeys.suggest.WordDictionary
import java.io.File

/** Builds real engines from the bundled word lists, without Android. */
object TestData {

    private val assetDir: File by lazy {
        listOf("src/main/assets", "app/src/main/assets", "../app/src/main/assets")
            .map { File(it) }
            .firstOrNull { File(it, "words.txt").isFile }
            ?: error("Could not find the assets folder from ${File(".").absolutePath}")
    }

    val dictionary: WordDictionary by lazy {
        val lines = File(assetDir, "words.txt").readLines() + File(assetDir, "extra_words.txt").readLines()
        WordDictionary.parse(lines.asSequence())
    }

    val typos: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        File(assetDir, "typos.tsv").forEachLine { line ->
            if (line.isNotEmpty() && line[0] != '#') {
                val tab = line.indexOf('\t')
                if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
            }
        }
        map
    }

    val bigrams: com.flexkeys.suggest.BigramTable by lazy {
        File(assetDir, "bigrams.txt").useLines { com.flexkeys.suggest.BigramTable.parse(it) }
    }

    /** A fresh engine with nothing learned. */
    fun engine(): SuggestionEngine {
        val file = File.createTempFile("user_words", ".tsv").apply { delete(); deleteOnExit() }
        val user = UserDictionary(file)
        user.install(emptyMap<String, Int>() to emptyMap())
        return SuggestionEngine(user).also { it.setData(dictionary, typos, bigrams) }
    }
}
