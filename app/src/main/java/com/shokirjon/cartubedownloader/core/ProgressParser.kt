package com.shokirjon.cartubedownloader.core

object ProgressParser {
    private val percent = Regex("(\\d+(?:\\.\\d+)?)%")
    private val bytes = Regex("([\\d.]+)\\s*(KiB|MiB|GiB|KB|MB|GB|B)", RegexOption.IGNORE_CASE)
    private val rate = Regex("at\\s+([\\d.]+)\\s*(KiB|MiB|GiB|KB|MB|GB|B)/s", RegexOption.IGNORE_CASE)
    private val eta = Regex("ETA\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)", RegexOption.IGNORE_CASE)
    fun parse(line: String): TransferProgress {
        val p = percent.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 100f)
        val sizes = bytes.findAll(line.substringBefore(" at ").substringBefore(" ETA ")).mapNotNull { m -> m.groupValues[1].toDoubleOrNull()?.let { scale(it, m.groupValues[2]) } }.toList()
        val total = if (sizes.size >= 2) sizes[1] else if (sizes.size == 1 && line.contains(" of ", true)) sizes[0] else null
        val done = if (sizes.size >= 2) sizes[0] else if (total != null && p != null) (total.toDouble() * p / 100.0).toLong() else sizes.firstOrNull()
        val speed = rate.find(line)?.let { m -> m.groupValues[1].toDoubleOrNull()?.let { scale(it, m.groupValues[2]) } }
        val seconds = eta.find(line)?.groupValues?.get(1)?.split(':')?.mapNotNull(String::toLongOrNull)?.let { parts ->
            when (parts.size) { 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> null }
        }
        val stage = line.takeIf { it.contains("ExtractAudio", true) || it.contains("Merger", true) || it.contains("ffmpeg", true) }
        return TransferProgress(p, done, total, speed, seconds, stage)
    }
    private fun scale(n: Double, unit: String): Long {
        val u = unit.uppercase()
        val base = if (u in setOf("KB", "MB", "GB")) 1000.0 else 1024.0
        var factor = 1.0
        repeat(when (u) { "KB", "KIB" -> 1; "MB", "MIB" -> 2; "GB", "GIB" -> 3; else -> 0 }) { factor *= base }
        return (n * factor).toLong().coerceAtLeast(0L)
    }
}
