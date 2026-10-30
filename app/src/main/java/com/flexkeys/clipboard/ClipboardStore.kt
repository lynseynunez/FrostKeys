package com.flexkeys.clipboard

import android.content.Context
import android.util.Log
import com.flexkeys.theme.Prefs
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

    private val prefs = Prefs.get(context)
    private val file = File(context.filesDir, FILE_NAME)
    private val items = ArrayList<Item>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private var nextId = 1L

    init {
        load()
    }

    /** Pinned items first, then newest first. */
    fun items(): List<Item> {
        prune()
        return sorted()
    }

    /** The most recently copied item, or null. */
    fun newest(): Item? {
        prune()
        return items.maxByOrNull { it.time }
    }

    /** The code from the newest clip if it is a fresh one-time code, or null. */
    fun freshOtp(): Pair<Item, String>? {
        prune()
        val newest = items.maxByOrNull { it.time } ?: return null
        if (System.currentTimeMillis() - newest.time > OTP_OFFER_MS) return null
        val code = OtpDetector.find(newest.text) ?: return null
        return newest to code
    }

    private var dismissedId = -1L

    /** The newest clip if it is, in full, a fresh code, email, phone number or link worth offering as a one-tap paste. */
    fun freshSmart(): Pair<Item, com.flexkeys.suggest.SmartClip.Found>? {
        prune()
        val newest = items.maxByOrNull { it.time } ?: return null
        if (newest.id == dismissedId) return null
        val found = com.flexkeys.suggest.SmartClip.find(newest.text) ?: return null
        val limit = if (found.kind == com.flexkeys.suggest.SmartClip.Kind.CODE) OTP_OFFER_MS else SMART_OFFER_MS
        if (System.currentTimeMillis() - newest.time > limit) return null
        return newest to found
    }

    /** Stops offering [id] (after it was pasted). */
    fun dismissOffer(id: Long) { dismissedId = id }

    /** Forgets clips that have outlived their time: codes after [OTP_KEEP_MS], the rest per the setting. */
    private fun prune() {
        val now = System.currentTimeMillis()
        val keep = EXPIRY_MS.getOrElse(prefs.clipboardExpiry) { 0L }
        val before = items.size
        items.removeAll { item ->
            !item.pinned && (
                now - item.time > OTP_KEEP_MS && OtpDetector.find(item.text) != null ||
                    keep > 0 && now - item.time > keep
                )
        }
        if (items.size != before) changed()
    }

    private fun sorted(): List<Item> = items.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })

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

    /** Adds [text] as a pinned item (or pins it if it is already in the history). Returns false if it is blank. */
    fun pinText(text: String): Boolean {
        val clean = text.take(MAX_LENGTH)
        if (clean.isBlank()) return false
        val existing = items.indexOfFirst { it.text == clean }
        if (existing >= 0) {
            items[existing] = items[existing].copy(pinned = true)
        } else {
            items.add(Item(nextId++, clean, System.currentTimeMillis(), true))
        }
        changed()
        return true
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

    /** The pinned items' text, for backups. */
    fun pinnedTexts(): List<String> = items.filter { it.pinned }.sortedByDescending { it.time }.map { it.text }

    /** Includes the pinned items in backups (only what you pinned, never the rest of the history). */
    object BackupSection : com.flexkeys.settings.BackupManager.Section {
        override val id = "pinned_clips"
        override fun export(context: Context): String = pinnedToJson(get(context).pinnedTexts())
        override fun import(context: Context, data: String) {
            val store = get(context)
            // Oldest first, so the restored list keeps its order.
            for (text in pinnedFromJson(data).reversed()) store.pinText(text)
        }
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
        private const val OTP_OFFER_MS = 3 * 60_000L
        private const val SMART_OFFER_MS = 2 * 60_000L
        private const val OTP_KEEP_MS = 10 * 60_000L
        /** Auto-delete choices in the same order as the setting: never, 1 hour, 1 day, 1 week. */
        private val EXPIRY_MS = longArrayOf(0L, 3_600_000L, 86_400_000L, 604_800_000L)
        private const val MAX_LENGTH = 10_000
        private const val MAX_PINNED_IN_BACKUP = 100

        internal fun pinnedToJson(texts: List<String>): String {
            val array = JSONArray()
            texts.take(MAX_PINNED_IN_BACKUP).forEach { array.put(it) }
            return array.toString()
        }

        internal fun pinnedFromJson(data: String): List<String> = try {
            val array = JSONArray(data)
            (0 until minOf(array.length(), MAX_PINNED_IN_BACKUP)).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() }?.take(MAX_LENGTH) }
        } catch (e: JSONException) {
            emptyList()
        }
        private val writer = Executors.newSingleThreadExecutor()

        @Volatile private var instance: ClipboardStore? = null

        fun get(context: Context): ClipboardStore =
            instance ?: synchronized(this) {
                instance ?: ClipboardStore(context.applicationContext).also { instance = it }
            }
    }
}
