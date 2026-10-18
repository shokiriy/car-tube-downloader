package com.shokirjon.cartubedownloader.core

object FileNames {
    private val forbidden = Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]")
    fun sanitize(title: String, maxLength: Int = 120): String {
        require(maxLength > 0)
        val safe = title.replace(forbidden, " ").replace(Regex("\\s+"), " ").trim().trim('.', ' ')
            .take(maxLength).trimEnd('.', ' ')
        return safe.ifBlank { "CarTube media" }
    }
}
