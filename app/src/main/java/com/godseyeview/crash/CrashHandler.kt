package com.godseyeview.crash

import android.app.Application
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/** Plan Q25, with Brandon's correction: like Pool Helper's crash handler -
 *  a crash saves a report on the phone and opens a screen with the reason
 *  and Copy / Restart / Close. There is NO test-crash button. */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private lateinit var app: Application
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(application: Application) {
        app = application
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = buildReport(thread, throwable)
            val file = File(app.filesDir, "crash/last_crash.txt")
            file.parentFile?.mkdirs()
            file.writeText(report)
            val intent = Intent(app, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashActivity.EXTRA_REPORT_FILE, file.absolutePath)
            }
            app.startActivity(intent)
        } catch (ignored: Exception) {
            previous?.uncaughtException(thread, throwable)
        } finally {
            exitProcess(1)
        }
    }

    internal fun buildReport(thread: Thread, throwable: Throwable): String {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        // Stage 5 (issue #55): the facts a diagnosis needs up front.
        val version = runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrNull() ?: "?"
        return buildString {
            appendLine("God's Eye View Android - crash report")
            appendLine("App version: $version")
            appendLine(
                "Android: ${android.os.Build.VERSION.RELEASE} " +
                    "(API ${android.os.Build.VERSION.SDK_INT})",
            )
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine("Time: ${java.util.Date()}")
            appendLine()
            append(trace.toString())
        }
    }
}
