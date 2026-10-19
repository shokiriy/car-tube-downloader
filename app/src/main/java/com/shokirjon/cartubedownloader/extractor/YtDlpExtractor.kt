package com.shokirjon.cartubedownloader.extractor

import android.content.Context
import android.os.Build
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.diagnostics.DiagnosticLog
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class YtDlpExtractor(context: Context) : MediaExtractor {
    private val appContext = context.applicationContext
    private val runtime = YtDlpRuntime(appContext)
    private val mutex = Mutex()
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override suspend fun extractInfo(url: String): MediaInfo = mutex.withLock { withContext(Dispatchers.IO) {
        runtime.initialize()
        try {
            val request = YoutubeDLRequest(url).addOption("--ignore-config").addOption("--dump-single-json")
                .addOption("--skip-download").addOption("--no-playlist").addOption("--no-warnings")
            val result = YoutubeDL.execute(request)
            val json = JSONObject(result.out.trim())
            val formats = json.optJSONArray("formats")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { format(it) } }
            }.orEmpty()
            MediaInfo(json.getString("id"), json.optString("title", "Untitled"), json.optString("channel", json.optString("uploader", "")),
                json.optLong("duration").takeIf { it > 0 }, json.textOrNull("thumbnail"), json.optString("webpage_url", url), formats,
                json.optLong("filesize_approx").takeIf { it > 0 })
        } catch (e: Exception) { throw classify(e) }
    } }

    override suspend fun extractPlaylist(url: String, offset: Int, limit: Int): PlaylistPage = mutex.withLock { withContext(Dispatchers.IO) {
        runtime.initialize()
        require(offset >= 0 && limit in 1..50)
        val target = YouTubeUrlParser.parse(url) as? YouTubeTarget.Playlist
            ?: throw EngineException(ErrorCategory.INVALID_URL, "Enter a public playlist URL.")
        try {
            val request = YoutubeDLRequest(PlaylistSource.forExtractor(target)).addOption("--ignore-config").addOption("--flat-playlist")
                .addOption("--yes-playlist")
                .addOption("--dump-single-json").addOption("--playlist-items", "${offset + 1}:${offset + limit}")
                .addOption("--skip-download")
            val json = JSONObject(YoutubeDL.execute(request).out.trim())
            val array = json.optJSONArray("entries")
            val items = if (array == null) emptyList() else (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val id = entry.textOrNull("id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return@mapNotNull null
                val title = entry.textOrNull("title")?.takeUnless { it.equals("[Private video]", true) || it.equals("[Deleted video]", true) } ?: return@mapNotNull null
                PlaylistItem(id, title, "https://www.youtube.com/watch?v=$id", entry.textOrNull("thumbnail"))
            }
            PlaylistPage(target.id, json.optString("title", "Playlist"), json.optInt("playlist_count").takeIf { it > 0 }, items)
        } catch (e: Exception) {
            val cause = classify(e)
            DiagnosticLog.record(appContext, "PLAYLIST ${target.id}", cause.diagnostic ?: e.message)
            if (target.id.startsWith("RD") && cause.category == ErrorCategory.EXTRACTION)
                throw EngineException(ErrorCategory.UNAVAILABLE,
                    "This YouTube Mix could not be loaded. Try its full watch link or a regular public playlist.", cause.diagnostic, cause)
            throw cause
        }
    } }

    override suspend fun download(taskId: String, url: String, kind: OutputKind, quality: Int, workingDirectory: File, onProgress: (TransferProgress) -> Unit): File = mutex.withLock { withContext(Dispatchers.IO) {
        runtime.initialize()
        cancelled.remove(taskId)
        workingDirectory.mkdirs()
        val recent = ArrayDeque<String>()
        try {
            val request = YoutubeDLRequest(url).addOption("--ignore-config").addOption("--no-playlist")
                .addOption("--no-continue").addOption("--newline")
                .addOption("-o", File(workingDirectory, "%(id)s.%(ext)s").absolutePath)
            if (kind == OutputKind.MP3) {
                request.addOption("-f", "bestaudio[ext=m4a]/bestaudio").addOption("-x")
                    .addOption("--audio-format", "mp3").addOption("--audio-quality", "${quality}K")
                    .addOption("--add-metadata").addOption("--embed-thumbnail").addOption("--convert-thumbnails", "jpg")
            } else {
                request.addOption("-f", FormatSelector.mp4(quality)).addOption("--merge-output-format", "mp4")
            }
            // The Android wrapper reports failures from stderr. Redirecting it into stdout
            // discards that diagnostic when execute throws, leaving an unhelpful empty error.
            val response = YoutubeDL.execute(request, taskId, false) { _, _, line ->
                if (recent.size >= 12) recent.removeFirst()
                recent.addLast(line.take(400))
                onProgress(ProgressParser.parse(line))
            }
            if (cancelled.contains(taskId)) throw EngineException(ErrorCategory.CANCELLED, "Download cancelled.")
            if (response.exitCode != 0) throw EngineException(ErrorCategory.EXTRACTION, "Media transfer did not finish.", response.err.take(2000))
            DiagnosticLog.record(appContext, "YTDLP_RETURNED $taskId", recent.joinToString(" | ").takeLast(650))
            val extension = if (kind == OutputKind.MP3) "mp3" else "mp4"
            val videoId = (YouTubeUrlParser.parse(url) as? YouTubeTarget.Video)?.id
                ?: throw EngineException(ErrorCategory.EXTRACTION, "Video ID is unavailable.")
            DownloadedFileSelector.finalFile(workingDirectory, videoId, extension)
                ?: throw EngineException(ErrorCategory.CONVERSION, "The media engine did not produce a complete $extension file.",
                    "yt-dlp exit=${response.exitCode}; files=${workingDirectory.listFiles()?.joinToString { "${it.name}:${it.length()}" }}; output=${recent.joinToString(" | ")}".take(2500))
        } catch (e: Exception) {
            if (cancelled.contains(taskId)) throw EngineException(ErrorCategory.CANCELLED, "Download cancelled.")
            val detail = if (e.message.isNullOrBlank() && recent.isNotEmpty())
                Exception(recent.joinToString(" | ").takeLast(1500), e) else e
            throw classify(detail)
        } finally { cancelled.remove(taskId) }
    } }

    override fun cancel(taskId: String) { cancelled.add(taskId); YoutubeDL.destroyProcessById(taskId) }

    override suspend fun checkEngineHealth(): EngineHealth = withContext(Dispatchers.IO) {
        try {
            runtime.initialize()
            val response = YoutubeDL.execute(YoutubeDLRequest(emptyList<String>()).addOption("--version"))
            EngineHealth(response.exitCode == 0, "Bundled binaries and EJS files are present. A live extraction is still needed to verify YouTube compatibility.", response.out.trim(), Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        } catch (e: Exception) { EngineHealth(false, e.message ?: "Engine failed", null, Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown") }
    }

    private fun JSONObject.textOrNull(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
    private fun format(json: JSONObject): MediaFormat = MediaFormat(json.optString("format_id"), json.textOrNull("ext"),
        json.optInt("height").takeIf { it > 0 }, json.textOrNull("vcodec")?.takeUnless { it == "none" },
        json.textOrNull("acodec")?.takeUnless { it == "none" },
        json.optLong("filesize").takeIf { it > 0 } ?: json.optLong("filesize_approx").takeIf { it > 0 })
    private fun classify(e: Exception): EngineException {
        if (e is EngineException) return e
        val detail = e.message.orEmpty()
        val lower = detail.lowercase()
        val category = when {
            "403" in lower -> ErrorCategory.ACCESS_RESTRICTED
            "429" in lower || "rate limit" in lower -> ErrorCategory.RATE_LIMITED
            "private" in lower || "sign in" in lower || "login" in lower || "age-restricted" in lower -> ErrorCategory.ACCESS_RESTRICTED
            "unavailable" in lower || "not available" in lower -> ErrorCategory.UNAVAILABLE
            "ejs" in lower -> ErrorCategory.EJS_MISSING
            "javascript" in lower || "quickjs" in lower -> ErrorCategory.JS_RUNTIME_MISSING
            "ffmpeg" in lower -> ErrorCategory.CONVERSION
            "dns" in lower || "ssl" in lower || "timed out" in lower || "network" in lower -> ErrorCategory.NETWORK
            else -> ErrorCategory.EXTRACTION
        }
        val friendly = when (category) {
            ErrorCategory.ACCESS_RESTRICTED -> "This item is restricted or the media server denied access."
            ErrorCategory.RATE_LIMITED -> "The service is rate limiting requests. Retry later."
            ErrorCategory.UNAVAILABLE -> "This item is unavailable."
            ErrorCategory.EJS_MISSING, ErrorCategory.JS_RUNTIME_MISSING -> "The JavaScript extraction runtime is unavailable."
            ErrorCategory.NETWORK -> "Network connection failed. Check Wi-Fi and retry."
            ErrorCategory.CONVERSION -> "FFmpeg could not finish the media conversion."
            else -> "Media extraction failed. This item may no longer be supported."
        }
        return EngineException(category, friendly, detail.take(1000), e)
    }
}
