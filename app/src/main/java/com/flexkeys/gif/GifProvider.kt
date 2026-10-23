package com.flexkeys.gif

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Shares downloaded GIFs from the app cache with the app being typed in, through the keyboard
 * image API. Not exported; access is granted per GIF by the system when a GIF is sent.
 */
class GifProvider : ContentProvider() {

    override fun onCreate() = true

    private fun fileFor(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        // Plain file names only: no paths.
        if (!NAME.matches(name)) throw FileNotFoundException(name)
        val f = File(File(context!!.cacheDir, GifLoader.SEND_DIR), name)
        if (!f.isFile) throw FileNotFoundException(name)
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = try { fileFor(uri) } catch (e: FileNotFoundException) { return null }
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { if (it == OpenableColumns.SIZE) f.length() else f.name }.toTypedArray())
        return cursor
    }

    override fun getType(uri: Uri): String = "image/gif"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val NAME = Regex("[A-Za-z0-9_.-]+")

        fun uriFor(context: android.content.Context, file: File): Uri =
            Uri.parse("content://${context.packageName}.gifs/${file.name}")
    }
}
