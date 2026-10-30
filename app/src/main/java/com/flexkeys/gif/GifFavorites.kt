package com.flexkeys.gif

import com.flexkeys.theme.Prefs
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** GIFs the user starred. Kept as a small list of links in the settings (so backups carry them); no picture files are stored. */
object GifFavorites {

    const val MAX = 80

    fun all(prefs: Prefs): List<GifItem> = parse(prefs.gifFavorites)

    fun contains(prefs: Prefs, id: String): Boolean = all(prefs).any { it.id == id }

    /** Stars [item], or un-stars it if it already is one. Returns true if it is now a favorite. */
    fun toggle(prefs: Prefs, item: GifItem): Boolean {
        val list = all(prefs).toMutableList()
        val existing = list.indexOfFirst { it.id == item.id }
        val nowFavorite = existing < 0
        if (existing >= 0) list.removeAt(existing) else list.add(0, item)
        prefs.gifFavorites = format(list.take(MAX))
        return nowFavorite
    }

    internal fun parse(json: String): List<GifItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val preview = o.optString("p")
                val send = o.optString("s")
                if (id.isEmpty() || preview.isEmpty() || send.isEmpty()) return@mapNotNull null
                GifItem(id, preview, send, o.optInt("w", 1), o.optInt("h", 1), o.optString("t").takeIf { it.isNotEmpty() })
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    internal fun format(items: List<GifItem>): String {
        val array = JSONArray()
        for (g in items) {
            array.put(JSONObject().put("id", g.id).put("p", g.previewUrl).put("s", g.sendUrl).put("w", g.width).put("h", g.height).put("t", g.title ?: ""))
        }
        return array.toString()
    }
}
