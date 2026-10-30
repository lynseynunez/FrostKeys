package com.flexkeys.settings

import android.content.Context
import android.util.Base64
import com.flexkeys.phrases.SnippetStore
import com.flexkeys.stickers.StickerStore
import com.flexkeys.suggest.SuggestionEngine
import com.flexkeys.theme.BackgroundImageStore
import com.flexkeys.theme.Prefs
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Backs up and restores settings, learned words and (optionally) the background photo as one JSON
 * file the user keeps. API keys and the translator email are never included.
 */
object BackupManager {

    private const val APP = "SakuriKey"
    private const val VERSION = 1
    private const val MAX_STICKERS = 200
    private const val MAX_STICKER_BYTES = 2_000_000
    private const val MAX_STICKER_TOTAL_BYTES = 24_000_000L
    private val STICKER_NAME = Regex("[A-Za-z0-9_-]{1,64}\\.png")
    private val SECRET_KEYS = setOf(Prefs.KEY_KLIPY_KEY, Prefs.KEY_KLIPY_CUSTOMER, Prefs.KEY_TRANSLATE_EMAIL, Prefs.KEY_GEMINI_KEY, Prefs.KEY_GROQ_KEY)

    /** Extra data (like phrases) that other parts of the app add to a backup. */
    interface Section {
        val id: String
        fun export(context: Context): String
        fun import(context: Context, data: String)
    }

    private val sections: List<Section> = listOf(SnippetStore.BackupSection)

    /** Settings as JSON, each with its type so it can be restored exactly. Secrets are left out. */
    internal fun prefsToJson(all: Map<String, *>): JSONObject {
        val p = JSONObject()
        for ((key, value) in all) {
            if (key in SECRET_KEYS) continue
            val o = JSONObject()
            when (value) {
                is Boolean -> o.put("t", "b").put("v", value)
                is Int -> o.put("t", "i").put("v", value)
                is Long -> o.put("t", "l").put("v", value)
                is Float -> o.put("t", "f").put("v", value.toDouble())
                is String -> o.put("t", "s").put("v", value)
                else -> continue
            }
            p.put(key, o)
        }
        return p
    }

    /** The settings in [p] with their original types. Secrets are skipped even if a file contains them. */
    internal fun parsePrefs(p: JSONObject): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        for (key in p.keys()) {
            if (key in SECRET_KEYS) continue
            val o = p.optJSONObject(key) ?: continue
            when (o.optString("t")) {
                "b" -> result[key] = o.getBoolean("v")
                "i" -> result[key] = o.getInt("v")
                "l" -> result[key] = o.getLong("v")
                "f" -> result[key] = o.getDouble("v").toFloat()
                "s" -> result[key] = o.getString("v")
            }
        }
        return result
    }

    /** A sticker file name that is safe to write: letters, digits, underscores and dashes, then .png. */
    internal fun isSafeStickerName(name: String): Boolean = STICKER_NAME.matches(name)

    fun export(context: Context, includeImages: Boolean): String {
        val prefs = Prefs.get(context)
        val root = JSONObject().put("app", APP).put("version", VERSION)
        root.put("prefs", prefsToJson(prefs.sp.all))
        root.put("words", SuggestionEngine.get(context).user.exportText())
        val extra = JSONObject()
        for (s in sections) extra.put(s.id, s.export(context))
        root.put("sections", extra)
        if (includeImages) {
            val f = BackgroundImageStore.file(context)
            if (f.isFile) root.put("photo", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            val stickers = JSONObject()
            var total = 0L
            for (file in StickerStore.list(context)) {
                // Keep the backup a size that can be built in memory, and skip what import would refuse.
                if (file.length() > MAX_STICKER_BYTES || total + file.length() > MAX_STICKER_TOTAL_BYTES) continue
                total += file.length()
                stickers.put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
            if (stickers.length() > 0) root.put("stickers", stickers)
        }
        return root.toString()
    }

    class Result(val ok: Boolean, val message: String)

    fun import(context: Context, text: String): Result {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return Result(false, "That file isn't a SakuriKey backup.")
        }
        if (root.optString("app") != APP) return Result(false, "That file isn't a SakuriKey backup.")

        // Read and check everything first, so a damaged file changes nothing.
        val settings: Map<String, Any>
        val photo: ByteArray?
        val stickerFiles = ArrayList<Pair<String, ByteArray>>()
        try {
            settings = root.optJSONObject("prefs")?.let { parsePrefs(it) } ?: emptyMap()
            photo = root.optString("photo").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.DEFAULT) }
            root.optJSONObject("stickers")?.let { stickers ->
                for (name in stickers.keys()) {
                    if (stickerFiles.size >= MAX_STICKERS || !isSafeStickerName(name)) continue
                    val bytes = Base64.decode(stickers.getString(name), Base64.DEFAULT)
                    if (bytes.size <= MAX_STICKER_BYTES) stickerFiles.add(name to bytes)
                }
            }
        } catch (e: JSONException) {
            return Result(false, "That backup is damaged.")
        } catch (e: IllegalArgumentException) {
            return Result(false, "That backup is damaged.")
        }

        val prefs = Prefs.get(context)
        val editor = prefs.sp.edit()
        for ((key, value) in settings) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
        root.optString("words").takeIf { it.isNotEmpty() }?.let { SuggestionEngine.get(context).user.importText(it) }
        val extra = root.optJSONObject("sections")
        if (extra != null) for (s in sections) extra.optString(s.id).takeIf { it.isNotEmpty() }?.let { s.import(context, it) }

        var imagesFailed = 0
        for ((name, bytes) in stickerFiles) if (!StickerStore.restore(context, name, bytes)) imagesFailed++
        if (photo != null) {
            try {
                BackgroundImageStore.file(context).writeBytes(photo)
                prefs.backgroundVersion = System.currentTimeMillis()
            } catch (e: IOException) {
                imagesFailed++
            }
        }
        return if (imagesFailed == 0) Result(true, "Backup restored.")
        else Result(true, "Backup restored, but $imagesFailed image(s) could not be restored.")
    }
}
