package com.flexkeys.settings

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Downloads a build and hands it to Android's package installer, which asks the user to confirm. */
object ApkInstaller {

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Downloads [url] and starts the install. [onStatus] gets short progress messages; [onError] a reason if it fails. */
    fun downloadAndInstall(context: Context, url: String, onStatus: (String) -> Unit, onError: (String) -> Unit) {
        val app = context.applicationContext
        pool.execute {
            try {
                val file = File(app.cacheDir, "update.apk")
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15_000
                    c.readTimeout = 30_000
                    c.setRequestProperty("User-Agent", "SakuriKey")
                    if (c.responseCode !in 200..299) {
                        main.post { onError("The download failed (error ${c.responseCode}).") }
                        return@execute
                    }
                    val total = c.contentLengthLong
                    var done = 0L
                    var lastPercent = -1
                    c.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent / 10 != lastPercent / 10) {
                                        lastPercent = percent
                                        main.post { onStatus("Downloading… $percent%") }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                install(app, file)
                main.post { onStatus("Downloaded. Confirm the install on the next screen.") }
            } catch (e: Exception) {
                main.post { onError("Couldn't download the update: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("sakurikey.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
    }
}

/** Android reports the install here: it asks us to show its confirm screen, and tells us if something went wrong. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't open the install screen.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"
                Toast.makeText(context, "The update was not installed: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }
}
