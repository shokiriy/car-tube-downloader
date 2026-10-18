package com.shokirjon.cartubedownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shokirjon.cartubedownloader.CarTubeApp
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.download.DownloadService
import com.shokirjon.cartubedownloader.download.EnqueueResult
import com.shokirjon.cartubedownloader.playlist.PlaylistScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


data class Preview(val info: MediaInfo, val selected: Boolean = true)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CarTubeApp
    private val analysisMutex = Mutex()
    val tasks = app.downloads.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = app.playlists.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.shokirjon.cartubedownloader.settings.AppSettings())
    private val _previews = MutableStateFlow<List<Preview>>(emptyList())
    val previews = _previews.asStateFlow()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _playlistReady = MutableStateFlow(false)
    val playlistReady = _playlistReady.asStateFlow()
    var inputText = ""
    private var analyzedText = ""
    var outputKind = OutputKind.MP3
    var audioQuality = AudioQuality.K192
    var videoQuality = VideoQuality.P480
    init { viewModelScope.launch {
        val preferred = app.settings.current()
        audioQuality = runCatching { AudioQuality.valueOf(preferred.audioQuality) }.getOrDefault(AudioQuality.K192)
        videoQuality = runCatching { VideoQuality.valueOf(preferred.videoQuality) }.getOrDefault(VideoQuality.P480)
    } }

    fun analyze() = viewModelScope.launch(Dispatchers.IO) {
        analysisMutex.withLock {
            _busy.value = true
            _previews.value = emptyList()
            _playlistReady.value = false
            analyzedText = ""
            _message.value = "Havola tahlil qilinmoqda…"
            try {
                val sourceText = inputText
                val urls = YouTubeUrlParser.parseMany(sourceText)
                val invalid = sourceText.lineSequence().map(String::trim).filter(String::isNotBlank).count { runCatching { YouTubeUrlParser.parse(it) }.isFailure }
                if (urls.isEmpty()) { _message.value = "YouTube video yoki pleylist havolasini kiriting."; return@withLock }
                val found = ArrayList<Preview>()
                var failed = invalid; var playlistCount = 0
                for (target in urls) {
                    try {
                        when (target) {
                            is YouTubeTarget.Video -> found.add(Preview(app.extractor.extractInfo(target.canonicalUrl)))
                            is YouTubeTarget.Playlist -> {
                                app.playlists.add(target.canonicalUrl)
                                app.playlists.sync(target.id)
                                playlistCount++
                                _playlistReady.value = true
                            }
                        }
                        _previews.value = found.toList()
                        _message.value = "${found.size} ta video topildi, $playlistCount ta pleylist yuklandi, $failed ta xato. Formatni tanlab yuklang."
                    } catch (e: Exception) { failed++; _message.value = e.message ?: "Havolani tahlil qilib bo‘lmadi." }
                }
                analyzedText = sourceText
            } finally { _busy.value = false }
        }
    }
    fun analysisMatchesInput() = inputText == analyzedText
    fun selectAll(value: Boolean) { _previews.value = _previews.value.map { it.copy(selected = value) } }
    fun select(index: Int, value: Boolean) { _previews.value = _previews.value.mapIndexed { i, p -> if (i == index) p.copy(selected = value) else p } }
    fun removeSelected() { _previews.value = _previews.value.filterNot { it.selected } }
    fun queueSelected() = viewModelScope.launch(Dispatchers.IO) {
        if (!analysisMatchesInput()) { _message.value = "Havola o‘zgardi. Qayta tahlil qiling."; return@launch }
        val picked = _previews.value.filter { it.selected }
        var added = 0; var duplicates = 0
        for (p in picked) {
            try {
                val quality = if (outputKind == OutputKind.MP3) audioQuality.name else videoQuality.name
                when (app.downloads.enqueue(p.info, p.info.url, outputKind, quality)) {
                    is EnqueueResult.Added -> added++
                    is EnqueueResult.Duplicate -> duplicates++
                }
            } catch (e: Exception) { _message.value = e.message ?: "Could not queue item." }
        }
        if (added > 0) DownloadService.start(app)
        _message.value = "$added queued, $duplicates duplicates."
    }
    fun queueAction(id: String, action: String) = viewModelScope.launch(Dispatchers.IO) {
        when (action) {
            "pause" -> DownloadService.pause(app, id)
            "cancel" -> DownloadService.cancel(app, id)
            "retry" -> { app.storage.cleanup(id); app.downloads.retry(id); DownloadService.start(app) }
            "remove" -> { app.storage.cleanup(id); app.downloads.remove(id) }
            "up" -> app.downloads.move(id, -1)
            "down" -> app.downloads.move(id, 1)
        }
    }
    fun cancelRemaining() = viewModelScope.launch(Dispatchers.IO) { app.downloads.pending().forEach { app.downloads.state(it.id, TaskState.CANCELLED) } }
    fun clearCompleted() = viewModelScope.launch(Dispatchers.IO) { app.downloads.clearCompleted() }
    fun clearFailed() = viewModelScope.launch(Dispatchers.IO) { app.downloads.clearFailed() }
    fun addPlaylist(url: String, interval: String, wifi: Boolean, autoAdd: Boolean, kind: OutputKind, quality: String) = viewModelScope.launch(Dispatchers.IO) {
        _busy.value = true
        try {
            val playlist = app.playlists.add(url, interval, wifi, autoAdd, kind, quality)
            val count = app.playlists.sync(playlist.id)
            PlaylistScheduler.schedule(app)
            _message.value = "Playlist saved: $count items discovered."
            if (autoAdd) DownloadService.start(app)
        } catch (e: Exception) { _message.value = e.message ?: "Playlist check failed." }
        finally { _busy.value = false }
    }
    fun syncPlaylist(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _busy.value = true
        try { _message.value = "${app.playlists.sync(id)} new items found."; if (app.dao.playlist(id)?.autoAdd == true) DownloadService.start(app) }
        catch (e: Exception) { _message.value = e.message ?: "Playlist check failed." }
        finally { _busy.value = false }
    }
    fun queuePlaylist(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _busy.value = true
        try {
            val q = if (outputKind == OutputKind.MP3) audioQuality.name else videoQuality.name
            val (added, failed) = app.playlists.queueSelected(id, outputKind, q)
            _message.value = "$added playlist items queued; $failed failed analysis."
            if (added > 0) DownloadService.start(app)
        } catch (e: Exception) { _message.value = e.message ?: "Could not queue playlist." }
        finally { _busy.value = false }
    }
    suspend fun playlistPage(id: String, offset: Int) = app.playlists.page(id, offset, 20)
    fun playlistSelect(id: String, videoId: String, selected: Boolean) = viewModelScope.launch(Dispatchers.IO) { app.playlists.select(id, videoId, selected) }
    fun playlistSelectAll(id: String, selected: Boolean) = viewModelScope.launch(Dispatchers.IO) { app.playlists.selectAll(id, selected) }
    fun removePlaylist(id: String) = viewModelScope.launch(Dispatchers.IO) { app.playlists.remove(id); PlaylistScheduler.schedule(app) }
    fun setWifi(value: Boolean) = viewModelScope.launch { app.settings.setWifiOnly(value) }
    fun setAudio(value: String) = viewModelScope.launch { app.settings.setAudio(value) }
    fun setVideo(value: String) = viewModelScope.launch { app.settings.setVideo(value) }
    fun setFolder(value: String?) = viewModelScope.launch { app.settings.setFolder(value) }
    fun setAutoAdd(value: Boolean) = viewModelScope.launch { app.settings.setAutoAdd(value) }
    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        app.cacheDir.resolve("cartube").listFiles()?.forEach { dir ->
            if (tasks.value.none { it.id == dir.name && it.state in listOf("DOWNLOADING", "PROCESSING", "ANALYZING") }) dir.deleteRecursively()
        }
        _message.value = "Unused temporary files cleared."
    }
    fun checkHealth() = viewModelScope.launch(Dispatchers.IO) { val h = app.extractor.checkEngineHealth(); _message.value = "${if (h.ready) "Engine ready" else "Engine error"}: ${h.detail} (yt-dlp ${h.ytDlpVersion ?: "unknown"}, ${h.abi})" }
    fun removeLibrary(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val task = app.downloads.get(id) ?: return@launch
        try { task.fileUri?.let { app.contentResolver.delete(android.net.Uri.parse(it), null, null) }; app.downloads.remove(id); _message.value = "Deleted media." }
        catch (e: Exception) { _message.value = "Could not delete file: ${e.message}" }
    }
}
