package com.flexkeys.translate

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Html
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Translates text with the free MyMemory service (https://mymemory.translated.net). No key is
 * needed; an optional email address raises the daily free limit. Only the text being translated,
 * the language pair and that optional email are sent.
 */
object MyMemoryClient {

    private class QuotaException(message: String) : IOException(message)
    private class ApiException(message: String) : IOException(message)

    private const val HOST = "api.mymemory.translated.net"
    private const val MAX_CHARS = 2000
    private const val MAX_REQUESTS = 12

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Calls [onDone] on the main thread with the translation or an error message. */
    fun translate(text: String, from: String, to: String, email: String, onDone: (String?, String?) -> Unit) {
        pool.execute {
            var result: String? = null
            var error: String? = null
            try {
                result = run(text.take(MAX_CHARS), from, to, email.trim())
            } catch (e: QuotaException) {
                error = "The free daily limit is used up. Add your email in SakuriKey settings for a higher limit, or try again tomorrow."
            } catch (e: ApiException) {
                error = e.message
            } catch (e: IOException) {
                error = "Couldn't reach the translator. Check your connection."
            } catch (e: JSONException) {
                error = "Unexpected reply from the translator."
            }
            main.post { onDone(result, error) }
        }
    }

    private fun run(text: String, from: String, to: String, email: String): String {
        if (from == to) return text
        val paragraphs = TextChunker.paragraphs(text)
        if (paragraphs.sumOf { it.size } > MAX_REQUESTS) throw ApiException("That text is too long to translate at once.")
        return paragraphs.joinToString("\n") { chunks -> chunks.joinToString(" ") { translateChunk(it, from, to, email) } }
    }

    private fun translateChunk(chunk: String, from: String, to: String, email: String): String {
        val source = if (from == Languages.AUTO) "Autodetect" else from
        val b = Uri.Builder().scheme("https").authority(HOST).appendPath("get")
            .appendQueryParameter("q", chunk)
            .appendQueryParameter("langpair", "$source|$to")
        if (email.contains('@')) b.appendQueryParameter("de", email)
        val c = URL(b.build().toString()).openConnection() as HttpURLConnection
        val body = try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            val code = c.responseCode
            if (code == 429) throw QuotaException("Rate limited")
            if (code !in 200..299) throw ApiException("The translator returned error $code.")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val json = JSONObject(body)
        val status = json.opt("responseStatus")?.toString()?.toIntOrNull() ?: 200
        val translated = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        if (status == 429 || json.optBoolean("quotaFinished") || translated.startsWith("MYMEMORY WARNING")) {
            throw QuotaException("Quota")
        }
        if (status != 200) {
            val detail = json.optString("responseDetails").takeIf { it.isNotBlank() }
            throw ApiException(detail ?: "The translator couldn't translate that.")
        }
        // The service sometimes returns HTML entities such as &#39;.
        return if (translated.contains('&') && translated.contains(';')) {
            Html.fromHtml(translated, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            translated
        }
    }
}
