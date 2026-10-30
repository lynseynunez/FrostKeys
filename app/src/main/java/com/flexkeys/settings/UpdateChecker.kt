package com.flexkeys.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Looks for a newer release on GitHub when the user asks. Nothing is checked automatically. */
object UpdateChecker {

    private const val REPO = "lynseynunez/FrostKeys"

    class Result(val latest: String?, val url: String?, val newer: Boolean, val error: String?, val notes: String = "")

    /** The newest test build, published as the "latest" pre-release on every successful build. */
    class TestBuild(val commit: String?, val apkUrl: String?, val sizeBytes: Long, val error: String?)

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun currentVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

    fun check(context: Context, onDone: (Result) -> Unit) {
        val current = currentVersion(context)
        pool.execute {
            val result = try {
                val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 10_000
                    c.readTimeout = 10_000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.setRequestProperty("User-Agent", "SakuriKey")
                    when (val code = c.responseCode) {
                        404 -> Result(null, null, false, "No release has been published yet.")
                        in 200..299 -> {
                            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                            val tag = o.optString("tag_name")
                            Result(tag, o.optString("html_url"), isNewer(tag, current), null, o.optString("body").trim().take(900))
                        }
                        else -> Result(null, null, false, "GitHub returned error $code.")
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: IOException) {
                Result(null, null, false, "Couldn't reach GitHub. Check your connection.")
            } catch (e: JSONException) {
                Result(null, null, false, "Unexpected reply from GitHub.")
            }
            main.post { onDone(result) }
        }
    }

    fun checkTestBuild(onDone: (TestBuild) -> Unit) {
        pool.execute {
            val result = try {
                val c = URL("https://api.github.com/repos/$REPO/releases/tags/latest").openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 10_000
                    c.readTimeout = 10_000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.setRequestProperty("User-Agent", "SakuriKey")
                    when (val code = c.responseCode) {
                        404 -> TestBuild(null, null, 0, null)
                        in 200..299 -> {
                            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                            val asset = o.optJSONArray("assets")?.optJSONObject(0)
                            TestBuild(
                                o.optString("target_commitish").takeIf { it.isNotEmpty() },
                                asset?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
                                asset?.optLong("size") ?: 0L,
                                null,
                            )
                        }
                        else -> TestBuild(null, null, 0, "GitHub returned error $code.")
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: IOException) {
                TestBuild(null, null, 0, "Couldn't reach GitHub. Check your connection.")
            } catch (e: JSONException) {
                TestBuild(null, null, 0, "Unexpected reply from GitHub.")
            }
            main.post { onDone(result) }
        }
    }

    fun isNewer(latest: String, current: String): Boolean = Versions.isNewer(latest, current)
}
