package com.flexkeys.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.flexkeys.theme.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a backup once a week into a folder the user chose, keeping the newest four. Off until a folder is picked.
 * The files hold the same things as a manual backup without the photo and stickers (and never any API keys).
 */
object AutoBackup {
    const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    const val KEEP = 4
    private const val PREFIX = "SakuriKey-auto-"
    @Volatile private var running = false

    /** Keeps only the [KEEP] newest of [names] (file names with a sortable date), returning the ones to delete. */
    internal fun toDelete(names: List<String>): List<String> =
        names.filter { it.startsWith(PREFIX) }.sortedDescending().drop(KEEP)

    internal fun fileName(now: Long): String =
        PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now)) + ".json"

    fun isDue(prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean =
        prefs.autoBackup && prefs.backupFolder.isNotEmpty() && now - prefs.lastAutoBackup >= INTERVAL_MS

    /** Takes a backup now if one is due. Does the work on its own thread; safe to call often. */
    fun runIfDue(context: Context) {
        val app = context.applicationContext
        if (!isDue(Prefs.get(app))) return
        runNow(app, null)
    }

    /** Takes a backup now. [done] gets a plain-words result on the background thread. */
    fun runNow(context: Context, done: ((String) -> Unit)?) {
        val app = context.applicationContext
        if (running) return
        running = true
        Thread {
            val message = try {
                save(app)
            } catch (e: Throwable) {
                "Couldn't save the backup: ${e.message ?: "unknown problem"}"
            } finally {
                running = false
            }
            done?.invoke(message)
        }.start()
    }

    private fun save(app: Context): String {
        val prefs = Prefs.get(app)
        val tree = Uri.parse(prefs.backupFolder.ifEmpty { return "Pick a folder first." })
        val resolver = app.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val now = System.currentTimeMillis()
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", fileName(now))
            ?: return "Couldn't create a file in that folder. Pick the folder again."
        val json = BackupManager.export(app, false)
        resolver.openOutputStream(doc, "wt")?.use { it.write(json.toByteArray()) }
            ?: return "Couldn't write to that folder. Pick it again."
        prefs.lastAutoBackup = now
        // Remove the oldest backups, leaving the newest four.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val found = ArrayList<Pair<String, String>>() // name to document id
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
            while (c.moveToNext()) found.add(c.getString(0) to c.getString(1))
        }
        for (name in toDelete(found.map { it.first })) {
            found.filter { it.first == name }.forEach {
                runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.second)) }
            }
        }
        return "Backup saved"
    }

    /** Remembers access to the folder the user picked, so backups can be written later without asking again. */
    fun rememberFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val prefs = Prefs.get(context)
        prefs.backupFolder = uri.toString()
        prefs.autoBackup = true
        prefs.lastAutoBackup = 0L
    }

    /** A short name for the chosen folder, or null when none is chosen. */
    fun folderLabel(prefs: Prefs): String? {
        if (prefs.backupFolder.isEmpty()) return null
        return try {
            Uri.parse(prefs.backupFolder).let { DocumentsContract.getTreeDocumentId(it) }.substringAfter(':').ifEmpty { "Internal storage" }
        } catch (e: Exception) {
            "chosen folder"
        }
    }
}
