package com.shokirjon.cartubedownloader.download

import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.CarTubeDao
import com.shokirjon.cartubedownloader.db.DownloadTask
import java.util.UUID

sealed interface EnqueueResult {
    data class Added(val task: DownloadTask) : EnqueueResult
    data class Duplicate(val task: DownloadTask) : EnqueueResult
}

class DownloadRepository(private val dao: CarTubeDao) {
    val tasks = dao.tasks()
    suspend fun enqueue(info: MediaInfo, originalUrl: String, kind: OutputKind, quality: String, playlistId: String? = null): EnqueueResult {
        val target = YouTubeUrlParser.parse(originalUrl) as? YouTubeTarget.Video ?: throw IllegalArgumentException("A single video URL is required.")
        val previous = dao.latest(target.canonicalUrl, kind.name, quality)
        if (previous != null && previous.state !in setOf("FAILED", "CANCELLED", "PAUSED", "INTERRUPTED")) return EnqueueResult.Duplicate(previous)
        val now = System.currentTimeMillis()
        val task = DownloadTask(UUID.randomUUID().toString(), originalUrl, target.canonicalUrl, info.id, playlistId ?: target.playlistId,
            info.title, info.channel, info.thumbnail, info.duration, kind.name, quality, TaskState.PENDING.name, 0f, 0, info.size,
            null, null, "Queued", null, null, null, 0, dao.nextOrder(), now, now)
        dao.insert(task)
        return EnqueueResult.Added(task)
    }
    suspend fun get(id: String) = dao.task(id)
    suspend fun pending() = dao.pending()
    suspend fun recover() = dao.interruptActive(System.currentTimeMillis())
    suspend fun state(id: String, state: TaskState, stage: String? = null, category: ErrorCategory? = null, error: String? = null) {
        val old = dao.task(id) ?: return
        dao.update(old.copy(state = state.name, stage = stage ?: state.name.lowercase().replaceFirstChar(Char::uppercase),
            errorCategory = category?.name, errorMessage = error, updatedAt = System.currentTimeMillis()))
    }
    suspend fun progress(id: String, value: TransferProgress) {
        val old = dao.task(id) ?: return
        if (old.state in setOf("PAUSED", "CANCELLED", "COMPLETED", "FAILED")) return
        dao.update(old.copy(state = if (value.stage != null) "PROCESSING" else "DOWNLOADING",
            progress = value.percent ?: old.progress, downloadedBytes = value.downloaded ?: old.downloadedBytes,
            totalBytes = value.total ?: old.totalBytes, speed = value.speed, etaSeconds = value.etaSeconds,
            stage = if (value.stage != null) "Processing: ${value.stage.take(100)}" else "Downloading stream", updatedAt = System.currentTimeMillis()))
    }
    suspend fun complete(id: String, uri: String, size: Long) {
        val old = dao.task(id) ?: return
        dao.update(old.copy(state = "COMPLETED", progress = 100f, downloadedBytes = size, totalBytes = size,
            speed = null, etaSeconds = 0, stage = "Saved", fileUri = uri, errorCategory = null, errorMessage = null, updatedAt = System.currentTimeMillis()))
    }
    suspend fun retry(id: String) {
        val old = dao.task(id) ?: return
        dao.update(old.copy(state = "PENDING", progress = 0f, downloadedBytes = 0, speed = null, etaSeconds = null,
            stage = "Queued", errorCategory = null, errorMessage = null, retryCount = old.retryCount + 1, updatedAt = System.currentTimeMillis()))
    }
    suspend fun move(id: String, delta: Int) {
        val tasks = dao.allTasks().filter { it.state in setOf("PENDING", "QUEUED") }; val index = tasks.indexOfFirst { it.id == id }; val other = index + delta
        if (index < 0 || other !in tasks.indices) return
        val now = System.currentTimeMillis()
        dao.update(tasks[index].copy(queueOrder = tasks[other].queueOrder, updatedAt = now))
        dao.update(tasks[other].copy(queueOrder = tasks[index].queueOrder, updatedAt = now))
    }
    suspend fun remove(id: String) = dao.remove(id)
    suspend fun clearCompleted() = dao.clearCompleted()
    suspend fun clearFailed() = dao.clearFailed()
}
