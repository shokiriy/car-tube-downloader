package com.shokirjon.cartubedownloader.extractor

import java.io.File

/** yt-dlp keeps individual stream files beside the merged file until post-processing completes. */
internal object DownloadedFileSelector {
    fun finalFile(directory: File, videoId: String, extension: String): File? {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        require(extension == "mp3" || extension == "mp4")
        return File(directory, "$videoId.$extension").takeIf { it.isFile && it.length() > 1024L }
    }
}
