package com.flexkeys.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The user's own stickers: images picked from the gallery, shrunk and kept in app-internal storage
 * (files/stickers). Transparent PNGs keep their transparency.
 */
object StickerStore {

    private const val TAG = "FlexKeysStickers"
    private const val DIR = "stickers"
    private const val MAX_DIM = 512
    private const val MAX_COUNT = 200

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /** Newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".png") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File) {
        file.delete()
    }

    /** Puts back a sticker from a backup, under its original name. The caller checks the name and size. */
    fun restore(context: Context, name: String, bytes: ByteArray) {
        try {
            File(dir(context), name).writeBytes(bytes)
        } catch (e: IOException) {
            Log.w(TAG, "Could not restore sticker", e)
        }
    }

    /** Adds the image at [uri] as a sticker. Blocking; call from a background thread. */
    fun import(context: Context, uri: Uri): Boolean {
        if (list(context).size >= MAX_COUNT) return false
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return false
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > MAX_DIM) {
                val scale = MAX_DIM.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            val file = File(dir(context), "s${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not import sticker", e)
            false
        } catch (e: SecurityException) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }
}
