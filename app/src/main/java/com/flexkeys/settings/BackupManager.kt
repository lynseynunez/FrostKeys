package com.flexkeys.settings

import android.content.Context
import android.util.Base64
import com.flexkeys.phrases.SnippetStore
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
    private val SECRET_KEYS = setOf(Prefs.KEY_KLIPY_KEY, Prefs.KEY_KLIPY_CUSTOMER, Prefs.KEY_TRANSLATE_EMAIL)

    /** Extra data (like phrases) that other parts of the app add to a backup. */
    interface Section {
        val id: String
        fun export(context: Context): String
        fun import(context: Context, data: String)
    }

    private val sections: List<Section> = listOf(SnippetStore.BackupSection)

    fun export(context: Context, includePhoto: Boolean): String {
        val prefs = Prefs.get(context)
        val root = JSONObject().put("app", APP).put("version", VERSION)
        val p = JSONObject()
        for ((key, value) in prefs.sp.all) {
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
        root.put("prefs", p)
        root.put("words", SuggestionEngine.get(context).user.exportText())
        val extra = JSONObject()
        for (s in sections) extra.put(s.id, s.export(context))
        root.put("sections", extra)
        if (includePhoto) {
            val f = BackgroundImageStore.file(context)
            if (f.isFile) root.put("photo", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
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
        try {
            val prefs = Prefs.get(context)
            val editor = prefs.sp.edit()
            val p = root.optJSONObject("prefs")
            if (p != null) {
                for (key in p.keys()) {
                    if (key in SECRET_KEYS) continue
                    val o = p.optJSONObject(key) ?: continue
                    when (o.optString("t")) {
                        "b" -> editor.putBoolean(key, o.getBoolean("v"))
                        "i" -> editor.putInt(key, o.getInt("v"))
                        "l" -> editor.putLong(key, o.getLong("v"))
                        "f" -> editor.putFloat(key, o.getDouble("v").toFloat())
                        "s" -> editor.putString(key, o.getString("v"))
                    }
                }
            }
            editor.apply()
            root.optString("words").takeIf { it.isNotEmpty() }?.let { SuggestionEngine.get(context).user.importText(it) }
            val extra = root.optJSONObject("sections")
            if (extra != null) for (s in sections) extra.optString(s.id).takeIf { it.isNotEmpty() }?.let { s.import(context, it) }
            root.optString("photo").takeIf { it.isNotEmpty() }?.let {
                BackgroundImageStore.file(context).writeBytes(Base64.decode(it, Base64.DEFAULT))
                prefs.backgroundVersion = System.currentTimeMillis()
            }
        } catch (e: JSONException) {
            return Result(false, "That backup is damaged.")
        } catch (e: IOException) {
            return Result(false, "Couldn't restore the photo.")
        } catch (e: IllegalArgumentException) {
            return Result(false, "That backup is damaged.")
        }
        return Result(true, "Backup restored.")
    }
}
