package com.flexkeys.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The keyboard background photo. The picked image is copied into app-internal storage, so it
 * keeps working after the original is deleted and no storage permission is ever needed.
 */
object BackgroundImageStore {

    private const val TAG = "FlexKeysBackground"
    private const val FILE_NAME = "background.jpg"
    /** Longest side of the stored copy. Plenty for a keyboard and keeps memory use small. */
    private const val MAX_STORED_DIM = 1600

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun delete(context: Context) {
        file(context).delete()
    }

    /**
     * Decodes [uri] with a sample size that keeps memory bounded, applies EXIF rotation,
     * and writes a JPEG copy into internal storage. Blocking; call from a background thread.
     */
    fun import(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_STORED_DIM)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return false

            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > MAX_STORED_DIM) {
                val scale = MAX_STORED_DIM.toFloat() / longest
                bitmap = replace(bitmap, Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true
                ))
            }

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
            exifMatrix(orientation)?.let { m ->
                bitmap = replace(bitmap, Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true))
            }

            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            bitmap.recycle()
            tmp.renameTo(file(context))
        } catch (e: IOException) {
            Log.w(TAG, "Could not import background", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to background", e)
            false
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Background too large", e)
            false
        }
    }

    /** Loads the stored photo so that its longest side is at most [maxDim]. Blocking. */
    fun load(context: Context, maxDim: Int): Bitmap? {
        val f = file(context)
        if (!f.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
            }
            BitmapFactory.decodeFile(f.path, options)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Could not load background", e)
            null
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    private fun replace(old: Bitmap, new: Bitmap): Bitmap {
        if (new !== old) old.recycle()
        return new
    }

    private fun exifMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }
}
