package com.flexkeys.gif

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** One GIF from Klipy. */
class GifItem(
    val id: String,
    /** Small animated file shown in the grid. */
    val previewUrl: String,
    /** Full size GIF that is sent to the app. */
    val sendUrl: String,
    val width: Int,
    val height: Int,
    val title: String?,
)

/**
 * Minimal client for Klipy's GIF API (https://klipy.com/api-overview). The user's own API key is
 * stored on the device and only ever sent to api.klipy.com, and only when GIF search is used.
 */
object KlipyClient {

    class Page(val items: List<GifItem>, val hasMore: Boolean)

    const val PER_PAGE = 24
    private const val HOST = "api.klipy.com"

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Loads trending GIFs (blank [query]) or search results. Calls [onDone] on the main thread with
     * either a page or an error message.
     */
    fun fetch(
        context: Context,
        apiKey: String,
        customerId: String,
        query: String,
        page: Int,
        onDone: (Page?, String?) -> Unit,
    ) {
        val locale = context.resources.configuration.locales[0]?.country?.lowercase().takeUnless { it.isNullOrEmpty() } ?: "us"
        pool.execute {
            var page0: Page? = null
            var error: String? = null
            try {
                page0 = load(apiKey, customerId, locale, query, page)
            } catch (e: HttpError) {
                error = when (e.code) {
                    401, 403 -> "Klipy rejected the API key. Check it in SakuriKey settings."
                    429 -> "Klipy rate limit reached. Try again in a moment."
                    else -> "Klipy error ${e.code}"
                }
            } catch (e: IOException) {
                error = "Couldn't reach Klipy. Check your connection."
            } catch (e: JSONException) {
                error = "Unexpected reply from Klipy."
            }
            main.post { onDone(page0, error) }
        }
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private fun load(apiKey: String, customerId: String, locale: String, query: String, page: Int): Page {
        if (query.isBlank()) {
            try {
                return parse(get(url(apiKey, "trending", null, customerId, locale, page)))
            } catch (e: HttpError) {
                // If trending isn't available for this key, fall back to a search for it.
                if (e.code != 404) throw e
                return parse(get(url(apiKey, "search", "trending", customerId, locale, page)))
            }
        }
        return parse(get(url(apiKey, "search", query.trim(), customerId, locale, page)))
    }

    private fun url(apiKey: String, endpoint: String, query: String?, customerId: String, locale: String, page: Int): URL {
        val b = Uri.Builder().scheme("https").authority(HOST)
            .appendPath("api").appendPath("v1").appendPath(apiKey).appendPath("gifs").appendPath(endpoint)
        if (query != null) b.appendQueryParameter("q", query)
        b.appendQueryParameter("page", page.toString())
            .appendQueryParameter("per_page", PER_PAGE.toString())
            .appendQueryParameter("customer_id", customerId)
            .appendQueryParameter("locale", locale)
            .appendQueryParameter("content_filter", "medium")
            .appendQueryParameter("format_filter", "webp,gif")
        return URL(b.build().toString())
    }

    private fun get(url: URL): String {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(body: String): Page {
        val root = JSONObject(body)
        if (!root.optBoolean("result", true)) return Page(emptyList(), false)
        val array = root.optJSONObject("data")?.optJSONArray("data") ?: return Page(emptyList(), false)
        val items = ArrayList<GifItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val file = o.optJSONObject("file") ?: continue
            val hd = file.optJSONObject("hd")
            val sm = file.optJSONObject("sm")
            fun url(group: JSONObject?, format: String): String? =
                group?.optJSONObject(format)?.optString("url")?.takeIf { it.startsWith("https://") }
            val preview = url(sm, "webp") ?: url(sm, "gif") ?: url(hd, "webp") ?: url(hd, "gif") ?: continue
            val send = url(hd, "gif") ?: url(sm, "gif") ?: continue
            val dims = hd?.optJSONObject("gif") ?: sm?.optJSONObject("gif")
            val w = o.optInt("width", 0).takeIf { it > 0 } ?: dims?.optInt("width", 0)?.takeIf { it > 0 } ?: 200
            val h = o.optInt("height", 0).takeIf { it > 0 } ?: dims?.optInt("height", 0)?.takeIf { it > 0 } ?: 200
            items.add(GifItem(o.opt("id")?.toString() ?: send, preview, send, w, h, o.optString("title").takeIf { it.isNotEmpty() }))
        }
        return Page(items, items.size >= PER_PAGE)
    }
}
