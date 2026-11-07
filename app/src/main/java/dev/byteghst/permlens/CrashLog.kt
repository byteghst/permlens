package dev.byteghst.permlens

import android.content.Context
import android.util.Log
import java.io.File

object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file(app).writeText(Log.getStackTraceString(e)) }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(c: Context): String? = file(c).takeIf { it.exists() }?.readText()

    fun clear(c: Context) {
        file(c).delete()
    }
}
