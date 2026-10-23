package com.flexkeys.phrases

import android.content.Context
import android.util.Log
import com.flexkeys.settings.BackupManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Saved phrases, kept in app-internal storage (files/snippets.json). A phrase with a shortcut
 * expands when the shortcut is typed and followed by a space; every phrase can also be inserted
 * from the phrases panel. Main thread only.
 */
class SnippetStore private constructor(context: Context) {

    data class Snippet(val shortcut: String, val text: String)

    private val file = File(context.filesDir, FILE_NAME)
    private val items = ArrayList<Snippet>()
    private var byShortcut: Map<String, String> = emptyMap()
    private val listeners = LinkedHashSet<() -> Unit>()

    init {
        load()
    }

    fun all(): List<Snippet> = items.toList()

    /** The expansion for a typed [shortcut] (ignoring case), or null. */
    fun expansion(shortcut: String): String? = byShortcut[shortcut.lowercase()]

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Adds or replaces a phrase. A shortcut can't contain spaces. Returns false if it was rejected. */
    fun put(shortcut: String, text: String): Boolean {
        val s = shortcut.trim()
        val body = text.trim('\n').take(MAX_LENGTH)
        if (body.isBlank() || s.any { it.isWhitespace() } || s.length > MAX_SHORTCUT) return false
        if (s.isNotEmpty()) items.removeAll { it.shortcut.equals(s, ignoreCase = true) }
        items.add(Snippet(s, body))
        changed()
        return true
    }

    fun remove(snippet: Snippet) {
        if (items.remove(snippet)) changed()
    }

    private fun changed() {
        byShortcut = items.filter { it.shortcut.isNotEmpty() }.associate { it.shortcut.lowercase() to it.text }
        save()
        listeners.toList().forEach { it() }
    }

    fun exportJson(): String = toJson().toString()

    fun importJson(data: String) {
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                put(o.optString("shortcut"), o.getString("text"))
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring damaged phrases in backup", e)
        }
    }

    private fun toJson(): JSONArray {
        val json = JSONArray()
        for (item in items) json.put(JSONObject().put("shortcut", item.shortcut).put("text", item.text))
        return json
    }

    private fun load() {
        if (file.isFile) {
            try {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    items.add(Snippet(o.optString("shortcut"), o.getString("text")))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read phrases", e)
            } catch (e: JSONException) {
                Log.w(TAG, "Phrases corrupted; starting over", e)
                items.clear()
            }
        }
        byShortcut = items.filter { it.shortcut.isNotEmpty() }.associate { it.shortcut.lowercase() to it.text }
    }

    private fun save() {
        val data = toJson().toString()
        writer.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(data)
                tmp.renameTo(file)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save phrases", e)
            }
        }
    }

    /** Includes the phrases in backups. */
    object BackupSection : BackupManager.Section {
        override val id = "phrases"
        override fun export(context: Context) = get(context).exportJson()
        override fun import(context: Context, data: String) = get(context).importJson(data)
    }

    companion object {
        private const val TAG = "FlexKeysPhrases"
        private const val FILE_NAME = "snippets.json"
        const val MAX_SHORTCUT = 24
        const val MAX_LENGTH = 2_000
        private val writer = Executors.newSingleThreadExecutor()

        @Volatile private var instance: SnippetStore? = null

        fun get(context: Context): SnippetStore =
            instance ?: synchronized(this) {
                instance ?: SnippetStore(context.applicationContext).also { instance = it }
            }
    }
}
