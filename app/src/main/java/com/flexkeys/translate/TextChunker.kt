package com.flexkeys.translate

/**
 * MyMemory accepts at most 500 bytes of text per request. This splits text into pieces that fit,
 * preferring sentence boundaries. Line breaks are kept by returning one list per paragraph.
 */
object TextChunker {

    const val MAX_BYTES = 450
    private const val HARD_SPLIT_CHARS = 100 // 100 chars is at most 400 bytes in UTF-8
    private val sentenceEnd = Regex("(?<=[.!?。！？])\\s+")

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    fun paragraphs(text: String): List<List<String>> = text.split('\n').map { chunk(it.trim()) }

    fun chunk(paragraph: String): List<String> {
        if (paragraph.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) {
                out.add(cur.toString())
                cur.clear()
            }
        }
        for (sentence in paragraph.split(sentenceEnd)) {
            // A single very long sentence is cut into fixed-size pieces.
            val pieces = if (bytes(sentence) > MAX_BYTES) sentence.chunked(HARD_SPLIT_CHARS) else listOf(sentence)
            for (piece in pieces) {
                if (cur.isNotEmpty() && bytes(cur.toString()) + 1 + bytes(piece) > MAX_BYTES) flush()
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(piece)
            }
        }
        flush()
        return out
    }
}
