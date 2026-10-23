package com.flexkeys.gif

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Downloads GIF files into the app cache and decodes previews. All callbacks run on the main thread. */
object GifLoader {

    const val SEND_DIR = "gifs"
    private const val PREVIEW_DIR = "gif_previews"
    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun file(context: Context, dir: String, url: String): File {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ext = if (url.substringBefore('?').endsWith(".webp", true)) "webp" else "gif"
        return File(File(context.cacheDir, dir).apply { mkdirs() }, "$name.$ext")
    }

    /** Downloads [url] into the send cache (the folder GifProvider serves from). */
    fun downloadForSending(context: Context, url: String, onDone: (File?) -> Unit) {
        val app = context.applicationContext
        pool.execute {
            val f = try { fetch(file(app, SEND_DIR, url), url) } catch (e: IOException) { null }
            main.post { onDone(f) }
        }
    }

    /** Loads a small animated (or still) preview scaled so its longest side is at most [maxDim]. */
    fun loadPreview(context: Context, url: String, maxDim: Int, onDone: (Drawable?) -> Unit) {
        val app = context.applicationContext
        pool.execute {
            val drawable = try {
                decode(app, fetch(file(app, PREVIEW_DIR, url), url), maxDim)
            } catch (e: IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
            main.post { onDone(drawable) }
        }
    }

    /** Removes cached files older than a day. */
    fun prune(context: Context) {
        val app = context.applicationContext
        pool.execute {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            for (dir in listOf(SEND_DIR, PREVIEW_DIR)) {
                File(app.cacheDir, dir).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            }
        }
    }

    private fun fetch(target: File, url: String): File {
        if (target.isFile && target.length() > 0) return target
        if (!url.startsWith("https://")) throw IOException("Only https downloads are allowed")
        val tmp = File(target.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("File too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Could not store download")
            return target
        } finally {
            c.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun decode(context: Context, file: File, maxDim: Int): Drawable? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
        // Before Android 9 only the first frame can be shown.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
