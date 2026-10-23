package com.flexkeys.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rewrites text with Groq's free API (console.groq.com), using the user's own key. It is fast and
 * rarely overloaded. Only the text being rewritten and the instruction are sent.
 */
object GroqClient {

    private class ApiException(message: String, val retry: Boolean = false) : IOException(message)

    private const val MAX_CHARS = 4000
    private const val TIMEOUT_MS = 50_000L
    const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    private const val FALLBACK_MODEL = "llama-3.1-8b-instant"

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Calls [onDone] on the main thread, exactly once, with the rewritten text or an error message. */
    fun rewrite(key: String, model: String, instruction: String, text: String, onDone: (String?, String?) -> Unit) {
        val finished = AtomicBoolean(false)
        fun finish(result: String?, error: String?) {
            if (finished.compareAndSet(false, true)) main.post { onDone(result, error) }
        }
        main.postDelayed({ finish(null, "Groq took too long to answer. Try again.") }, TIMEOUT_MS)
        pool.execute {
            try {
                var last: ApiException? = null
                for (m in listOf(model.trim().ifEmpty { DEFAULT_MODEL }, FALLBACK_MODEL).distinct()) {
                    try {
                        finish(call(key.trim(), m, instruction, text.take(MAX_CHARS)), null)
                        return@execute
                    } catch (e: ApiException) {
                        if (!e.retry) throw e
                        last = e
                    }
                }
                throw last ?: ApiException("Groq is busy. Try again in a moment.")
            } catch (e: ApiException) {
                finish(null, e.message)
            } catch (e: IOException) {
                finish(null, "Couldn't reach Groq. Check your connection.")
            } catch (e: JSONException) {
                finish(null, "Unexpected reply from Groq.")
            } catch (e: Exception) {
                finish(null, "Couldn't send that request (${e.javaClass.simpleName}). Check the key and model name in settings.")
            }
        }
    }

    private fun call(key: String, model: String, instruction: String, text: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("max_tokens", 1500)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", GeminiClient.SYSTEM))
                .put(JSONObject().put("role", "user").put("content", "Instruction: $instruction\n\nText:\n$text")))
            .toString()
        val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 25_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val reply = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(code, reply), retry = code == 429 || code >= 500)
            val out = JSONObject(reply).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content").orEmpty().trim()
            if (out.isEmpty()) throw ApiException("Groq returned nothing. Try another style.")
            return out
        } finally {
            c.disconnect()
        }
    }

    private fun errorMessage(code: Int, reply: String): String {
        val detail = try {
            JSONObject(reply).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: JSONException) {
            ""
        }
        return when {
            code == 401 -> "Groq didn't accept the key. Check it in SakuriKey settings."
            code == 404 || (code == 400 && detail.contains("model", ignoreCase = true)) ->
                "Groq doesn't know that model. Check the model name in SakuriKey settings."
            code == 429 -> "Groq's free limit was reached. Try again in a minute."
            detail.isNotEmpty() -> detail.take(120)
            else -> "Groq returned error $code."
        }
    }
}
