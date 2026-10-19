package com.shokirjon.cartubedownloader.extractor

import com.shokirjon.cartubedownloader.core.YouTubeTarget

internal object PlaylistSource {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun forExtractor(target: YouTubeTarget.Playlist): String {
        val mixVideoId = target.id.removePrefix("RD").takeIf { target.id.startsWith("RD") && videoId.matches(it) }
        return if (mixVideoId != null) "https://www.youtube.com/watch?v=$mixVideoId&list=${target.id}"
            else target.canonicalUrl
    }
}
