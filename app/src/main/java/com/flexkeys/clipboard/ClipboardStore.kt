package com.flexkeys.clipboard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Clipboard history kept in app-internal storage (files/clipboard.json). Pinned items are
 * kept until deleted; unpinned ones are capped at [MAX_UNPINNED]. Main thread only.
 */
class ClipboardStore private constructor(context: Context) {

    data class Item(val id: Long, val text: String, val time: Long, val pinned: Boolean)

    private val file = File(context.filesDir, FILE_NAME)
    private val items = ArrayList<Item>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private var nextId = 1L

    init {
        load()
    }

    /** Pinned items first, then newest first. */
    fun items(): List<Item> = items.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun add(text: String) {
        val clean = text.take(MAX_LENGTH)
        if (clean.isBlank()) return
        val existing = items.firstOrNull { it.text == clean }
        if (existing != null) items.remove(existing)
        items.add(Item(nextId++, clean, System.currentTimeMillis(), existing?.pinned ?: false))
        val unpinned = items.filter { !it.pinned }.sortedByDescending { it.time }
        if (unpinned.size > MAX_UNPINNED) items.removeAll(unpinned.drop(MAX_UNPINNED).toSet())
        changed()
    }

    fun togglePin(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(pinned = !items[i].pinned)
        changed()
    }

    fun delete(id: Long) {
        if (items.removeAll { it.id == id }) changed()
    }

    fun clearUnpinned() {
        if (items.removeAll { !it.pinned }) changed()
    }

    fun clearAll() {
        items.clear()
        changed()
    }

    private fun changed() {
        save()
        listeners.toList().forEach { it() }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                items.add(Item(nextId++, o.getString("text"), o.getLong("time"), o.optBoolean("pinned")))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read clipboard history", e)
        } catch (e: JSONException) {
            Log.w(TAG, "Clipboard history corrupted; starting over", e)
            items.clear()
        }
    }

    private fun save() {
        val json = JSONArray()
        for (item in items) {
            json.put(JSONObject().put("text", item.text).put("time", item.time).put("pinned", item.pinned))
        }
        val data = json.toString()
        writer.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(data)
                tmp.renameTo(file)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save clipboard history", e)
            }
        }
    }

    companion object {
        private const val TAG = "FlexKeysClipboard"
        private const val FILE_NAME = "clipboard.json"
        private const val MAX_UNPINNED = 30
        private const val MAX_LENGTH = 10_000
        private val writer = Executors.newSingleThreadExecutor()

        @Volatile private var instance: ClipboardStore? = null

        fun get(context: Context): ClipboardStore =
            instance ?: synchronized(this) {
                instance ?: ClipboardStore(context.applicationContext).also { instance = it }
            }
    }
}
