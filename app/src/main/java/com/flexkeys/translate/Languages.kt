package com.flexkeys.translate

/** Languages offered by the translator, with the codes MyMemory expects. */
object Languages {

    class Language(val code: String, val name: String)

    const val AUTO = "auto"

    val all: List<Language> = listOf(
        Language("en", "English"), Language("es", "Spanish"), Language("fr", "French"),
        Language("de", "German"), Language("it", "Italian"), Language("pt", "Portuguese"),
        Language("nl", "Dutch"), Language("ru", "Russian"), Language("zh-CN", "Chinese (Simplified)"),
        Language("zh-TW", "Chinese (Traditional)"), Language("ja", "Japanese"), Language("ko", "Korean"),
        Language("ar", "Arabic"), Language("hi", "Hindi"), Language("tr", "Turkish"),
        Language("pl", "Polish"), Language("uk", "Ukrainian"), Language("vi", "Vietnamese"),
        Language("th", "Thai"), Language("id", "Indonesian"), Language("sv", "Swedish"),
        Language("no", "Norwegian"), Language("da", "Danish"), Language("fi", "Finnish"),
        Language("el", "Greek"), Language("cs", "Czech"), Language("he", "Hebrew"),
        Language("ro", "Romanian"), Language("hu", "Hungarian"), Language("tl", "Filipino"),
    )

    /** Source languages also offer detection. */
    val sources: List<Language> = listOf(Language(AUTO, "Detect language")) + all

    fun nameOf(code: String): String = sources.firstOrNull { it.code == code }?.name ?: code
}
