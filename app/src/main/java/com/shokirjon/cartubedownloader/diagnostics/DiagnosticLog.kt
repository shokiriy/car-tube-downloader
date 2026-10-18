package com.shokirjon.cartubedownloader.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    @Synchronized fun record(context: Context, event: String, detail: String?) {
        val file = File(context.filesDir, "diagnostics.txt")
        if (file.length() > 256 * 1024) file.writeText(file.readText().takeLast(128 * 1024))
        val safe = detail.orEmpty().replace(Regex("https?://\\S+"), "[URL]").replace(Regex("[\\r\\n]+"), " ").take(700)
        file.appendText("${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())} $event $safe\n")
    }
    fun read(context: Context): String = File(context.filesDir, "diagnostics.txt").takeIf { it.isFile }?.readText() ?: "No diagnostics recorded.\n"
}
