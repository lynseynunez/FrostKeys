package com.flexkeys.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.flexkeys.theme.BackgroundImageStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The optional depth effect: finds the main subject of the background photo (a person, a character, a
 * pet) and keeps a copy with the rest made transparent, so the keyboard can draw it sharp in front of the
 * blurred glass. Uses Google's ML Kit subject segmentation, which runs on the phone through Google Play
 * services (the small model is downloaded once). The photo never leaves the phone.
 */
object SubjectCutout {

    private const val TAG = "FlexKeysCutout"
    private const val FILE_NAME = "background_subject.png"
    private const val WORK_SIZE = 1024

    private val main = Handler(Looper.getMainLooper())

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun delete(context: Context) {
        file(context).delete()
    }

    /** Loads the cutout so its longest side is at most [maxDim]. Blocking. */
    fun load(context: Context, maxDim: Int): Bitmap? {
        val f = file(context)
        if (!f.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Cuts the subject out of the stored background photo. [onDone] is called on the main thread with
     * whether it worked and a message for the user.
     */
    fun generate(context: Context, onDone: (Boolean, String) -> Unit) {
        val app = context.applicationContext
        Thread {
            val source = BackgroundImageStore.load(app, WORK_SIZE)
            main.post {
                if (source == null) {
                    onDone(false, "Choose a background photo first.")
                    return@post
                }
                run(app, source, onDone)
            }
        }.start()
    }

    private fun run(app: Context, source: Bitmap, onDone: (Boolean, String) -> Unit) {
        val segmenter = try {
            SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        } catch (e: Exception) {
            onDone(false, unavailable())
            return
        }
        segmenter.process(InputImage.fromBitmap(source, 0))
            .addOnSuccessListener { result ->
                segmenter.close()
                val cutout = result.foregroundBitmap
                if (cutout == null) {
                    onDone(false, "No clear subject was found in that photo. Try a photo with a person or character in front.")
                } else {
                    Thread {
                        val saved = save(app, cutout)
                        main.post {
                            if (saved) onDone(true, "Depth effect ready.") else onDone(false, "Couldn't save the cutout.")
                        }
                    }.start()
                }
            }
            .addOnFailureListener { e ->
                segmenter.close()
                Log.w(TAG, "Subject segmentation failed", e)
                onDone(false, unavailable())
            }
    }

    private fun save(context: Context, bitmap: Bitmap): Boolean = try {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file(context))
    } catch (e: IOException) {
        Log.w(TAG, "Could not save cutout", e)
        false
    }

    private fun unavailable() =
        "Couldn't cut out the subject. This needs Google Play services, and the first time it downloads a small model, " +
            "so connect to the internet and try again in a minute."
}
