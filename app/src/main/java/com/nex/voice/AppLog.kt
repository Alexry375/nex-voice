package com.nex.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shared file-based logger visible from SettingsActivity.
 * Logs go to both Logcat and a file in the app's cache directory.
 */
object AppLog {
    private const val TAG = "NexVoice"
    private const val MAX_LINES = 200
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "nex_debug.log")
    }

    fun d(message: String) {
        Log.d(TAG, message)
        write("D", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val extra = throwable?.let { ": ${it.message}" } ?: ""
        write("E", "$message$extra")
    }

    fun w(message: String) {
        Log.w(TAG, message)
        write("W", message)
    }

    private fun write(level: String, message: String) {
        try {
            val file = logFile ?: return
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "$ts [$level] $message\n"
            file.appendText(line)
            // Trim if too long
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
            }
        } catch (_: Exception) {}
    }

    fun read(): String {
        return try {
            logFile?.readText()?.trim() ?: "(no log file)"
        } catch (_: Exception) {
            "(error reading log)"
        }
    }

    fun clear() {
        try { logFile?.writeText("") } catch (_: Exception) {}
    }
}
