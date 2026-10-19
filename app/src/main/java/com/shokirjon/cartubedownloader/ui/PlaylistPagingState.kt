package com.shokirjon.cartubedownloader.ui

import com.shokirjon.cartubedownloader.db.PlaylistEntry

/** Paging over stored playlist rows. Only the currently visited pages are held in memory. */
internal class PlaylistPagingState(val playlistId: String, val pageSize: Int = 24) {
    val items = mutableListOf<PlaylistEntry>()
    var loading = false
        private set
    var exhausted = false
        private set
    var failed = false
        private set
    private val seen = mutableSetOf<String>()

    fun begin(): Int? {
        if (loading || exhausted) return null
        loading = true
        failed = false
        return items.size
    }

    fun append(page: List<PlaylistEntry>) {
        if (!loading) return
        page.forEach { if (seen.add(it.videoId)) items.add(it) }
        exhausted = page.size < pageSize
        loading = false
    }

    fun fail() { loading = false; failed = true }

    fun allowMore() { exhausted = false }

    fun update(videoId: String, transform: (PlaylistEntry) -> PlaylistEntry): Int {
        val index = items.indexOfFirst { it.videoId == videoId }
        if (index >= 0) items[index] = transform(items[index])
        return index
    }
}
