package com.shokirjon.cartubedownloader.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface YouTubeTarget {
    val canonicalUrl: String
    data class Video(val id: String, val playlistId: String?, override val canonicalUrl: String) : YouTubeTarget
    data class Playlist(val id: String, override val canonicalUrl: String) : YouTubeTarget
}

object YouTubeUrlParser {
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val playlistId = Regex("^[A-Za-z0-9_-]{10,100}$")

    fun parse(input: String): YouTubeTarget {
        val value = input.trim()
        require(value.isNotEmpty()) { "Enter a YouTube URL." }
        val raw = if (value.startsWith("https://", true) || value.startsWith("http://", true)) value else "https://$value"
        val uri = try { URI(raw) } catch (_: Exception) { throw IllegalArgumentException("Invalid URL.") }
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) { "Only HTTPS links are supported." }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Invalid host.")
        require(host in hosts && uri.userInfo == null) { "Only public YouTube links are supported." }
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { item ->
            val pair = item.split('=', limit = 2)
            if (pair.size == 2) pair[0] to URLDecoder.decode(pair[1], StandardCharsets.UTF_8) else null
        }.toMap()
        val list = query["list"]?.takeIf(playlistId::matches)
        val path = uri.path.orEmpty().trimEnd('/')
        val id = when {
            host.removePrefix("www.") == "youtu.be" -> path.removePrefix("/").substringBefore('/').takeIf(videoId::matches)
            path == "/watch" -> query["v"]?.takeIf(videoId::matches)
            path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/") -> path.substringAfterLast('/').takeIf(videoId::matches)
            else -> null
        }
        if (id != null) return YouTubeTarget.Video(id, list, "https://www.youtube.com/watch?v=$id" + (list?.let { "&list=$it" } ?: ""))
        if (path == "/playlist" && list != null) return YouTubeTarget.Playlist(list, "https://www.youtube.com/playlist?list=$list")
        throw IllegalArgumentException("Unsupported YouTube video or playlist URL.")
    }

    fun parseMany(input: String): List<YouTubeTarget> {
        val seen = LinkedHashSet<String>()
        return input.lineSequence().map(String::trim).filter(String::isNotBlank).mapNotNull {
            runCatching { parse(it) }.getOrNull()
        }.filter { seen.add(it.canonicalUrl) }.toList()
    }
}
