package com.shokirjon.cartubedownloader.core

enum class OutputKind { MP3, MP4 }
enum class AudioQuality(val kbps: Int) { K128(128), K192(192), K320(320) }
enum class VideoQuality(val height: Int) { P360(360), P480(480), P720(720) }
enum class TaskState { PENDING, ANALYZING, QUEUED, DOWNLOADING, PROCESSING, PAUSED, COMPLETED, FAILED, CANCELLED, INTERRUPTED }
enum class ErrorCategory { INVALID_URL, UNSUPPORTED_URL, NETWORK, RATE_LIMITED, ACCESS_RESTRICTED, UNAVAILABLE, EXTRACTION, JS_RUNTIME_MISSING, EJS_MISSING, UNSUPPORTED_ABI, ENGINE, CODEC, STORAGE_FULL, STORAGE_PERMISSION, FILE_WRITE, CONVERSION, CANCELLED, INTERRUPTED, UNKNOWN }

data class MediaFormat(val id: String, val ext: String?, val height: Int?, val vcodec: String?, val acodec: String?, val size: Long?)
data class MediaInfo(val id: String, val title: String, val channel: String, val duration: Long?, val thumbnail: String?, val url: String, val formats: List<MediaFormat>, val size: Long?)
data class PlaylistItem(val id: String, val title: String, val url: String, val thumbnail: String?)
data class PlaylistPage(val id: String, val title: String, val count: Int?, val items: List<PlaylistItem>)
data class TransferProgress(val percent: Float?, val downloaded: Long?, val total: Long?, val speed: Long?, val etaSeconds: Long?, val stage: String?)
data class EngineHealth(val ready: Boolean, val detail: String, val ytDlpVersion: String?, val abi: String)
class EngineException(val category: ErrorCategory, override val message: String, val diagnostic: String? = null, cause: Throwable? = null) : Exception(message, cause)
