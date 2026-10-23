package com.flexkeys.ai

import com.flexkeys.theme.Prefs

/** Sends a rewrite request to whichever AI service the user picked. */
object AiClient {

    const val GEMINI = 0
    const val GROQ = 1

    val providerNames = listOf("Groq", "Gemini")

    fun hasKey(prefs: Prefs): Boolean = key(prefs).isNotBlank()

    private fun key(prefs: Prefs) = if (prefs.aiProvider == GEMINI) prefs.geminiKey else prefs.groqKey

    fun rewrite(prefs: Prefs, instruction: String, text: String, onDone: (String?, String?) -> Unit) {
        if (prefs.aiProvider == GEMINI) {
            GeminiClient.rewrite(prefs.geminiKey, prefs.geminiModel, instruction, text, onDone)
        } else {
            GroqClient.rewrite(prefs.groqKey, prefs.groqModel, instruction, text, onDone)
        }
    }
}
