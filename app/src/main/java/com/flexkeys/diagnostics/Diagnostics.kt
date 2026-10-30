package com.flexkeys.diagnostics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Optional timing and crash records kept only on this phone. Typing timing is recorded only while the
 * setting is on; crash traces are always saved locally (never sent anywhere) so a crash can be looked at later.
 */
object Diagnostics {

    // The budget a key press should stay inside. See ROADMAP.md, "Performance budget".
    const val BUDGET_KEY_MEDIAN_MS = 4.0
    const val BUDGET_KEY_P95_MS = 12.0
    const val BUDGET_INPUT_DELAY_P95_MS = 20.0
    const val BUDGET_SLOW_FRAME_PERCENT = 10.0

    private const val SAMPLES = 400
    private const val CRASH_FILE = "crash.log"
    private const val MAX_CRASH_BYTES = 24_000

    /** A fixed-size ring of the latest measurements. */
    internal class Ring(private val size: Int) {
        private val values = LongArray(size)
        private var count = 0L

        @Synchronized fun add(v: Long) {
            values[(count % size).toInt()] = v
            count++
        }

        @Synchronized fun snapshot(): LongArray = values.copyOf(minOf(count, size.toLong()).toInt())
        @Synchronized fun clear() { count = 0 }
        @Synchronized fun total(): Long = count
    }

    private val keyMicros = Ring(SAMPLES)
    private val inputDelayMs = Ring(SAMPLES)
    @Volatile private var frames = 0L
    @Volatile private var slowFrames = 0L

    /** How long the keyboard took to handle one key press. */
    fun recordKey(nanos: Long) = keyMicros.add(nanos / 1_000)

    /** How long a touch waited before the keyboard got to look at it. A late touch means a busy main thread. */
    fun recordInputDelay(ms: Long) = inputDelayMs.add(ms.coerceAtLeast(0))

    fun clear(context: Context) {
        keyMicros.clear()
        inputDelayMs.clear()
        frames = 0
        slowFrames = 0
        File(context.filesDir, CRASH_FILE).delete()
    }

    // ---- frame timing while typing ----

    private var lastTyping = 0L
    private var watching = false
    private var lastFrameNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNanos != 0L) {
                val ms = (frameTimeNanos - lastFrameNanos) / 1_000_000
                frames++
                // More than about one and a half frames at 60 Hz counts as a hitch.
                if (ms > 24) slowFrames++
            }
            lastFrameNanos = frameTimeNanos
            if (SystemClock.uptimeMillis() - lastTyping < 800) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                watching = false
                lastFrameNanos = 0
            }
        }
    }

    /** Called on each key press while recording: watches frame times until typing stops. Main thread only. */
    fun noteTyping() {
        lastTyping = SystemClock.uptimeMillis()
        if (!watching) {
            watching = true
            lastFrameNanos = 0
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    // ---- report ----

    internal class Stats(val count: Int, val median: Double, val p95: Double, val max: Double)

    internal fun stats(values: LongArray, divisor: Double): Stats {
        if (values.isEmpty()) return Stats(0, 0.0, 0.0, 0.0)
        val sorted = values.sortedArray()
        fun pct(p: Int) = sorted[((sorted.size - 1) * p) / 100] / divisor
        return Stats(sorted.size, pct(50), pct(95), sorted.last() / divisor)
    }

    internal fun verdict(ok: Boolean) = if (ok) "OK" else "OVER BUDGET"

    fun report(context: Context): String {
        val key = stats(keyMicros.snapshot(), 1000.0)
        val delay = stats(inputDelayMs.snapshot(), 1.0)
        val f = frames
        val slowPercent = if (f == 0L) 0.0 else slowFrames * 100.0 / f
        val sb = StringBuilder()
        sb.append("SakuriKey ").append(version(context)).append('\n')
        sb.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
        if (key.count == 0) {
            sb.append("No typing recorded yet. Turn recording on, type for a while, then come back.\n")
        } else {
            sb.append("Key presses measured: ").append(keyMicros.total()).append('\n')
            sb.append("Time to handle a key: median %.1f ms, 95th %.1f ms, slowest %.1f ms  [%s]\n".format(
                key.median, key.p95, key.max, verdict(key.median < BUDGET_KEY_MEDIAN_MS && key.p95 < BUDGET_KEY_P95_MS)))
            sb.append("Touch waited before handling: median %.0f ms, 95th %.0f ms, slowest %.0f ms  [%s]\n".format(
                delay.median, delay.p95, delay.max, verdict(delay.p95 < BUDGET_INPUT_DELAY_P95_MS)))
            sb.append("Slow frames while typing: %d of %d (%.0f%%)  [%s]\n".format(
                slowFrames, f, slowPercent, verdict(slowPercent < BUDGET_SLOW_FRAME_PERCENT)))
            sb.append("\nBudget: key median under %.0f ms, 95th under %.0f ms, touch wait 95th under %.0f ms, slow frames under %.0f%%.\n"
                .format(BUDGET_KEY_MEDIAN_MS, BUDGET_KEY_P95_MS, BUDGET_INPUT_DELAY_P95_MS, BUDGET_SLOW_FRAME_PERCENT))
            sb.append("If \"touch waited\" is high but \"time to handle a key\" is low, the phone was busy with something else.\n")
        }
        val crash = crashLog(context)
        sb.append("\nCrashes: ").append(if (crash.isBlank()) "none recorded" else "\n$crash").append('\n')
        return sb.toString()
    }

    private fun version(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    // ---- crashes ----

    @Volatile private var crashHandlerInstalled = false

    /** Saves the trace of an uncaught exception to a file in the app's storage, then lets Android handle it as usual. */
    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val file = File(app.filesDir, CRASH_FILE)
                val entry = "--- crash on thread ${thread.name} at ${java.util.Date()} ---\n$trace\n"
                val old = if (file.isFile) file.readText() else ""
                file.writeText((entry + old).take(MAX_CRASH_BYTES))
            } catch (e: Exception) {
                // Never let the recorder itself get in the way.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun crashLog(context: Context): String {
        val file = File(context.filesDir, CRASH_FILE)
        return if (file.isFile) file.readText().take(4000) else ""
    }
}
