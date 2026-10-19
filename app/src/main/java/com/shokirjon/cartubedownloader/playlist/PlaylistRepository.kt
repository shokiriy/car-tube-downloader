package com.shokirjon.cartubedownloader.playlist

import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.CarTubeDao
import com.shokirjon.cartubedownloader.db.PlaylistEntry
import com.shokirjon.cartubedownloader.db.TrackedPlaylist
import com.shokirjon.cartubedownloader.download.DownloadRepository
import com.shokirjon.cartubedownloader.extractor.MediaExtractor
import com.shokirjon.cartubedownloader.settings.SettingsStore

class PlaylistRepository(
    private val dao: CarTubeDao,
    private val extractor: MediaExtractor,
    private val downloads: DownloadRepository,
    private val settings: SettingsStore
) {
    val playlists = dao.playlists()
    suspend fun add(url: String, interval: String = "MANUAL", wifiOnly: Boolean = true, autoAdd: Boolean = false,
                    kind: OutputKind = OutputKind.MP3, quality: String = "K192"): TrackedPlaylist {
        val target = YouTubeUrlParser.parse(url) as? YouTubeTarget.Playlist ?: throw IllegalArgumentException("Enter a playlist URL.")
        val existing = dao.playlist(target.id)
        val saved = TrackedPlaylist(target.id, target.canonicalUrl, existing?.title ?: "Loading playlist", interval, wifiOnly,
            autoAdd, kind.name, quality, existing?.lastChecked, existing?.knownCount ?: 0, existing?.newCount ?: 0)
        dao.upsertPlaylist(saved)
        return saved
    }
    suspend fun page(id: String, offset: Int, limit: Int = 50) = dao.playlistPage(id, limit.coerceIn(1, 50), offset.coerceAtLeast(0))
    suspend fun selectAll(id: String, value: Boolean) = dao.selectAll(id, value)
    suspend fun select(id: String, videoId: String, value: Boolean) = dao.selectItem(id, videoId, value)
    suspend fun selectOnlyNew(id: String): Int {
        val count = dao.playlist(id)?.newCount ?: 0
        val newest = dao.newestItems(id, count.coerceAtLeast(0)).filterNot { it.queued }
        dao.selectAll(id, false)
        newest.forEach { dao.selectItem(id, it.videoId, true) }
        return newest.size
    }
    suspend fun selectedCount(id: String) = dao.selectedUnqueued(id).size
    suspend fun remove(id: String) = dao.removePlaylist(id)

    suspend fun sync(id: String): Int {
        val saved = dao.playlist(id) ?: throw IllegalArgumentException("Unknown playlist")
        var offset = 0
        var newCount = 0
        var title = saved.title
        val newEntries = mutableListOf<PlaylistEntry>()
        while (offset < 10_000) {
            val batch = extractor.extractPlaylist(saved.url, offset, 50)
            title = batch.title
            batch.items.forEachIndexed { index, item ->
                val row = PlaylistEntry(id, item.id, item.title, item.url, item.thumbnail, offset + index,
                    saved.autoAdd, false, System.currentTimeMillis())
                if (dao.insertItem(row) != -1L) { newCount++; if (saved.autoAdd) newEntries.add(row) }
            }
            offset += 50
            if (batch.count != null && offset >= batch.count) break
            if (batch.items.isEmpty() && batch.count == null) break
        }
        dao.upsertPlaylist(saved.copy(title = title, lastChecked = System.currentTimeMillis(), knownCount = dao.playlistCount(id), newCount = newCount))
        if (saved.autoAdd) {
            for (item in newEntries) {
                try {
                    val info = extractor.extractInfo(item.url)
                    val kind = OutputKind.valueOf(saved.outputKind)
                    val quality = saved.quality
                    downloads.enqueue(info, item.url, kind, quality)
                    dao.markQueued(id, item.videoId)
                } catch (_: Exception) { /* Item remains unqueued for manual review and retry. */ }
            }
        }
        return newCount
    }

    suspend fun queueSelected(id: String, kind: OutputKind, quality: String): Pair<Int, Int> {
        val selected = dao.selectedUnqueued(id)
        var added = 0; var failed = 0
        for (item in selected) {
            try {
                val info = extractor.extractInfo(item.url)
                downloads.enqueue(info, item.url, kind, quality)
                dao.markQueued(id, item.videoId)
                added++
            } catch (_: Exception) { failed++ }
        }
        return added to failed
    }
}
