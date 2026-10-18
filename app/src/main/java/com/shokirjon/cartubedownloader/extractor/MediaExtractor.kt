package com.shokirjon.cartubedownloader.extractor

import com.shokirjon.cartubedownloader.core.*
import java.io.File

interface MediaExtractor {
    suspend fun extractInfo(url: String): MediaInfo
    suspend fun extractPlaylist(url: String, offset: Int, limit: Int): PlaylistPage
    suspend fun listFormats(url: String): List<MediaFormat> = extractInfo(url).formats
    suspend fun download(taskId: String, url: String, kind: OutputKind, quality: Int, workingDirectory: File, onProgress: (TransferProgress) -> Unit): File
    fun cancel(taskId: String)
    suspend fun checkEngineHealth(): EngineHealth
}
