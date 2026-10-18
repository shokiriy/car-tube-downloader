package com.shokirjon.cartubedownloader.core

object RuntimeChecks {
    private val supported = setOf("armeabi-v7a", "arm64-v8a")
    fun supportsAny(abis: Array<String>) = abis.any { it in supported }
    fun hasWorkingSpace(available: Long, estimated: Long?, reserve: Long): Boolean {
        val transfer = (estimated ?: 500L * 1024 * 1024).coerceAtLeast(100L * 1024 * 1024)
        val safety = reserve.coerceAtLeast(1_073_741_824L)
        if (transfer > (Long.MAX_VALUE - safety) / 3L) return false
        return available >= safety + transfer * 3L
    }
}
